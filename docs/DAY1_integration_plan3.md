# 2일차 작업 계획 — 담당자 2 (연동 트랙)

기준 문서: [PRD_090910.md](PRD_090910.md) · [DAY1_integration_plan2.md](DAY1_integration_plan2.md) · [HANDOFF_day1_integration.md](HANDOFF_day1_integration.md)
작성: 2026-09-09 14:10 · 대상: **2026-09-10 (2일차)** · 브랜치 `git_peristalsis` · 기준 커밋 `319a760`

> 파일 이름은 요청대로 `DAY1_integration_plan3` 이지만 내용은 **2일차(9/10)** 계획이다.

> 한 줄 요약: **오전에 PR 수집 → 요약 파이프라인 → 조회 API 를 붙여 담당자 1의 실서버 전환을 열어 주고, 15:00 에 main 으로 합친 뒤 오후에 초안 생성 → 실제 LLM → Mattermost 를 붙인다.**

---

## 0. 1일차 마감 상태

### 끝난 것 (태그 `day1-integration`)

| # | 작업 | 결과 |
|---|---|---|
| 2-0 | 보안 골격, `/health`, 오류 포맷, Repository 4개 | 미인증 요청 전부 `401 {code, message}` |
| 2-1 | `LlmProvider` + `MockLlmProvider` + 프롬프트 파일 | 단위 테스트 13개 |
| 2-2 | GitHub OAuth + JWT + `GET /me` | 실제 브라우저 로그인으로 검증, 토큰 AES 암호화 저장 |
| 2-3 | API Key 발급/폐기 + `X-Api-Key` 필터 | 경로별 인증 규칙 동작 (JWT 로 `POST /vscode/sessions` → 403) |
| 2-4 | 리포 등록·검증 + `GitHubCollector` 커밋 수집 | 실제 리포에서 커밋 3건, sync 2회에도 건수 불변 |

테스트 38개 통과. DB 에 `activities` 3건 (전부 `summary_status = PENDING`).

### 남겨둔 것

| 항목 | 처리 |
|---|---|
| 커밋 많은 리포 추가 등록 (계획2 §5-3) | 리포 이름 미정. 오늘 2-6 검증 때 같이 하면 좋다 |
| PR/머지 수집, 요약 채우기 | 원래 2일차 몫 — 아래 2-6, 2-7 |

---

## 1. 어제 이후 바뀐 것 — **읽고 시작할 것**

### ① 담당자 1이 API 명세를 바꿨다 (PR #2, 커밋 `319a760`)

확정된 UI 화면이 요구하는 필드를 PRD §7 에 반영했다. **PRD 를 먼저 고치고 알린 뒤 코드** 라는 규칙대로 진행된 정상적인 변경이고, 그만큼 오늘 내 작업이 늘었다.

| 대상 | 추가/변경 | 영향 |
|---|---|---|
| `GET /repos` | `todayActivityCount`, `syncStatus` (`OK`\|`SYNCING`\|`FAILED`) | **이미 배포한 엔드포인트를 고쳐야 한다.** `FAILED` 를 알려면 동기화 결과를 저장할 컬럼이 필요 → **V2 마이그레이션** |
| `GET /activities` | `externalId` 추가, `user.avatarUrl` 은 null 허용 | 응답 DTO 에 반영 |
| `GET /stats/daily` | `commitsDelta`, `staleSessions`, `byUser[].sessions` 추가 | 어제 대비 증감과 6시간 이상 방치 세션 계산 필요 |
| `GET /stats/people` (P2) | 상세 스키마 확정 (`totals`, `series[].draft`) | 3일차 2-14 |

### ② 사내 LLM 서버 2대 모두 응답한다

```
gemma4  http://61.32.164.99:18000/v1/models      → 200 (0.02s)
qwen3   http://agent.uxis.co.kr:11435/v1/models  → 200 (0.05s)
```
2-11(실제 LLM 연결)이 현실적이다. 오늘 안에 `provider=gemma4` 로 진짜 요약을 만들 수 있다.

### ③ 담당자 1 진행 상황

- 브랜치 `VsPeristalsis_dashboard` (PRD 상 이름은 `track/client`)
- `1588c9e` 프론트·확장 스캐폴딩 → `861eab5` 목업 데이터 + API 클라이언트 (1-1, 1-2 완료)
- 오늘 오전은 VS Code 확장(1-6, 1-7), 오후에 초안 CRUD(1-8)와 **실서버 전환(1-9)**

