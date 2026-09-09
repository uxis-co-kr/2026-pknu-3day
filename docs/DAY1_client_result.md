# 1일차 작업 결과 — 담당자 1 (클라이언트 트랙)

날짜: 2026-09-09 · 브랜치 `VsPeristalsis_dashboard` · 태그 `day1-client` · 커밋 `3254423`
기준 문서: [PRD_090910.md](PRD_090910.md) · [DESIGN_BRIEF.md](DESIGN_BRIEF.md) · [TODO_client.md](TODO_client.md)

> 한 줄 요약: **1일차 P0 전부 완료.** 디자인 확정 → 목업 → 화면 6개 → 세션 수신 API 까지 갔고,
> 3일차 예정이던 F9·F10 도 미리 끝냈다. 남은 것은 2일차의 확장(1-6·1-7), 초안 API(1-8), 실서버 전환(1-9).

---

## 1. 끝난 것

| ID | 작업 | 결과 |
|---|---|---|
| 1-0a | `frontend/` · `vscode-extension/` 스캐폴딩 | 둘 다 빌드 통과 (backend·docker 는 담당자 2) |
| 1-0c | §7 API 계약 확정 | PR #2 로 문서 반영 후 머지 |
| 1-1 | Figma 아트보드 9장 확정 + 목업 JSON 14개 | 아트보드 숫자와 목업이 일치하는지 스크립트로 검산 |
| 1-2 | `apiClient` + TanStack Query 훅 + 메모리 목업 서버 | 저장·확정·재생성이 목업 상태에서 실제로 동작 |
| 1-3 | **화면 6개 전부** | 헤드리스 크롬 1440×900 스크린샷으로 아트보드와 1:1 대조 |
| 1-4 | `POST/GET /vscode/sessions` | 실서버 curl 검증 통과 (§5) |
| 1-5 | 커밋 + 태그 `day1-client` | |
| 1-13 | F9 `/settings` LLM 옵션 · F10 `/people` 통계 | **3일차 예정이었으나 1일차에 완료** |

프론트 소스 30개(shadcn 생성분 제외) · 목업 14개 · 백엔드 테스트 54개 통과(내가 추가한 16개 포함).

## 2. 화면 6개

| 경로 | 아트보드 | 비고 |
|---|---|---|
| `/login` | 1 | GitHub 로그인은 fetch 가 아니라 브라우저 이동 |
| `/` 홈 | 2 | 요약 카드 4 · 필터 3종 · 사용자 카드 3 · 미커밋 세션 행 |
| `/drafts/:id` | 3, 4 | DRAFT/CONFIRMED 두 상태. 근거 행을 누르면 에디터의 해당 줄로 이동 |
| `/repos` | 5, 6 | 등록·동기화·삭제 + 빈 상태 |
| `/people` | 7 | Recharts 막대 + **이번 달은 GitHub 잔디 달력** + 확장되는 표 |
| `/settings` | 8, 9 | API Key 표 + 발급 Dialog(1회 노출) · Mattermost · LLM 라디오 |

- `/auth/done?token=` 콜백 라우트와 `RequireAuth` 가드는 미리 넣어 뒀다. 목업 모드에서는 가드가 통과된다.
- 첫 로딩 번들 **420 kB (gzip 132 kB)**. `recharts`·`react-markdown` 을 쓰는 초안 편집·인원 화면은 lazy 로 분리했다.
- 다크 모드 토큰은 두지 않았다 (디자인 브리프 5. "하지 말 것").

## 3. 목업 데이터 (`frontend/src/mocks/`)

`VITE_USE_MOCK=true` 면 메모리 목업 서버가 실서버와 **같은 경로·같은 응답 모양**으로 답한다.
목록은 배열, `/activities` 만 페이지 래퍼 — 담당자 2의 인수인계 문서 규약을 그대로 따랐다.

아트보드 2의 모든 숫자가 목업과 맞는지 검산했다.

| 검산 | 값 |
|---|---|
| 요약 카드 커밋 | 12 |
| 사용자별 | 배태일 커밋2·PR1·머지1 / 김민수 커밋6·PR1 / 이서연 커밋4·PR1 |
| 리포별 커밋 | punchcheck 9 · salty-web 3 (리포 표의 "오늘 활동 수") |
| 미커밋 세션 | 2건 (6시간 이상 1건) |

