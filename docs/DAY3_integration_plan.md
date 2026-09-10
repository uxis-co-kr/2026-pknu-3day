# 3일차 작업 계획 — 담당자 2 (연동 트랙)

작성: 2026-09-10 09:05 · 브랜치 `git_peristalsis` · `origin/main` = `4be133d`
기준: [DAY1_arrange.md](DAY1_arrange.md) §5 + 담당자 1의 [BACKLOG.md](BACKLOG.md)

> 한 줄 요약: **사고 위험 2건을 먼저 막고, 담당자 1 화면이 기다리는 2-14를 당겨서 풀어 준 뒤, 남은 P1(리마인드·Mattermost)과 E2E를 끝내고 16:00에 `v0.1.0`을 찍는다.**

---

## 0. 어젯밤 이후 바뀐 것

### ① 담당자 1이 `main`을 올렸다 (PR #5, `4be133d`)

1-10(재생성·Mattermost 전송 버튼) 완료 + 프론트 버그 3건 수정 + 문서 2개.

### ② 담당자 1이 내 확인 사항 5개에 전부 답했다

| # | 질문 | 답 | 내 조치 |
|---|---|---|---|
| 1 | `todayActivityCount` 커밋만? | **커밋만 맞다.** 필드명은 두고 PRD에 정의만 명시 | PRD 주석 보강 |
| 2 | 미가입 author 화면 표기 | 타임라인 제외 + 안내 문구. 근본 해결은 회원 체계 | 현행 유지 |
| 3 | `syncStatus=FAILED` 재시도 버튼 | **별도 버튼 안 둔다.** 기존 동기화 아이콘으로 충분 | 없음 |
| 4 | `staleSessions` 6시간 | 동의, 화면 문구도 고정 | 없음 |
| 5 | 초안 최신 버전 | 이미 `findLatestByWorkDate`로 구현됨 | 없음 |

### ③ 담당자 1이 내 영역에서 문제 2건을 찾았다 — **오늘 최우선**

| # | 문제 | 위험 |
|---|---|---|
| 2-1 | `RepoService.delete` 에 소유자 검사가 없다 | **팀원 누구나 남의 리포를 지울 수 있고, `ON DELETE CASCADE`로 활동 기록이 통째로 사라진다** |
| 2-2 | `POST /drafts/{id}/notify` 가 확정 여부를 안 본다 | 미확정 초안이 Mattermost로 나갈 수 있다. **webhook URL이 붙는 순간 발현** |

둘 다 내 코드다. 지적이 정확하다.

### ④ 회원 체계 기획 변경 — **이번 범위 밖** (BACKLOG §1)

자체 회원가입 도입이 결정됐지만 1~2일 규모라 다음 사이클로 넘긴다.
**오늘 `auth/` 를 건드리지 않는다.** V4 마이그레이션도 오늘 만들지 않는다.

### ⑤ 내 로컬에 문제가 두 개 있다

| 문제 | 처리 |
|---|---|
| 미푸시 커밋 `7fb7a49`(프론트 null 가드)가 담당자 1 수정과 **충돌 3건** | **버린다** — 아래 §2 |
| Docker 데몬이 내려가 있었다 (09:00에 재기동함) | 백엔드도 재기동 필요 |

---

## 1. 오늘 타임라인 (09:00 → 17:00)

| 시간 | 작업 | 커밋 메시지 |
|---|---|---|
| 09:05–09:25 | **0. 브랜치 정리 + 환경 복구** | — |
| 09:25–10:00 | **A. 리포 삭제 소유자 검사** 🔴 | `fix(github): 리포 삭제를 등록자에게만 허용` |
| 10:00–10:25 | **B. notify 확정 검사** 🔴 | `fix(draft): 확정되지 않은 초안은 전송을 막는다` |
| 10:25–12:30 | **C. 2-14 P2 백엔드** — `/stats/people`, `/settings/llm` | `feat(stats): 인원별 시계열 API` · `feat(llm): 사용자별 프로바이더 설정` |
| 12:30–13:30 | 점심 | |
| 13:30–14:40 | **D. 2-13 미커밋 리마인드 스케줄러** (마지막 P1) | `feat(notify): 미커밋 리마인드 스케줄러` |
| 14:40–15:10 | **E. Mattermost 실전송 검증** (webhook URL 받는 대로) | — |
| 15:10–15:50 | **F. 2-12 E2E 2·6·7·8 점검** | `test: E2E 시나리오 2·6·7·8 점검 결과` |
| **16:00–17:00** | **동기화 포인트 ③** — 최종 머지, 전체 E2E, 태그 `v0.1.0` | |

