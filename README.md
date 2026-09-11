# WorkLog Drafter

GitHub 활동과 VS Code 안의 미커밋 작업을 모아 **업무 일지 초안**을 만들어 주는 도구.
자세한 요구사항은 [docs/PRD_090910.md](docs/PRD_090910.md) 참고.

- 담당자 1 — 클라이언트: `frontend/`, `vscode-extension/`, 초안 CRUD API
- 담당자 2 — 연동: GitHub 수집, LLM 해석, 인증, Mattermost 알림
  - 1일차 계획: [docs/DAY1_integration_plan.md](docs/DAY1_integration_plan.md)

---

## 구성

```
2026-pknu-3day/
├── docker-compose.yml     postgres 16
├── backend/               Spring Boot 3.3 · Java 21 · Gradle
├── frontend/              React 18 + TypeScript + Vite   (담당자 1)
└── vscode-extension/      TypeScript + vsce              (담당자 1)
```

## 사전 요구사항

- **JDK 21** — Gradle 데몬이 JDK 21 위에서 돌아야 한다. Java 24 등이 기본이면 실행 전에 지정한다.
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
  ```
- Docker Desktop

## 로컬 실행

처음 클론했다면 **[docs/SETUP.md](docs/SETUP.md)** 를 본다. 아래는 요약이다.

```bash
./scripts/setup.sh          # ① 세팅 (한 번) — .env 생성·비밀값 발급·DB 기동·의존성 설치
./scripts/backend.sh        # ② 백엔드  http://localhost:8080/api
cd frontend && npm run dev  # ③ 프론트  http://localhost:5173
```

http://localhost:5173 에서 **admin / admin1234** 로 들어간다 — 기본 비밀번호라 첫 로그인에서
비밀번호 변경을 먼저 시킨다.
사원번호 로그인은 사원 명부(와플 API 또는 `WAPLE_FALLBACK_EMPLOYEES`)가 붙어야 된다.

프론트는 **따로 설정하지 않아도 실서버를 부른다.** `frontend/.env` 없이 그대로 띄우면 된다.
화면만 눌러 보려고 가짜 데이터를 쓰려면 그때만 `VITE_USE_MOCK=true` 를 켠다 — 켜 둔 채로
진짜 사원번호로 로그인하면 "사원번호 또는 비밀번호가 올바르지 않습니다" 가 뜬다. 서버까지
가지도 않은 것이라 서버 로그에는 아무것도 남지 않는다.

### 손으로 띄우려면

`setup.sh` 없이도 된다. 다만 `backend/.env` 의 `JWT_SECRET`·`ENCRYPTION_KEY` 는 비워 둘 수
없다 — 로그인 토큰과 GitHub 토큰 암호화에 쓰는 값이라, 비면 기동 중에 멈춘다.

```bash
cp .env.example .env
cp backend/.env.example backend/.env     # JWT_SECRET, ENCRYPTION_KEY 를 채운다
docker compose up -d
cd backend && set -a && source .env && set +a && ./gradlew bootRun
cd frontend && npm run dev
```

### 세팅이 됐는지 확인

[docs/SMOKE_CHECK.md](docs/SMOKE_CHECK.md) 를 따라 로그인 → 확장 전송 → 일지 생성·확정까지
한 바퀴 돌려 본다. 10분이면 된다. 고친 것이 무엇을 깨뜨리지 않았는지 볼 때도 같은 길을 쓴다.

### 기능 켜기

세팅만 하면 로그인·화면·업무 일지 작성까지 된다. GitHub 수집·AI 요약·사원 명부·Mattermost
는 `backend/.env` 를 채우는 만큼 켜진다 — 표는 [docs/SETUP.md §5](docs/SETUP.md) 에 있다.

### DB 확인

```bash
docker compose exec postgres psql -U worklog -d worklog -c '\dt'
```

호스트에서 직접 붙을 때는 노출 포트를 쓴다 (기본 5433).

```bash
psql -h localhost -p 5433 -U worklog -d worklog
```

## 데이터베이스

스키마는 Flyway 로만 바꾼다. `backend/src/main/resources/db/migration/` 에
`V2__xxx.sql` 처럼 새 파일을 추가하고, 이미 적용된 마이그레이션 파일은 수정하지 않는다.

JPA 는 `ddl-auto: validate` 라 엔티티와 스키마가 어긋나면 기동 시점에 실패한다.

## VS Code 확장 설치

배포용 `.vsix` 를 만들고 설치하는 경로다. 확장 자체의 사용법·설정·문제 해결은
[vscode-extension/README.md](vscode-extension/README.md) 에 있다.

```bash
cd vscode-extension
npm install
npm run package        # worklog-drafter-<버전>.vsix 생성
```

설치는 둘 중 하나로 한다.

```bash
code --install-extension worklog-drafter-<버전>.vsix
```

또는 VS Code → 확장 패널 → 우상단 `⋯` → **Install from VSIX…**.

설치 후 **API Key 를 넣어야 전송이 된다.** 대시보드 → 설정 → API Key 발급 →
VS Code 설정(`Cmd+,`)에서 `worklog` 검색 → **Api Key** 에 붙여넣는다.
키가 없으면 상태바에 `WorkLog: API Key 미설정` 이 뜨고, 클릭하면 설정 화면으로 안내한다.

## 브랜치

| 브랜치 | 용도 |
|---|---|
| `main` | 동기화 포인트마다 머지되는 통합 브랜치 |
| `track/client` | 담당자 1 |
| `git_peristalsis` | 담당자 2 (PRD 상 이름은 `track/integration`) |

커밋 메시지는 Conventional Commits (`feat:`, `fix:`, `chore:`).
