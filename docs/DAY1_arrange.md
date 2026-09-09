# 작업 정리 — 담당자 2 (연동 트랙)

작성: 2026-09-09 15:35 · 브랜치 `git_peristalsis` (`3b0b033`) · 태그 `day1-integration`, `day2-integration`
기준 문서: [PRD_090910.md](PRD_090910.md) · [DAY1_integration_plan2.md](DAY1_integration_plan2.md) · [DAY1_integration_plan3.md](DAY1_integration_plan3.md) · [HANDOFF_day1_integration.md](HANDOFF_day1_integration.md)

> 한 줄 요약: **연동 트랙의 P0·P1(F1·F2·F3a·F5·F7·F8)이 전부 붙었다. 남은 것은 3일차 몫(E2E 점검, 미커밋 리마인드, P2)과 아직 `main`에 올리지 않은 커밋 6개다.**

---

## 1. 오늘 끝낸 것

계획 문서 두 개(1일차 오후 2-0~2-5, 2일차 2-6~2-11)를 하루에 모두 소화했다.

### 1일차 오후 — 인증·수집 기반 (태그 `day1-integration`)

| # | 작업 | 검증 |
|---|---|---|
| 2-0 | 보안 골격, `/health`, `{code, message}` 오류 포맷, Repository 4개 | 미인증 요청 전부 `401` |
| 2-1 | `LlmProvider` + `MockLlmProvider` + 프롬프트 파일 분리 | 단위 테스트 13개 |
| 2-2 | GitHub OAuth → JWT(HS256, 12h) → `GET /me` | 실제 브라우저 로그인, 토큰 AES-256-GCM 저장 |
| 2-3 | API Key 발급/폐기 + `X-Api-Key` 필터 + 경로별 인증 규칙 | JWT 로 `POST /vscode/sessions` → 403 |
| 2-4 | 리포 등록·접근 검증 + `GitHubCollector` 커밋 수집 | 실제 리포에서 수집, sync 2회에도 건수 불변 |

### 2일차 — 수집 확장·요약·초안·알림 (태그 `day2-integration`)

| # | 작업 | 검증 |
|---|---|---|
| 2-6 | PR/머지 수집 | PR 하나가 `PR_OPENED`+`PR_MERGED` 두 활동으로. 연속 2회 sync 26건 불변 (E2E 2) |
| 2-7 | 요약 파이프라인 (PENDING→DONE, 3회 재시도) | 전건 `DONE` |
| **2-6b** | **조회 API** — `/activities`, `/activities/{id}`, `/stats/daily`, `/repos` 필드 보강 + V2 마이그레이션 | 담당자 1의 실서버 전환 차단 해제 |
| 2-8 | `DraftGenerator` + 템플릿 + 18:00 스케줄러 + `POST /drafts/generate` | 활동 11건 → 11줄, 재생성 v2·v3, 확정본 보존 (E2E 4) |
| 2-11 | `OpenAiCompatProvider` + gemma4/qwen3 프리셋 | 코드 수정 없이 프로바이더 전환, 실제 한국어 요약 (E2E 8) |
| 2-9 | Mattermost 알림 + `/settings/notify` | 코드·설정·실패 경로 확인. **실전송은 webhook URL 대기** |
| 2-10 | `/external/summary` | API Key 200 / JWT 403 (E2E 6) |

### 숫자

| 항목 | 값 |
|---|---|
| 커밋 | 19개 (연동 트랙 12 + 문서 4 + 머지 3) |
| 단위 테스트 | **90개** 통과 (담당자 1과 합치면 113개) |
| Flyway | V1 `init`, V2 `repo sync status` |
| 수집된 활동 | 커밋 23 · PR 열림 4 · PR 머지 4 = **31건, 전건 요약 완료** |
| 생성된 초안 | 4건 (v1 DRAFT, v2 CONFIRMED, v3·v4 DRAFT) |

---

## 2. 계획대로 안 풀려서 고친 것

문서에 남길 가치가 있는 것만. 전부 커밋 메시지에 근거가 들어 있다.

### ① PR 이 하나도 수집되지 않았다 → `?full=true` 백필 통로

`since` 가 `last_synced_at` 이라, PR 수집 코드를 새로 붙였을 때 **그 이전에 갱신된 PR 은 영영 들어오지 않았다.** 증분 로직 자체는 맞지만 수집 대상을 늘릴 때마다 같은 문제가 난다.