### 순서 근거

담당자 1의 제안(`2-1·2-2 → 2-14 → Mattermost → 2-13 → 2-12`)을 거의 그대로 따르되 **D와 E를 맞바꿨다.**

- **A·B를 맨 앞에**: 데이터 유실 위험과, webhook이 붙는 순간 터지는 문제다. 30분이면 끝난다
- **C를 오전에**: 담당자 1의 `/people`·`/settings` 화면이 **1일차부터 완성돼 목업 폴백 중**이다. 내가 유일한 병목이라 가장 먼저 풀어야 한다
- **D를 E보다 먼저**: E는 webhook URL이라는 외부 의존이 있다. 없으면 대기해야 하므로, 확실히 할 수 있는 D를 앞에 둔다. E가 끝나야 F의 7번을 볼 수 있으니 E는 F 직전에 둔다
- **밀리면 버리는 순서**: F(부분) → D → C의 `/settings/llm`. **A·B는 절대 버리지 않는다**

---

## 2. 작업 0 — 브랜치 정리 + 환경 복구 (09:05–09:25)

### 미푸시 커밋 `7fb7a49` 을 버린다

어제 흰 화면을 막으려 `frontend/`의 null 가드를 급히 넣었는데, 담당자 1이 같은 버그를
**더 낫게** 고쳤다(`a.user !== null` 필터 + "연결되지 않은 활동 N건" 안내 문구). 시험 병합 결과
`HomePage.tsx` · `types/api.ts` · `DraftEditorPage.tsx` **3건 충돌**이다.

`frontend/`는 담당자 1 영역이므로 **내 커밋을 버리고 그쪽을 취한다.** 푸시하지 않아 안전하다.

```bash
git fetch --all --prune
git reset --hard origin/git_peristalsis     # 7fb7a49 폐기
git merge origin/main                        # 충돌 없음
cd backend && ./gradlew test                 # 114개 통과 확인
git push origin git_peristalsis
```

### 환경 복구

```bash
docker compose up -d                         # 09:00에 이미 함
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
pkill -f WorklogApplication
set -a && source .env && set +a
WORKLOG_LLM_PROVIDER=gemma4 ./gradlew bootRun
```

> 백엔드가 DB 끊김을 겪고도 프로세스는 살아 있다. 커넥션 풀 상태가 의심스러우니 재기동한다.

---

## 3. 작업 A — 리포 삭제 소유자 검사 (09:25–10:00) 🔴

### 문제

```java
// RepoService.delete — 누가 부르든 지워진다
public void delete(Long repoId) {
    Repo repo = repoRepository.findById(repoId).orElseThrow(...);
    repoRepository.delete(repo);
}
```

`activities.repo_id` 가 `ON DELETE CASCADE` 라 리포를 지우면 **그 리포의 활동이 전부 사라진다.**
지금 화면의 휴지통 아이콘 한 번이면 27건이 날아간다.

### 고칠 것

| 파일 | 내용 |
|---|---|
| `RepoService.delete` | `userId` 를 받아 `registeredBy.id` 와 비교. 다르면 `403 REPO_NOT_OWNED` |
| `RepoController.delete` | `@AuthenticationPrincipal` 을 넘긴다 |
| `RepoRepository` | 소유자 비교에 `registeredBy` 가 필요하니 `findWithRegistrant` 재사용 |

**등록자가 없는(NULL) 리포**는 등록자가 탈퇴한 경우다. 지금은 그런 경로가 없으므로
"소유자 불명 → 아무도 못 지움"으로 둔다. 필요해지면 관리자 개념이 생길 때 다시 본다.

### `POST /repos/{id}/sync` 도 막을까 — **막지 않는다** (BACKLOG §4-2 답)