---

## 2. 오늘의 최우선 원칙

**담당자 1의 `VITE_USE_MOCK=false` 전환(1-9)을 막지 않는 것이 오늘 가장 중요하다.**

전환에 필요한 엔드포인트는 어제 만든 `/me`, `/repos` 만이 아니다. 홈 화면이 `GET /activities` 와 `GET /stats/daily` 를 부른다. 그런데 **이 두 개는 PRD §9 일정표의 어느 작업 행에도 없다.** 어제 §7 명세에만 있고 일정에서 빠진 항목이다.

→ 오늘 **2-6b** 로 새로 넣고, PRD §9 2일차 표에도 행을 추가한다. 15:00 동기화 포인트 전에 끝낸다.

---

## 3. 타임라인 (09:00 → 18:00)

| 시간 | 작업 | 커밋 메시지 |
|---|---|---|
| 09:00–10:10 | **2-6 PR/머지 수집** (F1 후반) | `feat(github): collect pull requests and merges` |
| 10:10–11:20 | **2-7 요약 파이프라인** (F2) | `feat(llm): summary pipeline with retry` |
| 11:20–12:30 | **2-6b 조회 API** — `/activities`, `/activities/{id}`, `/stats/daily`, `/repos` 필드 추가 (+V2) | `feat(activity): activity query and daily stats API` |
| 12:30–13:30 | 점심 | |
| 13:30–15:00 | **2-8 DraftGenerator** + 템플릿 + 18:00 스케줄러 + `POST /drafts/generate` (F3a) | `feat(draft): draft generator, template and scheduler` |
| **15:00–15:30** | **동기화 포인트 ②** — `main` 머지, 담당자 1의 실서버 전환 지원 | |
| 15:30–16:30 | **2-11 `OpenAiCompatProvider`** + gemma4/qwen3 프리셋 (F2b) | `feat(llm): OpenAI-compatible provider with presets` |
| 16:30–17:20 | **2-9 Mattermost 알림** + `/settings/notify` (F7) | `feat(notify): Mattermost notifier and settings` |
| 17:20–17:50 | **2-10 `/external/summary`** (F8) | `feat(external): daily summary endpoint for API keys` |
| 17:50–18:00 | **2-12 마무리** — 태그 `day2-integration`, 인수인계 갱신 | |

작업 단위마다 `./gradlew test` 통과 상태로 커밋한다.

### 순서를 이렇게 잡은 이유

- **2-7 을 2-6b 보다 먼저**: 조회 API 가 나가는 시점에 `summary` 가 이미 채워져 있어야 담당자 1이 빈 화면을 안 본다.
- **2-11 을 PRD 순서(2-9→2-10→2-11)보다 앞당김**: E2E 시나리오 8번이고 시연 가치가 가장 크다. 사내 LLM 이 느리거나 응답이 이상할 때 대응할 시간을 남긴다. 실패해도 `mock` 폴백이 있어 다른 기능을 막지 않는다.
- **밀리면 버리는 순서**: 2-10 → 2-9 → 2-11. **2-6b 와 2-8 은 버리지 않는다** (각각 담당자 1의 1-9, 1-8·1-10 이 걸려 있다).

---

