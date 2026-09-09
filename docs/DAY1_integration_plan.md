# 1일차 작업 계획 — 담당자 2 (연동 트랙)

기준 문서: [PRD_090910.md](PRD_090910.md) · 날짜: 2026-09-09 · 담당: 담당자 2 (GitHub 연동 / LLM / 인증)

> 한 줄 요약: **오전은 둘이 같이 기반을 깔고, 오후는 혼자 인증(F5) → Mock LLM(F2) → GitHub 커밋 수집(F1 전반)까지 붙여서 `day1-integration` 태그를 찍는다.**

---

## 0. 시작 전 확인할 것 (PRD와 다른 점)

| 항목 | PRD 기준 | 현재 상태 | 조치 |
|---|---|---|---|
| 브랜치 이름 | `main` + `track/client` + `track/integration` (§12 확정) | 로컬에 `git_peristalsis` 생성됨 | 담당자 1과 합의 필요. PRD대로 가면 `git branch -m git_peristalsis track/integration` |
| 원격 저장소 | 커밋 있는 상태에서 분기 | **빈 저장소** (커밋 0개) | 오전 공통 작업이 첫 커밋이 됨. `main`에 공통 커밋 → 정오에 분기 |
| 리포 루트 이름 | `worklog-drafter/` | `2026-pknu-3day/` | 무시해도 됨. 내부 구조(`backend/`, `frontend/`, `vscode-extension/`)만 맞추면 됨 |

**내 소유 영역** (여기만 건드린다 — §2)
```
backend/src/main/java/com/worklog/
├── auth/        GitHub OAuth, JWT, API Key
├── github/      GitHubCollector
├── activity/    Activity 엔티티·저장·조회
├── llm/         LlmProvider + MockLlmProvider + prompts/
├── draft/       ※ DraftGenerator만 (Controller/Service 조회·수정·확정은 담당자 1)
├── notify/      (2일차)
├── external/    (2일차)
└── config/
```
건드리면 안 되는 곳: `frontend/`, `vscode-extension/`, `backend/.../vscode/`, `draft/`의 CRUD 부분.

---

## 1. 오전 (09:00–12:00) — 공통 작업, 담당자 1과 함께

PRD §9 `1-0a ~ 1-0c`. 둘이 같은 시간에 하되 아래처럼 나누면 겹치지 않는다.

### 1-0a. 모노레포 스캐폴딩
| 누가 | 무엇 | 완료 조건 |
|---|---|---|
| **나** | `backend/` — Spring Boot 3.3, Java 21, Gradle. 의존성: web, data-jpa, security, oauth2-client, validation, flyway, postgresql | `./gradlew build` 통과 |
| **나** | `docker-compose.yml` — postgres 16 (`worklog`/`worklog`/`worklog`, 5432) | `docker compose up -d` 후 접속 |
| 담당자 1 | `frontend/` (Vite + React 18 + TS), `vscode-extension/` | 각각 빌드 통과 |
| 같이 | 루트 `README.md` (3개 명령 구동법: `docker compose up` / `bootRun` / `npm run dev`) | |

### 1-0b. Flyway V1 스키마 + JPA 엔티티 전부
- `backend/src/main/resources/db/migration/V1__init.sql` — §6 테이블 7개 전부
  `users, api_keys, repos, activities, vscode_sessions, drafts, notify_settings` (+ `user_llm_settings`는 P2지만 V1에 같이 넣어도 무방)
- ENUM: `activity_type`, `summary_status`, `draft_status`
- UNIQUE 제약 빠뜨리지 말 것: `activities(repo_id, type, external_id)`, `vscode_sessions(user_id, remote_url, branch, work_date)`, `drafts(user_id, work_date, version)`
- JPA 엔티티 전부 이 시간에 만든다. 특히 **`Draft` 엔티티는 여기서 확정** — 이후 변경은 합의 후에만 (§2 컨플릭트 방지)
- 완료 조건: `bootRun` 후 psql에서 테이블 생성 확인

### 1-0c. API 계약 확정
- §7 표를 같이 읽고 필드명 합의. 특히 담당자 1의 목업 JSON이 내 응답 스키마와 **필드명이 완전히 같아야** 함
- 확인 포인트: `GET /activities` 응답의 중첩 객체(`repo{id, fullName}`, `user{id, login, name, avatarUrl}`), `GET /me` 응답 필드, 오류 형식 `{code, message}`
- 여기서 합의한 뒤로 계약 변경은 **PRD 수정 → 상대에게 알림 → 코드** 순서로만

### 동기화 포인트 ① (정오)
```
git add -A && git commit -m "chore: scaffold monorepo, docker-compose, flyway V1 schema"
git push -u origin main
git checkout -b track/integration      # (또는 git_peristalsis, 합의한 이름)
```

---

## 2. 오후 (13:00–18:00) — 담당자 2 단독

PRD §9 `2-1 ~ 2-5`. **순서가 중요**: 2-4(GitHub 수집)는 2-2(OAuth로 받은 GitHub 토큰)에 의존한다.

