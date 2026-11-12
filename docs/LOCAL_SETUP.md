# 로컬 개발 환경 세팅

## 졸업작품 로컬 시연 프로파일 (`local`)

아래 기존 절차는 Flyway 기반 개발 환경 설명이다. 졸업작품 시연은 `local` 프로파일을
사용하며, Flyway 대신 별도로 준비한 스키마·더미 SQL을 DB에 적용한다.
MySQL·Redis는 루트 `compose.yml`로 실행한다. 비어 있는 MySQL 볼륨에서 최초 실행하면
최종 스키마와 더미 SQL이 순서대로 적용된다. Flyway는 실행하지 않는다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

- 기본 연결: 백엔드 `http://localhost:8080`, MySQL `localhost:3306/momo`,
  Redis `localhost:6379`, AI 서버 `http://localhost:8000`.
- MySQL 계정 기본값은 `momo`이다. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
  `REDIS_HOST`, `REDIS_PORT`, `AI_BASE_URL`, `SERVER_PORT`로 연결 위치를 변경할 수 있다.
- 비밀값 `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `INTERNAL_TOKEN`은 기존 루트
  `application-secret.yml` 또는 실행 환경변수로 제공한다. AI 서버와 `INTERNAL_TOKEN`을
  맞춰야 한다. 기본 토큰은 제공하지 않으며 기존 비밀 파일은 자동 생성·수정하지 않는다.
- Spring은 `.env`를 자동으로 읽지 않는다. 해당 파일은 Docker Compose용이다.
- Flyway와 Spring SQL 자동 초기화는 꺼져 있다. DB를 먼저 준비해야 하며,
  `ddl-auto=validate`로 구조가 코드와 맞지 않으면 부팅을 중단한다.
- CORS는 기본적으로 `http://localhost:3000`, `http://127.0.0.1:3000`을 허용한다.
  백엔드는 루프백 주소에서만 수신한다.
- `local`의 녹음과 프로젝트 첨부파일은 로컬 디스크에 실제 저장한다.
  녹음 조립은 ffmpeg·ffprobe를 사용한다. STT는 별도 연동이 필요하며 로컬 스텁만으로
  실제 전사가 완료되지는 않는다. 기본/테스트 프로파일의 저장소 스텁과 prod S3는 유지한다.

### MySQL·Redis 실행

Docker Desktop을 실행한 뒤 아래 명령을 사용한다.

```bash
docker compose up -d --wait mysql redis
docker compose ps
```

- MySQL 이미지는 `mysql:8.0.40`으로 고정한다. MySQL 서버를 macOS에 별도 설치할 필요는 없다.
- Compose에 `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`를 제공해야 한다.
  기존 `.env`는 자동 수정하지 않는다. 기존 파일에 없는 항목은 실행 셸의 환경변수로
  제공할 수 있다. Spring용 `application-secret.yml`을 Compose가 읽지는 않는다.
- `DB_PASSWORD`와 `REDIS_PASSWORD`는 백엔드에 제공하는 값과 일치해야 한다.
  root 비밀번호는 MySQL 관리용이며 백엔드는 `momo` 계정을 사용한다.
- `DB_PORT` 기본값은 `3306`이다. 포트 충돌로 변경한다면 백엔드 실행 환경에도
  같은 값을 제공한다. Redis는 `127.0.0.1:6379`를 사용한다.
- 관리 앱 연결: 호스트 `127.0.0.1`, 포트 `3306`, DB·사용자 `momo`
  (각각 변경했다면 변경한 값). 비밀번호는 제공한 `DB_PASSWORD`다.
- 최초 실행 시 DB·앱 계정과 함께 `db/local/schema.sql`, `db/local/data.sql`이 적용된다.
  파일은 컨테이너에 읽기 전용으로 마운트한다.
- MySQL·Redis 데이터는 각각 Docker 볼륨에 보존된다. 다음 명령으로 종료해도 유지된다.

```bash
docker compose down
```

MySQL 초기 계정·비밀번호·DB 생성 설정은 **비어 있는 데이터 볼륨에서만** 적용된다.
기존 볼륨에서 환경변수만 바꾸면 계정이나 비밀번호가 바뀌지 않는다.
`docker compose down -v`는 두 서비스의 데이터를 삭제하므로 일반 종료에 사용하지 않는다.

### 시연 계정과 데이터

기업 코드는 **`ZDEMO`**다. 시연 로그인 비밀번호는 팀 내 별도 경로로 공유하며
문서·SQL 주석·저장소에 평문을 적지 않는다. DB에는 BCrypt 해시만 저장한다.
비밀번호를 변경할 경우 `data.sql`의 BCrypt 해시도 함께 갱신해야 한다.

