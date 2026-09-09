# 1일차 오후 작업 계획 — 담당자 2 (연동 트랙)

기준 문서: [PRD_090910.md](PRD_090910.md) · [DAY1_integration_plan.md](DAY1_integration_plan.md) · 날짜: 2026-09-09 13:10 기준

> 한 줄 요약: **오전 공통 작업은 끝났다. 오후는 2-0(기반 20분) → 2-1(Mock LLM) → 2-2(OAuth/JWT) → 2-3(API Key) → 2-4(리포·커밋 수집) 순으로 붙이고 `day1-integration` 태그를 찍는다.**

---

## 0. 오전 결과 점검

### 완료

| # | 항목 | 근거 |
|---|---|---|
| 1-0a | `backend/` Spring Boot 3.3.13 · Java 21 · Gradle, 의존성 전부 | `build.gradle`, `./gradlew build` 통과 (11:44) |
| 1-0a | `docker-compose.yml` postgres 16 | `worklog-postgres` healthy, 호스트 **5433** |
| 1-0a | 루트 `README.md`, `.gitignore`, `backend/.env.example` | JDK 21 지정법·`.env` 사용법 포함 |
| 1-0b | Flyway V1 — 테이블 7개 + `user_llm_settings` | DB에 8개 테이블 생성, `flyway_schema_history` v1 success (11:48) |
| 1-0b | UNIQUE 3종 (`activities`, `vscode_sessions`, `drafts`) | `V1__init.sql` |
| 1-0b | JPA 엔티티 11개 + JSONB record 3개 + `HibernateJsonConfig` | `ddl-auto: validate`로 부팅 통과 |
| 동기화 ① | 공통 커밋 `1b8a767` → `origin/git_peristalsis` 푸시 | |

### 계획 대비 달라진 점 (문제 없음)

- DB 포트 `5432 → 5433` — 로컬 PostgreSQL 충돌 회피. README·`.env.example`에 반영됨
- 브랜치 이름 `git_peristalsis` 유지 — README에 `track/integration` 대응 명시

### 아직 없는 것 (오후의 전제)

| 항목 | 영향 | 처리 |
|---|---|---|
| 원격에 `main` 없음 | 담당자 1이 `track/client`를 분기할 기준점이 없음 | 2-0에서 `git push origin git_peristalsis:main` |
| `backend/.env` 없음 | JWT/AES 키 없이 2-2 불가 | 2-0 |
| `SecurityConfig` 없음 | security starter 기본값이라 `/health`도 basic auth에 막힘 | 2-0 |
| JPA `*Repository` 0개 | 모든 서비스가 필요 | 2-0 |
| `src/test/`, `backend/http/` 없음 | | 각 작업에서 생성 |
| 1-0c API 계약 합의 | 코드로 확인 불가 | 담당자 1과 구두 확인 |
| GitHub OAuth App | 2-2 검증 불가 | **사용자가 직접 생성** (아래 §5) |

환경: 기본 Java가 24 → 실행 전 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. `gh` CLI 미설치(필수 아님).

---

## 1. 타임라인 (13:10 → 18:00)

| 시간 | 작업 | 커밋 메시지 |
|---|---|---|
| 13:10–13:30 | **2-0 기반** — `.env`, `SecurityConfig` 골격, `/health`, 오류 포맷, Repository 4개, 원격 `main` | `chore: security skeleton, health, error format, repositories` |
| 13:30–14:20 | **2-1 LLM** — `LlmProvider`, Mock, 프롬프트 파일, 리졸버, 테스트 | `feat(llm): LlmProvider, MockLlmProvider, prompt loader` |
| 14:20–15:50 | **2-2 OAuth + JWT + `/me`** (OAuth App 생성은 사용자가 병행) | `feat(auth): GitHub OAuth login, JWT, GET /me` |
| 15:50–16:40 | **2-3 API Key** — 발급/폐기/필터 + 경로 규칙 | `feat(auth): API key issue/revoke and X-Api-Key filter` |
| 16:40–17:50 | **2-4 리포 API + GitHubCollector** — 커밋 수집 + 스케줄러 | `feat(github): repo register/sync and commit collector` |
| 17:50–18:00 | **2-5 마무리** — 태그, 푸시, 담당자 1 전달 | — |

작업 단위마다 `./gradlew test` 통과 상태로 커밋한다.

