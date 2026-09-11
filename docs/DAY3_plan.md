# 작업 계획 — 담당자 2 (관리자 콘솔 · 연동) · 2026-09-11

작성: 2026-09-11 09:00 · 브랜치 `git_peristalsis` = `d222400` (= `origin/main`)
기준: [BACKLOG2.md](BACKLOG2.md) (담당자 1, 9/10 저녁) + [DAY3_arrange.md](DAY3_arrange.md) §5 (내 어제 정리)

> 파일명은 `DAY3_plan.md` 이지만 BACKLOG2 의 표현으로는 **4일차(9/11)** 다. 어제(9/10)의 계획서는 [DAY3_integration_plan.md](DAY3_integration_plan.md), 정리는 [DAY3_arrange.md](DAY3_arrange.md).

> 한 줄 요약: **오전에 서버 쪽 "내부 IP 접속" 과 "미푸시 커밋 계약" 을 끝내 담당자 1 을 막지 않고, 오후에 보안 점검의 서버 몫을 닫은 뒤, 15:00 부터 두 PC 로 연동 테스트를 함께 한다.**

> **진행 (09:10 갱신)** — 작업 0 · A · B · C-1~C-4 · D 를 코드로 넣었다 (V10, 테스트 250개 통과, 백엔드·화면 떠 있음).
> 서버 혼자 한 확인은 [E2E_0911.md](E2E_0911.md) §1. 남은 것: 스탠드업(§0-④), 담당자 1 의 확장 쪽(`unpushedCommits` 전송 · 서버 주소 명령 · `VITE_API_TARGET`), 오후 E(두 PC 연동 테스트) · F · G, 그리고 §10-1 OAuth App callback 변경.

---

## 0. 어젯밤 이후 바뀐 것과 역할 분배

### ① 담당자 1 브랜치에 아직 안 받은 커밋 4개

| 커밋 | 내용 | 내 영향 |
|---|---|---|
| `d32b5a9` | **`/auth/github?link=` 구멍 고침** — `POST /me/github/start` 로 옮겨 id 를 토큰에서 꺼낸다 | 내가 어제 §3 에 적어 둔 것을 그쪽이 고쳤다. DAY3_arrange §5-1 은 **끝**. `GitHubLinkController` · `GitHubOAuthController` 가 바뀌었으니 오늘 A 작업은 **이 위에서** 한다 |
| `c26d833` | BACKLOG2.md | 어제 저녁 받아 둠 |
| `c23a4dd` | 프론트 30초 자동 갱신, "VSCode 내역" 개명 | 없음 |
| `e6637d7` | 머지 커밋 | — |

`git merge-tree` 로 미리 합쳐 봤다 — **충돌 0**.

### ② BACKLOG2 가 담당자 2 에게 준 것

| BACKLOG2 | 항목 | 내 판단 |
|---|---|---|
| §4 | **미푸시 커밋을 서버로** (계약 변경) | 오늘 오전. 확장 쪽은 이미 수집하고 있어 **서버가 병목** |
| §2-2 | **보안 점검** (둘 다) | 표의 9칸 중 서버 것 7칸이 내 코드. 오후 |
| §4 | F-2 `raw_diff` 가 "여전히 비어 있다" | **코드상으로는 채우고 있다** — `GitHubCollector.toActivity` 가 커밋 상세를 받아 `DiffTruncator` 로 넣는다 (`GitHubCollector.java:343`). DB 를 비웠으니 비어 보였을 것. 오후에 동기화 후 확인하고, 화면이 쓸 **파일 목록**만 보탠다 |
| §2-1 | 내부 IP 접속 — 담당자 1 로 적혀 있음 | **막는 것이 전부 서버 설정이다** (`FRONTEND_URL`, `GITHUB_REDIRECT_URI`, CORS). 아래 ③ 대로 나눈다 |

### ③ 오늘 역할 분배 — 09:20 스탠드업에서 확정

BACKLOG2 는 §2-1·§2-3 을 통째로 담당자 1 로 적었지만, 코드 소유로 가르면 이렇다.

