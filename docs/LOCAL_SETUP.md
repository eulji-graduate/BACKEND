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
- `local`은 `prod`가 아니므로 기존 파일·녹음·STT 스텁이 선택된다.
  프로파일 추가만으로 해당 기능이 실제 구현으로 바뀌지는 않는다.

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
  완료 회의 11개·예정 회의 1개, 업무 8개, 휴직·퇴사 인수인계 각 1개가 포함된다.
- 이하윤의 완료 업무 1개는 이력에 유지하고 미완료 업무를 1개 추가해 휴직 인계는 3건으로 구성한다.
  임지안의 업무는 중간 승인 완료 상태에 맞춰 정민석에게 재배정돼 있다.
- 이하윤은 팀장 중간 승인 대기, 임지안은 재분배·중간 승인 완료 후 최종 승인 대기다.
  인수인계 상태는 실제 코드의 SUBMITTED/REASSIGNED를 따른다.
- 회의록·요약·사용량·결제 이력은 사전 작성 데이터다. 실제 녹음 파일이나 실결제는 없다.
- 저장소 5개 프로젝트의 합성 집계값은 음성 37,473,589,657바이트(34.9GB),
  텍스트 7,301,444,403바이트(6.8GB), 합계 41.7GB이며 한도는 50GB다.
  GB 표시는 1024³ 기준 소수점 1자리 반올림이다. 실제 로컬 디스크 사용량과는 다르다.
- `hr-process`, `ops-automation`은 DONE으로 집계 삭제 시연이 가능하다.
  삭제 후 BE 사용량은 줄지만 FE 구독 더미 수치는 그대로이므로 삭제를 마지막에 시연한다.
- 저장소 시연용 회의는 프로젝트마다 2개이며 마지막 집계일은 초기화일 기준
  product-v2 -3일, infra-migration -8일, marketing-q3 -5일, hr-process -130일, ops-automation -260일이다.
- 날짜는 KST 기준 초기화 시점으로 계산한다. 재시작만으로 날짜를 갱신하지는 않는다.

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
`stt-temp/org-{companyId}/meeting-{meetingId}/...`만 허용한다. 외부 URL, 경로 이탈, 심볼릭 링크는 거절한다.

이 작업은 파일 **삭제**를 연결한다. 기존 로컬 업로드·재생 URL 발급은 스텁이므로 실제 녹음 시연을 위해서는
후속 로컬 저장 구현이 같은 파일 루트와 저장 키 규칙을 사용해야 한다. 시드의 저장 용량은 합성 집계이며 실제 GB 단위 파일을 생성하지 않는다.
FRONTEND 구독 화면은 더미이므로 삭제 후 사용량 변화는 저장소 화면에만 반영된다.
