# 동작 확인 — 한 바퀴 돌려 보기

세팅([SETUP.md](SETUP.md))이 끝났는지, 고친 것이 무엇을 깨뜨리지 않았는지 **10분 안에** 확인하는 길.
아래는 2026-09-11 에 실제로 돌려 본 순서와 결과다.

> **먼저 읽을 것 — 9번(알림)은 진짜로 채팅방에 올라간다.** 대표 채널이 설정돼 있으면 봇이
> 팀 채널에 글을 쓴다. 시험 삼아 누르면 동료들이 본다. 확인하려면 혼자 있는 채널로 바꾸고 하거나,
> 9번을 건너뛴다.

계정은 **데모 계정**(`9999`)으로 한다. 진짜 사람 계정으로 하면 그 사람 일지 버전이 늘고 활동에 섞인다.

```bash
BASE=http://localhost:5173/api          # 사내망이면 http://<서버IP>:5173/api
ORIGIN="Origin: http://localhost:5173"  # 주소를 맞춘다
TODAY=$(date +%F)
```

## 1~4. 로그인부터 확장 전송까지

| # | 확인 | 부르는 것 | 기대 |
|---|---|---|---|
| 1 | 사원번호 로그인 | `POST /auth/login` `{loginId, password}` | `token` 과 `mustChangePassword` |
| 2 | API Key 발급 | `POST /me/api-keys` `{label}` | `wl_` 로 시작하는 `key` — **이때 한 번만 보인다** |
| 3 | 확장이 보내는 전송 | `POST /vscode/sessions` + `X-Api-Key` | `{id}` — JWT 로 부르면 403 이 맞다 |
| 4 | 확장이 되읽기 | `GET /vscode/sessions?date=&mine=true` | 방금 보낸 것이 목록에 |

3번 본문은 `vscode-extension/src/types.ts` 와 같은 모양이어야 한다. 최소한 이만큼은 필요하다.

```json
{ "remoteUrl": "https://github.com/<org>/<repo>.git", "branch": "main",
  "workDate": "2026-09-11", "uncommittedFiles": [], "todos": [],
  "unsavedFiles": [], "aiSessions": [], "unpushedCommits": [] }
```

> `mine=true` 를 빠뜨리면 그날 **팀 전원**의 기록이 내려온다. 확장은 자기 `userId` 를 모르기
> 때문에 이 깃발이 있다.

## 5~8. 일지

| # | 확인 | 부르는 것 | 기대 |
|---|---|---|---|
| 5 | 초안 생성 | `POST /drafts/generate` `{date}` | 201 · `contentMd` 가 커밋을 묶어 쓴 글. **LLM 을 부르므로 10초쯤 걸린다** |
| 6 | 초안 읽기 | `GET /drafts/{id}` | `version` 이 1 씩 오른다 (덮어쓰지 않는다) |
| 7 | 저장 | `PATCH /drafts/{id}` `{contentMd}` | 200 |
| 8 | 확정 | `POST /drafts/{id}/confirm` | 200 · 상태 `CONFIRMED` |
| 10 | 주간 일지 | `POST /drafts/generate/weekly` `{from}` | 201 |

`WORKLOG_LLM_PROVIDER` 가 `mock` 이면 5번이 템플릿 글로 나온다 — 실패가 아니다.
활동도 세션도 없는 날은 **초안을 만들지 않는다**(빈 응답). 3번을 먼저 하는 이유다.

## 9. 알림 (선택 — 위 경고를 읽고)

`POST /drafts/{id}/notify` → `{"sent": true}`.

- **한 번이라도 저장한 일지만** 나간다. 자동 생성 그대로는 `DRAFT_NOT_EDITED` 로 막힌다
- 대표 채널이 있으면 봇이 그 채널에, 없으면 전역 웹훅으로 간다. 둘 다 없으면 503
- 본문이 아니라 **"요약되었습니다" 한 줄 + 링크**가 간다

## 11. 권한

| 확인 | 기대 |
|---|---|
| ADMIN 으로 `/admin/overview`·`/admin/people`·`/admin/settings/notify` | 200 |
| 일반 회원으로 `/admin/overview` | **403** |
| 일반 회원으로 남의 `userId` 를 붙여 `/activities`·`/stats/people` | **403** |
| 사내망 밖 Origin 으로 아무 POST | **403** (CORS 가 먼저 막는다) |

## 2026-09-11 결과

전부 기대대로였다. 눈여겨본 것만 적는다.

- 5번 초안 생성 **10초** — `gemma4` 기준. 커밋 7건을 묶어 "완료한 작업" 으로 썼다
- 9번이 `{"sent":true}` 를 돌려줬는데, `.env` 의 웹훅은 비어 있었다. **대표 채널(봇)로 나간
  것**이다 — 웹훅만 보고 "설정 안 됐으니 안 나갔겠지" 로 넘기면 틀린다
- 관리자 계정은 이날 기본 비밀번호에서 바뀌었다. `admin1234` 로는 이제 안 들어간다
- 시험용 API Key 는 지웠다. 데모사원(`9999`)의 오늘 일지는 확정 상태로 남아 있다

곁들여 볼 기록: [E2E_0911.md](E2E_0911.md) — 담당자 2 의 권한·보안 쪽 점검.