### 2-1. `LlmProvider` + `MockLlmProvider` + 프롬프트 파일 분리 (F2) — 약 1시간
```java
public interface LlmProvider {
    String id();                      // "mock" | "gemma4" | "qwen3"
    String complete(LlmRequest req);  // system + user prompt → text
}
```
- `LlmRequest` (system, user, temperature, maxTokens) 레코드
- `MockLlmProvider`: `"{커밋메시지} 작업을 수행했습니다. (변경 파일 N개)"` 반환
- 프롬프트는 코드에 박지 말고 `llm/prompts/commit-summary-system.txt`, `commit-summary-user.txt` 로 분리 (§F2 프롬프트 본문 그대로). `{repo}`, `{message}`, `{files}`, `{diff}` 치환기 하나
- `application.yml`에 §F2 설정 블록 미리 넣어둔다 (`worklog.llm.provider: ${WORKLOG_LLM_PROVIDER:mock}`, `presets.gemma4 / qwen3`). 오늘은 `mock`만 빈으로 등록, `OpenAiCompatProvider`는 2일차 (2-11)
- 프로바이더 선택 로직: `provider` 값으로 빈 고르기. 모르는 값이면 경고 후 `mock`
- **완료 조건: 단위 테스트 통과** — `MockLlmProviderTest`(출력 형식), 프롬프트 치환 테스트

### 2-2. GitHub OAuth + JWT 발급 + `GET /me` (F5 전반) — 약 1.5시간
**사전 준비 (내가 직접 GitHub에서)**: Settings → Developer settings → OAuth Apps → New
- Homepage: `http://localhost:5173`
- Callback: `http://localhost:8080/api/auth/github/callback`
- 발급된 Client ID / Secret → `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`

구현:
- `GET /api/auth/github` → GitHub 인가 페이지로 리다이렉트 (scope `read:user repo`)
- `GET /api/auth/github/callback` → code 교환 → 사용자 정보 조회 → `users` UPSERT (github_id 기준) → GitHub access token **AES 암호화**해 `github_token_enc`에 저장 → JWT(HS256, 12h) 발급 → `${FRONTEND_URL}/auth/done?token=...` 리다이렉트
  - Spring Security OAuth2 Client 기본 콜백은 `/login/oauth2/code/github`이므로 `redirect-uri`를 위 경로로 지정하거나 수동 교환 구현 중 택1
- `JwtAuthFilter`: `Authorization: Bearer` 검증 → SecurityContext 세팅
- `GET /api/me` ★ → `{id, login, name, avatarUrl}`
- `GET /api/health` 무인증
- 필요한 env: `JWT_SECRET`, `ENCRYPTION_KEY`, `FRONTEND_URL`
- **완료 조건: 브라우저에서 `/api/auth/github` → 로그인 → `token=` 붙은 리다이렉트 확인 → `curl -H "Authorization: Bearer <jwt>" /api/me` 응답**

> 담당자 1은 2일차 오전에 `VITE_USE_MOCK=false` 전환을 위해 F5가 먼저 필요하다 (§3.1). 오늘 여기까지는 반드시 끝낸다.

### 2-3. API Key 발급/검증 필터 (F5 후반) — 약 1시간
- `POST /api/me/api-keys` `{label}` → 키 생성(`wl_` 접두사 + 랜덤) → **해시만 저장**, 응답에 평문 1회 노출 `{id, key, label}`
- `DELETE /api/me/api-keys/{id}`
- `ApiKeyAuthFilter`: `X-Api-Key` 헤더 → 해시 비교 → 사용자 인증 + `last_used_at` 갱신
- ★ 엔드포인트는 JWT / API Key 둘 다 허용, `POST /vscode/sessions`·`/external/*`는 API Key만 (경로 매칭 규칙만 잡아두면 담당자 1의 `/vscode/sessions`도 그대로 탄다)
- **완료 조건: `curl -H "X-Api-Key: wl_..." /api/me` 응답**, 틀린 키는 `401 {code, message}`

### 2-4. 리포 등록·검증 API + `GitHubCollector` 커밋 수집 (F1 전반) — 약 1.5시간
리포 API:
- `POST /api/repos` `{fullName}` → 로그인 사용자의 GitHub 토큰(복호화)으로 `GET /repos/{o}/{r}` 호출해 접근 검증 → 실패 시 `403 REPO_NOT_ACCESSIBLE` → 성공 시 `repos` 저장 (`default_branch` 포함)
- `GET /api/repos` ★, `DELETE /api/repos/{id}`
- `POST /api/repos/{id}/sync` → `@Async`로 수집 실행, 즉시 `202`

