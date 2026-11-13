-- Presentation seed. Apply once to an empty schema, after schema.sql.
-- Meeting 899 supplies project history; meeting 900 is ready for live analysis.
-- Demo credentials are shared separately. Store only BCrypt hashes in the repository.
-- No real card keys, recordings or external AI calls are created by this seed.
SET NAMES utf8mb4;
SET time_zone = '+09:00';
SET @today = CURRENT_DATE();
SET @now = CURRENT_TIMESTAMP();
SET @demo_start = TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '10:00:00');
SET @demo_end = DATE_ADD(@demo_start, INTERVAL 3 MINUTE);
SET @period_start = CAST(DATE_FORMAT(@today, '%Y-%m-01') AS DATE);
SET @period_end = DATE_ADD(@period_start, INTERVAL 1 MONTH);
SET @demo_password_hash = '$2a$10$xMJCuwF1kvn6Ci7xPdT9g.dKuEUw.MHzqqKMts7g8CWH9XlIv.4CG';

START TRANSACTION;

INSERT INTO company (id, code, name, representative_name, manager_email, address,
                     employee_scale, terms_agreed_at, privacy_agreed_at, onboarded_at)
VALUES (1, 'ZDEMO', 'Z그룹 시연기업', '박대표', 'ceo@zgroup.co.kr', '졸업작품 로컬 시연',
        '6-20', @now, @now, @now);

INSERT INTO team (id, company_id, name) VALUES
(1, 1, '개발팀'), (2, 1, '마케팅팀'), (3, 1, '디자인팀'), (4, 1, '전략기획팀');

-- System roles have fixed IDs, as required by the existing application.
INSERT INTO `role` (id, company_id, team_id, name) VALUES
(1, NULL, NULL, '리더'), (2, NULL, NULL, '없음'),
(3, 1, 1, '프론트엔드'), (4, 1, 1, '백엔드'), (5, 1, 2, '브랜드'),
(6, 1, 2, '캠페인'), (7, 1, 3, '비주얼');

-- OWNER is assigned directly to the member, never to position.authority.
INSERT INTO position (id, company_id, name, authority, sort_order, description) VALUES
(1, 1, '팀장', 'LEADER', 1, '팀 업무와 회의 관리'),
(2, 1, '과장', 'LEADER', 2, '팀 업무와 회의 관리'),
(3, 1, '선임', 'MEMBER', 3, '개인 업무 수행'),
(4, 1, '대리', 'MEMBER', 4, '개인 업무 수행'),
(5, 1, '주임', 'MEMBER', 5, '개인 업무 수행'),
(6, 1, '사원', 'MEMBER', 6, '개인 업무 수행'),
(7, 1, '대표', 'MEMBER', 0, '시연 Owner 직급 표시용. 실제 권한은 member.authority에서 결정');

INSERT INTO member (id, company_id, team_id, role_id, position_id, email, password_hash,
                    name, authority, is_admin, status, joined_on) VALUES
(1, 1, NULL, 2, 7, 'ceo@zgroup.co.kr', @demo_password_hash, '박대표', 'OWNER', 0, 'ACTIVE', '2020-01-02'),
(2, 1, 1, 2, 1, 'seojun@zgroup.co.kr', @demo_password_hash, '김서준', 'LEADER', 0, 'ACTIVE', '2021-03-02'),
(3, 1, 1, 3, 3, 'hayun@zgroup.co.kr', @demo_password_hash, '이하윤', 'MEMBER', 1, 'WAITING', '2022-05-10'),
(4, 1, 1, 4, 5, 'dohyun@zgroup.co.kr', @demo_password_hash, '박도현', 'MEMBER', 0, 'ACTIVE', '2023-01-15'),
(5, 1, 2, 2, 1, 'yujin@zgroup.co.kr', @demo_password_hash, '최유진', 'LEADER', 0, 'ACTIVE', '2023-04-20'),
(6, 1, 2, 5, 4, 'minseok@zgroup.co.kr', @demo_password_hash, '정민석', 'MEMBER', 0, 'ACTIVE', '2020-09-01'),
(7, 1, 3, 2, 1, 'seoyeon@zgroup.co.kr', @demo_password_hash, '강서연', 'LEADER', 0, 'ACTIVE', '2024-02-19'),
(8, 1, 2, 6, 6, 'jian@zgroup.co.kr', @demo_password_hash, '임지안', 'MEMBER', 0, 'WAITING', '2024-06-01'),
(9, 1, 4, 2, 2, 'hyunwoo@zgroup.co.kr', @demo_password_hash, '오현우', 'LEADER', 0, 'ACTIVE', '2021-11-08'),
(10, 1, 3, 7, 6, 'soyul@zgroup.co.kr', @demo_password_hash, '한소율', 'MEMBER', 0, 'ACTIVE', '2024-08-12');

