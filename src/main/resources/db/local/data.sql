-- Local demo only. Apply once to an empty schema, after schema.sql.
-- Demo credentials are shared separately. Store only BCrypt hashes in the repository.
-- No real card keys, recordings or external AI calls are created by this seed.
SET NAMES utf8mb4;
SET time_zone = '+09:00';
SET @today = CURRENT_DATE();
SET @now = CURRENT_TIMESTAMP();
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

INSERT INTO meeting (id, company_id, project_id, team_id, meeting_room_id, host_member_id,
 title, status, start_at, end_at, recording_consent, started_at, ended_at, actions_confirmed_at) VALUES
(1, 1, 1, 1, 1, 2, '온보딩 개선 킥오프', 'DONE',
 TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '10:00:00'),
 TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '10:30:00'), TRUE,
 TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '10:00:00'),
 TIMESTAMP(DATE_SUB(@today, INTERVAL 1 DAY), '10:30:00'), @now),
(2, 1, 1, 1, 1, 2, '온보딩 개선 후속 회의', 'SCHEDULED',
 TIMESTAMP(DATE_ADD(@today, INTERVAL 1 DAY), '10:00:00'),
 TIMESTAMP(DATE_ADD(@today, INTERVAL 1 DAY), '10:30:00'), TRUE, NULL, NULL, NULL);
INSERT INTO meeting_attendee (meeting_id, member_id) VALUES
(1, 2), (1, 3), (1, 4), (2, 2), (2, 3), (2, 4);
INSERT INTO meeting_topic (id, meeting_id, parent_topic_id, topic_type, content, sort_order) VALUES
(1, 1, NULL, 'MAIN', '온보딩 개선 계획', 0),
(2, 1, 1, 'SUB', '와이어프레임과 API 문서', 1),
(3, 2, NULL, 'MAIN', '개선 진행 상황 점검', 0),
(4, 2, 3, 'SUB', '담당 업무와 차기 일정', 1);
INSERT INTO meeting_room_slot (meeting_room_id, slot_start, meeting_id)
VALUES (1, TIMESTAMP(DATE_ADD(@today, INTERVAL 1 DAY), '10:00:00'), 2);

-- Pre-written transcript and summary for repeatable demo viewing, not a claim of live AI completion.
INSERT INTO transcript_chunk (id, meeting_id, seq, content, offset_ms, end_offset_ms,
 speaker_member_id, speaker_source) VALUES
(1, 1, 1, '김서준입니다. 온보딩 플로우 개선을 시작하겠습니다.', 0, 4000, 2, 'SELF_STREAM'),
(2, 1, 2, '이하윤입니다. 온보딩 와이어프레임을 검토하겠습니다.', 4000, 9000, 3, 'SELF_STREAM'),
(3, 1, 3, '박도현입니다. 회의록 저장 API를 연동하겠습니다.', 9000, 14000, 4, 'SELF_STREAM');
INSERT INTO meeting_summary (company_id, meeting_id, overview, model_name, prompt_version)
VALUES (1, 1, '[사전 작성 시연 데이터] 온보딩 플로우를 개선하고 와이어프레임 검토, API 문서화, 회의록 저장 API 연동을 진행하기로 했다.', 'demo-seed', 'local-v1');

INSERT INTO `action` (id, company_id, project_id, parent_action_id, source_meeting_id,
 team_id, assignee_member_id, action_type, title, description, status, due_date,
 review_status, is_manual, confirmed_at, dispatched_at, is_done, start_date, created_at) VALUES
(201, 1, 1, NULL, 1, 1, NULL, 'TEAM', '온보딩 개선 개발', '개발팀 온보딩 개선 업무', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 20 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 3 DAY)),
(101, 1, 1, 201, 1, NULL, 3, 'PERSONAL', '온보딩 플로우 와이어프레임 검토', '화면 흐름과 입력 항목 검토', 'IN_PROGRESS',
 DATE_SUB(@today, INTERVAL 1 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 4 DAY)),
(102, 1, 1, 201, 1, NULL, 3, 'PERSONAL', 'API 엔드포인트 문서화', '프론트와 백엔드 요청·응답 계약 정리', 'TODO',
 DATE_ADD(@today, INTERVAL 2 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(103, 1, 1, 201, 1, NULL, 3, 'PERSONAL', '스프린트 보드 세팅', '업무 보드 초기 구성', 'DONE',
 DATE_SUB(@today, INTERVAL 10 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, TRUE, DATE_SUB(@today, INTERVAL 12 DAY), DATE_SUB(@now, INTERVAL 13 DAY)),
(104, 1, 1, 201, 1, NULL, 4, 'PERSONAL', '회의록 저장 API 연동', '회의 상세 화면 연결', 'TODO',
 DATE_ADD(@today, INTERVAL 10 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(105, 1, 2, NULL, NULL, NULL, 8, 'PERSONAL', '캠페인 소재 정리', '소재와 일정 인수인계', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 14 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, DATE_SUB(@today, INTERVAL 4 DAY), DATE_SUB(@now, INTERVAL 5 DAY));

-- Keep completed work in the member's history. Vacation handovers only include unfinished work.
INSERT INTO `action` (id, company_id, project_id, parent_action_id, source_meeting_id,
 assignee_member_id, action_type, title, description, status, due_date,
 review_status, is_manual, confirmed_at, dispatched_at, is_done, start_date, created_at) VALUES
(106, 1, 1, 201, 1, 3, 'PERSONAL', '온보딩 QA 체크리스트 작성', '휴직 중 전달할 미완료 QA 업무', 'TODO',
 DATE_ADD(@today, INTERVAL 4 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(107, 1, 1, NULL, NULL, 2, 'PERSONAL', '4분기 개발 로드맵 확정', '개발팀 차기 로드맵 검토', 'IN_PROGRESS',
 DATE_ADD(@today, INTERVAL 8 DAY), 'HUMAN_CONFIRMED', TRUE, @now, @now, FALSE,
 DATE_SUB(@today, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 3 DAY));

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
VALUES (1, 1, 1, 'local-demo-meeting-1', 12000, 3000, 15000, 'demo-seed', @now);
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
INSERT INTO meeting (id, company_id, project_id, host_member_id, title, status, is_online,
 recording_consent, start_at, end_at, started_at, ended_at, created_at, updated_at) VALUES
(110, 1, 11, 1, '제품 v2.0 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 4 DAY), '14:30:00')),
(111, 1, 11, 1, '제품 v2.0 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 3 DAY), '14:30:00')),
(120, 1, 12, 1, '인프라 마이그레이션 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 9 DAY), '14:30:00')),
(121, 1, 12, 1, '인프라 마이그레이션 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 8 DAY), '14:30:00')),
(130, 1, 13, 1, '마케팅 캠페인 Q3 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 6 DAY), '14:30:00')),
(131, 1, 13, 1, '마케팅 캠페인 Q3 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 5 DAY), '14:30:00')),
(140, 1, 14, 1, 'HR 프로세스 재정비 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 131 DAY), '14:30:00')),
(141, 1, 14, 1, 'HR 프로세스 재정비 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 130 DAY), '14:30:00')),
(150, 1, 15, 1, '운영 자동화 시연 회의 1', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 261 DAY), '14:30:00')),
(151, 1, 15, 1, '운영 자동화 시연 회의 2', 'DONE', FALSE, TRUE, TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:00:00'), TIMESTAMP(DATE_SUB(@today, INTERVAL 260 DAY), '14:30:00'));

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
