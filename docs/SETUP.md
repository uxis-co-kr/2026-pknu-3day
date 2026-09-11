# 처음 세팅하기

다른 PC 에서 막 클론했을 때 **로그인 화면까지 가는 길**. 순서대로 따라가면 된다.

```bash
git clone https://github.com/uxis-co-kr/2026-pknu-3day.git
cd 2026-pknu-3day
./scripts/setup.sh          # ① 세팅 (한 번)
./scripts/backend.sh        # ② 백엔드  — 터미널 1
cd frontend && npm run dev  # ③ 프론트  — 터미널 2
```

→ http://localhost:5173 에서 **admin / admin1234** 로 들어간다. 기본 비밀번호라 처음 로그인하면
화면이 **비밀번호 변경부터** 시킨다.

---

## 1. 사전 요구사항

셋만 있으면 된다. `setup.sh` 가 먼저 확인하고, 없으면 무엇이 없는지 알려 준다.

| 무엇 | 왜 | 맥에서 까는 법 |
|---|---|---|
| **JDK 21** | Gradle 이 21 위에서만 돈다 | `brew install --cask temurin@21` |
| **Docker Desktop** | postgres 16 을 컨테이너로 띄운다 | [docker.com](https://www.docker.com/products/docker-desktop) |
| **Node 20+** | Vite 8 이 그 아래에서 안 돈다 | `brew install node` |

JDK 가 여러 개 깔려 있으면 셸 설정(`~/.zshrc`)에 한 줄 넣어 둔다. 맥이라면
`scripts/backend.sh` 가 알아서 21 을 찾으므로 없어도 백엔드는 뜬다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## 2. 세팅 — `./scripts/setup.sh`

여러 번 돌려도 안전하다. 이미 된 것은 건너뛴다.

| 하는 일 | 설명 |
|---|---|
| 사전 요구사항 확인 | Docker 데몬이 꺼져 있는 것까지 본다 |
| `.env` 두 개 생성 | `.env.example` → `.env`, `backend/.env.example` → `backend/.env` |
| **비밀값 생성** | `JWT_SECRET`·`ENCRYPTION_KEY` 를 `openssl` 로 만들어 넣는다 |
| DB 기동 | `docker compose up -d` 후 `healthy` 가 될 때까지 기다린다 |
| 의존성 설치 | `frontend`·`vscode-extension` 의 `npm ci` |
| 백엔드 미리 빌드 | 첫 `bootRun` 이 몇 분 걸리는 것을 여기서 끝낸다 |

> **비밀값은 비어 있을 때만 만든다.** 이미 값이 있는데 새로 만들면 발급해 둔 로그인 토큰과
> DB 에 암호화해 둔 GitHub 토큰이 한꺼번에 죽는다. 그래서 덮어쓰지 않는다.

옵션: `--skip-db` (DB 는 이미 돌고 있다) · `--quick` (의존성·미리 빌드 건너뛰기)

## 3. 띄우기

```bash
./scripts/backend.sh          # http://localhost:8080/api
cd frontend && npm run dev    # http://localhost:5173
```

`backend.sh` 는 `backend/.env` 를 셸에 올린 뒤 `./gradlew bootRun` 을 부른다. 손으로 칠 때
`set -a && source .env && set +a` 를 빠뜨리기 쉬운데, 그러면 `JWT_SECRET 이 비어 있다` 로
기동 중에 멈춘다.

프론트는 **따로 설정하지 않아도 실서버를 부른다.** `/api` 요청은 vite 프록시가 8080 으로 넘긴다.

## 4. 로그인

| 계정 | 언제 쓰나 |
|---|---|
| **admin / admin1234** | 세팅 직후. 사원 명부를 붙이기 전까지 **유일하게 들어갈 수 있는 계정** |
| 사원번호 / 사원번호 | 와플 API 나 폴백 명부가 붙은 뒤. 첫 로그인에 계정이 만들어진다 |

`admin1234` 는 `.env.example` 에도 이 문서에도 적혀 있는, 사실상 공개된 비밀번호다. 그래서
**기본값으로 만들어진 관리자 계정은 첫 로그인에서 비밀번호를 바꿔야 다음 화면으로 넘어간다.**
`backend/.env` 의 `WORKLOG_ADMIN_PASSWORD` 를 미리 다른 값으로 적어 두면 그 단계가 없다.

## 5. 기능 켜기 — `backend/.env`

세팅만 하면 **로그인·화면·업무 일지 작성**까지 된다. 나머지는 값을 채우는 만큼 켜진다.

| 켜는 것 | 채울 값 | 안 채우면 |
|---|---|---|
| **GitHub 수집** | `GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET` | 설정 > 깃허브 연동이 "설정되지 않았습니다" 로 막힌다 |
| **AI 요약·일지 작성** | `WORKLOG_LLM_PROVIDER` 를 `gemma4`/`qwen3` 로 (기본 `mock`) | 일지가 템플릿으로 나온다. 화면은 다 돈다 |
| **사원 명부** | `WAPLE_API_BASE_URL`·`WAPLE_API_KEY` (사내망 전용) | 사원번호 로그인이 안 된다. `WAPLE_FALLBACK_EMPLOYEES=9998:테스트사원` 으로 대신할 수 있다 |
| **Mattermost 알림** | `MATTERMOST_WEBHOOK_URL` | 알림만 안 간다 |
| **Mattermost 질의응답** | `MATTERMOST_OUTGOING_TOKEN` | `/chat/mattermost` 가 **닫혀 있다** (503). 토큰 없이 열어 두면 남의 일지가 새므로 일부러 막는다 |

값을 바꾸면 백엔드를 다시 띄운다.

## 6. 여럿이 한 서버를 볼 때 (사내망)

서버는 **한 대만** 띄우고 나머지는 브라우저로 붙는다. 자세한 배경은 BACKLOG2 §2-1.

1. 서버 PC 의 주소를 확인한다 — `ipconfig getifaddr en0` (예: `192.168.1.224`)
2. 다른 사람은 `http://192.168.1.224:5173` 을 연다. 자기 PC 에는 아무것도 안 띄워도 된다
3. **GitHub 연동까지 쓰려면** OAuth App 의 Authorization callback URL 과 `backend/.env` 의
   `WORKLOG_OAUTH_REDIRECT_URI` 를 그 주소로 맞춘다 (`http://<IP>:5173/api/auth/github/callback`).
   둘 중 하나만 고치면 GitHub 이 `redirect_uri is not associated` 로 막는다
4. `FRONTEND_URL` 도 같은 주소로 두면 알림 링크가 그 주소로 나간다

> vite 는 이미 `0.0.0.0` 에 붙고(`server.host: true`), 백엔드도 모든 인터페이스에서 받는다.
> DB 만 `127.0.0.1` 로 묶여 있다 — 쓰는 것은 같은 PC 의 백엔드뿐이라 열 이유가 없다.

## 7. VS Code 확장

```bash
cd vscode-extension && npm run package        # worklog-drafter-<버전>.vsix
code --install-extension worklog-drafter-<버전>.vsix
```

설치 후 **둘**을 넣는다. 사이드바 "오늘 보낼 내용" 의 둘째 줄이 지금 부르는 주소다.

- **서버 주소** — 그 줄을 눌러 바꾼다. 기본값 `localhost` 는 **자기 PC** 를 가리킨다
- **API Key** — 대시보드 설정에서 발급한 **자기** 키. 주소는 같게, 키는 각자다

## 8. 막히면

| 증상 | 원인과 해결 |
|---|---|
| `JWT_SECRET 이 비어 있다` 로 기동 실패 | `.env` 를 셸에 안 올렸다. `./scripts/backend.sh` 로 띄운다 |
| `Ports are not available: 5433` | 로컬 postgres 가 이미 쓰고 있다. 루트 `.env` 에 `POSTGRES_PORT=5434` 를 적고 `backend/.env` 의 `DB_URL` 도 같이 고친다 |
| `Schema-validation: missing table` | 마이그레이션이 안 돌았다. DB 가 떠 있는지 보고 백엔드를 다시 띄운다. 스키마는 Flyway 로만 바꾼다 |
| 로그인 화면에서 계속 실패 | 사원 명부가 비었을 수 있다. 서버 로그에 `사원 목록이 비어 있어…` 가 있으면 명부 설정 문제다. admin 으로 먼저 들어간다 |
| 화면은 뜨는데 저장·로그인만 실패 | 사내 IP 로 열었는데 백엔드가 옛 버전이다. `516c7b4` 이후로 올린다 (그 전에는 CORS 가 `FRONTEND_URL` 한 곳만 허용했다) |
| GitHub 연동에서 `redirect_uri is not associated` | §6 의 3번. OAuth App 콜백과 `WORKLOG_OAUTH_REDIRECT_URI` 가 글자까지 같아야 한다 |
| 확장이 전송에 실패 | 사이드바 둘째 줄의 주소가 자기 PC 를 가리키고 있다. 눌러서 서버 PC 주소로 바꾼다 |
| Gradle 이 `Unsupported class file major version` | JDK 21 이 아니다. `java -version` 을 보고 `JAVA_HOME` 을 맞춘다 |

## 자주 쓰는 명령

```bash
./scripts/setup.sh --skip-db     # 세팅만 다시 (DB 는 그대로)
docker compose up -d             # DB 켜기
docker compose down              # DB 끄기 (데이터는 볼륨에 남는다)
docker compose exec postgres psql -U worklog -d worklog -c '\dt'
cd backend && ./gradlew test     # 백엔드 테스트
cd frontend && npm run lint
```