UPDATE team SET leader_member_id = 2 WHERE id = 1;
UPDATE team SET leader_member_id = 5 WHERE id = 2;
UPDATE team SET leader_member_id = 7 WHERE id = 3;
UPDATE team SET leader_member_id = 9 WHERE id = 4;

INSERT INTO subscription (id, company_id, plan, seats, status, started_on, current_period_start,
                          current_period_end, next_billing_date)
VALUES (1, 1, 'TEAM', 10, 'ACTIVE', @period_start, @period_start, @period_end, @period_end);
INSERT INTO company_storage_plan (company_id, storage_cap_bytes) VALUES (1, 53687091200);
INSERT INTO company_token_plan (company_id, plan_code, monthly_token_pool, base_fee,
 token_overage_price_per_1k, input_token_price_per_1k, output_token_price_per_1k, effective_from)
VALUES (1, 'DEMO', 1000000, 30000, 10, 5, 15, @period_start);
INSERT INTO company_billing_config (company_id, vat_included, storage_overage_price_per_gb)
VALUES (1, TRUE, 1000);
INSERT INTO payment_method (company_id, brand, last4, expires_on, billing_key, is_default)
VALUES (1, 'DEMO', '1234', DATE_ADD(@today, INTERVAL 3 YEAR), 'mock_billing_local_demo', TRUE);
INSERT INTO billing_history (company_id, billed_on, plan, seats, amount, overage_amount, status)
VALUES (1, @period_start, 'TEAM', 10, 30000, 0, 'PAID');

INSERT INTO project (id, company_id, tag, name, description, color, status, start_date, due_date, created_by) VALUES
(1, 1, 'ONBOARDING', '온보딩 플로우 개선', '신규 사용자의 온보딩 경험과 회의록 연동 개선', '#6366F1',
 'IN_PROGRESS', DATE_SUB(@today, INTERVAL 30 DAY), DATE_ADD(@today, INTERVAL 30 DAY), 1),
(2, 1, 'CAMPAIGN', '브랜드 캠페인', '캠페인 소재와 디자인 준비', '#10B981',
 'TODO', DATE_ADD(@today, INTERVAL 3 DAY), DATE_ADD(@today, INTERVAL 45 DAY), 1);
INSERT INTO project_team (project_id, team_id) VALUES (1, 1), (1, 3), (2, 2), (2, 3);
INSERT INTO meeting_room (id, company_id, name, location) VALUES
(1, 1, '대회의실', '3층 A동'), (2, 1, '소회의실 A', '3층 A동'),
(3, 1, '소회의실 B', '3층 B동'), (4, 1, '화상회의실', '본관 5층');

-- Prior review, the presentation meeting, and its scheduled follow-up share one project.
INSERT INTO meeting (id, company_id, project_id, team_id, meeting_room_id, host_member_id,
 title, status, start_at, end_at, recording_consent, started_at, ended_at, actions_confirmed_at) VALUES
(899, 1, 1, NULL, 1, 1, '온보딩 개선 킥오프', 'DONE',
 DATE_SUB(@demo_start, INTERVAL 14 DAY), DATE_SUB(@demo_end, INTERVAL 14 DAY), TRUE,
 DATE_SUB(@demo_start, INTERVAL 14 DAY), DATE_SUB(@demo_end, INTERVAL 14 DAY),
 DATE_SUB(@demo_end, INTERVAL 14 DAY)),
(900, 1, 1, 1, 1, 2, '온보딩 1차 출시 준비 회의', 'DONE',
 @demo_start, @demo_end, TRUE, @demo_start, @demo_end, NULL),