## 4. API 계약 변경 — PR #2 (머지됨)

확정된 화면이 요구하는데 §7 에 없던 필드를 추가했다. "PRD 먼저 → 알림 → 코드" 순서로 진행했다.

| 위치 | 필드 | 쓰는 곳 |
|---|---|---|
| `GET /activities` | `externalId` | "PR #12 열림" 표기 |
| `GET /stats/daily` | `commitsDelta` | 커밋 카드 "어제 대비 +3" |
| `GET /stats/daily` | `staleSessions` | 미커밋 카드 "⚠ 6시간 이상 1건" |
| `GET /stats/daily` | `byUser[].sessions` | 사용자 카드 헤더 "미커밋 1" |
| `GET /repos` | `todayActivityCount`, `syncStatus` | 리포 표의 활동 수 · 상태 배지 |
| `GET /stats/people` | `totals`, `series[].draft` | 인원 화면 (P2) |

## 5. 1-4 세션 수신 API — 검증 로그

`backend/src/main/java/com/worklog/vscode/` — 컨트롤러·서비스·리포지터리·URL 파서·DTO 2개.
`POST` 는 `SecurityConfig` 에서 `X-Api-Key` 전용으로 이미 열려 있어 컨트롤러엔 인증 설정이 없다.

실제 서버(postgres + bootRun)에 붙여 확인했다.

```
① POST 1회차             → 200 {"id":1}
② POST 2회차 (파일 1→2)  → 200 {"id":1}   행 수 1 유지, 내용만 갱신
③ planNote 를 null 로 재전송 → 서버에 남은 메모 유지
④ 등록된 리포            → repo {id:1, fullName:"withly/punchcheck"}
⑤ 미등록 ssh URL         → repo: null 로 저장
⑥ 잘못된 키 / 키 없음    → 401 {"code":"UNAUTHORIZED"}      ← E2E 시나리오 9
⑦ remoteUrl 누락         → 400 {"code":"VALIDATION_ERROR"}
⑧ userId=1 → 2건 / userId=99 → 0건 / 다른 날짜 → 0건
```

판단이 들어간 두 곳:

- **계획 메모는 null 로 와도 지우지 않는다.** VS Code 를 다시 켜면 확장이 메모를 잃고 null 을 보내는데,
  그때 서버에 남은 그날 메모까지 날아가면 안 된다.
- **미등록 리포도 세션은 저장한다.** URL 파싱 실패나 미등록은 예외가 아니라 `repo: null` 이다
  (PRD §6 이 `repo_id` NULL 을 허용한다). `RemoteUrlParser` 는 https / ssh / `.git` 유무를 모두 받는다.

`GET /vscode/sessions` 응답은 프론트 목업 `vscode-sessions.json` 과 필드가 같다.

```jsonc
[{ "id": 1, "userId": 3, "repo": {"id":1,"fullName":"withly/punchcheck"} | null,
   "remoteUrl": "...", "branch": "feature/attendance", "workDate": "2026-09-09",
   "uncommittedFiles": [{path, additions, deletions, diff}],
   "todos": [{path, line, text}], "planNote": "...",
   "editTimeline": [{path, firstSavedAt, lastSavedAt, saveCount}],
   "summary": null, "lastCommitAt": "...", "reportedAt": "..." }]
```

## 6. 디자인 확정본에서 바로잡은 것

아트보드를 그대로 구현하려다 내부 모순 3건을 찾아 `DESIGN_BRIEF.md` 를 고쳤다.

| 위치 | 있던 값 | 고친 값 | 이유 |
|---|---|---|---|
| 홈 PR 카드 보조 텍스트 | 열림 2 · 머지 1 | **열림 3 · 머지 1** | 세 명이 각각 PR 을 하나씩 열었다 |
| 홈 사용자 카드 헤더 예시 | 커밋 5 · PR 1 · 미커밋 1 | **커밋 2 · PR 1 · 머지 1 · 미커밋 1** | 아래 나열한 활동 4건과 맞춘다 |
| `/people` 합계·9/9 행 | 33 / 5 / 3, 9/9 커밋 12 | **23 / 4 / 3, 9/9 커밋 2** | 팀 전체 수치가 들어가 있었다. 선택된 사용자 본인 기준으로 |