**밀리면 버리는 순서**: 2-4 스케줄러(수동 sync만 남김) → 2-4 전체(내일 2-6과 합침). 2-1·2-2·2-3은 담당자 1의 내일 오전(`VITE_USE_MOCK=false` 전환)에 걸려 있으니 버리지 않는다.

---

## 2. 확정한 설계 결정

이 문서 작성 시점에 정했다. 바꾸려면 여기부터 고친다.

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| ① | Mock 출력용 원본 변수 전달 | `LlmRequest`에 `Map<String,String> vars` 포함. `MockLlmProvider`는 `vars.message`, `vars.fileCount`를 씀 | 프롬프트 문자열 재파싱보다 깔끔. 실제 프로바이더는 `vars` 무시 |
| ② | OAuth 흐름 | **수동 code 교환** (RestClient). JWT는 `spring-security-oauth2-jose`(oauth2-client starter에 딸려옴)의 `NimbusJwtEncoder/Decoder` HS256 | PRD의 콜백 경로·`/auth/done?token=` 규칙을 정확히 맞추기 쉽고, JWT 라이브러리 추가 불필요 |
| ③ | JWT / API Key 경로 구분 | principal에 권한 `AUTH_JWT` / `AUTH_API_KEY` 부여. `POST /vscode/sessions`, `/external/**`는 `hasAuthority("AUTH_API_KEY")`, 나머지 인증 경로는 둘 다 허용 | 담당자 1의 `/vscode/sessions` 컨트롤러가 규칙을 몰라도 그대로 탐 |
| ④ | 프롬프트 파일 위치 | `src/main/resources/llm/prompts/` | 계획서의 `java/.../llm/prompts/`는 클래스패스 로딩이 안 됨 |
| ⑤ | 수집 대상 브랜치 | 오늘은 기본 브랜치 커밋만. `activities.branch = repo.default_branch` | PR/브랜치별 수집은 2일차 2-6 |
| ⑥ | `last_synced_at` 갱신 시점 | 수집 **시작** 시각을 기록 | 수집 중 들어온 커밋 누락 방지 |
| ⑦ | 오류 형식 | 모든 4xx/5xx는 `{code, message}` — 401/403은 `AuthenticationEntryPoint`/`AccessDeniedHandler`로 통일 | PRD §7 |
| ⑧ | 검증용 리포 | `uxis-co-kr/2026-pknu-3day` + 커밋이 많은 리포 1개(사용자 지정) | 현재 리포는 커밋 1개라 "7일치·2회 sync 불변" 검증에 빈약 |

---

## 3. 작업별 상세

### 2-0. 기반 (13:10–13:30)

**할 일**
- [ ] `git push origin git_peristalsis:main` — 원격 `main` 생성 (현재 커밋 = 공통 커밋이라 안전)
- [ ] `cp backend/.env.example backend/.env` → `JWT_SECRET=$(openssl rand -base64 48)`, `ENCRYPTION_KEY=$(openssl rand -base64 32)`
- [ ] `config/SecurityConfig` — stateless, CSRF off, `permitAll`: `/health`, `/auth/**`. 나머지 `authenticated`. 필터 슬롯은 비워둠(2-2, 2-3에서 끼움)
  - **context-path가 `/api`이므로 매처는 `/me`처럼 prefix 없이 쓴다**
- [ ] `config/HealthController` — `GET /health` → `{"status":"ok"}`
- [ ] `config/ApiError` record `{code, message}` + `GlobalExceptionHandler` + JSON 401/403 핸들러
- [ ] `auth/UserRepository`, `auth/ApiKeyRepository`, `github/RepoRepository`, `activity/ActivityRepository`

**완료 조건**
```bash
curl -s localhost:8080/api/health          # 200 {"status":"ok"}
curl -s localhost:8080/api/me              # 401 {"code":"UNAUTHORIZED","message":"..."}
```

### 2-1. LLM — `LlmProvider` + Mock + 프롬프트 (13:30–14:20)

**파일**
```
llm/LlmProvider.java            String id(); String complete(LlmRequest req);
llm/LlmRequest.java             record(system, user, temperature, maxTokens, Map<String,String> vars)
llm/MockLlmProvider.java        @Component, id "mock"
                                → "{message} 작업을 수행했습니다. (변경 파일 N개)"
llm/LlmProperties.java          @ConfigurationProperties("worklog.llm") — provider, presets 맵 (yml 블록은 이미 있음)
llm/LlmProviderResolver.java    List<LlmProvider>에서 id로 선택. 모르는 값 → warn + mock
llm/PromptLoader.java           classpath 텍스트 로드 + {repo} {message} {files} {diff} 치환
resources/llm/prompts/commit-summary-system.txt   (PRD F2 프롬프트 본문 그대로)
resources/llm/prompts/commit-summary-user.txt
test/llm/MockLlmProviderTest    출력 형식
test/llm/PromptLoaderTest       치환, 누락 키 처리
test/llm/LlmProviderResolverTest  unknown → mock 폴백
```

