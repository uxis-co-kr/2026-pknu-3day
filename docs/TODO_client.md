# 담당자 1 (클라이언트) 할 일 — WorkLog Drafter

> **1·2일차 완료.** 결과는 [DAY1_client_result.md](DAY1_client_result.md),
> 남은 일과 기획 변경 논의는 [BACKLOG.md](BACKLOG.md) 참고.
> 3일차 예정이던 F9·F10(1-13)은 1일차에 미리 끝냈다(백엔드 엔드포인트만 대기).
> 남은 것은 3일차의 E2E 점검(1-11)과 패키징(1-12), 그리고 BACKLOG 의 미결 항목.

기준 문서: `docs/PRD_090910.md`, `docs/DESIGN_BRIEF.md` · 브랜치: `VsPeristalsis_dashboard` (PRD의 `track/client`에 해당)

## 내 소유 영역
- `frontend/` 전체
- `vscode-extension/` 전체
- `backend/.../vscode/` — 세션 수신·조회 API
- `backend/.../draft/` 중 `DraftController` + `DraftService`의 조회·수정·확정 (생성 로직 `DraftGenerator`는 담당자 2)

## 우선순위
| ID | 기능 | 우선순위 | 상태 |
|----|------|---------|------|
| F4 | 대시보드 — 일별 조회, 사용자/리포 필터, 초안 편집·확정 UI | P0 | ✅ 목업 기준 완료 (1일차) |
| F3b | 초안 CRUD/확정 API (`GET/PATCH /drafts`, `/confirm`) | P0 | ⬜ 2일차 (1-8) |
| F6 | VS Code 확장 (수집·전송) + `POST /vscode/sessions` 수신 API | P0 | 🔶 수신 API 완료 (1-4) / 확장 2일차 |
| F11 | `.vsix`, 프론트 빌드, 설치 README | P1 | ⬜ 3일차 (1-12) |
| F9 | `/settings` LLM 모델 옵션 UI | P2 | ✅ 1일차에 미리 완료 |
| F10 | `/people` 인원별 통계·Recharts 차트 | P2 | ✅ 1일차에 미리 완료 (+ 잔디 달력) |

---

## 1일차 — 목업 및 목업 데이터로 테스트

### 공통 (오전, 담당자 2와 함께)
- [x] 1-0a 모노레포 스캐폴딩 `backend/`(Spring Boot 3.3, Java 21, Gradle) · `frontend/`(React 18 + TS + Vite) · `vscode-extension/`(TS), `docker-compose.yml`(postgres 16), 루트 README → 세 프로젝트 각각 빌드
- [x] 1-0b Flyway V1 스키마(§6) + JPA 엔티티 전부 → `bootRun` 후 테이블 생성 확인
- [x] 1-0c §7 API 계약 최종 확인, 목업 JSON 필드명 합의
- [x] **동기화 포인트 ① (정오)** 공통 작업 커밋 → 브랜치 분기

### 내 트랙
- [x] 1-1 Claude Design으로 `DESIGN_BRIEF.md`의 9개 아트보드(6화면) 생성 → Figma 동기화 → 팀 리뷰 후 확정
- [x] 1-1 확정 화면의 예시 값을 §7 스키마 그대로 `frontend/src/mocks/*.json`으로 변환 (사용자 3 · 리포 2 · 활동 약 40 · 초안 3(DRAFT 2, CONFIRMED 1) · 세션 4). 필드명은 실 API와 완전히 동일
- [x] 1-2 `frontend/src/api/apiClient.ts` — `VITE_USE_MOCK=true`면 mocks JSON, 아니면 `fetch` 실서버. TanStack Query 훅
- [x] 1-3 shadcn/ui + Tailwind로 페이지 구현 (Figma 확정 화면 기준, 1440×900 데스크톱 전용)
  - [x] `/login` 로그인
  - [x] `/` 홈 — 날짜 선택기, 요약 카드 4개, 필터 바(리포·사용자·타입), 사용자별 활동 타임라인, 미커밋 세션 행, "초안 생성/열기" 버튼
  - [x] `/drafts/:id` 초안 편집 — 좌 에디터(편집/미리보기 Tabs) + 우 근거 패널, DRAFT/CONFIRMED 상태 변형
  - [x] `/repos` 리포 관리 — 등록·삭제·동기화, 빈 상태
  - [x] `/settings` 설정 — API Key 목록·발급 Dialog, Mattermost, LLM 모델 라디오
  - [x] 공통 레이아웃 — 사이드바(홈/초안/리포/인원/설정) + 상단 바(날짜 선택기, 아바타)