→ `POST /repos/{id}/sync?full=true` 로 최근 7일을 다시 훑는 통로를 만들었다. 3일차에 새 활동 타입을 붙이더라도 이걸로 백필하면 된다.

### ② qwen3 가 전건 실패 — 원인이 두 겹이었다

1. ollama 계열 엔드포인트가 JSON 을 `Content-Type: application/octet-stream` 으로 내려보내 디코딩이 실패했다 → 선언된 타입을 믿지 않고 JSON 으로 읽게 고쳤다.
2. 그래도 실패가 남았는데, **실패 간격이 정확히 60.0초**였다. 직접 재보니 3,000자 diff 한 건에 **51.7초**가 걸린다. PRD F2 의 타임아웃 60초로는 큰 커밋이 매번 터진다.

→ 프리셋별 `timeout-seconds`(기본 60초, qwen3 만 180초). **PRD F2 를 고쳐 실측값과 근거를 남겼다.**

### ③ `GET /activities?type=NOPE` 가 500

클라이언트 잘못인데 서버 오류로 나갔다 → `400 INVALID_PARAMETER` (가능한 값 안내 포함). 날짜 형식 오류, 숫자 형식 오류도 같은 경로로 처리된다.

### ④ PRD 일정표에 조회 API 가 없었다

`GET /activities`, `/stats/daily` 는 §7 명세에만 있고 §9 일정 어느 행에도 없었다. 담당자 1의 홈 화면이 이 둘을 부르므로 실서버 전환이 여기에 걸려 있었다 → **2-6b** 로 넣고 PRD §9 에도 행을 추가했다.

### ⑤ `GET /repos` 500 (1일차 말미)

응답 매핑이 트랜잭션 밖인데 `Repo.registeredBy` 가 LAZY 였다. fetch join 으로 고쳤고, 이후 수집기·조회 API 도 같은 방식으로 미리 읽는다.

---

## 3. 담당자 1과 오간 것

오늘 실제로 협업이 여러 번 오갔다. 모두 **PRD 먼저 → 알림 → 코드** 순서를 지켰다.

| PR | 누가 | 내용 | 내 대응 |
|---|---|---|---|
| #1 | 담당자 1 | DB 포트를 PRD 표준 5432 로 되돌리고 `POSTGRES_PORT` 로 변수화 | 받아서 루트 `.env` 에 `POSTGRES_PORT=5433` |
| #2 | 담당자 1 | 확정 UI 가 요구하는 응답 필드를 §7 에 추가 | `todayActivityCount`, `syncStatus`, `externalId`, `commitsDelta`, `staleSessions` 전부 구현 (2-6b) |
| #3 | 담당자 1 | 포트 기본값을 다시 5433 으로 (#1 판단 정정) | 받아서 머지. 근거가 정확했다 — 로컬 PostgreSQL 이 `127.0.0.1:5432` 를 잡으면 도커가 `*:5432` 에 떠도 루프백 바인딩이 이겨 엉뚱한 DB 에 붙는다 |
| #4 | 담당자 1 | **동기화 포인트 ②** — 자기 트랙 + 내 커밋(~`06940c0`)을 `main` 으로 | 아래 §4 참고 |

---

## 4. 지금 상태 — 브랜치가 어긋나 있다

```
origin/main                ab17f62   담당자 1 트랙 전체 + 내 작업 (2-8 까지)
origin/git_peristalsis     3b0b033   내 작업 전부
                                     └ main 에 없는 커밋 6개
```

**`main` 에 빠져 있는 내 커밋 6개**

| 커밋 | 내용 |
|---|---|
| `3fc6308` | 2-11 `OpenAiCompatProvider` + 프리셋 |
| `a655820` | 2-9 Mattermost 알림 + `/settings/notify` |
| `97a3c7c` | 2-10 `/external/summary` |
| `fae539c` | PRD 일정표·F2 갱신 |
| `850b721` | PR #3 머지 |
| `3b0b033` | 인수인계 문서 갱신 |

### 시험 병합 결과 — **문제 없음** (오늘 확인함)

임시 워크트리에서 `git merge origin/main` 을 해 봤다.

- 충돌 **0건** — `DraftRepository` 는 담당자 1이 내 것을 그대로 재사용했다
- `./gradlew test` **113개 전건 통과** (내 90 + 담당자 1의 23)
- 합친 상태로 기동 성공, 컨트롤러 매핑 충돌 없음
  (`DraftController` ↔ `DraftGenerateController` ↔ `DraftNotifyController` 가 같은 `/drafts` 아래 공존)
