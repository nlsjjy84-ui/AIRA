package com.aira.api.stock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * OpenDART XBRL 원본 파일(zip)에서 연구개발비를 읽는다. 재무제표 주석까지 XBRL로 낸 회사만 값이 있다.
 * 이름에 ResearchAndDevelopment가 들어간 비용 항목만 쓰고(개발비 자산·비율 제외), 1년 기간·연결(없으면 차원 없음, 그다음 별도) 값을 고른다.
 */
final class XbrlResearchReader {
    record Found(BigDecimal amount, String element, String basis) {}

    private record Context(LocalDate start, LocalDate end, List<String> members) {}

    private record Candidate(String element, String contextRef, String value) {}

    private XbrlResearchReader() {}

    static Found read(byte[] zip, int fiscalYear) {
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".xbrl") || (name.endsWith(".xml") && !name.contains("_lab") && !name.contains("_pre")
                        && !name.contains("_cal") && !name.contains("_def"))) {
                    Found found = readInstance(in, fiscalYear);
                    if (found != null) return found;
                }
            }
        } catch (IOException | XMLStreamException | RuntimeException e) {
            return null;
        }
        return null;
    }

    static boolean isResearchExpense(String localName) {
        String n = localName.toLowerCase(Locale.ROOT);
        if (!n.contains("researchanddevelopment")) return false;
        return !(n.contains("intangible") || n.contains("capitali") || n.contains("ratio") || n.contains("percent")
                || n.contains("asset") || n.contains("member") || n.contains("axis") || n.contains("abstract"));
    }

    private static Found readInstance(InputStream stream, int fiscalYear) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = factory.createXMLStreamReader(new java.io.FilterInputStream(stream) {
            @Override public void close() { /* zip 스트림은 바깥에서 닫는다 */ }
        });
        Map<String, Context> contexts = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        String ctxId = null;
        LocalDate start = null, end = null;
        List<String> members = new ArrayList<>();
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                String local = r.getLocalName();
                switch (local) {
                    case "context" -> { ctxId = r.getAttributeValue(null, "id"); start = null; end = null; members = new ArrayList<>(); }
                    case "startDate" -> start = date(r.getElementText());
                    case "endDate" -> end = date(r.getElementText());
                    case "instant" -> end = date(r.getElementText());
                    case "explicitMember" -> members.add(r.getElementText().trim());
                    default -> {
                        if (ctxId == null && isResearchExpense(local)) {
                            String ref = r.getAttributeValue(null, "contextRef");
                            String text = r.getElementText();
                            if (ref != null && text != null) candidates.add(new Candidate(r.getPrefix() + "_" + local, ref, text.trim()));
                        }
                    }
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT && "context".equals(r.getLocalName())) {
                if (ctxId != null) contexts.put(ctxId, new Context(start, end, List.copyOf(members)));
                ctxId = null;
            }
        }
        Found best = null;
        int bestRank = Integer.MAX_VALUE;
        for (var c : candidates) {
            Context ctx = contexts.get(c.contextRef());
            if (ctx == null || ctx.start() == null || ctx.end() == null) continue;
            if (ctx.end().getYear() != fiscalYear || ChronoUnit.DAYS.between(ctx.start(), ctx.end()) < 300) continue;
            String basis;
            int rank;
            if (ctx.members().isEmpty()) { basis = "CFS"; rank = 2; }
            else if (ctx.members().size() == 1 && ctx.members().get(0).endsWith("ConsolidatedMember")) { basis = "CFS"; rank = 1; }
            else if (ctx.members().size() == 1 && ctx.members().get(0).endsWith("SeparateMember")) { basis = "OFS"; rank = 3; }
            else continue; // 사업부문별처럼 쪼갠 값은 쓰지 않는다
            if (c.element().endsWith("_ResearchAndDevelopmentExpense")) rank -= 10; // IFRS 표준 이름을 먼저
            BigDecimal amount;
            try { amount = new BigDecimal(c.value().replace(",", "")); } catch (NumberFormatException e) { continue; }
            if (rank < bestRank) { bestRank = rank; best = new Found(amount.abs(), c.element(), basis); }
        }
        return best;
    }

    private static LocalDate date(String text) {
        try { return LocalDate.parse(text.trim().substring(0, 10)); } catch (RuntimeException e) { return null; }
    }
}
