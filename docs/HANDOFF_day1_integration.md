# 담당자 1에게 — 1일차 연동 트랙 결과

날짜: 2026-09-09 · 브랜치 `git_peristalsis` · 태그 `day1-integration` · 커밋 `22288ab`
기준 문서: [PRD_090910.md](PRD_090910.md) · [DAY1_integration_plan2.md](DAY1_integration_plan2.md)

내일 오전 `VITE_USE_MOCK=false` 전환에 필요한 것만 정리했다.

---

## 1. 로그인 붙이는 법

```
1) 브라우저를 GET http://localhost:8080/api/auth/github 로 보낸다 (fetch 가 아니라 이동)
2) GitHub 로그인·승인
3) 서버가 http://localhost:5173/auth/done?token=<jwt> 로 리다이렉트한다
4) 프론트는 /auth/done 라우트에서 token 쿼리를 꺼내 저장하고 홈으로 보낸다
5) 이후 모든 호출에 Authorization: Bearer <jwt>
```

- JWT 는 HS256, 만료 **12시간**. 만료되면 401 이 오므로 1)부터 다시 하면 된다.
- 리다이렉트 주소는 `FRONTEND_URL` 환경변수라, 포트를 바꾸면 `backend/.env` 도 같이 바꾼다.
- CORS 는 `FRONTEND_URL` 출처만 허용하도록 열어 뒀다.

## 2. 지금 실제로 동작하는 엔드포인트

| 메서드 | 경로 | 인증 | 응답 |
|---|---|---|---|
| GET | `/api/health` | 없음 | `{"status":"ok"}` |
| GET | `/api/auth/github` | 없음 | 302 GitHub |
| GET | `/api/auth/github/callback` | 없음 | 302 `/auth/done?token=` |
| GET | `/api/me` | JWT / API Key | `{id, login, name, avatarUrl}` |
| POST | `/api/me/api-keys` | JWT | 201 `{id, key, label, createdAt}` — `key` 는 이 응답에만 |
| GET | `/api/me/api-keys` | JWT / API Key | `[{id, label, createdAt, lastUsedAt}]` |
| DELETE | `/api/me/api-keys/{id}` | JWT | 204 |
| GET | `/api/repos` | JWT / API Key | `[{id, fullName, defaultBranch, lastSyncedAt, registeredBy{id, login}}]` |
| POST | `/api/repos` | JWT | 201, 본문 `{fullName}` |
| DELETE | `/api/repos/{id}` | JWT | 204 |
| POST | `/api/repos/{id}/sync` | JWT | 202 (수집은 비동기) |

실제 응답 예시:

```json
// GET /api/me
{"id":1,"login":"UngsikJo","name":"ungsikJo","avatarUrl":"https://avatars.githubusercontent.com/u/180127159?v=4"}

// GET /api/repos
[{"id":1,"fullName":"uxis-co-kr/2026-pknu-3day","defaultBranch":"git_peristalsis",
  "lastSyncedAt":"2026-09-09T13:54:05.098788+09:00","registeredBy":{"id":1,"login":"UngsikJo"}}]
```

**아직 없는 것** (담당자 2의 2일차): `/activities`, `/drafts/generate`, `/stats/*`, `/external/*`, 요약 채우기.
`activities` 테이블에는 이미 실제 커밋이 쌓여 있고 `summary` 는 아직 null, `summary_status = PENDING` 이다.

## 3. 오류 형식 — 전부 이 모양이다

```json
{ "code": "REPO_NOT_ACCESSIBLE", "message": "리포에 접근할 수 없습니다. 이름과 권한을 확인해 주세요." }
```

| 상황 | 상태 | code |
|---|---|---|
| 토큰 없음 / 만료 / 위조 | 401 | `UNAUTHORIZED` |
| 인증은 됐지만 그 수단으로는 못 여는 경로 | 403 | `FORBIDDEN` |
| 리포 접근 불가 | 403 | `REPO_NOT_ACCESSIBLE` |
| `owner/repo` 형식 아님 | 400 | `INVALID_FULL_NAME` |
| 이미 등록된 리포 | 409 | `REPO_ALREADY_REGISTERED` |
| 본문 검증 실패 | 400 | `VALIDATION_ERROR` |
| 없는 경로 | 404 | `NOT_FOUND` |

## 4. `POST /vscode/sessions` 를 만들 때 (1-4)

**이 경로는 `X-Api-Key` 로만 열린다. JWT 로 부르면 403 이다.** (`/external/**` 도 같다.)
`GET /vscode/sessions` 는 대시보드가 쓰는 ★ 경로라 JWT / API Key 둘 다 받는다.
이 규칙은 `SecurityConfig` 에 이미 걸려 있으니 컨트롤러에는 아무 설정도 필요 없다.

현재 사용자는 principal 에서 꺼낸다:

```java
@PostMapping("/vscode/sessions")
public ResponseEntity<?> upsert(
        @AuthenticationPrincipal AuthenticatedUser principal,   // com.worklog.auth
        @RequestBody SessionRequest request) {
    Long userId = principal.id();   // login(), authMethod() 도 있다
    ...
}
```

키 발급은 `POST /api/me/api-keys {"label":"vscode"}` → 응답의 `key` (`wl_...`). **평문은 그 응답에만 있고 DB 에는 해시만 남는다.**

`VscodeSession` 엔티티와 `vscode_sessions` 테이블은 오전에 만든 그대로다. UPSERT 키는
`(user_id, remote_url, branch, work_date)`.

## 5. 로컬에서 백엔드 띄우기

```bash
docker compose up -d                       # 5432 가 이미 쓰이면 루트 .env 에 POSTGRES_PORT=5433
cd backend
cp .env.example .env                       # GITHUB_CLIENT_ID/SECRET, JWT_SECRET, ENCRYPTION_KEY 필요
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
set -a && source .env && set +a && ./gradlew bootRun
```

- OAuth App 은 하나를 공유해도 되고 각자 만들어도 된다. 콜백은 `http://localhost:8080/api/auth/github/callback`.
- `JWT_SECRET` / `ENCRYPTION_KEY` 가 사람마다 다르면 서로의 DB 데이터를 복호화할 수 없다. 각자 로컬 DB 를 쓰면 문제없다.
- 수동 검증용 요청 모음: `backend/http/auth.http`, `backend/http/repos.http`

## 6. 브랜치

- `main` = `a27e71d` (오전 공통 커밋 + DB 포트 수정). `track/client` 는 여기서 분기하면 된다.
- 연동 트랙은 `git_peristalsis`, 태그 `day1-integration`.
- 스키마는 Flyway 로만 바꾼다. `V1__init.sql` 은 이미 적용됐으므로 수정하지 말고 `V2__*.sql` 을 추가한다.
- `Draft` 엔티티 필드 변경은 합의 후에 (PRD 2).