**하지 않는 것**: `OpenAiCompatProvider`, `/models` 기동 검증 — 2일차 2-11.

**완료 조건**: `./gradlew test` 통과.

### 2-2. GitHub OAuth + JWT + `GET /me` (14:20–15:50)

**사전 준비 (사용자, 2-1 진행 중 병행)** — §5 참고. `.env`에 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` 채움.

**파일**
```
auth/GitHubOAuthController.java   GET /auth/github          → state 쿠키 발급, GitHub 인가 URL로 302 (scope read:user repo)
                                  GET /auth/github/callback → state 검증 → code 교환 → /user 조회
                                                            → users UPSERT(github_id) → 토큰 AES 암호화 저장
                                                            → JWT 발급 → 302 ${FRONTEND_URL}/auth/done?token=...
auth/GitHubOAuthClient.java       RestClient — POST github.com/login/oauth/access_token (Accept: application/json),
                                  GET api.github.com/user
auth/UserService.java             github_id 기준 UPSERT (login/name/avatar 갱신)
auth/crypto/AesEncryptor.java     AES-256-GCM, 키 = ENCRYPTION_KEY(base64 32B), IV 12B 선두 부착, base64 출력
auth/JwtService.java              NimbusJwtEncoder/Decoder HS256, sub = userId, 만료 12h (worklog.jwt.ttl-hours)
auth/JwtAuthFilter.java           Authorization: Bearer → 검증 → SecurityContext (권한 AUTH_JWT)
auth/AuthenticatedUser.java       principal: userId, login, authMethod
auth/MeController.java            GET /me → {id, login, name, avatarUrl}
test/auth/JwtServiceTest          발급→검증 왕복, 만료·위조 거부
test/auth/crypto/AesEncryptorTest 암호화→복호화 왕복, 매번 다른 암호문
backend/http/auth.http
```

**완료 조건**
1. 브라우저 `http://localhost:8080/api/auth/github` → GitHub 로그인 → `http://localhost:5173/auth/done?token=eyJ...` 로 리다이렉트 (프론트가 없으면 404 떠도 URL의 `token=`만 확인하면 됨)
2. `curl -H "Authorization: Bearer $JWT" localhost:8080/api/me` → `{"id":1,"login":"...","name":"...","avatarUrl":"..."}`
3. `psql`에서 `users.github_token_enc`가 평문이 아닌 것 확인

### 2-3. API Key 발급/검증 (15:50–16:40)

**파일**
```
auth/ApiKeyService.java       generate: "wl_" + 32B base64url · 저장은 SHA-256 hex 해시만 · verify(plain) → ApiKey
auth/ApiKeyController.java    POST   /me/api-keys {label}   → 201 {id, key, label, createdAt}  (key는 이 응답에만)
                              DELETE /me/api-keys/{id}      → 204 (남의 키·없는 키는 404)
auth/ApiKeyAuthFilter.java    X-Api-Key → 해시 조회 → SecurityContext (권한 AUTH_API_KEY) + last_used_at 갱신
config/SecurityConfig.java    경로 규칙 추가:
                                POST /vscode/sessions, /external/**  → hasAuthority("AUTH_API_KEY")
                                그 외 인증 경로                       → hasAnyAuthority("AUTH_JWT","AUTH_API_KEY")
test/auth/ApiKeyServiceTest   접두사·길이, 해시 일치/불일치
```

**완료 조건**
```bash
curl -X POST -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
     -d '{"label":"vscode"}' localhost:8080/api/me/api-keys        # 201, key 1회 노출
curl -H "X-Api-Key: wl_..." localhost:8080/api/me                  # 200
curl -H "X-Api-Key: wl_wrong" localhost:8080/api/me                # 401 {code, message}
```

### 2-4. 리포 API + `GitHubCollector` 커밋 수집 (16:40–17:50)