- [x] 1-4 `POST /vscode/sessions` 수신 API (X-Api-Key, `(user, remoteUrl, branch, workDate)` UPSERT) + `GET /vscode/sessions?date&userId` → curl로 UPSERT 확인
- [x] 1-5 커밋, 태그 `day1-client`

## 2일차 — 백엔드 + API

- [x] 1-6 확장 `collector.ts` — `git status --porcelain` + `git diff`(스테이지 포함, 파일당 200줄), 브랜치·원격 URL, 변경 파일 내 `TODO:`/`FIXME:` 스캔, 명령 `WorkLog: 오늘 계획 기록`, 파일 저장 타임라인 → 콘솔 출력
- [x] 1-7 확장 `uploader.ts` — `settings.json`(`worklog.serverUrl / apiKey / intervalMinutes / collectDiff`), 30분 주기 + 종료 시 + 명령 `WorkLog: 지금 전송`, 상태바 "미커밋 N파일" / API Key 오류 표시(예외로 죽지 않음) → 확장 → 서버 → DB 반영
- [x] 1-8 초안 API — `GET /drafts?date&userId&status`, `GET /drafts/{id}`(sourceActivities·sourceSessions 포함), `PATCH /drafts/{id} {contentMd}`(본인만), `POST /drafts/{id}/confirm`
- [x] **동기화 포인트 ② (15:00)** 두 브랜치 `main` 머지. 담당자 2의 F5(인증)·F1·F3a가 붙어 있어야 함
- [x] 1-9 `VITE_USE_MOCK=false` 전환 — 어긋난 필드, JWT Bearer 헤더, `/auth/done?token=` 콜백 처리 → 실서버로 전 페이지 동작
- [x] 1-10 초안 편집 페이지에 **재생성**(`POST /drafts/generate`) · **Mattermost 전송**(`POST /drafts/{id}/notify`, 확정 후만 활성) 연결 → E2E 3·4·7 통과

## 3일차 — 리뷰 및 수정

- [ ] 1-11 E2E 시나리오 1·3·4·5·9 점검·수정 (아래 참고)
- [ ] 1-12 F11 — `vsce`로 `.vsix` 패키징, 프론트 빌드, 설치 README (Figma 링크 기재)
- [x] 1-13 (여유 시) F9 `/settings` LLM 옵션 UI, F10 `/people` 통계 페이지 + Recharts 막대 그래프
- [ ] 1-14 (P2까지 끝나면) X2 모바일 UI(열람·확정만) → X3 확장 사이드바 뷰
- [ ] **동기화 포인트 ③ (16:00)** 최종 머지, 전체 E2E 재실행, 태그 `v0.1.0`

## 내 담당 E2E 시나리오
- [x] 1. GitHub 로그인 → `/repos` 리포 등록 → 수동 동기화 → 홈에 오늘 커밋 표시
- [x] 3. 홈 "초안 생성" → 편집 페이지 → 수정·저장 → 확정 → CONFIRMED
- [x] 4. 확정 초안이 있는 날짜에 재생성 → version 2 DRAFT 생성, 확정본 유지
- [ ] 5. VS Code 파일 수정 + 계획 메모 + `지금 전송` → 홈 미커밋 세션 카드 증가, 재생성 시 "진행 중 / 미커밋"에 반영
- [x] 9. 잘못된 API Key로 확장 전송 → 401, 확장은 상태바 오류만 표시

## 지켜야 할 규칙
- 상대 트랙 파일(`auth/ github/ activity/ llm/ notify/ external/`, `DraftGenerator`)을 건드려야 하면 멈추고 알린다
- `Draft` 엔티티는 1일차 오전 공통 확정, 이후 변경은 합의 후에만
- API 계약(§7)·스키마(§6) 변경은 PRD 먼저 고치고 상대에게 알린 뒤 코드 수정
- 작업 단위마다 빌드·테스트 통과 상태로 커밋, Conventional Commits(`feat:` `fix:` `chore:`)
- 디자인 브리프 제약: 모바일·다크모드·검색창·알림 센터 없음, 예시 값 임의 변경 금지
- 프론트 검증 기준: 빌드 통과 + 목업 렌더 스모크 테스트
