# WorkLog Drafter — VS Code 확장

커밋되지 않은 작업과 오늘의 계획을 모아 WorkLog Drafter 서버로 보낸다.
GitHub 커밋만으로는 보이지 않는 **진행 중인 일**이 업무 일지 초안에 들어가게 하는 것이 목적이다.

## 무엇을 보내는가

열려 있는 워크스페이스가 git 저장소이면, 저장소마다 하루 한 건의 세션을 보낸다.

| 항목 | 설명 |
|---|---|
| 미커밋 파일 | 경로와 `+N −M`. `worklog.collectDiff` 가 켜져 있으면 diff 본문도 |
| TODO 주석 | 워크스페이스에서 찾은 `TODO` 주석의 파일·줄·내용 |
| 계획 메모 | `WorkLog: 오늘 계획 기록` 으로 직접 적은 한두 줄 |
| 편집 타임라인 | 파일별 첫 저장·마지막 저장 시각과 저장 횟수 |
| 브랜치·remote URL | 어느 저장소의 작업인지 서버가 알아보기 위한 값 |

같은 (사용자·저장소·브랜치·날짜) 세션은 **덮어쓰기(UPSERT)** 된다. 여러 번 보내도 건수가 늘지 않는다.

## 설치

1. `.vsix` 파일을 받는다 (배포처: 팀 공유 폴더 또는 `npm run package` 로 직접 만든 파일).
2. VS Code → 확장 패널 → 우상단 `⋯` → **Install from VSIX…** → 파일 선택.
   - 터미널을 선호하면: `code --install-extension worklog-drafter-0.1.0.vsix`
3. VS Code 를 다시 시작한다.

## 최초 설정

전송하려면 **개인 API Key** 가 필요하다. 대시보드에 로그인해서 발급받는다.

1. 대시보드(`http://localhost:5173`) → **설정** → API Key **발급**
2. 발급 직후 한 번만 보이는 `wl_...` 키를 복사한다. 창을 닫으면 다시 볼 수 없다
3. VS Code 설정(`Cmd+,`)에서 `worklog` 검색 → **Api Key** 에 붙여넣는다
4. 서버가 로컬이 아니면 **Server Url** 도 바꾼다 (`/api` 는 붙이지 않는다)

상태바의 경고를 클릭하면 이 설정 화면으로 바로 갈 수 있다.

## 사이드바

활동 표시줄의 **WorkLog Drafter** 아이콘을 누르면 **지금 무엇이 서버로 갈지** 보인다.
전송하기 전에 확인할 수 있어서, 상태바의 숫자 하나만 보고 짐작하지 않아도 된다.

```
전송 완료 09:48:36 · 미커밋 5파일
▾ uxis-co-kr/2026-pknu-3day · VsPeristalsis_dashboard
    오늘 계획 메모
  ▸ 미커밋 5개
      tracked.txt   +2 −1
      new.ts        +4 −0
  ▸ TODO 2개
      출석 중복 검증 마무리   new.ts:2
  ▸ 저장 이벤트 1개
      tracked.txt   2회
```

- 파일이나 TODO 를 누르면 **그 자리로 열린다** (TODO 는 해당 줄로).
- 맨 위 줄이 마지막 전송 결과다. 실패하면 사유가 그대로 뜬다.
- 뷰 제목 오른쪽에 **전송 · 계획 기록 · 새로고침** 버튼이 있다.
- 파일을 저장하면 1초 뒤 자동으로 갱신된다. diff 본문은 화면에 쓰지 않으므로 받아 오지 않는다.

## 쓰는 법

| 방법 | 동작 |
|---|---|
| 상태바 `미커밋 N파일` 클릭 | 지금 전송 |
| 명령 팔레트 → `WorkLog: 지금 전송` | 같음 |
| 명령 팔레트 → `WorkLog: 오늘 계획 기록` | 계획 메모 입력 |
| 그냥 두기 | 30분마다 자동 전송, VS Code 종료 시 한 번 더 |

전송이 되면 대시보드 홈의 **미커밋 세션** 카드가 올라가고, 그날 초안을 생성하면
"진행 중 / 미커밋" 과 "계획 / TODO" 에 반영된다.

