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

**새로 클론했다면 `backend/.env` 부터 만든다.** 이 파일은 커밋하지 않으므로 클론에는 없고,
`JWT_SECRET`·`ENCRYPTION_KEY` 가 비면 백엔드가 뜨다가 멈춘다 (아래 "백엔드 환경 변수").

```bash
# 0) 백엔드 환경 변수 (클론 직후 한 번)
cp backend/.env.example backend/.env   # 값을 채운다

# 1) DB — 호스트 5433 으로 노출된다 (루트 .env 의 POSTGRES_PORT 로 바꿀 수 있다)
docker compose up -d

# 2) 백엔드 (http://localhost:8080/api)
cd backend && set -a && source .env && set +a && ./gradlew bootRun

# 3) 프론트 (http://localhost:5173)
cd frontend && npm run dev
```

프론트는 **따로 설정하지 않아도 실서버를 부른다.** `frontend/.env` 없이 그대로 띄우면 된다.
화면만 눌러 보려고 가짜 데이터를 쓰려면 그때만 `VITE_USE_MOCK=true` 를 켠다 — 켜 둔 채로
진짜 사원번호로 로그인하면 "사원번호 또는 비밀번호가 올바르지 않습니다" 가 뜬다. 서버까지
가지도 않은 것이라 서버 로그에는 아무것도 남지 않는다.

### 백엔드 환경 변수

`backend/.env.example` 를 `backend/.env` 로 복사해 값을 채운다. **`.env` 는 커밋하지 않는다.**

```bash
cp backend/.env.example backend/.env
```

값을 셸에 올린 뒤 실행하려면:

```bash
cd backend && set -a && source .env && set +a && ./gradlew bootRun
```

DB 연결과 Flyway 마이그레이션은 기본값으로 동작한다. 다만 **`JWT_SECRET` 과 `ENCRYPTION_KEY`
는 비워 둘 수 없다** — 로그인 토큰과 GitHub 토큰 암호화에 쓰는 값이라, 비면 기동 중에
`JWT_SECRET 이 비어 있다` 로 멈춘다. 만드는 법은 `.env.example` 에 적어 두었다.

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
