package com.aira.api.assist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text helpers for filing quotes: plain text, passages, ranking, verbatim checks, diff. No I/O. */
final class FilingText {
    static final int MAX_PASSAGE = 400;
    static final int MAX_PASSAGES = 6000;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ROW_END = Pattern.compile("(?i)</(?:tr|p|div|li|table|title|h[1-6])\\s*>|<br\\s*/?>");
    private static final Pattern CELL_END = Pattern.compile("(?i)</(?:td|th|te|tu)\\s*>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?[0-9a-fA-F]{1,6});");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP = Set.of("무엇", "뭐야", "어떻게", "얼마", "알려줘", "알려주세요", "있나요", "있어",
            "주세요", "대해", "대한", "관련", "내용", "공시", "원문", "해줘", "설명", "어디", "언제", "누구", "무슨");
    private static final List<String> SUFFIXES = List.of("입니다", "인가요", "에서", "으로", "까지", "부터", "이란", "라는",
            "에는", "에도", "은", "는", "이", "가", "을", "를", "의", "에", "도", "로", "과", "와");

    private FilingText() {}

    record Pair(String before, String after) {}
    record Diff(List<Pair> changes, int total, boolean truncated) {}

    /** DART document XML to plain text: rows become lines, cells are joined with " | ". */
    static String toPlain(String xml) {
        if (xml == null) return "";
        String text = ROW_END.matcher(xml).replaceAll("\n");
        text = CELL_END.matcher(text).replaceAll(" | ");
        text = TAG.matcher(text).replaceAll(" ");
        return unescape(text);
    }

    static String unescape(String text) {
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            String replacement = " ";
            try {
                int point = code.startsWith("x") || code.startsWith("X")
                        ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
                if (Character.isValidCodePoint(point)) replacement = new String(Character.toChars(point));
            } catch (NumberFormatException ignored) {
                // keep the space
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString().replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    /** Removes control characters and angle brackets and collapses spaces. */
    static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\p{Cntrl}<>]", " ").replaceAll("[\\s\\u00A0\\u3000]+", " ").trim();
    }

    static List<String> passages(String plain) {
        List<String> out = new ArrayList<>();
        if (plain == null) return out;
        for (String line : plain.split("\n")) {
            String text = clean(line).replaceAll("(\\|\\s*){2,}", "| ").replaceAll("^[|\\s]+|[|\\s]+$", "");
            if (text.length() < 8) continue;
            if (text.length() <= MAX_PASSAGE) {
                out.add(text);
            } else {
                StringBuilder current = new StringBuilder();
                for (String sentence : text.split("(?<=[.!?])\\s+")) {
                    for (String piece : hardCut(sentence)) {
                        if (current.length() > 0 && current.length() + 1 + piece.length() > MAX_PASSAGE) {
                            out.add(current.toString());
                            current.setLength(0);
                        }
                        if (current.length() > 0) current.append(' ');
                        current.append(piece);
                    }
                }
                if (current.length() >= 8) out.add(current.toString());
            }
            if (out.size() >= MAX_PASSAGES) break;
        }
        return out;
    }

    private static List<String> hardCut(String sentence) {
        List<String> pieces = new ArrayList<>();
        String rest = sentence;
        while (rest.length() > MAX_PASSAGE) {
            int cut = rest.lastIndexOf(' ', MAX_PASSAGE);
            if (cut < MAX_PASSAGE / 2) cut = MAX_PASSAGE;
            pieces.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        if (!rest.isEmpty()) pieces.add(rest);
        return pieces;
    }

    static List<String> questionTokens(String question) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(question == null ? "" : question.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (STOP.contains(token)) continue;
            for (String suffix : SUFFIXES) {
                if (token.endsWith(suffix) && token.length() - suffix.length() >= 2) {
                    token = token.substring(0, token.length() - suffix.length());
                    break;
                }
            }
            if (token.length() >= 2 && !STOP.contains(token)) tokens.add(token);
        }
        return new ArrayList<>(tokens);
    }

    /** Indexes of the best matching passages, best first. Empty when nothing matches well enough. */
    static final List<String> CAUSE_WORDS = List.of("원인", "이유", "왜", "때문");
    static final List<String> CAUSE_MARKERS = List.of("때문", "영향", "기인", "인해", "인한", "따라", "효과", "덕분", "확대", "축소", "개선", "악화", "상승", "하락");

    /** 원인 질문에서 "무엇의" 원인인지 가리키는 낱말. 이 낱말이 맞아야 그 문단이 질문과 관련 있다고 본다. */
    static final List<String> SUBJECT_WORDS = List.of("매출", "영업이익", "순이익", "이익", "손실", "배당", "현금", "투자", "부채", "차입",
            "원가", "비용", "수요", "가격", "판매", "실적", "점유율", "제품", "주가", "수익", "마진");
    /** 어디에나 나오는 변화 낱말. 점수를 적게 준다(「환율변동」 같은 문단이 끼어들지 않게). */
    static final List<String> GENERIC_WORDS = List.of("증가", "감소", "변동", "상승", "하락", "개선", "악화", "시장", "환경", "요인", "정책", "주요", "내용", "결정", "기준");
    /** 회계 처리 방법을 설명하는 문단(원인 설명이 아니다). */
    static final List<String> POLICY_MARKERS = List.of("회계정책", "인식하고", "인식합니다", "인식하며", "측정합니다", "측정하고", "기대값", "공정가치",
            "손상검사", "추정치", "할인율", "기업회계기준서", "적용하고", "회계기준", "기준서", "범주로 분류", "공시 요건", "중간합계");
    /** 말하듯 쓴 변화 표현을 공시에 쓰이는 낱말로 바꿔 함께 찾는다(「늘었어」 → 증가). */
    static final List<String[]> SPOKEN_CHANGE = List.of(
            new String[] {"늘", "증가"}, new String[] {"줄", "감소"}, new String[] {"올랐", "상승"}, new String[] {"오른", "상승"},
            new String[] {"떨어", "하락"}, new String[] {"내렸", "하락"}, new String[] {"좋아", "개선"}, new String[] {"나빠", "악화"});
    static final List<String> COMPARE_MARKERS = List.of("전년", "전기 대비", "전년 동기", "대비");

