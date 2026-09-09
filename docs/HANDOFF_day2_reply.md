# 담당자 1 → 담당자 2 요청에 대한 회신

작성: 2026-09-09 16:05 · `main` = `ed83307` · 브랜치 `git_peristalsis` = `ed83307` (일치)

> **먼저 알릴 것: `main` 을 올려 뒀습니다.** 목업으로 대신하던 엔드포인트 중 두 개
> (`/settings/notify`, `/drafts/{id}/notify`)는 **이미 만들어져 있었고 `main` 에 없었을 뿐**입니다.
> `git pull` 하시면 1-10 을 바로 이어갈 수 있습니다.

---

## 부탁 1. GitHub 로그인 — 했고, 재발하지 않게 고쳤습니다

로그인했습니다. 다만 **로그인만으로는 반쪽짜리 해결**이라 원인을 고쳤습니다.

### 문제의 실체

사용자 매핑은 **수집 시점에만** 일어납니다. 그런데 수집기는 이미 저장한 sha 를 건너뛰므로,
**커밋이 먼저 쌓인 뒤에 로그인하면 그 활동들은 재수집으로도 영영 붙지 않습니다.**
`?full=true` 로 다시 훑어도 마찬가지입니다.

즉 로그인 순서에 따라 데이터가 갈리는 상태였고, 새 팀원이 합류할 때마다 같은 일이 납니다.

### 고친 방식

`UserService` 가 로그인 UPSERT 직후, `external_login` 이 일치하는 미매핑 활동을 이어 붙입니다.

```
UngsikJo 의 기존 활동 20건을 사용자에 연결했다.
```

**검증**: 제 활동 20건을 일부러 `user_id = null` 로 되돌린 뒤 다시 로그인 → 20건 전부 연결됐습니다.

### 그쪽에서 할 일

`git pull` 후 서버를 다시 띄우고 **`UngsikJo` 로 한 번 로그인**하시면 23건이 소급 연결됩니다.
(제 로그인은 제 로컬 DB 에만 반영되므로, 그쪽 DB 는 그쪽에서 한 번 필요합니다.)

---

## 부탁 2. PR_MERGED 제목 접두사 — 제거했습니다

지적이 정확합니다. 저장 단계에서 표기를 붙인 게 잘못이었습니다.

- `GitHubCollector` 는 이제 **GitHub 이 준 제목만** 저장합니다. `PR_OPENED` 와 `PR_MERGED` 가 동일한 값을 갖습니다.
- 표기(`PR #N 머지: `)는 읽는 쪽이 `type` 과 `externalId` 를 보고 붙입니다 — 화면도, 제 초안 템플릿도.
- **V3 마이그레이션**으로 기존 행의 접두사를 떼고, 오염된 제목으로 만들어진 `PR_MERGED` 요약을 다시 생성시킵니다. `git pull` 후 기동하면 자동 적용됩니다.

```
 PR_MERGED | 1 | fix: DB 노출 포트를 PRD 기준 5432 로 되돌리고 POSTGRES_PORT 로 변수화
 PR_OPENED | 1 | fix: DB 노출 포트를 PRD 기준 5432 로 되돌리고 POSTGRES_PORT 로 변수화
```

초안 본문의 겹침도 0건으로 확인했습니다. 회귀 테스트도 넣었습니다(`DraftTemplateTest.doesNotDoublePrefix`).

> **프론트의 방어 코드(`2f7dc24`)는 이제 빼셔도 됩니다.** 다만 마이그레이션이 돌기 전 상태의
> DB 를 보고 있다면 잠깐 겹쳐 보일 수 있으니, 서버 재기동 후에 빼시는 게 안전합니다.

PRD F1 수용 기준에 **"활동 제목은 GitHub 이 준 값만 저장한다"** 를 명시해 뒀습니다.

---

## 부탁 3. 미매핑 활동 통계 — (c) 로 가되, 설명할 수단을 드립니다

**판단: (c) 그대로 둡니다.** (b)는 채택하지 않습니다.

### 이유

`commits` 는 "그날 그 리포에 실제로 있었던 커밋 수"입니다. 매핑 여부는 **우리 서비스 사정**이지
리포의 사실이 아닙니다. (b)처럼 매핑된 것만 세면 총계가 실제보다 적게 나오고, 리포 관리 화면의
`todayActivityCount` 와도 어긋납니다.

(a)는 디자인에 없는 요소를 추가해야 하는데, 부탁 1 의 수정으로 팀 내부 계정은 자연히 해소됩니다.

### 다만 외부 기여자는 남습니다

지적하신 그대로입니다. 그래서 **차이를 설명할 수 있는 필드**를 추가했습니다.

```jsonc
// GET /stats/daily
{
  "commits": 24, "prs": 4, "merges": 4,
  "unmapped": { "commits": 4, "prs": 4, "merges": 4 },   // 추가
  "byUser": [ { "userId": 1, "commits": 20, ... } ]
}
```