## 설정 항목

| 키 | 기본값 | 설명 |
|---|---|---|
| `worklog.serverUrl` | `http://localhost:8080` | 백엔드 주소. `/api` 는 붙이지 않는다 |
| `worklog.apiKey` | (빈 값) | 대시보드에서 발급한 개인 키 |
| `worklog.intervalMinutes` | `30` | 자동 전송 주기(분). 최소 5 |
| `worklog.collectDiff` | `true` | 끄면 파일 경로와 변경 줄 수만 보낸다 |

## 문제가 생기면

상태바에 사유가 한 줄로 뜬다. 자세한 기록은 **출력 패널 → WorkLog** 채널에 있다.

| 상태바 | 뜻 | 할 일 |
|---|---|---|
| `API Key 미설정` | 키가 비어 있다 | 위 최초 설정 |
| `API Key 오류` | 서버가 401/403 을 돌려줬다 | 키를 다시 발급받는다 |
| `서버에 연결할 수 없음` | 주소가 틀렸거나 서버가 꺼져 있다 | `worklog.serverUrl` 과 백엔드 확인 |
| `서버 응답 없음` | 10초 안에 응답이 없었다 | 백엔드 로그 확인 |
| `서버 오류 5xx` | 백엔드가 실패했다 | 출력 패널의 응답 본문 확인 |

전송 실패는 확장을 멈추지 않는다. 다음 주기에 다시 시도하고, 같은 세션을 덮어쓴다.

## 알아 둘 것

- **워크스페이스가 git 저장소이기만 하면 등록 여부와 무관하게 보고한다.** 개인 프로젝트를
  열어 두면 그 미커밋 작업도 올라간다 (BACKLOG §5 결정 3번).
- API Key 는 VS Code 설정에 평문으로 저장된다. 설정 동기화를 켜 두었다면 계정에 함께 올라간다.

## 수집기 테스트

무엇이 서버로 갈지 **보내기 전에** 확인할 수 있다. VS Code 를 띄우지 않고 수집기만 돌린다.

```bash
npm run compile
npm run preview                       # 현재 폴더
npm run preview -- ~/some/repo        # 다른 저장소
npm run preview -- . --no-diff        # diff 본문 없이
npm run preview -- . --plan "메모"    # 계획 메모를 넣은 상태로
npm run preview -- . --full           # 서버로 갈 JSON 전체
```

출력은 이런 모양이다.

```
remoteUrl   https://github.com/uxis-co-kr/2026-pknu-3day.git
branch      VsPeristalsis_dashboard
workDate    2026-09-10   (KST)

미커밋 4개
  +1 −0  staged.txt  diff 8줄
  +2 −1  tracked.txt  diff 11줄
  +0 −0  blob.dat  diff (없음)
  +4 −0  new.ts  [새 파일]  diff 5줄

TODO 2개
  new.ts:2  출석 중복 검증 마무리
```

**전송은 하지 않는다.** 읽기만 한다.

알아 둘 것:

- `edit timeline` 은 항상 0 이다. VS Code 의 파일 저장 이벤트로만 쌓이므로 밖에서는 재현되지 않는다.
- 바이너리 파일은 `+0 −0`, diff 없음으로 나온다. 본문을 보내지 않는다.
- diff 는 파일당 200줄까지다. 그보다 길면 마지막에 생략 줄이 붙는다.
- `origin` 이 없는 저장소는 건너뛴다 — 서버가 어느 리포인지 알 수 없기 때문이다.

### VS Code 안에서 확인하기

전송까지 포함한 전체 경로는 실제 VS Code 로 본다.

- **F5** — Extension Development Host 가 뜬다. 설치 없이 수정한 코드를 바로 확인할 수 있다.
- 설치본으로 볼 때는 명령 팔레트 → `WorkLog: 지금 전송` → **출력 패널 → WorkLog** 채널.
  전송한 세션 id 와 미커밋 파일 수가 한 줄로 찍힌다.

## 개발

```bash
npm install
npm run compile        # tsc -p ./
npm run watch          # 감시 컴파일
npm run package        # .vsix 생성
```

F5 로 **Extension Development Host** 를 띄우면 설치 없이 디버깅할 수 있다.