(901, 1, 1, 1, 1, 2, '온보딩 출시 전 점검', 'SCHEDULED',
 TIMESTAMP(DATE_ADD(@today, INTERVAL 3 DAY), '10:00:00'),
 TIMESTAMP(DATE_ADD(@today, INTERVAL 3 DAY), '10:30:00'), TRUE, NULL, NULL, NULL);
INSERT INTO meeting_attendee (meeting_id, member_id) VALUES
(899, 1), (899, 2), (899, 3), (899, 4),
(900, 2), (900, 3), (900, 4), (901, 2), (901, 3), (901, 4);
INSERT INTO meeting_topic (meeting_id, parent_topic_id, topic_type, content, sort_order) VALUES
(899, NULL, 'MAIN', '온보딩 개선 계획', 0),
(900, NULL, 'MAIN', '가입 화면·인증 API 문서·출시 QA', 0),
(901, NULL, 'MAIN', '시연 전 개선 사항 점검', 0);
INSERT INTO meeting_room_slot (meeting_room_id, slot_start, meeting_id)
SELECT meeting_room_id, start_at, id FROM meeting WHERE id = 901;

-- Synthetic transcript for live analysis. Results for this meeting are loaded separately if needed.
INSERT INTO transcript_chunk (id, meeting_id, seq, content, offset_ms, end_offset_ms,
 speaker_member_id, speaker_source) VALUES
(90001, 900, 1, '온보딩 개선 1차 결과를 검토하고 이번 주에 넘길 일을 정하겠습니다.', 0, 5500, 2, 'SELF_STREAM'),
(90002, 900, 2, '가입 뒤 첫 화면까지는 구현됐고, 안내 문구 두 곳이 아직 비어 있습니다.', 6500, 12000, 3, 'SELF_STREAM'),
(90003, 900, 3, '회의록 링크는 목록에서 열리지만, 권한 오류가 나면 원인을 찾기 어렵습니다.', 13000, 18500, 4, 'SELF_STREAM'),
(90004, 900, 4, '이번 회의에서는 가입 화면, API 문서, QA 순서로 정리하겠습니다.', 19500, 25000, 2, 'SELF_STREAM'),
(90005, 900, 5, '가입 화면에는 기업 코드 예시와 오류 문구가 필요합니다.', 26000, 31500, 3, 'SELF_STREAM'),
(90006, 900, 6, '예시는 한 줄로 넣고, 기존 계정에는 추가 안내를 띄우지 않겠습니다.', 32500, 38000, 2, 'SELF_STREAM'),
(90007, 900, 7, '기업 코드를 잘못 입력한 경우도 화면에서 바로 확인되면 좋겠습니다.', 39000, 44500, 4, 'SELF_STREAM'),
(90008, 900, 8, '이하윤님, 가입 화면 안내 문구와 오류 메시지를 정리해 주세요.', 45500, 51000, 2, 'SELF_STREAM'),
(90009, 900, 9, '네, 기업 코드 예시까지 포함해서 다음 출시 전 점검 회의까지 반영하겠습니다.', 52000, 57500, 3, 'SELF_STREAM'),
(90010, 900, 10, '다음은 인증 API 문서입니다.', 58500, 64000, 2, 'SELF_STREAM'),
(90011, 900, 11, '현재 로그인 요청과 세션 만료 응답이 서로 다른 문서에 흩어져 있습니다.', 65000, 70500, 4, 'SELF_STREAM'),
(90012, 900, 12, '프론트에서는 정상 응답과 401 응답 예시가 같이 있으면 바로 연동할 수 있습니다.', 71500, 77000, 3, 'SELF_STREAM'),
(90013, 900, 13, '로그인과 세션 갱신을 한 페이지에 모으겠습니다.', 78000, 83500, 2, 'SELF_STREAM'),
(90014, 900, 14, '기업 코드가 누락됐을 때의 응답도 같이 적겠습니다.', 84500, 90000, 4, 'SELF_STREAM'),
(90015, 900, 15, '문서를 본 뒤 오류 문구가 응답과 일치하는지도 확인할게요.', 91000, 96500, 3, 'SELF_STREAM'),
(90016, 900, 16, '박도현님, 로그인과 세션 갱신의 인증 API 명세를 작성해 주세요.', 97500, 103000, 2, 'SELF_STREAM'),
(90017, 900, 17, '네, 성공과 실패 응답 예시를 넣어 다음 출시 전 점검 회의까지 공유하겠습니다.', 104000, 109500, 4, 'SELF_STREAM'),
(90018, 900, 18, '문서 위치는 프로젝트 기획 탭에 링크로 남기겠습니다.', 110500, 116000, 2, 'SELF_STREAM'),
(90019, 900, 19, '마지막으로 QA에서는 가입 직후부터 첫 프로젝트 화면까지 확인하면 됩니다.', 117000, 122500, 3, 'SELF_STREAM'),
(90020, 900, 20, '정상 가입뿐 아니라 만료된 세션으로 들어가는 경우도 넣으면 좋겠습니다.', 123500, 129000, 4, 'SELF_STREAM'),
(90021, 900, 21, '테스트 계정은 대표와 일반 사원 두 가지로 준비하겠습니다.', 130000, 135500, 2, 'SELF_STREAM'),
(90022, 900, 22, '모바일 화면은 별도 항목으로 남겨 두고 이번 검증은 데스크톱 기준으로 하겠습니다.', 136500, 142000, 3, 'SELF_STREAM'),
(90023, 900, 23, '오류 재현은 입력값과 기대 화면을 같이 남기면 쉽게 다시 확인할 수 있습니다.', 143000, 148500, 4, 'SELF_STREAM'),
(90024, 900, 24, '이하윤님, 가입부터 첫 프로젝트 진입까지의 QA 체크리스트를 작성해 주세요.', 149500, 155000, 2, 'SELF_STREAM'),
(90025, 900, 25, '네, 세션 만료와 기업 코드 오류도 다음 점검 전까지 체크리스트에 포함하겠습니다.', 156000, 161500, 3, 'SELF_STREAM'),
(90026, 900, 26, '세 작업은 다음 개발팀 회의에서 같이 점검하겠습니다.', 162500, 168000, 2, 'SELF_STREAM'),
(90027, 900, 27, '확인했습니다. 문서와 화면 링크는 프로젝트에 모아 두겠습니다.', 169000, 174500, 4, 'SELF_STREAM');