**`총계 = byUser 합계 + unmapped`** 가 항상 성립합니다. 위 예시로는 `24 = 20 + 4`.

- `unmapped` 가 전부 0 이면 모든 활동이 매핑돼 있다는 뜻 → 지금 디자인 그대로 두면 됩니다
- 0 이 아닐 때만 "외부 기여 N건" 같은 각주를 달지, 아니면 무시할지는 화면 판단에 맡깁니다

**추가 필드이므로 기존 파싱은 그대로 동작합니다.** PRD §7 에 반영했습니다.

---

## 목업으로 대신하던 엔드포인트 — 현황

| 경로 | 상태 |
|---|---|
| `GET/PUT /settings/notify` | ✅ **이미 있었습니다.** `main` 에 없었을 뿐 (커밋 `a655820`) |
| `POST /drafts/{id}/notify` | ✅ **이미 있었습니다.** 같은 커밋 |
| `GET/PUT /settings/llm` | ❌ P2 (F9b) — 3일차 `2-14` |
| `GET /stats/people` | ❌ P2 (F10b) — 3일차 `2-14` |

`MOCK_FALLBACK_PATHS` 에서 앞의 두 개를 지우셔도 됩니다.

### `POST /drafts/{id}/notify` 응답 규약 (1-10 연결용)

| 상황 | 응답 |
|---|---|
| 전송 성공 | `200 {"sent": true}` |
| webhook URL 미설정 또는 전송 실패 | `503 {"code":"NOTIFY_FAILED","message":"... /settings 에서 webhook URL 을 확인해 주세요."}` |
| 없는 초안 | `404 {"code":"DRAFT_NOT_FOUND"}` |

**지금은 webhook URL 이 없어 503 이 정상입니다.** 버튼을 붙이고 503 일 때
"설정에서 webhook URL 을 등록하세요" 로 안내하면 그대로 맞습니다.

webhook URL 우선순위: 사용자별 `/settings/notify` → 전역 행 → `MATTERMOST_WEBHOOK_URL` 환경변수.

### F7 일정

**코드는 끝났습니다.** 남은 건 실제 webhook URL 로 한 번 쏴 보는 것뿐이고,
URL 을 받는 대로(내일 오전 예정) 검증합니다. 1-10 은 지금 바로 진행하셔도 됩니다.

---

## 실제 LLM (2-11) — 붙어 있습니다

`main` 에 있습니다. 재시작만 하면 코드 수정 없이 바뀝니다.

```bash
WORKLOG_LLM_PROVIDER=gemma4 ./gradlew bootRun    # 기동 로그: "프리셋 gemma4 검증 성공"
```

- `mock` | `gemma4` | `qwen3` — 서버가 죽어 있으면 경고 후 `mock` 으로 폴백하므로 기동은 항상 됩니다
- `qwen3` 는 느립니다(3,000자 diff 한 건에 50초 이상). 화면 확인용으로는 `gemma4` 를 권합니다
- 기존 요약을 실제 LLM 것으로 바꾸려면:
  ```sql
  UPDATE activities SET summary=NULL, summary_status='PENDING', summary_retries=0;
  ```
  이후 파이프라인이 60초 주기로 채웁니다.

실제 요약 예시(gemma4):

> `/repos` API의 응답 스키마를 1일차 구현 기준에 맞춰 배열 형태로 변경하고, `registeredBy`
> 필드를 추가했습니다. 또한 화면 표시에 불필요한 필드들을 정리하고 `todayActivityCount` 와
> `syncStatus` 를 포함하여 실제 구현 및 화면 요구사항에 맞췄습니다.

---

## 통합 확인 (오늘 `main` 머지 시점)

| 항목 | 결과 |
|---|---|
| 충돌 | 0건 |
| 테스트 | **114개 통과** (담당자 2 91 + 담당자 1 23) |
| 기동 | 성공 — `DraftController` / `DraftGenerateController` / `DraftNotifyController` 매핑 충돌 없음 |
| `GET /drafts/{id}` | 제가 채운 `source_activity_ids` 로 근거 활동 11건 반환 확인 |
| `POST /vscode/sessions` JWT | 403 (API Key 전용 규칙 동작) |

---

## 내일(3일차) 제 예정

| 순서 | 작업 |
|---|---|
| 1 | Mattermost 실전송 검증 (webhook URL 받는 대로) — F7 종료 |
| 2 | `2-13` 미커밋 리마인드 스케줄러 (F7-2) — 확장이 세션을 보내기 시작했으니 실데이터로 검증 가능 |
| 3 | `2-12` E2E 시나리오 2·6·7·8 점검 |
| 4 | (여유 시) `2-14` — `GET /stats/people`, `GET/PUT /settings/llm` |

**4번이 그쪽 1-13(설정 UI·통계 페이지)의 전제**입니다. 우선순위를 올려야 하면 알려주세요 —
2번보다 앞으로 당길 수 있습니다.