## 4. 확정한 설계 결정

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| ⑨ | PR 활동의 `external_id` | PR 번호 문자열 (`"12"`) | UNIQUE 가 `(repo, type, external_id)` 라 같은 PR 이 `PR_OPENED` / `PR_MERGED` 두 행으로 공존한다 |
| ⑩ | PR 의 `raw_diff` | 저장하지 않는다 (null) | PR 파일 목록은 PR 당 API 호출이 또 필요하다. 요약은 제목+본문으로 충분하고 rate limit 을 아낀다 |
| ⑪ | PR 수집 중단 조건 | `sort=updated&direction=desc` 로 받아 `updated_at < since` 를 만나면 멈춘다 | 전체 PR 을 매번 훑지 않는다 |
| ⑫ | `syncStatus = FAILED` 저장 | **V2 마이그레이션**으로 `repos.last_sync_status`, `last_sync_error` 추가 | 컬럼 없이는 실패 배지를 만들 수 없다. `SYNCING` 은 수집기의 진행 중 집합에서 판단 |
| ⑬ | 날짜 경계 | 모든 `date` 파라미터는 **KST 기준 00:00~23:59:59.999** 를 `occurred_at` 범위로 변환 | `activities.occurred_at` 은 `TIMESTAMPTZ` 라 UTC 로 저장돼 있다 |
| ⑭ | `staleSessions` 기준 | `last_commit_at` 이 6시간 이상 지난 세션 수 | PRD F7-2 의 리마인드 조건과 같은 기준을 쓴다 |
| ⑮ | 요약 재시도 | `summary_status <> DONE` **이고** `summary_retries < 3` 인 것만 집는다. 3회 실패하면 `FAILED` 로 고정 | PRD F2 "최대 3회" |
| ⑯ | `POST /drafts/generate` 파일 위치 | **`draft/DraftGenerateController.java` 별도 파일** | `draft/` 는 두 사람이 겹치는 유일한 패키지다. 담당자 1의 `DraftController`(조회·수정·확정)와 파일을 나누면 충돌이 안 난다 (PRD §2) |
| ⑰ | 초안 재생성 | 덮어쓰지 않고 `version + 1` 로 새 행. 18:00 스케줄러는 `CONFIRMED` 가 있는 (user, date) 를 건너뛴다 | PRD F3 |
| ⑱ | 프리셋별 프로바이더 빈 | `OpenAiCompatProviderFactory` 를 두고 `LlmProviderResolver` 가 프리셋 수만큼 만들어 map 에 넣는다 | `@Bean` 메서드로는 설정 개수만큼 동적으로 만들 수 없다. **기존 `LlmProviderResolverTest` 생성자 시그니처가 바뀌므로 같이 고친다** |
| ⑲ | 알림 전송 실패 | 로그만 남기고 본 흐름을 막지 않는다 | PRD F7 |

---

## 5. 작업별 상세

### 2-6. PR / 머지 수집 (09:00–10:10)

**파일**
```
github/dto/GitHubPullRequestDto.java   number, title, body, html_url, state,
                                       created_at, merged_at, updated_at,
                                       user{login}, merged_by{login}, head{ref}, base{ref}
github/GitHubApiClient.java            listPullRequests(owner, name, since, token) 추가
                                       GET /repos/{o}/{r}/pulls?state=all&sort=updated&direction=desc&per_page=100
github/GitHubCollector.java            커밋 수집 뒤 PR 수집을 이어서 돈다
test/github/GitHubPullRequestParseTest + fixtures/pulls.json
```

**저장 규칙**

| | `PR_OPENED` | `PR_MERGED` |
|---|---|---|
| 만드는 조건 | 항상 | `merged_at != null` |
| `external_id` | `String.valueOf(number)` | 같음 |
| `occurred_at` | `created_at` | `merged_at` |
| 사용자 | `user.login` | `merged_by.login` |
| `title` | PR 제목 | `"PR #12 머지: " + 제목` |
| `branch` | `head.ref` | `head.ref` |
| `raw_diff` | null (결정 ⑩) | null |