| 항목 | 담당자 1 (배태일) | 담당자 2 (조웅식) |
|---|---|---|
| **§2-1 내부 IP** | 확장 `serverUrl` 을 명령으로 받기 · 프론트 `VITE_API_TARGET` (프록시 대상을 공용 백엔드로) | **콜백 복귀 주소를 요청에서 정하고 사내망 대역만 허용** (A+B), CORS, `.env` 정리 → §2 |
| **§4 미푸시 커밋** | 확장이 페이로드에 싣기 · README 의 "보내지 않는다" 수정 | **계약·V10·엔티티·초안 반영** → §3. **12:00 전에 푸시** |
| **§2-2 보안** | 확장 API Key `SecretStorage` | 읽기 API 범위 · JWT 무효화 · 관리자 기본 비밀번호 · 비밀번호 초기화 → §4 |
| **§2-3 확장** | 배포 `.vsix` · AI 세션 제목/답변/요약 · TODO·저장 확인 · 날짜별 페이지네이션 (`GET /vscode/sessions` 에 `from`·`to`) | 없음. 단 AI 세션 형식 변경은 **V11** 로 (내가 V10 을 쓴다) |
| **F-2** | 근거 패널 파일 트리 + diff 뷰어 | `raw_diff` 확인 + `files[]` 응답 → §5 |
| **연동 테스트** | 함께 | 함께 → §6 |
| Mattermost 다듬기 | — | 시간 남으면 → §7 |

**같은 파일을 만지는 곳이 한 군데 있다** — `SessionRequest` · `VscodeSession` · `SessionResponse` · `WorklogWriter`. 미푸시 커밋(나, 오전)과 AI 세션 변경(담당자 1, 오후)이 둘 다 여기를 고친다. **내가 먼저 끝내고 푸시하면 그쪽이 당겨서 그 위에 쌓는다.** 스탠드업에서 순서를 못 박는다.

### ④ 스탠드업에서 정할 것 (09:20, 20분)

1. **공용 백엔드를 어느 PC 에 띄우나** — 그 PC 의 IP 가 오늘의 `FRONTEND_URL` · `GITHUB_REDIRECT_URI` · 확장 `serverUrl` 이 된다
2. 마이그레이션 번호: **V10 = 담당자 2 (미푸시 커밋 + 보안 컬럼), V11 = 담당자 1 (AI 세션)**
3. 미푸시 커밋 필드명 — 확장의 `UnpushedCommit { sha, subject, at }` 를 서버는 `{ sha, subject, committedAt }` 로 받는다 (`at` 은 뜻이 안 보인다). 그쪽이 보낼 때 이름만 바꾼다
4. §2-1 의 A+B 로 가는지 — BACKLOG2 가 권한 안이고 나도 같은 판단. 이견 없으면 바로 시작

---

## 1. 오늘 타임라인 (09:00 → 18:00)

| 시간 | 작업 | 커밋 메시지 |
|---|---|---|
| 09:00–09:20 | **0. 병합 + 기동 확인** | — |
| 09:20–09:40 | **스탠드업** (§0-④) | — |
| 09:40–10:50 | **A. 내부 IP 접속 — 서버 쪽** 🔴 | `feat(auth): OAuth 복귀 주소를 요청에서 정하고 사내망 대역만 허용한다` |
| 10:50–12:00 | **B. 미푸시 커밋 계약 변경 + V10** 🟡 → **12:00 푸시** | `feat(vscode): 미푸시 커밋을 세션에 담는다 (V10)` |
| 12:00–13:00 | 점심 | |
| 13:00–14:30 | **C. 보안 점검 — 서버 몫** 🔴 | `fix(auth): 일반 회원은 자기 것만 조회한다` · `feat(auth): 비밀번호를 바꾸면 이전 토큰이 죽는다` · `feat(admin): 비밀번호 초기화, 기본 관리자 비밀번호 경고` |
| 14:30–15:00 | **D. F-2 — `raw_diff` 확인 + `files[]`** | `feat(activity): 상세 응답에 변경 파일 목록` |
| 15:00–16:30 | **E. 연동 테스트 — 두 PC, 함께** ⬜ | `test: 9/11 연동 테스트 결과` |
| 16:30–17:15 | **F. Mattermost 다듬기** (남으면) | `feat(chat): 봇 답에 한 줄 요약` |
| 17:15–18:00 | **G. 최종 리뷰 · 머지 · 정리 문서** | `docs: 4일차 작업 정리` |

### 순서 근거

- **A 를 맨 앞에**: E(연동 테스트)의 전제다. 담당자 1 의 확장·프론트 쪽도 오전에 끝나므로 점심 전에 양쪽이 맞물린다
- **B 를 12:00 전에**: 담당자 1 의 오후 작업(AI 세션 V11)이 같은 파일 위에 쌓인다. 내가 늦으면 그쪽이 충돌을 떠안는다
- **C 를 오후에**: 서버만 바꾸는 일이라 남을 기다리게 하지 않는다. 단 C-1(읽기 범위)은 E 전에 끝내야 테스트가 한 번에 된다
- **밀리면 버리는 순서**: F → D → C-3(비밀번호 초기화) → B 의 `DraftTemplate` 반영(`WorklogWriter` 만 남김). **A · B 의 계약 · C-1 · C-2 는 버리지 않는다**