| ID | 이름 | 로그인 이메일 | 권한 | 상태 |
|---|---|---|---|---|
| 1 | 박대표 | ceo@zgroup.co.kr | OWNER | ACTIVE |
| 2 | 김서준 | seojun@zgroup.co.kr | LEADER | ACTIVE |
| 3 | 이하윤 | hayun@zgroup.co.kr | MEMBER + 관리자 겸직 | WAITING |
| 4 | 박도현 | dohyun@zgroup.co.kr | MEMBER | ACTIVE |
| 5 | 최유진 | yujin@zgroup.co.kr | LEADER | ACTIVE |
| 6 | 정민석 | minseok@zgroup.co.kr | MEMBER | ACTIVE |
| 7 | 강서연 | seoyeon@zgroup.co.kr | LEADER | ACTIVE |
| 8 | 임지안 | jian@zgroup.co.kr | MEMBER | WAITING |
| 9 | 오현우 | hyunwoo@zgroup.co.kr | LEADER | ACTIVE |
| 10 | 한소율 | soyul@zgroup.co.kr | MEMBER | ACTIVE |

- 회사는 온보딩 완료, 구독은 ACTIVE이며 초기화일을 포함하는 월 단위 기간을 갖는다.
- 4개 부서, 업무 흐름용 프로젝트 2개와 저장소 시연용 프로젝트 5개, 회의실 4개,
  완료 회의 12개·예정 회의 1개, 기존 업무 8개, 휴직·퇴사 인수인계 각 1개가 포함된다.
- 이하윤의 완료 업무 1개는 이력에 유지하고 미완료 업무를 1개 추가해 휴직 인계는 3건으로 구성한다.
  임지안의 업무는 중간 승인 완료 상태에 맞춰 정민석에게 재배정돼 있다.
- 이하윤은 팀장 중간 승인 대기, 임지안은 재분배·중간 승인 완료 후 최종 승인 대기다.
  인수인계 상태는 실제 코드의 SUBMITTED/REASSIGNED를 따른다.
- 회의 발화·사용량·결제 이력은 사전 작성 데이터다. 실제 녹음 파일이나 실결제는 없다.
- 저장소 5개 프로젝트의 합성 집계값은 음성 37,473,589,657바이트(34.9GB),
  텍스트 7,301,444,403바이트(6.8GB), 합계 41.7GB이며 한도는 50GB다.
  GB 표시는 1024³ 기준 소수점 1자리 반올림이다. 실제 로컬 디스크 사용량과는 다르다.
- `hr-process`, `ops-automation`은 DONE으로 집계 삭제 시연이 가능하다.
  삭제 후 BE 사용량은 줄지만 FE 구독 더미 수치는 그대로이므로 삭제를 마지막에 시연한다.
- 저장소 시연용 회의는 프로젝트마다 2개이며 마지막 집계일은 초기화일 기준
  product-v2 -3일, infra-migration -8일, marketing-q3 -5일, hr-process -130일, ops-automation -260일이다.
- 날짜는 KST 기준 초기화 시점으로 계산한다. 재시작만으로 날짜를 갱신하지는 않는다.

### 발표용 온보딩 회의

`ONBOARDING` 프로젝트에서 박대표가 주최한 킥오프(899), 김서준 팀장의 출시 준비 회의(900),
후속 점검 회의(901)가 이어진다. 기존 액션과 휴직 인계 항목은 킥오프 이력에 연결된다.
팀장은 관련 팀 액션이 연결된 900·901번 회의를 최근 회의 API에서 조회할 수 있다.

- **실제 AI 분석 시연**은 기본 시드를 사용한다. 김서준 계정으로 900번 회의를 열면
  김서준·이하윤·박도현의 합성 발화 27개가 있다. 분석 결과는 비어 있으며 AI 연결 후 분석한다.
- **사전 결과 시연**은 초기화 직후 아래 SQL을 추가로 적용한다. 작성된 요약·결정 3건과
  검토 대기 액션 3건이 생긴다. 김서준 계정으로 900번 회의에서 검토·분배 확정한 다음,
  이하윤·박도현 계정의 보드에서 업무를 확인한다.

프로젝트 루트에서 실행한다.

```bash
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < src/main/resources/db/local/demo-analysis-snapshot.sql
```

결과에 `Presentation snapshot loaded`가 표시되면 적용된 것이다. 대상 회의가 없거나 이미
분석했거나 스냅샷을 적용한 경우에는 `Skipped`가 표시된다. 스냅샷은 화면 시연용 사전 작성
데이터다. 실제 분석을 보여줄 때는 별도로 초기화한 시연 DB를 사용한다.

### 기존 빈 DB 초기화 / 시연 데이터 복구

이미 빈 볼륨이 생성된 뒤 SQL 마운트를 추가했다면 Docker는 초기화 SQL을 다시 실행하지 않는다.
MySQL이 준비된 상태에서 아래 명령으로 빈 DB에만 적용할 수 있다.

```bash
bash scripts/local-db.sh init
```

시연 상태를 처음으로 되돌릴 때는 **백엔드를 먼저 종료한 뒤** 실행한다.
다음 명령은 ZDEMO 시드 회사가 있는 DB의 모든 테이블·변경 데이터를 삭제하고 다시 생성한다.
기존 비밀 설정과 Redis 볼륨은 수정하지 않는다. 복구 후에는 브라우저에서 로그아웃 후 재로그인한다.