    static List<Integer> rank(List<String> passages, String question, int top) {
        List<String> tokens = new ArrayList<>(questionTokens(question));
        boolean causal = CAUSE_WORDS.stream().anyMatch(word -> question != null && question.contains(word));
        // 원인을 묻는 질문: "원인·이유" 같은 낱말은 원문에 잘 안 쓰이므로 빼고, 대신 원인을 말하는 표현이 있는 문단에 점수를 더 준다.
        if (causal) tokens.removeIf(token -> CAUSE_WORDS.stream().anyMatch(token::startsWith) || token.matches("\\d{4}년?|대비"));
        if (causal) {
            for (String[] pair : SPOKEN_CHANGE) {
                if (tokens.removeIf(token -> token.startsWith(pair[0]))) tokens.add(pair[1]);
            }
        }
        if (tokens.isEmpty()) return List.of();
        List<String> subjects = tokens.stream().filter(t -> SUBJECT_WORDS.stream().anyMatch(t::contains)).toList();
        int need = causal ? 1 : tokens.size() >= 3 ? 2 : 1;
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < passages.size(); i++) {
            String lower = passages.get(i).toLowerCase();
            int score = 0;
            if (!causal) {
                for (String token : tokens) if (lower.contains(token)) score++;
                if (score >= need) scored.add(new int[] {i, score});
                continue;
            }
            int hits = 0;
            boolean subjectHit = subjects.isEmpty();
            for (String token : tokens) {
                if (!lower.contains(token)) continue;
                hits++;
                boolean subject = SUBJECT_WORDS.stream().anyMatch(token::contains);
                if (subject) subjectHit = true;
                score += subject ? 4 : GENERIC_WORDS.stream().anyMatch(token::startsWith) ? 1 : 2;
            }
            if (hits == 0 || !subjectHit) continue;
            long markers = CAUSE_MARKERS.stream().filter(lower::contains).count();
            score += (int) Math.min(3, markers) * 2;
            if (COMPARE_MARKERS.stream().anyMatch(lower::contains)) score += 1;
            if (POLICY_MARKERS.stream().anyMatch(lower::contains)) score -= 6;
            if (score >= 3) scored.add(new int[] {i, score});
        }
        scored.sort(Comparator.<int[]>comparingInt(item -> -item[1]).thenComparingInt(item -> item[0]));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(top, scored.size()); i++) out.add(scored.get(i)[0]);
        return out;
    }

    static String normalize(String text) {
        return clean(text);
    }

    /** True only when the quote is a literal piece of the passage (spaces collapsed). */
    static boolean verbatim(String quote, String passage) {
        String normalized = normalize(quote);
        return normalized.length() >= 10 && passage != null && passage.contains(normalized);
    }

    static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        Matcher matcher = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?").matcher(text == null ? "" : text);
        while (matcher.find()) out.add(matcher.group().replace(",", ""));
        return out;
    }

    static double similarity(String a, String b) {
        Set<String> left = words(a);
        Set<String> right = words(b);
        if (left.isEmpty() || right.isEmpty()) return 0;
        int common = 0;
        for (String word : left) if (right.contains(word)) common++;
        return (double) common / (left.size() + right.size() - common);
    }

    private static Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        Matcher matcher = WORD.matcher(text.toLowerCase());
        while (matcher.find()) out.add(matcher.group());
        return out;
    }

    /** Passage-level difference between an original and an amended filing. Pure string comparison. */
    static Diff diff(List<String> original, List<String> amended, int cap) {
        Set<String> inAmended = new HashSet<>(amended);
        Set<String> inOriginal = new HashSet<>(original);
        List<String> removed = original.stream().filter(p -> !inAmended.contains(p)).distinct().limit(300).toList();
        List<String> added = amended.stream().filter(p -> !inOriginal.contains(p)).distinct().limit(300).toList();
        boolean[] used = new boolean[added.size()];
        List<Pair> paired = new ArrayList<>();
        List<Pair> removedOnly = new ArrayList<>();
        for (String before : removed) {
            int best = -1;
            double bestScore = 0.4;
            for (int i = 0; i < added.size(); i++) {
                if (used[i]) continue;
                double score = similarity(before, added.get(i));
                if (score >= bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            if (best >= 0) {
                used[best] = true;
                paired.add(new Pair(before, added.get(best)));
            } else {
                removedOnly.add(new Pair(before, ""));
            }
        }
        List<Pair> addedOnly = new ArrayList<>();
        for (int i = 0; i < added.size(); i++) if (!used[i]) addedOnly.add(new Pair("", added.get(i)));
        List<Pair> all = new ArrayList<>(paired);
        all.addAll(removedOnly);
        all.addAll(addedOnly);
        int total = all.size();
        return new Diff(all.size() > cap ? all.subList(0, cap) : all, total, total > cap);
    }
}