---

## 2. 작업 0 — 병합 + 기동 확인 (09:00–09:20)

```bash
git fetch --all --prune
git merge origin/VsPeristalsis_dashboard          # 4개, 충돌 없음 (merge-tree 로 확인함)
cd backend && ./gradlew test -q                    # 어제 235개 통과. 담당자 1 커밋 포함해 다시
set -a && . ./.env && set +a && ./gradlew bootRun
git push origin git_peristalsis
```

확인 세 가지 (DAY3_arrange §5):

- [ ] 관리자 콘솔 → 알림 → 봇 상태 **연결됨** (재기동 후 5초 안에 다시 로그인한다)
- [ ] `9998` 로그인 → 설정 → GitHub 연결이 **새 경로(`POST /me/github/start`)** 로 도는지 — 이 Mac 에서
- [ ] 팀원 내역에 배태일 76건이 붙는지

> 태그는 `day2-integration` 까지만 있다. 어제 계획의 `v0.1.0` 은 찍지 않았다. **오늘 G 에서 `v0.2.0`** 을 찍는다 (메뉴 재구성·관리자 콘솔·자체 로그인이 다 들어간 첫 판이라 0.1 은 아니다).

---

## 3. 작업 A — 내부 IP 접속, 서버 쪽 (09:40–10:50) 🔴

### 지금 무엇이 막는가 (코드 기준)

| 자리 | 파일 | 문제 |
|---|---|---|
| `worklog.frontend-url` 하나로 세 가지를 한다 | `application.yml:34` | ① 콜백 뒤 복귀 주소 (`GitHubOAuthController:117,120,129`) ② CORS 허용 원본 (`SecurityConfig:97`) ③ Mattermost 메시지의 링크 (`NotifyService`, `WorkLogAnswerService`) |
| `GITHUB_REDIRECT_URI` | `application.yml:67` | OAuth App 에 등록한 주소와 글자까지 같아야 한다. 비우면 요청 호스트로 만드는데, 프록시 뒤라 `localhost:8080` 이 된다 |
| Vite 프록시 대상 | `frontend/vite.config.ts` | `localhost:8080` 고정 — 각자 vite 를 띄우면 각자 백엔드를 부른다 (**담당자 1**: `VITE_API_TARGET`) |

담당자 2 가 어제 state 를 서명으로 옮겨(`7812b51`) 쿠키 문제는 풀었다. 남은 것은 **"어디로 돌아가느냐"** 다.

### 설계 — BACKLOG2 §2-1 의 A + B

`POST /me/github/start` 는 프론트가 **fetch 로 부르는 인증된 요청**이라 `Origin` 헤더가 온다 (Vite 프록시는 `Host` 만 바꾸고 `Origin` 은 그대로 넘긴다). 이 값을 **서명된 state 에 넣어** 콜백에서 그리로 돌려보낸다.

| 단계 | 파일 | 변경 |
|---|---|---|
| 1 | `auth/OriginPolicy.java` (신규) | 허용 판정 한 곳. 기본 허용: `localhost`, `127.0.0.1`, `10/8`, `172.16/12`, `192.168/16`. `WORKLOG_ALLOWED_ORIGINS` (쉼표, 호스트·CIDR)가 있으면 그것을 **더한다**. 포트는 보지 않는다(vite 5173 · 백엔드 8080 둘 다 올 수 있다). 스킴은 `http`·`https` 만 |
| 2 | `auth/OAuthStateCodec.java` | `issue(linkUserId, returnTo)` — `Parsed` 에 `returnTo` 추가. 서명 범위에 포함 (바꿔치기 방지) |
| 3 | `auth/GitHubLinkController.start` | `Origin` 을 `OriginPolicy` 로 검사 → 통과하면 state 에 싣고, 아니면 `null` (그러면 `FRONTEND_URL` 로 간다). **거절해도 400 을 내지 않는다** — 그냥 기본값으로 돌아가게 두고 로그만 남긴다 |
| 4 | `auth/GitHubOAuthController.callback` | 복귀 주소 = `parsed.returnTo()` 가 있으면 그것, 없으면 `frontendUrl`. **세 곳(`:117`, `:120`, `:129`)을 한 메서드로** |
| 5 | `config/SecurityConfig.corsConfigurationSource` | `setAllowedOrigins(List.of(frontendUrl))` → `setAllowedOriginPatterns` + `OriginPolicy` 와 같은 대역. 프록시를 쓰면 CORS 를 안 타지만, 프록시 없이 `:8080` 을 직접 부르는 사람이 생겨도 막히지 않게 |
| 6 | `.env.example` · `application.yml` | `WORKLOG_ALLOWED_ORIGINS` 설명. `GITHUB_REDIRECT_URI` 주석을 **"공용 호스트 IP 의 5173 (프록시 경유)"** 로 |