그 외 결정:

- **초안 목록 화면은 만들지 않는다.** 기획에 없다. 사이드바 "초안" 은 오늘 내 초안으로 보내고, 없으면 비활성.
- **목업의 상대 시각은 2026-09-09 18:00 기준으로 고정**했다. 아트보드가 그 시각을 "지금" 으로 놓고
  그려졌는데 실제 시계를 쓰면 "마지막 커밋 6시간 전" 이 볼 때마다 달라진다. 실서버 모드는 실제 시계를 쓴다.
- 초안 목업은 **7건(CONFIRMED 6 · DRAFT 1)**. TODO 에 적어둔 "DRAFT 2, CONFIRMED 1" 은 디자인 확정 전에
  쓴 메모라, 아트보드 7 의 초안 상태 열을 따랐다.
- PR 활동에는 요약을 넣지 않았다. 아트보드가 커밋 행에만 요약을 그렸다.

## 7. DB 노출 포트 — 내가 낸 PR 을 스스로 되돌린다

PR #1 에서 기본값을 PRD 표준인 5432 로 바꿨는데, **오늘 실서버에 붙이다가 그 판단이 틀렸다는 걸 확인했다.**

내 개발기에 로컬 PostgreSQL 이 `127.0.0.1:5432` 를 잡고 있었다. 도커는 `*:5432` 에 바인딩돼
**충돌 없이 정상으로 뜨지만**, `localhost` 접속은 루프백 전용 바인딩이 이겨서 컨테이너가 아니라
로컬 DB 로 붙는다. `bootRun` 이 `role "worklog" does not exist` 로 죽는다.

→ **PR #3** 으로 기본값을 5433 으로 되돌렸다. PR #1 의 `POSTGRES_PORT` 변수화는 그대로 남는다.

## 8. 담당자 2에게

- **PR #3 리뷰 부탁드립니다.** 위 §7 건입니다.
- 1-4 가 끝났으니 `vscode_sessions` 에 실제 행이 쌓입니다. `DraftGenerator`(F3a)가 "진행 중 / 미커밋"
  절을 채울 때 이 테이블을 그대로 읽으면 됩니다. UPSERT 키는 `(user_id, remote_url, branch, work_date)`.
- 내일 제 **1-9(실서버 전환)** 는 `GET /activities` 와 `GET /stats/daily` 에 걸려 있습니다.
  그쪽 2일차 계획의 `2-6b` 로 잡혀 있는 걸 확인했습니다.
- 검증용으로 넣었던 임시 사용자·키·리포는 지웠습니다. 로컬 DB 는 비어 있습니다.

## 9. 2일차에 할 일

| ID | 작업 |
|---|---|
| 1-6 | 확장 `collector.ts` — `git status/diff`, TODO 스캔, 계획 메모, 저장 타임라인 |
| 1-7 | 확장 `uploader.ts` — 30분 주기 + 종료 시 + `지금 전송`, 상태바 |
| 1-8 | 초안 CRUD API — `GET /drafts`, `GET /drafts/{id}`, `PATCH`, `confirm` |
| 1-9 | `VITE_USE_MOCK=false` 전환 |
| 1-10 | 재생성 · Mattermost 전송을 실 API 로 연결 (UI 는 이미 붙어 있음) |

## 10. 실행 방법

```bash
# DB — 호스트 5433 (루트 .env 의 POSTGRES_PORT)
docker compose up -d

# 백엔드 :8080
cd backend && cp .env.example .env      # JWT_SECRET, ENCRYPTION_KEY 채우기
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
set -a && source .env && set +a && ./gradlew bootRun

# 프론트 :5173 — 목업 모드로 뜬다
cd frontend && cp .env.example .env && npm install && npm run dev

# 확장
cd vscode-extension && npm install && npm run compile
```

디자인 원본: [Figma](https://www.figma.com/design/lxfeTOHM81dyc4TIVEBm8q/)