`GitHubCollector` (오늘은 커밋만, PR/머지는 2일차 2-6):
1. `GET /repos/{o}/{r}/commits?since={lastSyncedAt}` (첫 동기화면 최근 7일)
2. 커밋별 `GET /repos/{o}/{r}/commits/{sha}` → files, patch
3. diff 자르기: **파일당 200줄, 커밋당 3,000자** → `raw_diff`
4. `activities` UPSERT — `(repo_id, type='COMMIT', external_id=sha)` 기준, 중복이면 건너뜀
5. author login → `users.login` 매핑, 없으면 `external_login`만 저장 (`user_id NULL`)
6. `summary_status = PENDING`으로 저장 (요약 파이프라인은 2일차 2-7)
7. 완료 후 `repos.last_synced_at` 갱신
- `@Scheduled(cron = "${GITHUB_SYNC_CRON}")` 10분 폴링도 같이 걸어둔다
- **완료 조건: 실제 리포(이 저장소 `uxis-co-kr/2026-pknu-3day` 등록해 보면 됨) 수동 sync 후 `activities`에 row가 쌓임**
- 단위 테스트 (§11): GitHub 커밋 응답 **고정 JSON fixture**로 파싱·diff 절단 테스트. OAuth가 막혀도 이 테스트는 독립적으로 만들 수 있다

### 2-5. 1일차 커밋 + 태그
```
git commit -m "feat: ..."          # 작업 단위마다 이미 커밋했다면 마지막 정리만
git tag day1-integration
git push origin track/integration --tags
```

---

## 3. 오늘 만들어야 하는 파일 (체크리스트)

```
docker-compose.yml
backend/build.gradle
backend/src/main/resources/application.yml           (db, security, worklog.llm 블록)
backend/src/main/resources/db/migration/V1__init.sql
backend/src/main/java/com/worklog/
  config/SecurityConfig.java
  auth/   GitHubOAuthController, JwtService, JwtAuthFilter,
          ApiKeyService, ApiKeyAuthFilter, ApiKeyController, MeController
          User, UserRepository, ApiKey, ApiKeyRepository
          crypto/AesEncryptor
  llm/    LlmProvider, LlmRequest, MockLlmProvider, LlmProviderResolver, PromptLoader
          prompts/commit-summary-system.txt, commit-summary-user.txt
  github/ GitHubApiClient, GitHubCollector, RepoController, RepoService
          Repo, RepoRepository
  activity/ Activity, ActivityType, SummaryStatus, ActivityRepository
  draft/  Draft (엔티티만, 오전 공통)
  vscode/ VscodeSession (엔티티만, 오전 공통 — 이후 담당자 1 영역)
backend/src/test/java/com/worklog/
  llm/MockLlmProviderTest
  github/GitHubCollectorParseTest  (+ src/test/resources/fixtures/commits.json)
backend/http/auth.http, repos.http                    (curl 대신 검증용)
```

---

## 4. 협업 규칙 (오늘 지킬 것)

- **커밋**: 작업 단위마다 빌드·테스트 통과 상태로. Conventional Commits (`feat:`, `fix:`, `chore:`)
- **상대 영역 건드릴 일이 생기면 멈추고 알린다** (예: `Draft` 엔티티 필드 추가, `/vscode/sessions` 인증 규칙)
- **API 계약 변경** = PRD 먼저 수정 → 담당자 1에게 알림 → 코드
- 프론트 없이 검증한다: `curl` 또는 `backend/http/*.http`
- 담당자 1에게 오늘 중 전달할 것: **JWT 얻는 방법**(`/api/auth/github` 주소, `/auth/done?token=` 리다이렉트 규칙), `GET /me` 응답 예시 — 내일 오전 실서버 전환에 바로 쓴다

---

## 5. 환경 변수 (오늘 필요한 것만)

```
DB_URL=jdbc:postgresql://localhost:5432/worklog
DB_USER=worklog
DB_PASSWORD=worklog
GITHUB_CLIENT_ID=            # OAuth App 만들고 채움
GITHUB_CLIENT_SECRET=
JWT_SECRET=                  # 32바이트 이상 랜덤
ENCRYPTION_KEY=              # AES-256용 32바이트
WORKLOG_LLM_PROVIDER=mock
FRONTEND_URL=http://localhost:5173
GITHUB_SYNC_CRON=0 */10 * * * *
```
비밀값은 `backend/.env`(gitignore) 또는 IDE 실행 설정에만. **커밋 금지.**

---

## 6. 오늘 끝났을 때 상태 (수용 기준)

- [ ] `docker compose up` + `bootRun`으로 서버가 뜨고 테이블 7개가 생성돼 있다
- [ ] `provider=mock` 프로바이더 단위 테스트 통과
- [ ] 브라우저 GitHub 로그인 → JWT 획득 → `GET /me` 응답
- [ ] `X-Api-Key`로 `GET /me` 응답, 잘못된 키는 401
- [ ] 리포 등록 → 수동 sync → `activities`에 실제 커밋이 쌓이고, 같은 sync 두 번 해도 row 수 불변
- [ ] `day1-integration` 태그 푸시됨

**밀리면 버리는 순서**: 2-4의 스케줄러(수동 sync만 남김) → 2-4 전체(내일 2-6과 합침). 2-1·2-2·2-3은 담당자 1의 내일 일정에 걸려 있으니 버리지 않는다.
