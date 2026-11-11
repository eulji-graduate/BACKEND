package com.module06.backend.capture.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.TupleRow;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.ItemView;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;

class TopicOverlapTupleDeduplicatorTest {
    private static final LocalDate DUE = LocalDate.of(2026, 11, 18);
    private static final List<Utterance> UTTERANCES = List.of(
            new Utterance(100L, 42L, 0, 1, "첫 발화", null),
            new Utterance(900L, 42L, 1, 2, "문서 작성", null),
            new Utterance(5L, 42L, 2, 3, "다음 주제", null));
    private static final List<TopicSegment> TOPICS = List.of(
            new TopicSegment(1, "문서", 100L, 900L, List.of(100L, 900L)),
            new TopicSegment(2, "다음", 5L, 5L, List.of(900L, 5L)));
    private static final Map<Integer, List<ItemView>> SOURCES = Map.of(
            1, List.of(new ItemView(11L, ItemType.DECISION, "API 문서를 작성한다", "합의", 900L, "CONFIRMED")),
            2, List.of(new ItemView(12L, ItemType.DECISION, "API 문서를 작성한다", "합의", 900L, "CONFIRMED")));

    private TupleRow row(int topic, String title, Long assignee, LocalDate due, Long evidence) {
        return new TupleRow(new AssignmentTuple(title, assignee, AssigneeSource.EXPLICIT_CALL, due, evidence),
                topic == 1 ? 11L : 12L, topic, "주제", "model", "prompt", false);
    }
    private TupleRow original() { return row(1, "API 문서 작성", 42L, DUE, 900L); }
    private TupleRow copy() { return row(2, "API 문서 작성", 42L, DUE, 900L); }
    private List<TupleRow> deduplicate(List<TupleRow> rows) {
        return TopicOverlapTupleDeduplicator.deduplicate(1, 20, rows, TOPICS, UTTERANCES, SOURCES);
    }

    @Test
    void keepsUniqueCoreOwnerInOriginalOrderUsingTranscriptOrderNotNumericIdRange() {
        TupleRow unrelated = row(2, "다른 업무", 43L, DUE, 5L);
        assertThat(deduplicate(List.of(copy(), unrelated, original()))).containsExactly(unrelated, original());
        assertThat(deduplicate(List.of(original(), copy()))).containsExactly(original());
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "assignee", "due", "evidence", "unknown-assignee", "missing-evidence", "source", "near-match", "decision"})
    void ambiguousOrDifferentWorkIsNeverDeleted(String change) {
        TupleRow altered = switch (change) {
            case "title" -> row(2, "인증 문서 작성", 42L, DUE, 900L);
            case "assignee" -> row(2, "API 문서 작성", 43L, DUE, 900L);
            case "due" -> row(2, "API 문서 작성", 42L, DUE.plusDays(1), 900L);
            case "evidence" -> row(2, "API 문서 작성", 42L, DUE, 5L);
            case "unknown-assignee" -> row(2, "API 문서 작성", null, DUE, 900L);
            case "missing-evidence" -> row(2, "API 문서 작성", 42L, DUE, null);
            case "source" -> new TupleRow(new AssignmentTuple("API 문서 작성", 42L, AssigneeSource.FIRST_PERSON, DUE, 900L),
                    12L, 2, "주제", "model", "prompt", false);
            case "near-match" -> new TupleRow(copy().tuple(), 12L, 2, "주제", "model", "prompt", true);
            default -> new TupleRow(copy().tuple(), null, 2, "주제", "model", "prompt", false);
        };
        List<TupleRow> input = List.of(original(), altered);
        assertThat(deduplicate(input)).containsExactlyElementsOf(input);
    }

    @Test
    void reportedParaphrasesRemainReviewableRatherThanGuessingSemanticEquivalence() {
        var input = List.of(row(1, "인증 API 명세 문서화", 42L, DUE, 900L),
                row(2, "인증 문서 작성", 42L, DUE, 900L));
        assertThat(deduplicate(input)).containsExactlyElementsOf(input);
    }

    @Test
    void retainsRowsWhenLinkedDecisionContentDiffersOrCannotBeResolved() {
        for (Map<Integer, List<ItemView>> sources : List.of(Map.<Integer, List<ItemView>>of(), Map.of(
                1, SOURCES.get(1), 2, List.of(new ItemView(12L, ItemType.DECISION, "별도 업무", "합의", 900L, "CONFIRMED"))))) {
            assertThat(TopicOverlapTupleDeduplicator.deduplicate(1, 20, List.of(original(), copy()), TOPICS, UTTERANCES, sources))
                    .containsExactly(original(), copy());
        }
    }

    @Test
    void retainsSameTopicDuplicatesMissingOriginalAndMultipleOriginals() {
        for (List<TupleRow> input : List.of(List.of(copy(), copy()), List.of(original(), original(), copy()), List.of(original(), original()))) {
            assertThat(deduplicate(input)).containsExactlyElementsOf(input);
        }
    }

    @Test
    void inconsistentOrOverlappingCoresDoNotCauseAutomaticDeletion() {
        for (List<TopicSegment> topics : List.of(
                List.of(TOPICS.get(0), new TopicSegment(2, "잘못된 경계", 900L, 5L, List.of(900L, 5L))),
                List.of(TOPICS.get(0), new TopicSegment(2, "없는 경계", 99L, 5L, List.of(900L, 5L))),
                List.of(new TopicSegment(1, "역전", 900L, 100L, List.of(100L, 900L)), TOPICS.get(1)),
                List.of(new TopicSegment(1, "구간 소유자 없음", 100L, 100L, List.of(100L, 900L)), TOPICS.get(1)),
                List.of(TOPICS.get(0), new TopicSegment(2, "오버랩 입력 아님", 5L, 5L, List.of(5L))))) {
            assertThat(TopicOverlapTupleDeduplicator.deduplicate(1, 20, List.of(original(), copy()), topics, UTTERANCES, SOURCES))
                    .containsExactly(original(), copy());
        }
    }

    @Test
    void aConflictingThirdAssigneePreventsRemovingOtherwiseIdenticalCopies() {
        var input = List.of(original(), copy(), row(2, "API 문서 작성", 43L, DUE, 900L));
        assertThat(deduplicate(input)).containsExactlyElementsOf(input);
    }
}
