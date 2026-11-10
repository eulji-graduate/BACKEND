package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.TupleRow;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.ItemView;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;
import lombok.extern.slf4j.Slf4j;

/** Removes only structurally identical overlap copies; uncertain rows still go through L6 review. */
@Slf4j
public final class TopicOverlapTupleDeduplicator {
    private TopicOverlapTupleDeduplicator() { }

    public static List<TupleRow> deduplicate(long companyId, long meetingId, List<TupleRow> rows,
            List<TopicSegment> topics, List<Utterance> utterances, Map<Integer, List<ItemView>> confirmedByTopic) {
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < utterances.size(); i++) {
            Long id = utterances.get(i).utteranceId();
            if (id == null || position.put(id, i) != null) return rows;
        }
        Map<Integer, Core> cores = new LinkedHashMap<>();
        for (TopicSegment topic : topics) {
            Integer start = position.get(topic.startUtteranceId());
            Integer end = position.get(topic.endUtteranceId());
            if (start == null || end == null || start > end || topic.utteranceIds() == null
                    || cores.containsKey(topic.topicSeq())) return rows;
            Core core = new Core(start, end, topic.utteranceIds());
            if (cores.values().stream().anyMatch(other -> start <= other.end() && end >= other.start())) return rows;
            cores.put(topic.topicSeq(), core);
        }

        Map<Long, Set<Long>> assigneesByEvidence = new HashMap<>();
        for (TupleRow row : rows) {
            if (row.tuple().evidenceUtteranceId() != null) {
                assigneesByEvidence.computeIfAbsent(row.tuple().evidenceUtteranceId(), ignored -> new HashSet<>())
                        .add(row.tuple().assigneeCandidateMemberId());
            }
        }
        Map<Signature, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            TupleRow row = rows.get(i);
            AssignmentTuple tuple = row.tuple();
            if (tuple.assigneeCandidateMemberId() == null || tuple.assigneeSource() == null
                    || tuple.evidenceUtteranceId() == null || row.decisionId() == null
                    || normalize(tuple.title()).isEmpty()) continue;
            List<ItemView> sources = confirmedByTopic.getOrDefault(row.topicSeq(), List.of()).stream()
                    .filter(item -> "CONFIRMED".equals(item.gateStatus()) && Objects.equals(item.id(), row.decisionId())
                            && Objects.equals(item.evidenceUtteranceId(), tuple.evidenceUtteranceId()))
                    .toList();
            if (sources.size() != 1 || sources.get(0).itemType() == null
                    || normalize(sources.get(0).content()).isEmpty()) continue;
            ItemView source = sources.get(0);
            AssignmentTuple normalized = new AssignmentTuple(normalize(tuple.title()), tuple.assigneeCandidateMemberId(),
                    tuple.assigneeSource(), tuple.dueDate(), tuple.evidenceUtteranceId());
            Signature signature = new Signature(normalized, source.itemType().name(), normalize(source.content()),
                    normalize(source.reason()), row.assigneeNearMatched());
            groups.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(i);
        }

        Set<Integer> removed = new HashSet<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() < 2) continue;
            Long evidence = rows.get(group.get(0)).tuple().evidenceUtteranceId();
            Integer at = position.get(evidence);
            if (at == null || assigneesByEvidence.get(evidence).size() != 1) continue;
            List<Integer> owners = cores.entrySet().stream()
                    .filter(entry -> at >= entry.getValue().start() && at <= entry.getValue().end())
                    .map(Map.Entry::getKey).toList();
            if (owners.size() != 1) continue;
            int owner = owners.get(0);
            List<Integer> originals = group.stream().filter(i -> rows.get(i).topicSeq() == owner).toList();
            if (originals.size() != 1) continue;
            Set<Integer> seenTopics = new HashSet<>();
            boolean ambiguous = false;
            for (int index : group) {
                int topic = rows.get(index).topicSeq();
                Core core = cores.get(topic);
                if (core == null || !core.context().contains(evidence) || !seenTopics.add(topic)) {
                    ambiguous = true;
                    break;
                }
            }
            if (ambiguous) continue;
            for (int index : group) {
                if (index == originals.get(0)) continue;
                removed.add(index);
                log.info("L4 overlap copy omitted — companyId={} meetingId={} removedTopic={} retainedTopic={} evidenceId={} decisionId={}",
                        companyId, meetingId, rows.get(index).topicSeq(), owner, evidence, rows.get(index).decisionId());
            }
        }
        if (removed.isEmpty()) return rows;
        List<TupleRow> retained = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) if (!removed.contains(i)) retained.add(rows.get(i));
        return retained;
    }

    private static String normalize(String value) { return value == null ? "" : value.strip().replaceAll("\\s+", " "); }
    private record Core(int start, int end, List<Long> context) { }
    private record Signature(AssignmentTuple tuple, String itemType, String content, String reason, boolean nearMatched) { }
}