**완료 조건**
- 실제 리포에 PR 이 2건 있으므로(#1 db-port, #2 api-contract) `activities` 에 `PR_OPENED` 2건 + `PR_MERGED` 2건이 들어온다
- **sync 를 2번 돌려도 총 건수가 변하지 않는다** (PRD F1 수용 기준)
- fixture 파싱 테스트 통과

### 2-7. 요약 파이프라인 (10:10–11:20)

**파일**
```
llm/SummaryService.java        PENDING 활동을 집어 프로바이더를 호출하고 결과를 저장
llm/SummaryScheduler.java      @Scheduled(fixedDelayString) 로 주기 실행
activity/ActivityRepository    @Query 로 "요약 대상" 조회 (Pageable 로 한 번에 20건)
application.yml                worklog.llm.summary-interval-ms (기본 60000)
test/llm/SummaryServiceTest    성공 → DONE, 실패 → 재시도 증가, 3회 후 FAILED,
                               이미 summary 가 있으면 호출하지 않음
```

**도는 순서**
```
1. summary_status <> 'DONE' AND summary_retries < 3 인 활동 20건 (occurred_at 최신순)
2. summary 가 이미 있으면 DONE 으로만 바꾸고 호출하지 않는다 (PRD F2)
3. PromptLoader.render(commit-summary-user, {repo, message, files, diff})
   + LlmRequest.vars 에 {message, fileCount} — Mock 이 쓴다
4. resolver.resolve().complete(req)
   성공 → summary 저장, status = DONE
   실패 → summary_retries + 1, status = FAILED (다음 주기에 다시 집힌다, 3회까지)
5. 수집(2-6) 직후에도 한 번 돌려 대시보드에 바로 뜨게 한다
```

**완료 조건**: `provider=mock` 으로 기존 3건 + PR 활동까지 **전건 `summary_status = DONE`**.

### 2-6b. 조회 API + `/repos` 필드 추가 (11:20–12:30) ★ 담당자 1 차단 해제

**V2 마이그레이션**
```sql
-- V2__repo_sync_status.sql
ALTER TABLE repos ADD COLUMN last_sync_status VARCHAR(20) NOT NULL DEFAULT 'OK';
ALTER TABLE repos ADD COLUMN last_sync_error  TEXT;
ALTER TABLE repos ADD CONSTRAINT ck_repos_sync_status
    CHECK (last_sync_status IN ('OK', 'FAILED'));
```
`V1__init.sql` 은 이미 적용됐으므로 **절대 수정하지 않는다.**

**파일**
```
activity/ActivityController.java   GET /activities  ★ → {items, page, size, total}
                                   GET /activities/{id} ★ → 상세 (rawDiff 포함)
activity/ActivityQueryService.java 필터 조합 (Specification 또는 @Query)
activity/dto/ActivityResponse.java §7 대표 응답 스키마와 필드명이 완전히 같아야 한다
stats/StatsController.java         GET /stats/daily ★
stats/StatsService.java            집계 쿼리
github/RepoController.java         todayActivityCount, syncStatus 추가
github/GitHubCollector.java        성공/실패를 last_sync_status 에 기록
```

**`GET /activities` 쿼리 파라미터**: `date` 또는 `from`,`to` / `userId` / `repoId` / `type` / `page`(기본 0), `size`(기본 50)

**응답 — PRD §7 그대로**
```jsonc
{
  "items": [{
    "id": 101, "type": "COMMIT",
    "repo": {"id": 1, "fullName": "uxis-co-kr/2026-pknu-3day"},
    "user": {"id": 3, "login": "taeil", "name": "배태일", "avatarUrl": null},
    "externalId": "abc1234",
    "sha": "abc1234", "title": "feat: 출석 API 추가", "url": "https://github.com/...",
    "branch": "main", "filesChanged": 4, "additions": 120, "deletions": 8,
    "summary": "...", "summaryStatus": "DONE",
    "occurredAt": "2026-09-09T10:12:00+09:00"
  }],
  "page": 0, "size": 50, "total": 12
}
```
- 미가입 author 는 `user: null` 이고 `externalLogin` 만 있다 → **담당자 1과 이 경우의 화면 표기를 맞춘다** (§8)

**`GET /stats/daily`**
```jsonc
{
  "date": "2026-09-09",
  "commits": 12, "prs": 3, "merges": 1, "sessions": 2,
  "commitsDelta": 3,       // 어제 커밋 수와의 차
  "staleSessions": 1,      // last_commit_at 이 6시간 이상 지난 세션 (결정 ⑭)
  "byUser": [{ "userId": 3, "commits": 2, "prs": 1, "merges": 1, "sessions": 1 }]
}
```
`sessions` 는 담당자 1의 `vscode_sessions` 를 읽는다. 오늘 데이터가 없으면 0 이 나오는 게 정상이다.

**`GET /repos` 추가 필드**

| 필드 | 계산 |
|---|---|
| `todayActivityCount` | 오늘(KST) 이 리포의 **커밋 수** (PRD 주석 기준) — §8 에서 담당자 1과 확인 |
| `syncStatus` | 수집기 진행 중 집합에 있으면 `SYNCING`, 아니면 `last_sync_status` |

**완료 조건**
```bash
curl -H "Authorization: Bearer $JWT" 'localhost:8080/api/activities?date=2026-09-10'
curl -H "Authorization: Bearer $JWT" 'localhost:8080/api/activities?type=COMMIT&page=0&size=10'
curl -H "Authorization: Bearer $JWT" 'localhost:8080/api/stats/daily?date=2026-09-10'
curl -H "X-Api-Key: $KEY"           'localhost:8080/api/activities?date=2026-09-10'   # ★ 둘 다 허용
```
`./gradlew test` 통과. **끝나는 즉시 담당자 1에게 알린다.**

### 2-8. `DraftGenerator` + 템플릿 + 스케줄러 (13:30–15:00)

**파일**
```
draft/DraftGenerator.java          생성 로직 — 담당자 2 소유
draft/DraftTemplate.java           PRD F3 고정 Markdown 템플릿
draft/DraftGenerateController.java POST /drafts/generate  ← 별도 파일 (결정 ⑯)
draft/DraftScheduler.java          @Scheduled(cron = "${worklog.draft.schedule-cron}")  18:00 KST
draft/DraftRepository.java         (담당자 1과 겹칠 수 있으니 먼저 있는지 확인하고 없으면 만든다)
test/draft/DraftGeneratorTest      활동 3건 → "완료한 작업" 3줄, 0건 → 생성 안 함, 버전 증가
```

**템플릿 (PRD F3 그대로)**
```markdown
# {YYYY-MM-DD} 업무 일지 — {이름}

## 완료한 작업
- [{repo}] {summary}  (commit abc1234)
- [{repo}] PR #12 머지: {제목}

## 진행 중 / 미커밋
- [{repo}] {vscode 세션 요약 또는 변경 파일 목록}

## 계획 / TODO
- {코드 내 TODO 주석 또는 VS Code 에 기록한 계획}

## 메모
(직접 작성)
```

**규칙**
- 입력: 그날(KST) 의 `activities` + `vscode_sessions`
- 활동도 세션도 0건이면 초안을 만들지 않고 **204**
- 같은 (user, date) 에 이미 있으면 `version + 1` 로 새 행 (덮어쓰지 않는다)
- 18:00 스케줄러는 `CONFIRMED` 초안이 있는 (user, date) 를 건너뛴다
- `source_activity_ids`, `source_session_ids` 를 채운다 — 담당자 1의 초안 편집 화면 우측 목록이 이걸 쓴다
- 미가입 author (`user_id IS NULL`) 는 초안 대상에서 제외 (PRD §12)

**완료 조건**: 커밋 3개가 있는 날짜에 생성 → "완료한 작업" 3줄. 확정 후 재생성 → version 2 DRAFT 가 생기고 확정본은 남는다.

### 동기화 포인트 ② (15:00–15:30)

```bash
git checkout main && git pull
git merge git_peristalsis          # 담당자 1도 VsPeristalsis_dashboard 를 머지
./gradlew test && ./gradlew bootRun    # 합친 상태에서 기동 확인
git push origin main
```
- 담당자 1의 `VITE_USE_MOCK=false` 전환(1-9)을 옆에서 지원한다. 필드 불일치가 나오면 **PRD 를 먼저 고치고** 양쪽이 코드를 고친다
- 이 시점에 내 F5·F1·F3a 가 붙어 있어야 한다 (PRD §9)

### 2-11. `OpenAiCompatProvider` (15:30–16:30)

**파일**
```
llm/OpenAiCompatProvider.java        POST {baseUrl}/chat/completions
llm/OpenAiCompatProviderFactory.java 프리셋 → 프로바이더 (결정 ⑱)
llm/LlmProviderResolver.java         프리셋 수만큼 만들어 map 에 등록 + 기동 시 /models 검증
test/llm/LlmProviderResolverTest     생성자 변경에 맞춰 수정
test/llm/OpenAiCompatProviderTest    <think> 제거, 응답 파싱
```

**요청 형식**
```jsonc
POST {baseUrl}/chat/completions
{ "model": "google/gemma-4-26b-a4b-qat",
  "messages": [{"role":"system","content":"..."}, {"role":"user","content":"..."}],
  "temperature": 0.2, "max_tokens": 400, "stream": false }
```
- 타임아웃 60초
- **qwen3 는 `<think>...</think>` 블록이 섞여 나온다 → 제거한 뒤 저장** (PRD F2)
- 기동 시 선택된 프리셋의 `GET {baseUrl}/models` 를 한 번 호출해 검증. 실패하면 **경고 로그 후 mock 폴백** — 서비스는 떠야 한다

**완료 조건** (E2E 시나리오 8)
```bash
WORKLOG_LLM_PROVIDER=gemma4 ./gradlew bootRun    # 기동 로그에 /models 검증 성공
# activities 의 summary 를 비우고 파이프라인 재실행 → 진짜 한국어 요약이 들어온다
WORKLOG_LLM_PROVIDER=qwen3  ./gradlew bootRun    # 코드 수정 없이 전환, <think> 없는 요약
```

### 2-9. Mattermost 알림 (16:30–17:20)

**파일**
```
notify/Notifier.java              인터페이스
notify/MattermostNotifier.java    Incoming Webhook 으로 POST {"text": "..."}
notify/NotifyService.java         이벤트 조립
notify/NotifySettingRepository.java
notify/NotifySettingController.java  GET/PUT /settings/notify
draft/DraftGenerator.java         생성 완료 시 알림 (이벤트 1)
draft/DraftNotifyController.java  POST /drafts/{id}/notify (이벤트 3)
```

**이벤트**

| # | 시점 | 메시지 |
|---|---|---|
| 1 | 초안 생성 완료 | `📝 {이름}의 {날짜} 업무 일지 초안이 생성되었습니다. {링크}` + 완료 작업 상위 3줄 |
| 3 | 사용자가 "Mattermost 전송" 클릭 | 초안 전체 Markdown 게시 |

이벤트 2(미커밋 리마인드)는 **3일차 2-13**. 전송 실패는 로그만 남긴다 (결정 ⑲).

**웹훅 URL 우선순위**: 사용자별 `notify_settings` → 전역 행(`user_id IS NULL`) → `MATTERMOST_WEBHOOK_URL`.

**완료 조건**: 초안을 생성하면 채널에 메시지가 도착한다.

### 2-10. `/external/summary` (17:20–17:50)

```
external/ExternalController.java   GET /external/summary?date=2026-09-10
                                   → 그날 전 사용자의 초안을 사용자별 5줄 요약으로
```
- 인증은 이미 `SecurityConfig` 에서 **API Key 전용**으로 걸려 있다. 컨트롤러에 설정 불필요
- 완료 조건: `curl -H "X-Api-Key: wl_..." 'localhost:8080/api/external/summary?date=2026-09-10'` → 200, JWT 로는 403

### 2-12. 마무리 (17:50–18:00)

- [ ] `./gradlew test` 전건 통과 확인
- [ ] `git tag day2-integration && git push origin git_peristalsis --tags`
- [ ] [HANDOFF_day1_integration.md](HANDOFF_day1_integration.md) 에 오늘 추가된 엔드포인트 반영
- [ ] PRD §9 2일차 표에 **2-6b** 행 추가 (일정표에 빠져 있던 항목)

---

## 6. 오늘 만들 파일 체크리스트

```
backend/src/main/resources/db/migration/V2__repo_sync_status.sql
backend/src/main/java/com/worklog/
  github/   dto/GitHubPullRequestDto
            GitHubApiClient(listPullRequests), GitHubCollector(PR 수집·sync 상태 기록)
            RepoController(todayActivityCount, syncStatus)
  activity/ ActivityController, ActivityQueryService, dto/ActivityResponse
            ActivityRepository(요약 대상·집계 쿼리)
  stats/    StatsController, StatsService, dto/DailyStatsResponse
  llm/      SummaryService, SummaryScheduler,
            OpenAiCompatProvider, OpenAiCompatProviderFactory, LlmProviderResolver(수정)
  draft/    DraftGenerator, DraftTemplate, DraftGenerateController,
            DraftNotifyController, DraftScheduler, DraftRepository
  notify/   Notifier, MattermostNotifier, NotifyService,
            NotifySettingRepository, NotifySettingController
  external/ ExternalController, ExternalSummaryService
backend/src/test/java/com/worklog/
  github/GitHubPullRequestParseTest       (+ fixtures/pulls.json)
  llm/SummaryServiceTest, OpenAiCompatProviderTest, LlmProviderResolverTest(수정)
  draft/DraftGeneratorTest, DraftTemplateTest
  stats/StatsServiceTest
backend/http/activities.http, drafts.http
```

건드리지 않는 곳: `frontend/`, `vscode-extension/`, `backend/.../vscode/`, `draft/` 의 조회·수정·확정 API, `Draft` 엔티티 필드.

---

## 7. 사용자가 직접 해야 하는 것

**2-9 전(늦어도 16:00)까지** 해두면 알림 검증이 막히지 않는다.

1. **Mattermost Incoming Webhook URL**
   메인 메뉴 → 통합(Integrations) → Incoming Webhooks → 추가 → 채널 선택 → 생성된 URL 복사
   → `backend/.env` 의 `MATTERMOST_WEBHOOK_URL` 에 넣는다
2. **2-6 검증용 리포** (선택) — 커밋·PR 이 많은 리포 이름 하나. 지금 리포는 PR 이 2건뿐이라 페이징까지는 확인이 어렵다
3. **GitHub OAuth App client secret 재발급** — 어제 채팅에 노출됐다. 재발급 후 `.env` 갱신

---

## 8. 담당자 1과 맞출 것

15:00 동기화 포인트 **전에** 확인해야 오후에 되돌리는 일이 없다.

| # | 질문 | 내 제안 |
|---|---|---|
| 1 | `todayActivityCount` 는 커밋만인가, PR·머지 포함인가 | PRD 주석이 "그날 이 리포의 커밋 수" 라 **커밋만**. 이름과 정의가 어긋나 보이니 확인 |
| 2 | `syncStatus` 의 `FAILED` 는 언제 풀리나 | 다음 sync 가 성공하면 `OK`. 화면에 "재시도" 버튼을 둘지 |
| 3 | 미가입 author 활동의 화면 표기 | 응답은 `user: null` + `externalLogin`. 아바타 자리와 이름을 어떻게 보일지 |
| 4 | `staleSessions` 6시간 기준 | PRD F7-2 와 같은 기준으로 통일 |
| 5 | 초안 편집 화면의 `sourceActivities` | `GET /drafts/{id}` 는 담당자 1 소유. 내가 채우는 `source_activity_ids` 로 조회하면 된다 |
| 6 | `DraftRepository` 를 누가 만드나 | 먼저 만드는 쪽이 만들고 상대는 재사용. 파일 충돌만 피하면 된다 |

---

## 9. 오늘 끝났을 때 상태 (수용 기준)

- [ ] PR/머지가 `activities` 에 쌓이고, **sync 2회에도 총 건수 불변** (E2E 2)
- [ ] `provider=mock` 으로 전체 활동의 `summary_status = DONE`
- [ ] `GET /activities?date=` 가 §7 스키마 그대로 응답하고 `externalId` 가 들어 있다
- [ ] `GET /stats/daily?date=` 가 `commitsDelta`, `staleSessions`, `byUser[].sessions` 를 포함한다
- [ ] `GET /repos` 가 `todayActivityCount`, `syncStatus` 를 포함한다
- [ ] 활동이 있는 날짜에 `POST /drafts/generate` → 201, "완료한 작업" 줄 수가 활동 수와 같다
- [ ] 활동 0건인 날짜 → 204
- [ ] 확정된 초안이 있는 날짜에 재생성 → version 2 DRAFT, 확정본 유지 (E2E 4)
- [ ] `WORKLOG_LLM_PROVIDER=gemma4` 재시작 → 코드 수정 없이 실제 한국어 요약, 기동 로그에 `/models` 검증 성공 (E2E 8)
- [ ] `qwen3` 로 바꿔도 동작하고 `<think>` 가 남지 않는다
- [ ] Mattermost 채널에 초안 생성 알림 도착 (E2E 7)
- [ ] `curl -H "X-Api-Key" /api/external/summary?date=` 응답, JWT 로는 403 (E2E 6)
- [ ] `./gradlew test` 전건 통과
- [ ] `main` 에 양쪽 브랜치가 머지되고 담당자 1의 실서버 전환이 동작
- [ ] `day2-integration` 태그 푸시

---

## 10. 3일차로 넘기는 것 (참고)

| # | 작업 |
|---|---|
| 2-12 | E2E 시나리오 §10 의 2·6·7·8 점검·수정 |
| 2-13 | 미커밋 리마인드 스케줄러 (F7-2) — diff 300줄 이상 또는 마지막 커밋 6시간 경과 |
| 2-14 | (여유 시) 사용자별 LLM 설정 백엔드 (F9b), `GET /stats/people` (F10b) |
| 2-15 | (P2 까지 끝나면) 확장 계획 X1 Mattermost 봇 DM → X4 GitHub Webhook |
