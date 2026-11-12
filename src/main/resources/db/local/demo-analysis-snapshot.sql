-- Optional, hand-authored presentation results for the fresh meeting 900 in data.sql.
-- Load before live analysis, on a presentation database initialized from the matching seed.
SET NAMES utf8mb4;
SET time_zone = '+09:00';
START TRANSACTION;

SET @snapshot_meeting = NULL;
SELECT id INTO @snapshot_meeting
FROM meeting
WHERE id = 900 AND company_id = 1 AND project_id = 1 AND host_member_id = 2
  AND title = '온보딩 1차 출시 준비 회의' AND status = 'DONE' AND actions_confirmed_at IS NULL
FOR UPDATE;

SET @apply_snapshot = @snapshot_meeting IS NOT NULL
  AND EXISTS (SELECT 1 FROM meeting WHERE id = 901 AND company_id = 1 AND project_id = 1 AND start_at IS NOT NULL)
  AND (SELECT COUNT(*) FROM transcript_chunk WHERE meeting_id = 900) = 27
  AND (SELECT COUNT(*) FROM transcript_chunk WHERE meeting_id = 900
       AND ((id = 90008 AND seq = 8) OR (id = 90016 AND seq = 16) OR (id = 90024 AND seq = 24))) = 3
  AND NOT EXISTS (SELECT 1 FROM meeting_analysis_run WHERE meeting_id = 900)
  AND NOT EXISTS (SELECT 1 FROM analysis_layer WHERE meeting_id = 900)
  AND NOT EXISTS (SELECT 1 FROM meeting_summary WHERE meeting_id = 900)
  AND NOT EXISTS (SELECT 1 FROM meeting_assignment_tuple WHERE meeting_id = 900)
  AND NOT EXISTS (SELECT 1 FROM `action` WHERE source_meeting_id = 900);

INSERT INTO meeting_analysis_run (meeting_id, run_seq)
SELECT 900, 1 WHERE @apply_snapshot;

INSERT INTO meeting_summary (company_id, meeting_id, overview, model_name, prompt_version)
SELECT 1, 900, '[사전 작성 시연 결과] 가입 화면의 안내를 개선하고, 인증 API 명세와 출시 QA 체크리스트를 준비하기로 했습니다.',
       'demo-seed', 'presentation-v1'
WHERE @apply_snapshot;

INSERT INTO meeting_decision (meeting_summary_id, meeting_id, topic_seq, topic, item_type, content,
 reason, evidence_transcript_id, gate_status, sort_order)
SELECT s.id, s.meeting_id, d.seq, d.topic, 'DECISION', d.content, '시연용 결정 항목',
       d.evidence_id, 'CONFIRMED', 0
FROM meeting_summary s
CROSS JOIN (
    SELECT 1 AS seq, '가입 화면' AS topic, '가입 화면 안내 문구와 오류 메시지 정리' AS content, 90008 AS evidence_id
    UNION ALL SELECT 2, '인증 API 문서', '로그인과 세션 갱신의 인증 API 명세 작성', 90016
    UNION ALL SELECT 3, '출시 QA', '가입부터 첫 프로젝트 진입까지 QA 체크리스트 작성', 90024
) d
WHERE @apply_snapshot AND s.meeting_id = 900;

-- The host can review and dispatch these drafts. The assignee board receives them on dispatch.
INSERT INTO `action` (company_id, project_id, parent_action_id, source_meeting_id, assignee_member_id,
 action_type, title, description, status, due_date, assignee_source, evidence_transcript_id, is_manual, review_status)
SELECT m.company_id, m.project_id, m.related_action_id, m.id,
       CASE WHEN d.topic_seq = 2 THEN 4 ELSE 3 END, 'PERSONAL', d.content,
       CASE d.topic_seq
           WHEN 1 THEN '기업 코드 예시와 잘못된 코드의 오류 문구를 정리합니다.'
           WHEN 2 THEN '로그인과 세션 갱신의 성공·실패 응답 예시를 문서에 포함합니다.'
           ELSE '정상 가입, 기업 코드 오류, 세션 만료를 포함해 첫 프로젝트 진입을 확인합니다.'
       END,
       'TODO', DATE(follow_up.start_at), 'EXPLICIT_CALL', d.evidence_transcript_id, FALSE, 'PENDING'
FROM meeting m JOIN meeting_decision d ON d.meeting_id = m.id
JOIN meeting follow_up ON follow_up.id = 901 AND follow_up.company_id = m.company_id
    AND follow_up.project_id = m.project_id AND follow_up.start_at IS NOT NULL
WHERE @apply_snapshot AND m.id = 900;

INSERT INTO meeting_assignment_tuple (company_id, meeting_id, meeting_decision_id, topic_seq, topic, title,
 assignee_candidate_member_id, assignee_source, assignee_near_matched, due_date, evidence_transcript_id,
 model_name, prompt_version, action_id, sort_order)
SELECT a.company_id, d.meeting_id, d.id, d.topic_seq, d.topic, a.title,
       a.assignee_member_id, a.assignee_source, FALSE, a.due_date, a.evidence_transcript_id,
       'demo-seed', 'presentation-v1', a.id, d.topic_seq - 1
FROM meeting_decision d
JOIN `action` a ON a.source_meeting_id = d.meeting_id AND a.evidence_transcript_id = d.evidence_transcript_id
WHERE @apply_snapshot AND d.meeting_id = 900;

INSERT INTO analysis_layer (meeting_id, layer, status, finished_at)
SELECT 900, stages.layer, 'DONE', CURRENT_TIMESTAMP
FROM (
    SELECT 'L1' AS layer UNION ALL SELECT 'L1.5' UNION ALL SELECT 'L2' UNION ALL SELECT 'L3'
    UNION ALL SELECT 'L3.5' UNION ALL SELECT 'L4' UNION ALL SELECT 'L5' UNION ALL SELECT 'L6'
    UNION ALL SELECT 'L7' UNION ALL SELECT 'DIST' UNION ALL SELECT 'OVERVIEW'
) stages
WHERE @apply_snapshot;

COMMIT;
SELECT IF(@apply_snapshot, 'Presentation snapshot loaded', 'Skipped: meeting 900 missing or already analyzed') AS result;