```bash
bash scripts/local-db.sh reset
```

`data.sql`은 한 번 적용하는 INSERT 파일이다. 중복 실행을 허용하지 않으며,
SQL 실패 시 원인을 해결한 뒤 DB 상태를 확인해야 한다. 초기화 중 실패한 부분 스키마는
자동 삭제하지 않는다. `schema.sql`은 빈 DB에서만 실행한다.

### SQL 변경 규칙과 검증

- 로컬 DB 구조의 정본은 `src/main/resources/db/local/schema.sql`이다.
  엔티티 변경 시 스키마와 필요한 시드 변경을 같은 PR에 반영한다.
- 기존 `db/migration`은 참고·기존 환경 검증용으로 보관한다. 로컬 시연 초기화에는 사용하지 않는다.
- 별도 Testcontainers 검증은 실제 MySQL 8.0.40에 최종 SQL만 적용하고 엔티티 정합성,
  데모 비밀번호, 구독 기간, 관리자 겸직·대기 상태와 인수인계 관계를 검사한다.
  일반 `test`에서는 Docker가 필요한 이 검증을 제외한다.

```bash
./gradlew localSqlCheck
```

로그인 비밀번호를 검증 실행 환경의 `DEMO_LOGIN_PASSWORD`로 제공하면 실제 BCrypt 일치 검사와
Owner·Leader·대기 관리자 로그인 및 관리자 API 호출도 검사한다. 값을 제공하지 않으면
로그인·관리 API 시나리오 1개만 건너뛰며, 나머지 스키마·데이터·승인 흐름 검증은 실행한다.
비밀번호 값은 별도 공유받아 입력하고 문서·스크립트·저장소에는 기록하지 않는다.

---

클론 후 **한 번만** 하면 된다. 순서대로 하면 애플리케이션이 뜬다.

