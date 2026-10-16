package com.aira.api.stock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * OpenDART 공시서류 원본(document.xml, zip)에서 표만 꺼내 읽는다.
 * 사업보고서 「연구개발활동」의 연구개발비용 표, 「영업(잠정)실적」 공정공시의 실적 표처럼 양식이 정해진 표만 쓴다.
 */
final class DartDocumentReader {
    private DartDocumentReader() {}

    /** 표 하나. unit은 표 안이나 바로 앞에 적힌 「단위 : …」 문구(없으면 null). */
    record Table(String unit, List<List<String>> rows) {}

    record Research(BigDecimal amount, String unitText, String label) {}

    record Preliminary(String period, String unitText, Map<String, BigDecimal> values, Map<String, BigDecimal> cumulative) {}

    private static final int MAX_ENTRY_BYTES = 40 * 1024 * 1024;
    private static final Pattern TABLE = Pattern.compile("<table\\b[^>]*>(.*?)</table>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<(td|th|te|tu)\\b[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNIT = Pattern.compile("단위\\s*[:：]\\s*([^)\\]\\n|]{1,20})");
    private static final Pattern PERIOD = Pattern.compile("\\((\\d{2})\\.(\\d)Q\\)");

    /** zip 안의 모든 xml·html 문서에서 표를 순서대로 꺼낸다. */
    static List<Table> tables(byte[] zip) {
        List<Table> out = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || !(name.endsWith(".xml") || name.endsWith(".htm") || name.endsWith(".html"))) continue;
                out.addAll(tablesOf(decode(readLimited(in))));
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    static List<Table> tablesOf(String text) {
        List<Table> out = new ArrayList<>();
        Matcher t = TABLE.matcher(text);
        int last = 0;
        String carriedUnit = null;
        while (t.find()) {
            String before = plain(text.substring(last, t.start()));
            Matcher u = UNIT.matcher(before);
            while (u.find()) carriedUnit = u.group(1).trim();
            List<List<String>> rows = new ArrayList<>();
            Matcher r = ROW.matcher(t.group(1));
            while (r.find()) {
                List<String> cells = new ArrayList<>();
                Matcher c = CELL.matcher(r.group(1));
                while (c.find()) cells.add(plain(c.group(2)));
                if (!cells.isEmpty()) rows.add(cells);
            }
            String unit = null;
            for (var row : rows) for (var cell : row) {
                Matcher m = UNIT.matcher(cell);
                if (m.find()) unit = m.group(1).trim();
            }
            if (unit == null) unit = carriedUnit;
            else carriedUnit = unit;
            out.add(new Table(unit, rows));
            last = t.end();
        }
        return out;
    }

    /** 사업보고서 「연구개발비용」 표의 총계(정부보조금 차감 전). 없으면 계. 첫 숫자 칸이 당기다. */
    static Research research(List<Table> tables) {
        for (String wanted : List.of("연구개발비용총계", "연구개발비총계", "연구개발비용합계", "연구개발비합계", "연구개발비용계", "연구개발비계")) {
            for (Table table : tables) {
                for (var row : table.rows()) {
                    if (row.isEmpty() || !squash(row.get(0)).equals(wanted)) continue;
                    BigDecimal value = firstNumber(row.subList(1, row.size()));
                    BigDecimal factor = factor(table.unit());
                    if (value == null || factor == null) continue;
                    return new Research(value.multiply(factor), table.unit(), row.get(0).trim());
                }
            }
        }
        return null;
    }

    /** 「영업(잠정)실적」 표: 항목마다 당해실적·누계실적 두 줄. 첫 숫자 칸이 이번 분기다. */
    static Preliminary preliminary(List<Table> tables) {
        for (Table table : tables) {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            Map<String, BigDecimal> cumulative = new LinkedHashMap<>();
            String period = null;
            String item = null;
            BigDecimal factor = factor(table.unit());
            for (var row : table.rows()) {
                for (var cell : row) {
                    Matcher p = PERIOD.matcher(cell);
                    if (period == null && p.find()) period = "20" + p.group(1) + "-Q" + p.group(2);
                }
                int kind = -1;
                for (int i = 0; i < row.size(); i++) {
                    String cell = squash(row.get(i));
                    String code = ITEMS.get(cell);
                    if (code != null) item = code;
                    if (cell.equals("당해실적") || cell.equals("누계실적")) {
                        kind = cell.equals("당해실적") ? 0 : 1;
                        BigDecimal value = firstNumber(row.subList(i + 1, row.size()));
                        if (item != null && value != null && factor != null) (kind == 0 ? values : cumulative).put(item, value.multiply(factor));
                        break;
                    }
                }
            }
            if (!values.isEmpty() || !cumulative.isEmpty()) return new Preliminary(period, table.unit(), values, cumulative);
        }
        return null;
    }

    record MixItem(String name, BigDecimal amount, BigDecimal share) {}

    record SalesMix(String unitText, List<MixItem> items) {}

    private static final Pattern MIX_TITLE = Pattern.compile("<title[^>]*>[^<]*주요\\s*제품[^<]*</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_TITLE = Pattern.compile("<title[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PCT = Pattern.compile("^\\(?\\s*([△-]?[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*%?\\s*\\)?$");
    private static final Pattern AMOUNT_WITH_PCT = Pattern.compile("^([△-]?[0-9][0-9,]*)\\s*\\(\\s*([△-]?[0-9][0-9.]*)\\s*%\\s*\\)$");
    private static final Pattern SKIP_NAME = Pattern.compile("^(합\\s*계|총\\s*계|계|소\\s*계|매출\\s*총\\s*합\\s*계|총\\s*합\\s*계|\\(?내수.*|\\(?수출.*|매출\\s*합계)$");
    private static final Pattern TYPE_WORD = Pattern.compile("^(제품|상품|용역|제품\\s*외|제품\\s*매출|상품\\s*매출|기타\\s*매출|용역\\s*매출|매출유형|-)$");

    /** 사업보고서 「주요 제품 및 서비스」 표: 사업부문(또는 품목)별 매출액과 비중. 회사마다 표 모양이 달라 숫자 칸을 오른쪽에서 찾는다. */
    static SalesMix salesMix(byte[] zip) {
        for (String doc : documents(zip)) {
            Matcher m = MIX_TITLE.matcher(doc);
            if (!m.find()) continue;
            Matcher next = NEXT_TITLE.matcher(doc);
            int end = next.find(m.end()) ? next.start() : Math.min(doc.length(), m.end() + 300_000);
            for (Table table : tablesOf(doc.substring(m.start(), end))) {
                SalesMix mix = mixOf(table);
                if (mix != null) return mix;
            }
        }
        return null;
    }

    static SalesMix mixOf(Table table) {
        boolean mentionsSales = table.rows().stream().anyMatch(r -> r.stream().anyMatch(c -> c.contains("매출")));
        if (!mentionsSales) return null;
        BigDecimal factor = factor(table.unit());
        // 이름 칸: 머리글에서 「품목」이 있으면 그 칸, 없으면 「부문」 칸. 위 칸과 합쳐진(rowspan) 줄은 왼쪽 칸이 빠져 있으니 오른쪽 맞춤으로 옮긴다.
        int nameCol = -1;
        for (var row : table.rows()) {
            int item = -1, part = -1;
            for (int i = 0; i < row.size(); i++) {
                String c = squash(row.get(i));
                if (item < 0 && c.matches("품목|품명|제품명|제품군")) item = i;
                if (part < 0 && c.matches("(사업)?부문|구분")) part = i;
            }
            if (item >= 0 || part >= 0) { nameCol = item >= 0 ? item : part; break; }
        }
        int width = table.rows().stream().filter(r -> r.stream().anyMatch(DartDocumentReader::isFigure)).mapToInt(List::size).max().orElse(0);
        List<MixItem> items = new ArrayList<>();
        for (var row : table.rows()) {
            BigDecimal amount = null, share = null;
            int firstNumber = -1;
            for (int i = 0; i < row.size(); i++) {
                String c = row.get(i).trim().replace(" ", "");
                Matcher both = AMOUNT_WITH_PCT.matcher(c);
                if (both.matches()) { amount = number(both.group(1)); share = number(both.group(2)); if (firstNumber < 0) firstNumber = i; continue; }
                if (c.endsWith("%")) { Matcher pm = PCT.matcher(c); if (pm.matches()) { share = number(pm.group(1)); if (firstNumber < 0) firstNumber = i; continue; } }
                BigDecimal n = number(c);
                if (n == null) continue;
                if (firstNumber < 0) firstNumber = i;
                if (amount == null) amount = n;
                else if (share == null && n.abs().compareTo(new BigDecimal("100.5")) <= 0 && c.contains(".")) share = n;
            }
            if (firstNumber <= 0 || (amount == null && share == null)) continue;
            String name = null;
            if (nameCol >= 0) {
                int idx = nameCol - (width - row.size());
                if (idx >= 0 && idx < firstNumber) name = row.get(idx).trim();
            }
            if (name == null || name.isEmpty() || TYPE_WORD.matcher(squash(name)).matches()) {
                name = null;
                for (int i = 0; i < firstNumber; i++) {
                    String c = row.get(i).trim();
                    if (!c.isEmpty() && c.length() <= 40 && !TYPE_WORD.matcher(squash(c)).matches()) { name = c; break; }
                }
            }
            if (name == null || SKIP_NAME.matcher(squash(name)).matches()) continue;
            items.add(new MixItem(name.replaceAll("\\s+", " "), amount == null || factor == null ? null : amount.multiply(factor), share));
        }
        if (items.isEmpty()) return null;
        // 비중이 비어 있으면 매출액 합계로 계산한다.
        BigDecimal total = items.stream().map(MixItem::amount).filter(x -> x != null && x.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<MixItem> out = new ArrayList<>();
        for (var it : items) {
            BigDecimal share = it.share();
            if (share == null && it.amount() != null && total.signum() > 0)
                share = it.amount().multiply(BigDecimal.valueOf(100)).divide(total, 1, java.math.RoundingMode.HALF_UP);
            out.add(new MixItem(it.name(), it.amount(), share));
        }
        return new SalesMix(table.unit(), out.size() > 12 ? out.subList(0, 12) : out);
    }

    private static boolean isFigure(String cell) {
        String c = cell.trim().replace(" ", "");
        return number(c) != null || AMOUNT_WITH_PCT.matcher(c).matches() || (c.endsWith("%") && PCT.matcher(c).matches());
    }

    private static List<String> documents(byte[] zip) {
        List<String> out = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || !(name.endsWith(".xml") || name.endsWith(".htm") || name.endsWith(".html"))) continue;
                out.add(decode(readLimited(in)));
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static final Map<String, String> ITEMS = Map.of(
            "매출액", "REVENUE", "영업수익", "REVENUE", "영업이익", "OPERATING_INCOME",
            "법인세비용차감전계속사업이익", "PRETAX_INCOME", "당기순이익", "NET_INCOME",
            "지배기업소유주지분순이익", "CONTROLLING_NET_INCOME");

    /** 「백만원」「억원」「조원」「천원」「원」 → 원 단위로 바꿀 배수. 모르는 단위면 null. */
    static BigDecimal factor(String unit) {
        if (unit == null) return null;
        String u = squash(unit);
        if (u.startsWith("조원")) return new BigDecimal("1000000000000");
        if (u.startsWith("억원")) return new BigDecimal("100000000");
        if (u.startsWith("백만원")) return new BigDecimal("1000000");
        if (u.startsWith("천원")) return new BigDecimal("1000");
        if (u.startsWith("원")) return BigDecimal.ONE;
        return null;
    }

    static BigDecimal firstNumber(List<String> cells) {
        for (String cell : cells) {
            BigDecimal n = number(cell);
            if (n != null) return n;
        }
        return null;
    }

    static BigDecimal number(String cell) {
        if (cell == null) return null;
        String s = cell.replace(",", "").replace(" ", "").trim();
        boolean negative = s.startsWith("△") || s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"));
        s = s.replaceAll("^[△(\\-]+|\\)$", "");
        if (!s.matches("[0-9]+(\\.[0-9]+)?")) return null;
        BigDecimal value = new BigDecimal(s);
        return negative ? value.negate() : value;
    }

    private static String squash(String s) { return s == null ? "" : s.replaceAll("[\\s\\u00a0]+", ""); }

    private static String plain(String html) {
        return html.replaceAll("(?is)<br\\s*/?>", "\n").replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&cr;", "\n").replaceAll("[ \\t\\u00a0]+", " ").trim();
    }

    private static String decode(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 400), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        Charset cs = head.contains("euc-kr") || head.contains("ks_c_5601") ? Charset.forName("MS949") : StandardCharsets.UTF_8;
        return new String(bytes, cs);
    }

    private static byte[] readLimited(ZipInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_ENTRY_BYTES) throw new IOException("document entry too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