INSERT INTO `action` (id, company_id, project_id, parent_action_id, source_meeting_id,
 team_id, assignee_member_id, action_type, title, description, status, due_date,
 review_status, is_manual, confirmed_at, dispatched_at, is_done, start_date, created_at) VALUES
(201, 1, 1, NULL, 899, 1, NULL, 'TEAM', '온보딩 개선 개발', '개발팀 온보딩 개선 업무', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 20 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
(101, 1, 1, 201, 899, NULL, 3, 'PERSONAL', '온보딩 플로우 와이어프레임 검토', '화면 흐름과 입력 항목 검토', 'IN_PROGRESS',
 DATE_SUB(@today, INTERVAL 1 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 4 DAY)),
(102, 1, 1, 201, 899, NULL, 3, 'PERSONAL', 'API 엔드포인트 문서화', '프론트와 백엔드 요청·응답 계약 정리', 'TODO',
 DATE_ADD(@today, INTERVAL 2 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(103, 1, 1, 201, 899, NULL, 3, 'PERSONAL', '스프린트 보드 세팅', '업무 보드 초기 구성', 'DONE',
 DATE_SUB(@today, INTERVAL 10 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, TRUE, DATE_SUB(@today, INTERVAL 12 DAY), DATE_SUB(@now, INTERVAL 13 DAY)),
(104, 1, 1, 201, 899, NULL, 4, 'PERSONAL', '회의록 저장 API 연동', '회의 상세 화면 연결', 'TODO',
 DATE_ADD(@today, INTERVAL 10 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(105, 1, 2, NULL, NULL, NULL, 8, 'PERSONAL', '캠페인 소재 정리', '소재와 일정 인수인계', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 14 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 4 DAY), DATE_SUB(@now, INTERVAL 5 DAY));

-- Keep completed work in the member's history. Vacation handovers only include unfinished work.
INSERT INTO `action` (id, company_id, project_id, parent_action_id, source_meeting_id,
 assignee_member_id, action_type, title, description, status, due_date,
 review_status, is_manual, confirmed_at, dispatched_at, is_done, start_date, created_at) VALUES
(106, 1, 1, 201, 899, 3, 'PERSONAL', '온보딩 QA 체크리스트 작성', '휴직 중 전달할 미완료 QA 업무', 'TODO',
 DATE_ADD(@today, INTERVAL 4 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(107, 1, 1, NULL, NULL, 2, 'PERSONAL', '4분기 개발 로드맵 확정', '개발팀 차기 로드맵 검토', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 8 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE,
 DATE_SUB(@today, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 3 DAY));

-- The kickoff actions were dispatched together when the previous meeting ended.
UPDATE `action` a JOIN meeting m ON m.id = a.source_meeting_id
SET a.created_at = m.ended_at, a.confirmed_at = m.actions_confirmed_at,
    a.dispatched_at = m.actions_confirmed_at
WHERE m.id = 899;

UPDATE meeting SET related_action_id = 201 WHERE id IN (900, 901);

INSERT INTO handover (id, writer_member_id, team_id, team_name_snap, handover_type, status,
 leave_start_at, leave_end_at, last_working_day, writer_name_snap, writer_position_snap,
 intermediate_approver_id, intermediate_approver_name_snap, intermediate_approved_at, version, note) VALUES
(301, 3, 1, '개발팀', 'VACATION', 'SUBMITTED',
 TIMESTAMP(DATE_ADD(@today, INTERVAL 3 DAY), '00:00:00'),
 TIMESTAMP(DATE_ADD(@today, INTERVAL 7 DAY), '23:59:59'), NULL, '이하윤', '선임',
 NULL, NULL, NULL, 0, '시연용 휴직 신청: 팀장 중간 승인 대기'),
(302, 8, 2, '마케팅팀', 'OFFBOARDING', 'REASSIGNED', NULL, NULL,
 DATE_ADD(@today, INTERVAL 14 DAY), '임지안', '사원', 5, '최유진', DATE_SUB(@now, INTERVAL 2 DAY), 0,
 '시연용 퇴사 신청: 중간 승인 완료, 최종 승인 대기');
UPDATE handover SET created_at = DATE_SUB(@now, INTERVAL 3 DAY),
 updated_at = DATE_SUB(@now, INTERVAL 2 DAY) WHERE id = 302;
INSERT INTO handover_item (handover_id, action_id, action_title_snap, action_status_snap,
 project_tag_snap, action_type_snap, deadline_snap, source_meeting_id, source_meeting_title_snap,
 content_snap, parent_action_title_snap, start_date_snap, action_created_at_snap, reassign_required)
SELECT 301, id, title, status, 'ONBOARDING', action_type, due_date, source_meeting_id,
 '온보딩 개선 킥오프', description, '온보딩 개선 개발', start_date, created_at, NOT is_done
FROM `action` WHERE id IN (101, 102, 106);
INSERT INTO handover_item (handover_id, action_id, action_title_snap, action_status_snap,
 project_tag_snap, action_type_snap, deadline_snap, content_snap, start_date_snap,
 action_created_at_snap, reassign_required, reassignee_id, reassignee_name_snap,
 reassignee_position_snap, reassigned_at, committed_at)
SELECT 302, id, title, status, 'CAMPAIGN', action_type, due_date, description,
 start_date, created_at, TRUE, 6, '정민석', '대리', DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY) FROM `action` WHERE id = 105;
UPDATE `action` SET assignee_member_id = 6, pending_handover_ack = TRUE WHERE id = 105;

INSERT INTO personal_todo (company_id, member_id, title, `date`, end_date)
VALUES (1, 3, '시연 흐름 점검', @today, @today),
       (1, 4, 'API 연동 확인', @today, DATE_ADD(@today, INTERVAL 2 DAY));
INSERT INTO token_usage_record (company_id, team_id, meeting_id, job_id, input_tokens,
 output_tokens, total_tokens, model, recorded_at)
VALUES (1, 1, 899, 'local-demo-history-899', 12000, 3000, 15000, 'demo-seed', @now);
-- Synthetic admin-screen metering fixtures. No corresponding audio/text files are created.
-- These five projects match the FE demo totals; the two workflow projects stay unmetered.
INSERT INTO project (id, company_id, tag, name, description, color, status, start_date, due_date, created_by) VALUES
(11, 1, 'product-v2', '제품 v2.0', '관리자 저장소 화면 시연용 집계 데이터', '#6366F1', 'IN_PROGRESS', DATE_SUB(@today, INTERVAL 33 DAY), DATE_ADD(@today, INTERVAL 30 DAY), 1),
(12, 1, 'infra-migration', '인프라 마이그레이션', '관리자 저장소 화면 시연용 집계 데이터', '#6366F1', 'IN_PROGRESS', DATE_SUB(@today, INTERVAL 38 DAY), DATE_ADD(@today, INTERVAL 30 DAY), 1),
(13, 1, 'marketing-q3', '마케팅 캠페인 Q3', '관리자 저장소 화면 시연용 집계 데이터', '#6366F1', 'IN_PROGRESS', DATE_SUB(@today, INTERVAL 35 DAY), DATE_ADD(@today, INTERVAL 30 DAY), 1),
(14, 1, 'hr-process', 'HR 프로세스 재정비', '관리자 저장소 화면 시연용 집계 데이터', '#6366F1', 'DONE', DATE_SUB(@today, INTERVAL 160 DAY), DATE_SUB(@today, INTERVAL 123 DAY), 1),
(15, 1, 'ops-automation', '운영 자동화', '관리자 저장소 화면 시연용 집계 데이터', '#6366F1', 'DONE', DATE_SUB(@today, INTERVAL 290 DAY), DATE_SUB(@today, INTERVAL 253 DAY), 1);
INSERT INTO project_team (project_id, team_id) VALUES (11, 1), (12, 1), (13, 2), (14, 4), (15, 4);

-- Two completed meetings per project; newer report dates match FE lastRecordedAt.
INSERT INTO meeting (id, company_id, project_id, meeting_room_id, host_member_id, title, status, is_online,
 recording_consent, start_at, end_at, started_at, ended_at, created_at, updated_at) VALUES
(110, 1, 11, 1, 1, '제품 v2.0 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00')),
(111, 1, 11, 1, 1, '제품 v2.0 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00')),
(120, 1, 12, 1, 1, '인프라 마이그레이션 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00')),
(121, 1, 12, 1, 1, '인프라 마이그레이션 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00')),
(130, 1, 13, 1, 1, '마케팅 캠페인 Q3 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00')),
(131, 1, 13, 1, 1, '마케팅 캠페인 Q3 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00')),
(140, 1, 14, 1, 1, 'HR 프로세스 재정비 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00')),
(141, 1, 14, 1, 1, 'HR 프로세스 재정비 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00')),
(150, 1, 15, 1, 1, '운영 자동화 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00')),
(151, 1, 15, 1, 1, '운영 자동화 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'));