`GET /auth/github` (GitHub 로그인)은 브라우저 이동이라 `Origin` 이 없다 → `Referer` 를 같은 정책으로 보고, 없으면 `FRONTEND_URL`. 사원번호 로그인이 주 경로가 됐으니 여기에 공을 들이지 않는다.

### 열린 리다이렉트를 막는 근거

`returnTo` 는 (1) 서명 안에 있어 위조가 안 되고 (2) 넣을 때 사내망 대역만 통과한다. 둘 중 하나만 있어도 되지만, (1)만 있으면 **서버가 직접 서명해 준 공격자 주소**가 되니 (2)가 필수다 — BACKLOG2 §2-2 셋째 줄.

### 오늘 테스트에 필요한 설정 (공용 호스트 = 이 Mac 이라고 가정, IP 는 스탠드업에서)

```
# backend/.env (공용 호스트 PC)
FRONTEND_URL=http://192.168.1.224:5173
GITHUB_REDIRECT_URI=http://192.168.1.224:5173/api/auth/github/callback   # 5173 프록시 경유 — 8080 을 열 필요가 없다
# GitHub OAuth App 의 Authorization callback URL 도 같은 값으로 (§8-1)
```

### 테스트

- `OriginPolicyTest` — `http://192.168.1.218:5173` 허용 / `http://evil.example.com` 거절 / `ftp://…` 거절 / `WORKLOG_ALLOWED_ORIGINS=203.0.113.0/24` 추가 허용
- `OAuthStateCodecTest` — `returnTo` 왕복, 한 글자 바꾸면 검증 실패 (기존 테스트에 케이스 추가)
- 컨트롤러 — `Origin` 허용 시 콜백이 그 주소로 302 / 거절 시 `FRONTEND_URL` 로 302

### 완료 조건

- [ ] 담당자 1 PC 에서 `http://<공용 IP>:5173` 열기 → `9998` 로그인 → 설정 → GitHub 연결 → **그 PC 의 화면으로** `?github=linked` 복귀
- [ ] 같은 흐름을 이 Mac 의 `localhost:5173` 에서 해도 된다 (예전 경로 유지)

---

## 4. 작업 B — 미푸시 커밋 계약 변경 + V10 (10:50–12:00) 🟡

TODO_0910 §3-2 · BACKLOG2 §4. 확장은 이미 `git log upstream..HEAD` 로 모아 사이드바에 보여 준다 (`vscode-extension/src/git.ts:109`). **서버에 자리가 없어 못 보내고 있다.**

### 계약 (PRD §7 F6 페이로드에 6번 항목으로 추가)

```jsonc
// POST /vscode/sessions — 기존 필드에 더한다
"unpushedCommits": [
  { "sha": "be292b4", "subject": "fix: 재생성 후 흰 화면", "committedAt": "2026-09-11T10:12:00+09:00" }
]
// GET /vscode/sessions 응답에도 같은 이름으로 돌려준다
```

- 없으면 빈 배열. 업스트림이 없어 확장이 `undefined` 를 주면 서버는 빈 배열로 본다 (`orEmpty` 재사용)
- 상한 50건. 넘으면 앞 50건 + `unpushedCount` 는 두지 않는다 — 50개 넘게 안 올린 사람은 없다고 본다

### 변경