**파일**
```
github/GitHubApiClient.java     RestClient base https://api.github.com
                                헤더: Authorization: Bearer <token>, Accept: application/vnd.github+json,
                                      X-GitHub-Api-Version: 2022-11-28
                                getRepo(owner, name) / listCommits(owner, name, since, per_page=100, 페이지 순회) / getCommit(sha)
github/dto/GitHubRepoDto        default_branch, full_name
github/dto/GitHubCommitDto      sha, html_url, commit.message, commit.author.date, author.login(nullable),
                                files[]{filename, additions, deletions, patch}, stats{additions, deletions}
github/RepoService.java         register: "owner/name" 형식 검증 → 400 INVALID_FULL_NAME
                                          등록자 토큰 복호화 → getRepo → 404/403이면 403 REPO_NOT_ACCESSIBLE
                                          중복 → 409 REPO_ALREADY_REGISTERED
                                          default_branch 저장
github/RepoController.java      GET /repos ★ → [{id, fullName, defaultBranch, lastSyncedAt, registeredBy{id, login}}]
                                POST /repos {fullName} → 201
                                DELETE /repos/{id} → 204
                                POST /repos/{id}/sync → 202 (즉시 반환, @Async 수집)
github/DiffTruncator.java       순수 static — 파일당 200줄, 커밋당 3,000자
github/GitHubCollector.java     @Async syncRepo(repoId) — 아래 절차
github/GitHubSyncScheduler.java @Scheduled(cron = "${worklog.github.sync-cron}") → 전체 리포 순회
config/AsyncConfig.java         @EnableAsync @EnableScheduling
test/github/GitHubCollectorParseTest   fixtures/commits-list.json, commit-detail.json → DTO 파싱
test/github/DiffTruncatorTest          200줄/3,000자 절단
backend/http/repos.http
```

**`GitHubCollector.syncRepo` 절차**
1. 진행 중 세트(`ConcurrentHashMap`)에 있으면 skip — 동시 sync 방지
2. 토큰 = `repo.registeredBy`의 `github_token_enc` 복호화. 등록자 없으면 warn 후 skip
3. `syncStartedAt = now()`, `since = lastSyncedAt ?? now-7d`
4. `listCommits(since)` 순회. `(repo_id, 'COMMIT', sha)` 존재하면 **상세 호출 없이** 건너뜀 (rate limit 절약, PRD §11)
5. 신규만 `getCommit(sha)` → `DiffTruncator` → `activities` INSERT
   - `type=COMMIT`, `external_id=sha`, `title`=메시지 첫 줄, `message`=전체, `branch=default_branch`, `summary_status=PENDING`
   - `author.login` → `users.login` 매칭 시 `user_id`, 아니면 `external_login`만
   - UNIQUE 위반 시 `DataIntegrityViolationException` 잡아서 건너뜀
6. `repos.last_synced_at = syncStartedAt`

**완료 조건**
```bash
curl -X POST -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
     -d '{"fullName":"uxis-co-kr/2026-pknu-3day"}' localhost:8080/api/repos   # 201
curl -X POST -H "Authorization: Bearer $JWT" localhost:8080/api/repos/1/sync   # 202
# 잠시 후
docker compose exec postgres psql -U worklog -d worklog -c 'select count(*) from activities'
curl -X POST -H "Authorization: Bearer $JWT" localhost:8080/api/repos/1/sync   # 한 번 더
docker compose exec postgres psql -U worklog -d worklog -c 'select count(*) from activities'   # 같아야 함
```
- 없는 리포 등록 → `403 {"code":"REPO_NOT_ACCESSIBLE"}`
- `./gradlew test` 통과

### 2-5. 마무리 (17:50–18:00)

- [ ] 미커밋분 정리 커밋
- [ ] `git tag day1-integration && git push origin git_peristalsis --tags`
- [ ] 담당자 1에게 전달 (§6)

---

## 4. 오늘 만들 파일 체크리스트

```
backend/.env                                   (gitignore, 커밋 금지)
backend/http/auth.http, repos.http
backend/src/main/java/com/worklog/
  config/  SecurityConfig, HealthController, ApiError, GlobalExceptionHandler, AsyncConfig
  auth/    GitHubOAuthController, GitHubOAuthClient, UserService, UserRepository,
           JwtService, JwtAuthFilter, AuthenticatedUser, MeController,
           ApiKeyService, ApiKeyController, ApiKeyAuthFilter, ApiKeyRepository
           crypto/AesEncryptor
  llm/     LlmProvider, LlmRequest, MockLlmProvider, LlmProperties, LlmProviderResolver, PromptLoader
  github/  GitHubApiClient, dto/GitHubRepoDto, dto/GitHubCommitDto,
           RepoService, RepoController, RepoRepository,
           DiffTruncator, GitHubCollector, GitHubSyncScheduler
  activity/ ActivityRepository
backend/src/main/resources/llm/prompts/commit-summary-system.txt, commit-summary-user.txt
backend/src/test/java/com/worklog/
  llm/     MockLlmProviderTest, PromptLoaderTest, LlmProviderResolverTest
  auth/    JwtServiceTest, ApiKeyServiceTest, crypto/AesEncryptorTest
  github/  GitHubCollectorParseTest, DiffTruncatorTest
backend/src/test/resources/fixtures/commits-list.json, commit-detail.json
```