동기화는 **읽기성 갱신**이다. 남이 눌러도 데이터가 늘기만 하고 사라지지 않는다.
등록자의 토큰으로 도는 것도 같은 리포를 읽을 뿐이다. 단일 팀 전제(PRD §12)에서
누구나 "지금 새로고침"을 누를 수 있는 편이 낫다. **삭제만 제한한다.**

### 완료 조건

```bash
# 남의 리포 삭제 → 403
curl -X DELETE -H "Authorization: Bearer $OTHER_JWT" localhost:8080/api/repos/1
# → 403 {"code":"REPO_NOT_OWNED", ...}
# 본인 리포 삭제 → 204
```
단위 테스트: 소유자 성공 / 타인 403 / 없는 리포 404.

---

## 4. 작업 B — notify 확정 검사 (10:00–10:25) 🔴

### 문제

`DraftNotifyController` 가 초안 존재만 확인하고 바로 전송한다. 디자인 브리프 3.3과 PRD F3은
**확정본만** 보내는 흐름이고, 담당자 1의 목업도 그 규칙으로 동작한다. 화면 버튼은 잠겨 있지만
API 직접 호출로는 미확정본이 나간다.

### 고칠 것

`status != CONFIRMED` → `409 DRAFT_NOT_CONFIRMED` ("확정한 뒤에 전송할 수 있습니다.")

전송 자체보다 **검사가 먼저** 와야 한다 — webhook 미설정이라 지금은 503이 먼저 나서
문제가 가려져 있다.

### 완료 조건

- DRAFT 초안 → `409 DRAFT_NOT_CONFIRMED`
- CONFIRMED 초안 + webhook 없음 → `503 NOTIFY_FAILED` (기존 동작 유지)
- 단위 테스트 2건

---

## 5. 작업 C — 2-14 P2 백엔드 (10:25–12:30) ★ 담당자 1 대기 해제

### C-1. `GET /stats/people` (F10b) — 약 1시간 20분

스키마는 PRD §7에 이미 확정돼 있다.

```jsonc
GET /stats/people?from=2026-09-03&to=2026-09-09&granularity=day&userId=3
{
  "from": "...", "to": "...", "granularity": "day",
  "items": [{
    "user": { "id": 3, "login": "taeil", "name": "배태일", "avatarUrl": null },
    "totals": { "commits": 23, "prs": 4, "merges": 3 },
    "series": [
      { "date": "2026-09-09", "commits": 2, "prs": 1, "merges": 1,
        "draft": { "id": 7, "status": "DRAFT" } },
      { "date": "2026-09-05", "commits": 0, "prs": 0, "merges": 0, "draft": null }
    ]
  }]
}
```

**구현 메모**

- `granularity=day|week` — 주 단위는 ISO 주 시작(월요일) 기준으로 묶는다
- **활동이 없는 날짜도 0으로 채운다** — 화면이 막대 그래프를 그리므로 구멍이 있으면 안 된다
- `series[].draft` 는 그날 그 사용자의 **최신 버전** 초안 (담당자 1의 `findLatestByWorkDate` 와 같은 규칙)
- `userId` 생략 시 전원. 미가입 계정은 `user` 가 없으므로 제외한다 (`byUser` 와 같은 규칙)
- 날짜 경계는 `KstDates` 재사용
- 기간 상한을 둔다 — `from`~`to` 가 92일을 넘으면 `400 INVALID_DATE_RANGE`

**파일**: `stats/dto/PeopleStatsResponse`, `StatsService.people()`, `StatsController`,
`ActivityRepository` 집계 쿼리 1개, `DraftRepository` 기간 조회 1개
**테스트**: 빈 날짜 채움 / 주 단위 묶기 / 기간 상한 / draft 연결

### C-2. `GET/PUT /settings/llm` (F9b) — 약 40분

`user_llm_settings` 테이블과 `UserLlmSetting` 엔티티는 **1일차에 이미 만들어 뒀다.**
`LlmProviderResolver.resolve(providerId)` 오버로드도 그때 넣어 뒀으니 이어 붙이기만 하면 된다.

```jsonc
GET  /settings/llm → { "provider": "gemma4", "model": "google/gemma-4-26b-a4b-qat", "available": ["mock","gemma4","qwen3"] }
PUT  /settings/llm { "provider": "qwen3" }
```