| 파일 | 내용 |
|---|---|
| `db/migration/V10__unpushed_commits_and_password_changed.sql` | `vscode_sessions.unpushed_commits JSONB NOT NULL DEFAULT '[]'` · **`users.password_changed_at TIMESTAMPTZ`** (C-2 가 쓴다 — 마이그레이션 하나로) · **`activities.files JSONB NOT NULL DEFAULT '[]'`** (D 가 쓴다). 내 오늘 변경을 V10 하나에 모은다. V11 은 담당자 1 |
| `vscode/UnpushedCommit.java` (신규) | `record(String sha, String subject, OffsetDateTime committedAt)` |
| `vscode/SessionRequest.java` · `VscodeSession.java` · `SessionResponse.java` · `VscodeSessionService.upsert` | 필드 하나씩. 기존 `aiSessions` 와 같은 모양으로 |
| `draft/WorklogWriter.sessionLines` | 세션 아래 `· 커밋(푸시 전) be292b4 fix: …` 줄. **커밋 메시지는 사람이 이미 쓴 요약이라 미커밋 파일 목록보다 위에** 둔다 |
| `draft/DraftTemplate.inProgressLine` | 템플릿 폴백에도 같은 줄 |
| `notify/RemindPolicy.java` (판정 한 곳) | 미푸시 커밋이 있으면 "마지막 커밋 6시간" 판정에 **그 커밋 시각을 쓴다** — 커밋은 했는데 푸시만 안 한 사람에게 "커밋하세요" 라고 하면 틀린 말이다. `lastCommitAt` 이 이미 확장에서 오니 여기서 새로 계산하지는 않고, `lastCommitAt` 이 null 이고 `unpushedCommits` 가 있으면 그 최신 시각으로 대신한다 |
| `docs/PRD_090910.md` §7 · F6 | 페이로드 6번 항목, `GET` 응답 |

### 테스트

- `VscodeSessionServiceTest` — 저장·재전송(UPSERT) 시 덮어쓰기, `null` → 빈 배열
- `WorklogWriterTest` · `DraftTemplateTest` — 미푸시 줄이 들어가는지, 없으면 안 들어가는지
- 리마인드 — `lastCommitAt=null` + 미푸시 커밋 1시간 전 → 리마인드 **안 함**

### 완료 조건

- [ ] `curl -X POST -H "X-Api-Key: …" /api/vscode/sessions` 에 `unpushedCommits` 넣어 200 → `GET` 으로 돌아온다
- [ ] 초안 AI 생성 프롬프트(`WorklogWriter`)에 줄이 들어간다 — 로그로 확인
- [ ] **12:00 푸시**, 담당자 1 에게 "V10 올렸다, 확장에서 `unpushedCommits` 보내면 된다" 전달

---

## 5. 작업 C — 보안 점검, 서버 몫 (13:00–14:30) 🔴

BACKLOG2 §2-2 표를 코드로 다시 봤다. 순서는 **위험 × 싸게 막을 수 있는 정도**.

### C-1. 일반 회원이 `userId` 로 남의 것을 본다 (30분) — 가장 크다

담당자 1 이 "팀 데이터를 화면에서 끊었다" 고 했지만 **서버는 그대로다.** `MEMBER` 토큰으로 `?userId=` 를 바꾸면 남의 것이 나온다.

| 경로 | 지금 | 파일 |
|---|---|---|
| `GET /activities?userId=` | 아무 값 | `ActivityController:33` |
| `GET /activities/{id}` | 아무 id | `ActivityController:47` |
| `GET /drafts?userId=` · `GET /drafts/{id}` | 아무 값. **일지 본문**이 그대로 나온다 | `DraftController:48,68` · `DraftService.detail:112` (소유자 검사 없음 — `updateContent` 에는 있다) |
| `GET /stats/daily` | 전원 합계 | `StatsController:29` — BACKLOG2 가 "팀 전원 숫자가 실려 온다" 고 한 곳 |
| `GET /stats/people?userId=` | 아무 값 | `StatsController:47` |
| `GET /vscode/sessions?userId=` | 아무 값. **미커밋 diff 본문**이 그대로 나온다 | `VscodeSessionController:41` |

**규칙 하나**: `MEMBER` 는 `userId` 를 뭘 주든 **자기 id 로 바꾼다**. 남의 id 를 명시하면 `403 NOT_YOUR_DATA` (조용히 바꾸면 화면 버그를 못 찾는다). 단건 조회(`/{id}`)는 소유자가 다르면 404 (있는지 없는지도 알리지 않는다). `ADMIN` 은 지금처럼.

- `auth/DataScope.java` (신규) — `Long userIdFor(AuthenticatedUser p, Long requested)` 한 메서드. 여섯 컨트롤러가 이것만 부른다
- `/stats/daily` 는 `userId` 를 새로 받아 `MEMBER` 면 자기 것만 합산 — `StatsService.daily(date, userId)` 오버로드. 관리자 콘솔 개요는 `ADMIN` 이라 그대로 전원
- 주인 없는 활동(`user=null`, 미가입 기여자)은 `MEMBER` 에게는 안 보인다 — 담당자 1 이 이미 `a.user !== null` 로 거르고 있어 화면 변화 없음
- **관리자 콘솔의 팀원 내역**(`/stats/people`, `/activities` 를 `ADMIN` 으로 부름)은 영향 없음 — E 에서 다시 확인

