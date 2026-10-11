package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FilingTextTests {
    private static final String XML = "<DOCUMENT><BODY><P>1. 계약 내용</P><TABLE><TR><TD>계약금액</TD><TD>1,234억원</TD></TR>"
            + "<TR><TD>계약상대</TD><TD>A&amp;B 주식회사</TD></TR></TABLE><P>계약기간은 2026.01.01부터 2026.12.31까지 입니다.</P></BODY></DOCUMENT>";

    @Test
    void turnsTableRowsIntoOneLineEach() {
        List<String> passages = FilingText.passages(FilingText.toPlain(XML));
        assertTrue(passages.contains("계약금액 | 1,234억원"));
        assertTrue(passages.contains("계약상대 | A&B 주식회사"));
    }

    @Test
    void splitsLongLinesWithinTheLimit() {
        String sentence = "이 문장은 길이를 늘리기 위한 문장입니다. ";
        List<String> passages = FilingText.passages(sentence.repeat(60));
        assertTrue(passages.size() > 1);
        for (String passage : passages) assertTrue(passage.length() <= FilingText.MAX_PASSAGE);
    }

    @Test
    void stripsParticlesFromQuestionWords() {
        List<String> tokens = FilingText.questionTokens("계약금액은 얼마인가요?");
        assertEquals(List.of("계약금액"), tokens);
    }

    @Test
    void ranksTheMatchingPassageFirst() {
        List<String> passages = FilingText.passages(FilingText.toPlain(XML));
        List<Integer> top = FilingText.rank(passages, "계약금액이 얼마야", 3);
        assertFalse(top.isEmpty());
        assertEquals("계약금액 | 1,234억원", passages.get(top.get(0)));
        assertTrue(FilingText.rank(passages, "배당 정책", 3).isEmpty());
    }

    @Test
    void verbatimNeedsALiteralPieceOfThePassage() {
        String passage = "계약기간은 2026.01.01부터 2026.12.31까지 입니다.";
        assertTrue(FilingText.verbatim("2026.01.01부터 2026.12.31까지", passage));
        assertTrue(FilingText.verbatim("계약기간은   2026.01.01부터", passage));
        assertFalse(FilingText.verbatim("계약기간은 2027.01.01부터", passage));
        assertFalse(FilingText.verbatim("짧은말", passage));
    }

    @Test
    void diffPairsSimilarPassagesAndKeepsTheRest() {
        List<String> before = List.of("계약금액 | 1,000억원 | 확정", "계약상대 | 주식회사 가나다", "공통 문장은 그대로입니다.");
        List<String> after = List.of("계약금액 | 1,200억원 | 확정", "공통 문장은 그대로입니다.", "정정 사유를 새로 적었습니다.");
        FilingText.Diff diff = FilingText.diff(before, after, 8);
        assertEquals(3, diff.total());
        assertFalse(diff.truncated());
        assertEquals("계약금액 | 1,000억원 | 확정", diff.changes().get(0).before());
        assertEquals("계약금액 | 1,200억원 | 확정", diff.changes().get(0).after());
        assertTrue(diff.changes().stream().anyMatch(pair -> pair.after().isEmpty() && pair.before().startsWith("계약상대")));
        assertTrue(diff.changes().stream().anyMatch(pair -> pair.before().isEmpty() && pair.after().startsWith("정정 사유")));
    }

    @Test
    void diffTruncatesAtTheCap() {
        List<String> before = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) before.add("전혀 다른 문단 번호 " + i + " 입니다 abc" + i);
        FilingText.Diff diff = FilingText.diff(before, List.of(), 8);
        assertEquals(8, diff.changes().size());
        assertEquals(12, diff.total());
        assertTrue(diff.truncated());
    }
}