- 두 트랙이 실제로 맞물린다: **담당자 1의 `GET /drafts/{id}` 가 내가 채운 `source_activity_ids` 로 근거 활동 11건을 되살린다**

| 통합 확인 | 결과 |
|---|---|
| `GET /me`, `/activities`, `/stats/daily` (내 것) | 200 |
| `GET /drafts`, `/drafts/{id}`, `/vscode/sessions` (담당자 1) | 200 |
| `POST /drafts/generate` (내 것) | 201 |
| `POST /vscode/sessions` JWT 로 | 403 (규칙 동작) |
| `GET /external/summary` API Key 로 | 200 |

---

## 5. 내일 시작 가이드 (3일차)

### 09:00 — 가장 먼저 할 것 (30분)

**① 원격 확인부터.** 어제도 자는 사이에 PR 이 세 개 들어왔다.

```bash
git fetch --all --prune
git log --oneline HEAD..origin/git_peristalsis      # 내 브랜치에 뭐가 들어왔나
git log --oneline HEAD..origin/main                 # main 이 얼마나 앞서 있나
git ls-remote --heads origin                        # 새 브랜치 = 새 PR 신호
```

**② `main` 병합을 끝낸다.** 위 §4 의 커밋 6개가 아직 `main` 에 없다. 시험 병합이 깨끗했으니 그대로 하면 된다.

```bash
git checkout git_peristalsis && git pull
git merge origin/main                                # 충돌 없음 (확인함)
cd backend && ./gradlew test                         # 113개 통과해야 한다
git push origin git_peristalsis
# 그다음 main 으로 (담당자 1과 한마디 맞추고)
git checkout main && git pull && git merge git_peristalsis && git push origin main
```

> 이걸 먼저 하는 이유: 담당자 1의 1-9(실서버 전환)·1-10(재생성·전송 버튼)이 내 `/drafts/generate` 와 `/drafts/{id}/notify` 에 걸려 있는데, 그 두 개가 아직 `main` 에 없다.

**③ 서버를 띄워 둔다.**

```bash
docker compose up -d
cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 21)
set -a && source .env && set +a && ./gradlew bootRun
```

### 09:30~ — 작업 순서

| 순서 | 작업 | 근거 | 예상 |
|---|---|---|---|
| 1 | **Mattermost 실전송 검증** (2-9 마무리) | 유일하게 코드가 끝났는데 검증만 못 한 항목. webhook URL 만 있으면 5분 | 20분 |
| 2 | **2-13 미커밋 리마인드 스케줄러** (F7-2) | P1 중 유일하게 남은 기능. 담당자 1의 확장(1-6·1-7)이 어제 들어와 `vscode_sessions` 에 실제 데이터가 쌓이기 시작한다 | 1시간 |
| 3 | **2-12 E2E 시나리오 2·6·7·8 점검** | 내 담당 4개. 2·6·8 은 이미 통과했으니 통합본에서 재확인 + 7(Mattermost)은 1번이 끝나야 가능 | 1시간 |
| 4 | **PRD §12 미확정 항목 정리** | 아래 §6 의 담당자 1 확인 사항 6개 | 30분 |
| 5 | **2-14 (여유 시) P2** — `GET /stats/people`, 사용자별 LLM 설정(F9b) | PRD §7 에 스키마가 이미 확정돼 있다 (`totals`, `series[].draft`) | 2시간 |
| — | **동기화 포인트 ③ (16:00)** | 최종 머지, 전체 E2E, 태그 `v0.1.0` | |

### 왜 이 순서인가

- **1번을 먼저**: 코드가 이미 끝나 있어 가장 싸게 완료 칸을 채운다. E2E 7 도 여기에 걸려 있다.
- **2번을 3번보다 먼저**: 리마인드는 새 코드고, E2E 점검은 그 결과까지 포함해서 한 번에 도는 게 낫다.
- **5번은 마지막**: P2 라 버려도 되는 유일한 항목이다.
- **밀리면 버리는 순서**: 5 → 4 → 2. **1과 3은 버리지 않는다** (제출물 검수에 직결).

### 2-13 을 짤 때 참고할 것 (이미 준비돼 있음)