테스트: 컨트롤러마다 `MEMBER` + 남의 `userId` → 403 / 자기 것 → 200 / `ADMIN` → 200. `DraftService.detail` 남의 것 → 404.

### C-2. JWT 는 12시간 동안 아무도 못 죽인다 (30분)

로그아웃이 클라이언트에서 버리는 것뿐이고, 비밀번호를 바꿔도 옛 토큰이 산다. **`iat` < `users.password_changed_at` 이면 401** — 가장 싼 무효화. V10 컬럼 (§4).

- `JwtAuthFilter` — 이미 요청마다 `findById` 로 사용자를 읽는다 (`:53`, 어제 `2ee94c1`). 거기서 한 줄 더 비교. `JwtService.verify` 가 `issuedAt` 을 돌려주게 `AuthenticatedUser` 에 `issuedAt` 추가
- `LocalAuthController.changePassword` — 성공 시 `passwordChangedAt = now`. **바꾼 직후의 요청이 자기 새 토큰에 막히지 않게** 응답에 **새 토큰**을 실어 준다 (`ChangePasswordResponse.token`). 프론트 `PasswordPage` 가 그것을 저장하게 — **담당자 1 파일** 이라 한 줄 요청
- 효과: "비밀번호 변경 = 다른 기기 강제 로그아웃". C-3 의 초기화도 이걸 탄다

### C-3. 최초 비밀번호 = 사원번호 (30분) — 결정은 유지, **되돌릴 수단**만 만든다

회의가 "강제하지 않는다" 로 정했다 (`908839b`). 사원번호는 목록 API 로 누구나 보니, **아직 로그인 안 한 사람 계정에 남이 먼저 들어갈 수 있는 것**은 사실이다. 정책을 내가 뒤집지 않는다. 대신:

- `POST /admin/users/{id}/password-reset` — 비밀번호를 사원번호로 되돌리고 `mustChangePassword=true`, `passwordChangedAt=now` (C-2 로 도둑의 토큰도 죽는다). 관리자 콘솔 직원·계정 표에 [초기화] 버튼 (`EmployeeTable.tsx` — 내 파일)
- `EmployeeAccountService.provisionOnFirstLogin` 에 **INFO 로그** "사번 N 최초 로그인 (IP)". 사고 났을 때 언제 누가 들어왔는지 볼 최소한
- 정책 자체는 §8-2 에 올린다

### C-4. `admin` / `admin1234` (15분)

- `AdminAccountInitializer` — 초기 비밀번호가 기본값이면 기동 로그에 **WARN**
- `GET /admin/overview` 응답에 `defaultAdminPassword: boolean` → 콘솔 개요 상단에 빨간 띠 "관리자 비밀번호가 기본값입니다" (`AdminOverviewPage.tsx` — 내 파일)
- 판정은 해시 비교 (`PasswordHasher.matches("admin1234", hash)`), 기동 때 한 번 계산해 들고 있는다

### 손대지 않는 것 (BACKLOG2 표에 있지만)

| 항목 | 이유 |
|---|---|
| 확장 API Key 평문 → `SecretStorage` | 담당자 1 영역 |
| 확장이 미커밋 diff 본문을 보내는 것 | 기획 판단 (§8-4). `collectDiff` 설정으로 끌 수 있다 |
| `/settings/llm` · `/settings/notify` ADMIN 잠금 · 와플 키 | ✅ 이미 됨 |

---

## 6. 작업 D — F-2, `raw_diff` 확인 + `files[]` (14:30–15:00)

### 먼저 사실 확인 (5분)

```sql
SELECT count(*) FILTER (WHERE raw_diff IS NOT NULL AND raw_diff <> '') AS with_diff, count(*) AS total
FROM activities WHERE type = 'COMMIT';
```

콘솔에서 [전체 동기화] 후 실행. 코드상 `toActivity` 가 커밋 상세(`getCommit`, `GitHubCollector:149`)를 받아 `DiffTruncator.truncate(files)` 로 채운다 — **파일당 200줄 · 커밋당 상한** 으로 잘린 텍스트다. 비어 있다면 그건 버그이고 오늘 여기서 잡는다. 차 있으면 BACKLOG2 §4 의 "여전히 비어 있다" 를 정정한다.