- **커스텀 엔드포인트 입력은 받지 않는다** (PRD F9 — 프리셋 중 고르기만)
- 모르는 프리셋 이름 → `400 INVALID_PARAMETER` + 가능한 값 안내
- `available` 은 `LlmProviderResolver` 가 실제로 들고 있는 id 목록. 화면이 라디오를 그린다
- **요약 생성 시 적용**: `SummaryService` 가 활동 소유자의 설정을 먼저 보고, 없으면 전역 설정
  → `resolve(userSetting)` 한 줄. 소유자가 없는(미매핑) 활동은 전역 설정

**완료 조건**: `PUT` 으로 `qwen3` 저장 → `GET` 반영 → 그 사용자 활동의 요약이 qwen3로 생성됨.

---

## 6. 작업 D — 2-13 미커밋 리마인드 (13:30–14:40)

PRD F7-2. **마지막 남은 P1 기능**이다.

### 판정 기준 (PRD F6·F7-2)

미커밋 diff **300줄 이상** 이거나 마지막 커밋이 **6시간 이상** 지난 세션.

### 이미 준비된 재료

| 있는 것 | 위치 |
|---|---|
| 6시간 상수 | `StatsService.STALE_AFTER` — **뽑아서 공유한다. 같은 값을 두 곳에 두지 않는다** |
| 방치 세션 조회 | `VscodeSessionRepository.countStale(workDate, threshold)` — 목록 버전 메서드 하나만 추가 |
| webhook 우선순위 | `NotifyService.webhookUrlFor(userId)` 그대로 재사용 |
| 사용자별 on/off | `notify_settings.remind_uncommitted` — false면 건너뛴다 |

### 메시지 (PRD F7 이벤트 2)

```
⚠️ {repo}@{branch}에 미커밋 변경 {N}파일이 {H}시간째 있습니다.
```

### 설계 결정

- **주기**: `@Scheduled(cron = "${worklog.notify.remind-cron:0 0 10-18 * * *}")` — 업무 시간에 매시 정각
- **중복 알림 방지**: 같은 세션에 하루 한 번만. `vscode_sessions` 에 컬럼을 더하면 **V4 마이그레이션**이
  필요한데, 회원 체계(§0-④)가 V4를 쓸 예정이라 번호가 겹칠 수 있다.
  → **오늘은 메모리 집합으로 막는다.** 프로세스가 재시작되면 다시 보낼 수 있지만, 하루짜리 제약이고
  스케줄러가 시간당 1회라 실해가 없다. 영속화가 필요하면 회원 체계 작업과 함께 컬럼을 넣는다
- **300줄 계산**: `uncommitted_files[].additions + deletions` 합
- 마지막 커밋이 **아예 없는**(`last_commit_at IS NULL`) 세션도 방치로 본다 (`countStale` 과 같은 규칙)

### 완료 조건

강제 테스트: `last_commit_at` 을 7시간 전으로 바꾼 세션을 하나 만들고 스케줄러 메서드를 직접 호출
→ 메시지 조립 확인. 단위 테스트로 300줄·6시간 경계와 `remind_uncommitted=false` 건너뛰기를 고정.

---

## 7. 작업 E — Mattermost 실전송 (14:40–15:10)

**webhook URL 하나만 있으면 20분이면 끝난다.** 코드·설정·실패 경로는 어제 다 확인했다.

1. `backend/.env` 의 `MATTERMOST_WEBHOOK_URL` 에 넣고 재기동
2. 초안 생성 → 채널에 "📝 …초안이 생성되었습니다" + 완료 작업 3줄 도착 확인 (이벤트 1)
3. 초안 확정 → `POST /drafts/{id}/notify` → 전체 Markdown 게시 확인 (이벤트 3)
4. 작업 D의 리마인드도 실제로 한 번 쏴 본다 (이벤트 2)

**URL을 못 받으면**: E를 건너뛰고 F로 간다. E2E 7번만 "미검증"으로 남기고 나머지를 마감한다.

---

## 8. 작업 F — 2-12 E2E 점검 (15:10–15:50)

내 담당은 **2·6·7·8**. 어제 통합본에서 2·6·8은 통과를 봤지만, 오늘 A~D 변경 후 다시 돌린다.