- 판정 기준은 **`last_commit_at` 이 6시간 이상 지났거나 미커밋 diff 300줄 이상** (PRD F6·F7-2)
- 6시간 기준은 이미 `StatsService.STALE_AFTER` 에 상수로 있고 `/stats/daily` 의 `staleSessions` 가 같은 기준을 쓴다 — **같은 값을 두 군데 두지 말고 뽑아 쓴다**
- 조회 메서드도 이미 있다: `VscodeSessionRepository.countStale(workDate, threshold)` — 건수 대신 목록이 필요하면 같은 조건으로 메서드 하나만 더 만들면 된다
- 알림 전송은 `NotifyService` 에 이벤트 2 메서드를 추가하면 끝난다 (webhook URL 우선순위 로직 재사용)
- `notify_settings.remind_uncommitted` 가 false 인 사용자는 건너뛴다

---

## 6. 사람이 해야 하는 것

내가 대신 할 수 없는 항목. 내일 오전 중에 정리하면 오후가 막히지 않는다.

### 사용자

1. **Mattermost Incoming Webhook URL** — 메인 메뉴 → 통합(Integrations) → Incoming Webhooks → 추가 → 채널 선택 → URL 복사. 주면 `.env` 반영과 검증은 내가 한다
2. **GitHub OAuth client secret 재발급** — 채팅에 노출됐다. 기능 영향은 없고 급하지도 않다 (콜백이 `localhost` 고정)
3. (선택) 커밋·PR 이 많은 리포 이름 하나 — 지금 리포로도 검증은 끝났고, 페이징까지 보고 싶을 때만

### 담당자 1과 맞출 것

| # | 질문 | 내 현재 구현 |
|---|---|---|
| 1 | `todayActivityCount` 는 커밋만인가, PR·머지 포함인가 | **커밋만** (PRD 주석 기준). 필드 이름과 정의가 어긋나 보인다 |
| 2 | 미가입 author 활동의 화면 표기 | `user: null` + `externalLogin`. **실제로 `Ae-Ti` 의 PR 4건이 이 상태다.** 아바타·이름 자리를 어떻게 할지 |
| 3 | `syncStatus = FAILED` 해제 시점 | 다음 sync 성공 시 `OK`. 화면에 재시도 버튼을 둘지 |
| 4 | `staleSessions` 6시간 기준 | F7-2 리마인드와 같은 값으로 통일했다 |
| 5 | 초안 목록에서 최신 버전 고르기 | 재생성은 `version+1` 이라 같은 (user, date) 에 여러 행이 있다. `max(version)` 을 써야 한다 |

---

## 7. 참고 — 지금 동작하는 엔드포인트

★ = JWT / API Key 둘 다 허용

| 메서드 | 경로 | 인증 | 담당 |
|---|---|---|---|
| GET | `/health` | 없음 | 2 |
| GET | `/auth/github`, `/auth/github/callback` | 없음 | 2 |
| GET | `/me` ★ | | 2 |
| POST/GET | `/me/api-keys`, DELETE `/me/api-keys/{id}` | JWT | 2 |
| GET | `/repos` ★ · POST `/repos` · DELETE `/repos/{id}` | | 2 |
| POST | `/repos/{id}/sync[?full=true]` | JWT | 2 |
| GET | `/activities` ★ · `/activities/{id}` ★ | | 2 |
| GET | `/stats/daily` ★ | | 2 |
| POST | `/drafts/generate` | JWT | 2 |
| POST | `/drafts/{id}/notify` | JWT | 2 |
| GET/PUT | `/settings/notify` | JWT | 2 |
| GET | `/external/summary` | **API Key 만** | 2 |
| GET | `/drafts` ★ · `/drafts/{id}` ★ · PATCH `/drafts/{id}` · POST `/drafts/{id}/confirm` | | 1 |
| POST | `/vscode/sessions` (**API Key 만**) · GET `/vscode/sessions` ★ | | 1 |

**아직 없는 것**: `GET /stats/people` (P2), `GET/PUT /settings/llm` (P2), 미커밋 리마인드 스케줄러.

### 로컬 구동

```bash
docker compose up -d                                  # postgres, 호스트 5433
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)      # 기본 JDK 가 24 라 지정 필요
set -a && source .env && set +a && ./gradlew bootRun

# LLM 전환 — 코드 수정 없이 재시작만
WORKLOG_LLM_PROVIDER=gemma4 ./gradlew bootRun         # 기동 로그에 "프리셋 gemma4 검증 성공"
WORKLOG_LLM_PROVIDER=qwen3  ./gradlew bootRun         # 느리다 (diff 한 건에 50초 이상)
```

검증용 요청 모음: `backend/http/auth.http`, `repos.http`, `activities.http`, `drafts.http`