### 화면이 쓸 것 — 파일 목록 (25분)

담당자 1 의 파일 트리 + diff 뷰어는 **파일 단위**가 필요하다. `raw_diff` 는 `--- <path>\n` 헤더로 이어 붙인 한 덩어리라 파싱하기 나쁘다.

- `activities.files JSONB` (V10) — `[{ path, status, additions, deletions }]`. GitHub 커밋 상세의 `files[]` 에서 그대로. diff 본문은 **지금처럼 `raw_diff` 한 덩어리**로 둔다 (BACKLOG F-2 의 "목록·통계는 저장, 본문은 절약" 과 같은 선)
- `ActivityDetailResponse.files` 추가. `rawDiff` 는 그대로
- 이미 저장된 활동은 `files` 가 빈 배열 — 오늘 DB 를 비우고 다시 받으니 문제 없다. 운영에서는 [전체 동기화] `full=true` 가 다시 채운다 (수집기가 UNIQUE 위반을 "이미 있음" 으로 넘기므로 **갱신 경로가 없다** → `full` 일 때는 `files` 가 비어 있으면 채우도록 `save` 앞에 한 줄)

담당자 1 에게: "`GET /activities/{id}` 에 `files[]` 가 붙는다. diff 본문은 `rawDiff` 를 `--- path` 로 쪼개 쓰면 된다."

---

## 7. 작업 E — 연동 테스트, 두 PC 에서 함께 (15:00–16:30) ⬜

BACKLOG2 §3 "처음부터 테스트하는 순서" 를 **담당자 1 PC 에서** 돌리고, 나는 공용 백엔드 쪽·콘솔 쪽을 본다. 결과는 `docs/E2E_0911.md` 에 남긴다 (어제 계획의 `E2E_day3.md` 는 만들지 못했다).

| # | 담당자 1 PC 에서 | 내가 보는 것 |
|---|---|---|
| 1 | `http://<공용 IP>:5173` → `9998` 로그인 | 서버 로그 "최초 로그인" (C-3) · 직원·계정 표에 서비스 계정 생김 |
| 2 | 설정 → GitHub 연결 → **그 PC 로 복귀** (A) → 전체 등록 | 직원 표 GitHub 연동 ✓ · 리포 수 · **팀원 내역에 배태일 76건** |
| 3 | 깃허브 내역 | `raw_diff` · `files[]` (D) · `MEMBER` 로 남의 `userId` → 403 (C-1) |
| 4 | VS Code → API Key 입력 → **서버 주소 입력**(담당자 1 신규) → 전송 | 직원 표 **VS Code 연동 → 연결됨** (DAY3_arrange §5-2) · `unpushedCommits` 가 들어오는지 (B) · 미커밋 리마인드 스케줄러를 손으로 한 번 |
| 5 | 업무 일지 → AI 생성 | 프롬프트에 미푸시 커밋 줄 · Mattermost 에 "초안 생성" 알림 |
| 6 | Mattermost 채널에 "배태일 오늘 뭐함" | 봇이 답한다 (초안이 있으면 초안) |
| 7 | 비밀번호 변경 → 다른 탭의 옛 토큰으로 요청 | 401 (C-2) |

**두 PC 가 같은 DB 를 보는지** 가 이 테스트의 전부다 — 각자 백엔드를 띄우면 다 통과하면서 아무 의미가 없다. 시작할 때 `GET /api/health` 응답을 양쪽에서 비교한다.

---

## 8. 작업 F — Mattermost 다듬기 (16:30–17:15, 남으면)

DAY3_arrange §5-3·4. 둘 다 작다.

- **봇 답에 한 줄 요약** — `WorkLogAnswerService.render` 가 지금은 초안 본문을 통째로 준다. 맨 위에 LLM 한 줄 ("오늘은 관리자 콘솔 봇 연결과 권한 버그 셋을 고쳤다"). `SummaryService` 의 프로바이더를 그대로 쓰고, 실패하면 요약 줄만 빼고 그대로 준다. gemma4 5~10초는 봇(비동기)에서 부담 없다
- **`worklog-bot` 별도 계정** — 사람 계정(`@ungsikjo`)이 묻고 답하는 모양이 어색하다. 계정만 만들어지면 콘솔에서 두 줄 (§8-3)

---

## 9. 작업 G — 최종 리뷰 · 머지 · 정리 (17:15–18:00)