건드리지 않는 곳: `frontend/`, `vscode-extension/`, `backend/.../vscode/`, `draft/` CRUD, `Draft` 엔티티 필드.

---

## 5. 사용자가 직접 해야 하는 것

제가 대신 할 수 없는 항목. **2-1 진행 중에 해두면 2-2 시작할 때 바로 검증할 수 있다.**

1. GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**
   - Application name: `worklog-drafter-local`
   - Homepage URL: `http://localhost:5173`
   - Authorization callback URL: `http://localhost:8080/api/auth/github/callback`
2. Client ID 복사, **Generate a new client secret** → `backend/.env`의 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`에 채움
3. 2-4 검증용으로 본인 토큰으로 접근 가능한 **커밋이 많은 리포 하나** 이름 알려주기 (결정 ⑧)

---

## 6. 담당자 1에게 오늘 중 전달할 것

내일 오전 `VITE_USE_MOCK=false` 전환에 바로 쓴다.

- 로그인 시작 주소: `GET http://localhost:8080/api/auth/github`
- 로그인 완료 후 서버가 `${FRONTEND_URL}/auth/done?token=<jwt>` 로 리다이렉트 → 프론트는 `token`을 저장하고 이후 `Authorization: Bearer <jwt>`
- `GET /api/me` 응답: `{"id":1,"login":"ungsik","name":"...","avatarUrl":"https://..."}`
- 오류 형식: 모든 오류가 `{"code":"...","message":"..."}` + HTTP 상태. 미인증 401 `UNAUTHORIZED`, 권한 없음 403
- `POST /api/vscode/sessions`는 **`X-Api-Key` 헤더만** 통과한다 (JWT로는 403). 컨트롤러는 아무 설정 없이 만들면 되고, 현재 사용자는 `AuthenticatedUser` principal에서 꺼낸다
- API Key 발급: `POST /api/me/api-keys {"label":"..."}` → 응답의 `key`는 1회만 노출
- 원격 `main`에 공통 커밋이 있으니 `track/client`는 거기서 분기

---

## 7. 환경 변수 (오늘 값이 필요한 것)

```
DB_URL=jdbc:postgresql://localhost:5433/worklog     # 5433 주의
DB_USER=worklog
DB_PASSWORD=worklog
GITHUB_CLIENT_ID=            # §5 에서 발급
GITHUB_CLIENT_SECRET=
JWT_SECRET=                  # openssl rand -base64 48
ENCRYPTION_KEY=              # openssl rand -base64 32
WORKLOG_LLM_PROVIDER=mock
FRONTEND_URL=http://localhost:5173
GITHUB_SYNC_CRON=0 */10 * * * *
```

실행: `cd backend && set -a && source .env && set +a && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun`

---

## 8. 오늘 끝났을 때 상태 (수용 기준)

- [x] `docker compose up` + `bootRun`으로 서버가 뜨고 테이블이 생성돼 있다 (오전 완료)
- [ ] 원격 `main`에 공통 커밋이 있다
- [ ] `GET /api/health` 무인증 200, 미인증 `GET /api/me` → `401 {code, message}`
- [ ] `provider=mock` 프로바이더 단위 테스트 통과
- [ ] 브라우저 GitHub 로그인 → JWT 획득 → `GET /me` 응답
- [ ] `X-Api-Key`로 `GET /me` 응답, 잘못된 키는 401
- [ ] 리포 등록 → 수동 sync → `activities`에 실제 커밋이 쌓이고, 같은 sync 두 번 해도 row 수 불변
- [ ] 없는 리포 등록 시 `403 REPO_NOT_ACCESSIBLE`
- [ ] `./gradlew test` 전부 통과
- [ ] `day1-integration` 태그 푸시됨
- [ ] 담당자 1에게 §6 전달 완료