> 이 문서가 없어서 실제로 막힌 사례가 있다(#44). 순서 중 하나라도 빠지면
> **원인을 가리키지 않는 에러**가 난다 — 증상별 원인은 맨 아래 표에 있다.

---

## 필요한 것

- JDK 17
- MySQL 8 (로컬 실행)
- Docker Desktop (로컬 Redis용)

---

## 1. MySQL 계정·데이터베이스 만들기

MySQL을 새로 설치하면 `root`만 있다. 애플리케이션이 쓰는 계정을 만든다:

```sql
CREATE USER IF NOT EXISTS 'module06'@'localhost' IDENTIFIED BY '본인이_정할_비밀번호';
-- ⚠️ CREATE USER IF NOT EXISTS 는 계정이 이미 있으면 비밀번호를 바꾸지 않는다(조용히 넘어간다).
--    그래서 ALTER USER 를 함께 실행한다 — 신규·기존 계정 모두 같은 비밀번호가 된다.
--    이게 없으면 "계정은 만들었는데 Access denied"가 계속 난다.
ALTER USER 'module06'@'localhost' IDENTIFIED BY '본인이_정할_비밀번호';
CREATE DATABASE IF NOT EXISTS module06 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON module06.* TO 'module06'@'localhost';
FLUSH PRIVILEGES;
```

> 위 두 곳의 비밀번호는 같은 값이어야 하고, 3번에서 `application-secret.yml`의 `DB_PASSWORD`에도 같은 값을 넣는다.

> 테이블은 만들지 않는다. **스키마의 주인은 Flyway**이고, 애플리케이션이 뜰 때 마이그레이션이 적용된다.
> 상세: [DB_MIGRATION_RULES.md](DB_MIGRATION_RULES.md)

## 2. Redis 비밀번호 정하기

로컬 Redis의 비밀번호는 **어딘가에 이미 있는 값이 아니다.** 본인이 정한 값이 컨테이너의 비밀번호가 된다.

```bash
openssl rand -hex 32
```

이 값을 **두 파일에 같이** 넣는다. 하나만 넣으면 안 된다.

| 파일 | 읽는 주체 | 역할 |
|---|---|---|
| `.env` | docker compose | 이 값으로 Redis를 **띄운다** (`--requirepass`) |
| `application-secret.yml` | Spring | 이 값으로 Redis에 **접속한다** (AUTH) |

## 3. 설정 파일 두 개 만들기

```bash
cp .env.example .env
cp application-secret.yml.example application-secret.yml
```

- `.env` → `REDIS_PASSWORD=` 에 2번에서 만든 값
- `application-secret.yml` → `DB_PASSWORD`에 1번 비밀번호, `REDIS_PASSWORD`에 **2번과 같은 값**

둘 다 **프로젝트 루트**에 둔다. `src/main/resources` 아래에 두면 읽히지 않고, `Dockerfile`의 `COPY src` 때문에 **이미지에 비밀값이 실려 나간다.**

> 두 파일 모두 `.gitignore` 처리돼 있다. 커밋되지 않는다.

## 4. Redis 띄우기

Docker Desktop을 먼저 실행한 뒤:

```bash
docker compose up -d redis
```

확인 — `healthy`가 나와야 한다:

```bash
docker ps --filter name=z-redis-local --format "{{.Names}} · {{.Status}}"
```

## 5. 애플리케이션 실행

IntelliJ에서 `BackendApplication`을 실행하거나:

```bash
./gradlew bootRun
```

성공하면 이렇게 보인다:

```text
Migrating schema `module06` to version "1 - init schema"
...
Successfully applied N migrations to schema `module06`
Tomcat started on port 8080 (http)
Started BackendApplication in N seconds
```

확인:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

---

## 증상별 원인

`spring.config.import`가 `optional:`이라 **설정 파일이 없거나 키가 빠져도 경고가 없다.** 그래서 실패가 엉뚱한 곳에서 터진다. 아래 표로 역추적한다.

| 증상 | 원인 | 조치 |
|---|---|---|
| `Could not resolve placeholder 'DB_HOST'` | `application-secret.yml`이 없거나 루트에 없음 | 3번. `src/main/resources`가 아니라 **루트** |
| `UnknownHostException (${DB_HOST})` | 같은 원인 — 치환 안 된 문자열이 그대로 접속 시도 | 3번 |
| `Could not resolve placeholder 'REDIS_PASSWORD'` | secret 파일에 그 키가 없음(기본값 없는 키) | 3번 |
| `Access denied for user 'module06'@'localhost'` | MySQL 계정이 없거나 `DB_PASSWORD`가 틀림 | 1번 |
| `Communications link failure` | MySQL이 안 떠 있음 / `DB_HOST` 오타 | MySQL 서비스 확인 |
| `NOAUTH Authentication required` | Redis엔 비밀번호가 있는데 Spring이 안 보냄 | `application-secret.yml`의 `REDIS_PASSWORD` |
| `AUTH ... without any password configured` | Spring은 보내는데 Redis엔 비밀번호가 없음 | `.env`가 비어 있다 → 2·3번 |
| `WRONGPASS invalid username-password pair` | 두 파일의 값이 다름 | 같은 값으로 맞춘다 |
| `Schema validation: wrong column type ...` | 엔티티와 마이그레이션 불일치 | **코드 버그다.** 이슈로 올릴 것(#44 참고) |
| `Port 8080 was already in use` | 이전 인스턴스가 살아 있음 | 그 프로세스 종료 |

> 마지막 항목 주의: `ddl-auto: validate`라서 엔티티와 실제 스키마가 다르면 부팅이 막힌다. **내 설정 문제가 아니라 코드 문제**다.
> 테스트는 H2 + `create-drop`이라 이 불일치를 잡지 못한다(스키마를 엔티티에서 생성하므로) — 항상 로컬에서 먼저 발견된다.

---

## 참고

| 문서 | 내용 |
|---|---|
| [DB_MIGRATION_RULES.md](DB_MIGRATION_RULES.md) | 담당자별 버전 영역 · 머지된 마이그레이션 수정 금지 |
| [../review-loop/SETUP.md](../review-loop/SETUP.md) | AI 코드 리뷰 루프 세팅(push 게이트) |
| `compose.yml` | 로컬 Redis |
| `infra/docker-compose.yml` · `infra/deploy.sh` | 운영 배포(SSM에서 비밀값 주입) |


## 프로젝트 저장소 삭제

`DELETE /api/companies/me/storage/projects/{tag}`는 Owner 또는 관리자 겸직 계정이 호출한다.
DONE 프로젝트만 허용하며 녹음·업로드·STT·분석 처리 중인 회의가 있으면 `409 MT-011`로 거절한다.

- 삭제: 녹음본, 잔여 청크, STT 블록 오디오, 자막, 전사문, 요약·요약 항목, 분석 중간 산출물 및 관련 처리 기록.
- 유지: 프로젝트, 회의 기본 정보·참석자, 액션, 분배 이력, 감사·학습 기록, 토큰 사용 이력.
- 삭제한 전사문·요약 항목을 가리키던 액션·분배 이력의 참조는 비운다. 액션 본문은 유지한다.
- 파일 삭제가 모두 성공한 뒤 DB 콘텐츠와 사용량을 정리한다. 일부 파일만 삭제된 채 실패하면 DB와 키·사용량은 유지하며 재시도 시 이미 없는 파일은 통과한다.
- 파일과 DB는 하나의 원자적 트랜잭션이 아니다. 파일 삭제 후 DB 실패 시에도 DB의 키가 남으므로 같은 요청으로 재시도한다.

로컬 삭제 어댑터의 파일 루트 기본값은 백엔드 실행 디렉터리의 `.local/storage`이며,
`app.local-storage.root` 설정으로 변경할 수 있다. 저장 키를 이 루트 아래 상대 경로로 해석한다.
회사·회의 접두가 일치하는 `recordings/org-{companyId}/meeting-{meetingId}/...`와
`stt-temp/org-{companyId}/meeting-{meetingId}/...`, 해당 회사의 온라인 회의 임시 키를 허용한다. 외부 URL, 경로 이탈, 심볼릭 링크는 거절한다.

local 프로파일의 수동 녹음 업로드·재생과 파일 삭제는 같은 파일 루트와 저장 키 규칙을 사용한다.
local 프로파일은 청크 자동 조립도 지원한다. STT·AI 처리는 후속 연결이 필요하다. 시드의 저장 용량은 합성 집계이며 실제 GB 단위 파일을 생성하지 않는다.
FRONTEND 구독 화면은 더미이므로 삭제 후 사용량 변화는 저장소 화면에만 반영된다.


## 로컬 녹음 업로드·재생

`local` 프로파일에서 녹음 파일은 호스트 노트북에 저장한다. AWS나 별도 파일 서버는 필요하지 않다.
기본/테스트 프로파일의 기존 저장소 스텁과 prod의 기존 S3 어댑터는 유지한다.

| 설정 | 기본값 | 설명 |
|---|---|---|
| `app.local-storage.root` / `LOCAL_STORAGE_ROOT` | `.local/storage` | 업로드·개별 삭제·프로젝트 삭제가 공유하는 루트 |
| `app.local-storage.base-url` / `LOCAL_STORAGE_BASE_URL` | `http://localhost:8080` | 실제 백엔드 포트 기준의 루프백 HTTP 주소 |
| `app.local-storage.max-upload-bytes` / `LOCAL_STORAGE_MAX_UPLOAD_BYTES` | `5368709120` | 파일 상한. 5GiB 이내로 낮출 수 있음 |

상대 파일 루트는 백엔드 실행 디렉터리 기준이므로 재실행 때 동일한 디렉터리를 사용한다.
설정 경로가 달라지면 이전 파일을 찾을 수 없다. `.local/storage/`는 Git에 포함하지 않는다.
JWT 비밀값을 기반으로 별도 용도의 HMAC 서명을 계산하므로 추가 비밀 설정은 필요하지 않다.

FRONTEND 연동 순서:

1. 기존 인증으로 `POST /api/meetings/{meetingId}/recordings/manual/upload-url` 호출.
2. 응답의 `uploadUrl`에 파일 바이트를 직접 **PUT**. 발급 요청과 동일한 `Content-Type`을 보낸다.
   multipart/form-data가 아니며 별도 Authorization 헤더는 필요 없다. 성공은 HTTP 204.
3. PUT 성공 후 기존 `POST /api/meetings/{meetingId}/recordings/manual`로
   `s3Key`, 표시용 `fileName`, 실제 `sizeBytes`를 보내 등록한다.
4. 기존 인증으로 `GET /api/meetings/{meetingId}/recordings/playback-url` 호출 후
   응답의 `url`을 audio 요소 등에 그대로 사용한다. GET·HEAD·Range 요청을 지원한다.

`s3Key`라는 기존 필드 이름과 URL 응답 구조는 유지한다. 로컬에서는 S3가 아닌 상대 파일 키다.
서명 URL은 `http://localhost:8080/{저장키}?token=...` 형식이다. 기존 청크 클라이언트처럼
URL pathname을 디코드해 키를 추출해도 같은 값이 나온다. 온라인 회의의
`recordings/org-{companyId}/member-{memberId}/online-pending/...` 형식과 한글 파일명도 지원한다.
다른 프로젝트가 같은 온라인 녹음 키를 공유하면 프로젝트 저장소 일괄 삭제는 409 MT-012로 막는다.
URL을 특정 도메인으로 치환하거나 Bearer 토큰으로 바꾸지 않는다.
PUT URL은 15분, 재생 URL은 3시간 유효하다. 파일 키·메서드·만료 시각과 PUT Content-Type을 서명으로 검증한다.
파일 URL은 소지자에게 해당 파일 접근을 허용하는 임시 자격이므로 공유·저장하지 않는다.

- 같은 키의 기존 파일은 덮어쓰지 않는다(409 CAP-027). 실패 후 파일이 없는 상태라면 유효한 PUT URL로 재시도할 수 있다.
- 업로드 중인 임시 파일은 재생·완료 검증 대상에 노출하지 않는다. 파일 존재·크기가 맞아야 완료 통보가 성공한다.
- 기존 녹음 삭제 API와 관리자 저장소 삭제가 실제 파일을 삭제한다. 미확인 STT 상태의 개별 삭제는 기존 confirm 규칙을 따른다.
- 403 CAP-026: 서명 만료·변조·잘못된 메서드 또는 PUT Content-Type 불일치.
- 409 CAP-027: 기존 파일 덮어쓰기 시도. 503 CAP-028: 로컬 파일 I/O 실패.
- 400 CAP-029: 잘못된 경로. 400 CAP-030: 전송 길이 불일치. 413 CAP-024: 빈 파일·용량 상한 초과.
- 유효하지 않은 Range는 416, 삭제되었거나 없는 파일은 404다.

수동 녹음·청크 저장·조립은 로컬에서 지원한다. 온디바이스 STT, AI 분석, 프로젝트 첨부파일은 별도 작업이다.


## 로컬 녹음 자동 조립

local 프로파일에서 회의 종료 또는 기존 CAP-05 조립 요청이 로컬 조립 어댑터를 실행한다.
기존 비동기 디스패처와 202/ASSEMBLING 응답 계약은 유지한다. 조립이 끝나야 녹음 재생 URL을 발급할 수 있다.

### 필요한 실행 파일

시연 노트북에 `ffmpeg`와 `ffprobe`가 필요하다. 두 실행 파일을 PATH에 두거나 아래 설정으로 절대 경로를 지정한다.
IntelliJ 등 GUI 앱의 PATH가 터미널과 다르면 실행 설정에 경로를 지정한다. 쉘 명령문이 아닌 실행 파일 경로만 받는다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `LOCAL_FFMPEG_PATH` | `ffmpeg` | 정규화·OGG 인코딩 실행 파일 |
| `LOCAL_FFPROBE_PATH` | `ffprobe` | 최종 재생 시간 측정 실행 파일 |
| `LOCAL_ASSEMBLY_TIMEOUT_SECONDS` | `600` | 외부 프로세스 1회당 제한 시간. ffprobe는 최대 30초 |

시연 전 프로젝트 루트에서 실행한다:

```bash
bash scripts/check-local-media.sh
./gradlew localAssemblyCheck
```

`localAssemblyCheck`는 실제 바이너리가 필요하며 없으면 실패한다. 일반 `test`에서는 local-media 태그를 제외해
CI의 호스트 도구 설치 여부에 영향을 받지 않게 했다. 이 검증은 임시 파일·격리된 H2 DB를 사용한다.

### 처리·실패 규칙

- 세그먼트·순번을 정렬하고 누락·추가 청크·업로드 상태·실제 파일 크기를 다시 확인한다.
- 같은 세그먼트의 WebM 헤더 재등장과 MP4 ftyp 경계를 새 스트림으로 분리한다.
- 스트림별로 48kHz mono WAV로 정규화한 뒤 `recording.ogg`로 결합한다. 완성 파일 키는 assembly-UUID.ogg다.
- 프로세스는 인자 배열로 실행하고 타임아웃·실패를 검사한다. 미디어 디코더의 네트워크 프로토콜은 허용하지 않는다.
- 완성 파일 저장 후 녹음 메타데이터·실제 바이트 사용량을 짧은 DB 트랜잭션으로 함께 저장한다.
  기존 사용량 원장의 revision보다 큰 값을 사용한다. 등록 중 업로드 범위가 바뀌면 거절한다.
- 커밋 성공 후 조립한 청크 파일·행만 정리한다. 나중에 올라온 청크는 삭제하지 않는다.
- 처리·등록 실패 시 원본 청크를 유지하고, 등록되지 않은 완성 파일은 보상 삭제한다.
- 등록 후 정리만 실패하면 다음 시도에서 재인코딩 없이 정리만 수행한다.
- 같은 회의의 동시 조립은 하나만 실행한다. 실패는 기존 재시도 워커에 등록하며 횟수 소진 시 로그에 남긴다.
  재시도 대기는 메모리이므로 재시작하면 사라진다. 메타 등록 전 실패는 기존 CAP-05로 재요청할 수 있다.
  메타 등록 후 정리만 실패한 상태에서 재시작했다면 원본 잔여 파일을 보존하며, 전체 녹음 삭제로 정리할 수 있다.

완성본은 기존 서명 URL·Range 재생·개별/프로젝트 저장소 삭제와 같은 파일 루트를 사용한다.
이 단계는 재생용 조립이며 STT 블록 오디오 조립·온디바이스 STT·AI 분석을 새로 연결하지 않는다.


### 프로젝트 첨부파일 실제 저장 (`local`)

프로젝트 첨부는 녹음 전용 경로와 분리된다. AWS 없이 PDF·문서 등 일반 파일을 저장하며,
기존 FE 계약(업로드 URL 발급 → raw PUT → 확정)은 바꾸지 않는다.

1. 로그인 후 `POST /api/projects/{projectId}/attachments/upload-url`에
   `fileName`, `fileSize`를 보낸다. Owner 또는 관리자 겸직자만 발급할 수 있다.
2. 응답의 `uploadUrl`에 파일 바이트를 `PUT`한다. multipart가 아니라 파일 본문 자체다.
   JWT 없이 접근하지만 키·메서드·파일 크기·15분 만료를 묶은 서명을 검증한다.
   브라우저가 정하는 `Content-Type`은 서명하지 않으므로 PDF가 녹음 형식 검사에 걸리지 않는다.
3. `POST /api/projects/{projectId}/attachments/confirm`으로 `fileName`, `fileUrl`, `fileSize`를 보낸다.
   `fileUrl`에는 업로드 응답의 **객체 키**를 그대로 넣는다. 실제 파일 존재·크기가 맞아야 등록된다.
4. 로그인한 회사 구성원은
   `GET /api/projects/{projectId}/attachments/{attachmentId}/download-url`로 5분 만료 URL을 받는다.
   다운로드 URL은 JWT 대신 서명을 사용하며 GET·HEAD·Range를 지원한다.
   응답은 `application/octet-stream`, `Content-Disposition: attachment`로 원본 파일명을 제공한다.
   파일 내용을 실행하거나 실제 형식을 판별하는 기능은 제공하지 않는다.
5. `DELETE /api/projects/{projectId}/attachments/{attachmentId}`는 Owner만 호출할 수 있다.
   실제 파일 삭제 후 DB 메타데이터를 삭제한다. 파일이 이미 없어도 DB 삭제를 재시도할 수 있다.

저장 위치: `${LOCAL_STORAGE_ROOT:-.local/storage}/project-attachments/company-{companyId}/project-{projectId}/{uuid}.{ext}`.
확장자가 없는 파일도 지원한다. 파일은 서버 재시작 후에도 유지되며, 이미 있는 키에 덮어쓰지 않는다.
파일명에서 사용한 마지막 확장자는 문자·숫자·밑줄·하이픈 1~32자로 제한한다.

| 설정 | 기본값 | 설명 |
|---|---|---|
| `app.local-attachments.max-upload-bytes` / `LOCAL_ATTACHMENT_MAX_UPLOAD_BYTES` | `104857600` | 첨부파일 상한 100MiB. 1바이트~5GiB 사이로 설정 가능 |
| `LOCAL_STORAGE_ROOT` | `.local/storage` | 녹음과 루트는 공유하고 첨부 전용 하위 경로를 사용 |
| `LOCAL_STORAGE_BASE_URL` | `http://localhost:8080` | 시연 노트북의 루프백 백엔드 주소 |

기존 JWT 비밀값으로 첨부 전용 범위의 HMAC을 계산한다. 별도 비밀 파일을 만들거나 수정하지 않는다.
만료·변조·다른 키·다른 메서드의 서명은 `PJ-008` / 403으로 거부한다.
기존 녹음 URL은 첨부파일 접근에 사용할 수 없다. URL 발급·확정·삭제 API의 JWT 검사는 유지한다.

주요 오류: `PJ-009` / 413(크기 상한), `PJ-010` / 400(업로드 크기 불일치),
`PJ-011` / 409(동일 키 재업로드), `PJ-012` / 400(확정할 실제 파일 없음·크기 불일치),
`PJ-013` / 500(저장소 처리 실패). 업로드 실패 시 임시 파일은 정리한다.

제한: 업로드 후 확정하지 않은 파일은 자동 정리하지 않는다. 삭제는 파일시스템과 DB 사이에
분산 트랜잭션이 없으므로, DB 커밋 실패 시 파일만 먼저 지워질 수 있다. 이 경우 삭제를 재시도한다.
첨부파일 용량은 기존 음성·자막용 저장소 사용량 집계에 추가하지 않는다.

검증:

```bash
./gradlew test --tests '*LocalAttachment*Test' --tests '*ProjectAttachment*Test'
```

임시 테스트 저장소와 H2를 사용하므로 실제 시연 파일·DB를 삭제하지 않는다.
최종적으로 프론트를 실제 BE 모드로 연결해 PDF 업로드 → 다운로드 → 삭제를 확인한다.


### 휴직·오프보딩 최종 승인 판단 응답

`GET /api/handovers`의 목록 원소와 `GET /api/handovers/{id}` 상세에 다음 필드를 추가한다.
같은 DTO를 쓰는 신청 생성·재배정·중간 승인·최종 승인·반려 응답 및 귀속 대기 목록에도 포함된다.
`/package`·`/insights` 응답이나 사원 목록 응답에 추가되는 필드는 아니다.

| 필드 | 타입 | 의미 |
|---|---|---|
| `canFinalize` | boolean | 현재 조회자가 지금 이 신청을 최종 승인할 수 있는지 |
| `requiresIntermediateApproval` | boolean | 현재 팀장 중간 승인이 더 필요한지. 완료 이력 자체가 아니라 남은 절차를 뜻함 |
| `finalizeBlockReason` | string 또는 null | 승인 불가 사유. 승인 가능하면 null |

기존 최종 승인 API의 정책을 그대로 사용한다. **현재 실제 팀장**(팀의 `leaderMemberId`)이
신청자인지로 직접 승인 경로를 결정한다. 신청 당시 권한 스냅샷이나 현재 Member/Leader 라벨을
추정해 판정하지 않는다. 이번 작업에서는 신청 당시 권한 저장·DB 스키마 변경을 하지 않는다.
이 판정은 휴직(VACATION)과 오프보딩(OFFBOARDING)에 공통으로 적용된다.

| 상태·조회자 | canFinalize | requiresIntermediateApproval | finalizeBlockReason |
|---|---|---|---|
| Owner, 일반 팀원 SUBMITTED | false | true | INTERMEDIATE_APPROVAL_REQUIRED |
| Owner, 현재 팀장 본인 SUBMITTED | true | false | null |
| Owner, REASSIGNED | true | false | null |
| Owner 아닌 관리자·팀장·팀원, 일반 팀원 SUBMITTED | false | true | OWNER_REQUIRED |
| Owner 아닌 관리자·팀장·팀원, 현재 팀장 SUBMITTED 또는 REASSIGNED | false | false | OWNER_REQUIRED |
| FINALIZED | false | false | ALREADY_FINALIZED |
| REJECTED | false | false | ALREADY_REJECTED |

사유 우선순위는 종료 상태 → Owner 권한 → 중간 승인 대기 순서다.
예: 관리자 겸직자가 중간 승인 전 신청을 조회하면 사유는 OWNER_REQUIRED이지만,
requiresIntermediateApproval은 true이므로 진행 단계도 함께 안내할 수 있다.

프론트 연동 요청:
- 실제 BE 모드에서는 최종 승인 버튼의 가능 여부를 `canFinalize`로 판단한다.
- `requiresIntermediateApproval`로 팀장 중간 승인 대기 안내를 표시한다.
- `finalizeBlockReason`은 사용자 문구로 매핑한다. 이 값은 HTTP 오류 코드가 아니다.
- `canFinalize`가 false여도 반려까지 불가능하다는 뜻은 아니다. 반려는 기존 권한·단계 규칙을 유지한다.
- 신청자 권한을 현재 값으로 추정하는 기존 로직을 최종 승인 버튼 판정에서 제거한다.
- 이 필드는 신청 당시 권한 스냅샷을 대체하지 않으므로, 신청 당시 직책을 표시하는 근거로 쓰지 않는다.

조회 권한과 회사 범위는 기존처럼 먼저 검증한다. 응답 필드는 권한을 부여하는 토큰이 아니다.
조회 뒤 팀장·신청 상태가 바뀔 수 있으므로 `PATCH /api/handovers/{id}/finalize`는 호출 시점에 다시 검증한다.
또한 canFinalize는 결재 자격·절차 상태의 판단이지, 후속 DB·액션·분석 처리의 성공을 보장하지 않는다.


### L4 주제 오버랩 배정 사본 정리 (#28 관련)

L4의 주제별 tuple을 모은 뒤 `assignmentTupleRepository.replace` 직전에
`TopicOverlapTupleDeduplicator`가 확실히 같은 사본만 정리한다. 정리된 목록만 저장하므로
제거한 사본은 L5 검증·L6 판정·DIST 액션 생성 대상에서 빠진다. L4 자체의 모델 호출 수나
이미 소비한 토큰은 줄지 않으며, 기존에 저장·분배된 데이터는 변경하지 않는다.

제거 조건(모두 충족):
- 담당자 ID와 근거 발화 ID가 존재하고 같으며 담당자 판정 출처도 일치한다.
- 제목(앞뒤·연속 공백만 정리), 기한, 근접 매칭 여부가 같다.
- 연결된 CONFIRMED 원본 항목이 유일하고, 유형·내용·분류 근거가 같다.
- 서로 다른 주제에서 나온 행이며 주제마다 해당 사본이 한 건이다.
- 발화의 고유 구간을 소유한 주제가 전체 구간 중 유일하고, 그 주제의 행이 한 건 존재한다.
- 모든 후보 주제의 실제 입력에 해당 발화가 들어 있다.
- 같은 발화에 다른 담당자 또는 담당자 미정 행이 함께 있지 않다.

고유 구간은 발화 ID의 숫자 대소가 아니라 실제 전사 목록의 순서로 판정한다.
구간이 겹치거나 경계가 없고 순서가 역전되는 등 메타데이터가 불명확하면 제거하지 않는다.
남기는 행은 고유 구간 쪽 원본이며 기존 행 순서·decisionId·모델·프롬프트 정보를 유지한다.
제거 시 회사·회의·제거 주제·유지 주제·근거 발화·제거 행의 decisionId를 로그로 남긴다.
발화 원문이나 업무 제목은 로그에 기록하지 않는다. 제거한 tuple 사본은 별도 DB 행으로 저장하지 않는다.

**남은 범위:** 같은 담당자·같은 근거라는 사실만으로 같은 업무라고 판단하지 않는다.
예를 들어 #28의 `인증 API 명세 문서화`와 `인증 문서 작성`처럼 제목이 다른 의역은
자동 제거하지 않는다. 해당 행들은 기존 DUPLICATE_EVIDENCE 검사를 거쳐 검토 대상으로 남는다.
따라서 이 변경만으로 #28의 실제 의역 중복 재현 사례가 해결됐다고 볼 수 없으며 이슈를 닫지 않는다.
의역 중복까지 처리하려면 원본 L2·L3·L4 출력 확인과 업무 동일성을 판정할 추가 계약·정책이 필요하다.
L3 주제별 결정 항목 중복 및 충돌 종류의 리뷰 API 노출도 이번 범위에 포함하지 않는다.

검증(외부 AI 호출 없이 가짜 계층 사용):

```bash
./gradlew test --tests '*TopicOverlapTupleDeduplicatorTest' --tests '*AnalysisOrchestratorTest' --tests '*ConflictDetectorTest'
```