```bash
cd backend && ./gradlew test -q
cd ../frontend && npm run build
git checkout main && git pull && git merge git_peristalsis && git push origin main
git tag -a v0.2.0 -m "WorkLog Drafter v0.2.0 — 사원번호 로그인·관리자 콘솔·내부망 접속" && git push origin v0.2.0
```

- 담당자 1 과 **서로의 브랜치를 30분 리뷰** — BACKLOG2 §2-4 "리뷰까지 하고 마무리". 나는 그쪽 `GitHubLinkController` 변경(`d32b5a9`)과 AI 세션 V11 을 본다
- `docs/DAY4_arrange.md` — 오늘 정리와 내일 가이드. BACKLOG2 §4 표를 갱신해 그쪽에 넘긴다

---

## 10. 사람이 해야 하는 것 (기획 · 계정 주인)

| # | 항목 | 언제까지 | 없으면 |
|---|---|---|---|
| 1 | **GitHub OAuth App 의 callback URL** 을 `http://<공용 IP>:5173/api/auth/github/callback` 으로 바꾸기 (OAuth App 주인) | 10:50 (A 끝날 때) | 다른 PC 의 GitHub 연동만 안 된다. 이 Mac 에서는 됨 |
| 2 | **최초 비밀번호 = 사원번호 정책** — 첫 로그인 전 계정을 남이 선점할 수 있다. 유지할지, 관리자가 발급하는 방식(BACKLOG F-4·F-5)으로 갈지 | 오늘 중 | 오늘은 C-3 의 [초기화] 로 사고 뒤 복구만 가능 |
| 3 | Mattermost 에 `worklog-bot` 회원 초대 | F 시작 전 | 지금처럼 `@ungsikjo` 로 |
| 4 | 확장이 **미등록 리포**(개인 프로젝트)의 diff 도 보낼지 · "전체 등록" 이 조직 전체인지 | 오늘 중 | BACKLOG2 §4 그대로. 오늘 작업에 영향 없음 |
| 5 | 시연 뒤 **Mattermost 비밀번호 · API Key 교체** (채팅에 적혔다, DAY3_arrange §4) | 시연 뒤 | — |

---

## 11. 오늘 끝났을 때 (수용 기준)

- [ ] 담당자 1 브랜치 4개 병합, 테스트 전건 통과
- [ ] 다른 PC 에서 시작한 GitHub 연동이 **그 PC 로 돌아온다**. 사내망 밖 주소는 `FRONTEND_URL` 로 떨어진다
- [ ] `POST /vscode/sessions` 가 `unpushedCommits` 를 받고 `GET` 이 돌려준다. 초안 프롬프트와 템플릿에 줄이 들어간다. **12:00 푸시**
- [ ] `MEMBER` 가 남의 `userId` 로 `/activities` · `/drafts` · `/stats/*` · `/vscode/sessions` 를 부르면 403, 남의 `/drafts/{id}` 는 404
- [ ] 비밀번호를 바꾸면 이전 토큰이 401
- [ ] 관리자 콘솔에 [비밀번호 초기화], 기본 관리자 비밀번호 경고 띠
- [ ] `activities.raw_diff` 가 채워지는 것을 SQL 로 확인, `GET /activities/{id}` 에 `files[]`
- [ ] `docs/E2E_0911.md` — 두 PC · 한 백엔드로 7단계 결과
- [ ] `main` 머지 + `v0.2.0` + `DAY4_arrange.md`

---

## 12. 오늘 하지 않는 것

| 항목 | 이유 |
|---|---|
| AI 세션 제목·답변·요약 (V11) | 담당자 1. 수신 API 도 그쪽 소유 (BACKLOG2 §2-3) |
| `GET /vscode/sessions` 에 `from`·`to` | 위와 같은 이유. 내 C-1 의 `DataScope` 를 그대로 쓰면 된다고 알려 준다 |
| `.vsix` 배포 · 마켓플레이스 | 담당자 1. A 안(GitHub 릴리스)에 동의 |
| 최초 비밀번호 정책 변경 | 회의 결정. §10-2 로 올리고 오늘은 복구 수단만 |
| 로그인 실패 잠금 (rate limit) | 문제는 "비밀번호를 안다" 는 것이라 잠금이 막지 못한다. 위 결정 뒤에 |
| 확장 API Key `SecretStorage` | 담당자 1 |
| F-2 diff 본문을 파일별 컬럼으로 | 오늘은 목록만. 본문은 `raw_diff` 한 덩어리로 충분히 그릴 수 있다 |
| 회원 발급을 관리자가 (F-4·F-5) | §10-2 결정에 딸려 있다 |