| # | 시나리오 | 어제 | 오늘 확인할 점 |
|---|---|---|---|
| 2 | 동기화 2회 → 건수 불변 | ✅ | 그대로 |
| 6 | `/settings` API Key 발급 → `curl -H "X-Api-Key" /external/summary` | ✅ | 그대로 |
| 7 | 초안 "Mattermost 전송" → 채널에 Markdown | ❌ | **작업 E·B 이후.** 미확정본은 409로 막히는지도 함께 |
| 8 | `gemma4` → `qwen3` 전환 → 코드 수정 없이 요약 변화 | ✅ | **작업 C-2 이후 사용자별 설정으로도 되는지** 추가 확인 |

결과를 `docs/E2E_day3.md` 에 남긴다 — 3일차 검수 기록이 필요하다.

---

## 9. 동기화 포인트 ③ (16:00–17:00)

```bash
cd backend && ./gradlew test          # 전건 통과
cd ../frontend && npm run build       # 타입체크 포함
git checkout main && git pull
git merge git_peristalsis
git push origin main
git tag -a v0.1.0 -m "WorkLog Drafter v0.1.0"
git push origin v0.1.0
```

- 담당자 1의 1-11(E2E 1·3·4·5·9)·1-12(`.vsix` 패키징)와 시각을 맞춘다
- **1-12가 오늘 가장 큰 미지수다.** 확장을 실제 설치해 보는 게 처음이라, 문제가 나오면
  내 F를 줄여서라도 시간을 넘긴다 — 제출물은 패키징이지 내 점검 기록이 아니다

---

## 10. 사람이 해야 하는 것

| # | 항목 | 언제까지 | 없으면 |
|---|---|---|---|
| 1 | **Mattermost Incoming Webhook URL** | 14:40 | 작업 E·F7 만 미검증으로 남는다. 나머지는 진행됨 |
| 2 | GitHub OAuth client secret 재발급 | 아무 때나 | 기능 영향 없음 |
| 3 | 회원 체계(BACKLOG §1) 다음 사이클 편성 여부 | 오늘 중 | 오늘 작업에는 영향 없음 — 범위 밖으로 확정했다 |
| 4 | 확장이 **미등록 리포**도 보고할지 (BACKLOG §4-3) | 오늘 중 | 프라이버시 판단. 담당자 1 영역이라 내 작업에는 영향 없음 |

---

## 11. 오늘 끝났을 때 (수용 기준)

- [ ] 남의 리포 삭제 → `403 REPO_NOT_OWNED`, 본인 것 → 204
- [ ] 미확정 초안 전송 → `409 DRAFT_NOT_CONFIRMED`
- [ ] `GET /stats/people` 이 §7 스키마대로 응답하고 **빈 날짜가 0으로 채워진다**
- [ ] `GET/PUT /settings/llm` 동작, 사용자별 프로바이더가 요약에 실제로 적용된다
- [ ] 담당자 1이 `MOCK_FALLBACK_PATHS` 를 **비울 수 있다** (목업 폴백 0건)
- [ ] 미커밋 리마인드가 300줄/6시간 조건에서 메시지를 만든다
- [ ] Mattermost 채널에 초안 생성·확정본·리마인드 3종 도착 (webhook 있을 때)
- [ ] E2E 2·6·7·8 결과가 `docs/E2E_day3.md` 에 남는다
- [ ] `./gradlew test` 전건 통과, `npm run build` 통과
- [ ] `main` 최종 머지 + 태그 `v0.1.0`

---

## 12. 오늘 하지 않는 것

명시해 둔다. 나중에 "왜 안 했나"가 되지 않도록.

| 항목 | 이유 |
|---|---|
| 자체 회원 체계 (V4, `auth/` 개편) | 1~2일 규모. BACKLOG §1에서 **범위 밖으로 확정** |
| 미가입 기여자를 사용자 카드로 표시 | 회원 체계가 전제. 지금은 안내 문구로 충분 |
| `syncStatus` 재시도 버튼 | 담당자 1이 "두지 않는다"로 답함 |
| 확장 계획 X1(봇 DM)·X4(Webhook) | P2까지 끝난 뒤에만. 오늘은 P2 마무리가 목표 |
| `POST /repos/{id}/sync` 소유자 제한 | 읽기성 갱신이라 제한하지 않기로 결정 (§3) |
