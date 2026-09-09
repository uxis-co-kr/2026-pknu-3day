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

세 개의 명령으로 전체가 뜬다.

```bash
# 1) DB — 호스트 5432 로 노출된다 (루트 .env 의 POSTGRES_PORT 로 바꿀 수 있다)
docker compose up -d

# 2) 백엔드 (http://localhost:8080/api)
cd backend && ./gradlew bootRun

# 3) 프론트 (http://localhost:5173)
cd frontend && npm run dev
```

### 백엔드 환경 변수

`backend/.env.example` 를 `backend/.env` 로 복사해 값을 채운다. **`.env` 는 커밋하지 않는다.**

```bash
cp backend/.env.example backend/.env
```

값을 셸에 올린 뒤 실행하려면:

```bash
cd backend && set -a && source .env && set +a && ./gradlew bootRun
```

기본값만으로도 DB 연결과 Flyway 마이그레이션은 동작한다. GitHub OAuth·JWT·LLM 관련 값은
해당 기능을 붙이는 시점(1일차 오후)부터 필요하다.

### DB 확인

```bash
docker compose exec postgres psql -U worklog -d worklog -c '\dt'
```

호스트에서 직접 붙을 때는 노출 포트를 쓴다 (기본 5432).

```bash
psql -h localhost -p 5432 -U worklog -d worklog
```

## 데이터베이스

스키마는 Flyway 로만 바꾼다. `backend/src/main/resources/db/migration/` 에
`V2__xxx.sql` 처럼 새 파일을 추가하고, 이미 적용된 마이그레이션 파일은 수정하지 않는다.

JPA 는 `ddl-auto: validate` 라 엔티티와 스키마가 어긋나면 기동 시점에 실패한다.

## 브랜치

| 브랜치 | 용도 |
|---|---|
| `main` | 동기화 포인트마다 머지되는 통합 브랜치 |
| `track/client` | 담당자 1 |
| `git_peristalsis` | 담당자 2 (PRD 상 이름은 `track/integration`) |

커밋 메시지는 Conventional Commits (`feat:`, `fix:`, `chore:`).