INSERT INTO meeting_attendee (meeting_id, member_id) SELECT id, 1 FROM meeting WHERE project_id BETWEEN 11 AND 15;

INSERT INTO meeting_storage_usage (meeting_id, company_id, project_id, used_bytes, revision, updated_at) VALUES
(110, 1, 11, 4885525299, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00')),
(111, 1, 11, 4885525299, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00')),
(120, 1, 12, 5851892941, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00')),
(121, 1, 12, 5851892941, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00')),
(130, 1, 13, 3650722201, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00')),
(131, 1, 13, 3650722202, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00')),
(140, 1, 14, 2415919104, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00')),
(141, 1, 14, 2415919104, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00')),
(150, 1, 15, 1932735283, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00')),
(151, 1, 15, 1932735283, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'));

INSERT INTO meeting_text_storage_usage (meeting_id, company_id, project_id, caption_bytes, caption_revision, transcript_bytes, transcript_revision, summary_bytes, summary_revision, updated_at) VALUES
(110, 1, 11, 187904819, 1, 375809638, 1, 187904820, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00')),
(111, 1, 11, 187904819, 1, 375809638, 1, 187904820, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00')),
(120, 1, 12, 228170137, 1, 456340275, 1, 228170138, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00')),
(121, 1, 12, 228170137, 1, 456340275, 1, 228170139, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00')),
(130, 1, 13, 147639500, 1, 295279001, 1, 147639502, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00')),
(131, 1, 13, 147639500, 1, 295279001, 1, 147639502, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00')),
(140, 1, 14, 174483046, 1, 348966092, 1, 174483047, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00')),
(141, 1, 14, 174483046, 1, 348966093, 1, 174483047, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00')),
(150, 1, 15, 174483046, 1, 348966092, 1, 174483047, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00')),
(151, 1, 15, 174483046, 1, 348966093, 1, 174483047, 1, TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'));

COMMIT;
