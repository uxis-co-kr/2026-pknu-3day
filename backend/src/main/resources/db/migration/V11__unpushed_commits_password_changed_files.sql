-- 4일차(9/11) 담당자 2 의 컬럼 셋. 마이그레이션 하나로 모은다.
--
-- 처음엔 V10 이었는데 담당자 1 도 같은 번호로 AI 대화 형식을 바꿨다 (스탠드업을 못 해서
-- 번호를 맞추지 못했다). 먼저 푸시된 쪽에 번호를 두고 이쪽을 V11 로 옮긴다.
-- 옮기기 전 V10 으로 이미 적용된 개발 DB 가 있어 IF NOT EXISTS 로 다시 돌아도 되게 한다.

-- (1) 커밋했지만 아직 푸시하지 않은 구간 (TODO_0910 §3-2, BACKLOG2 §4).
--     GitHub 수집기는 원격에 없는 커밋을 볼 수 없다. 확장이 git log upstream..HEAD 로 모아 보낸다.
ALTER TABLE vscode_sessions
    ADD COLUMN IF NOT EXISTS unpushed_commits JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN vscode_sessions.unpushed_commits IS
    '[{sha, subject, committedAt}] — 원격에 아직 없는 커밋. 초안의 "진행 중" 재료';

-- (2) 비밀번호를 바꾼 시각. 이보다 먼저 발급된 JWT 는 거절한다 (BACKLOG2 §2-2 "JWT").
--     로그아웃이 클라이언트에서 버리는 것뿐이라, 유출된 토큰을 죽일 방법이 이것 하나다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN users.password_changed_at IS
    '비밀번호를 마지막으로 바꾼 시각. iat 가 이보다 앞선 토큰은 401';

-- (3) 커밋의 변경 파일 목록 (BACKLOG F-2). raw_diff 는 한 덩어리 텍스트라 화면이 파일 단위로
--     그리기 어렵다. 목록·통계는 여기, 본문은 raw_diff 그대로.
ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS files JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN activities.files IS
    '[{path, status, additions, deletions}] — GitHub 커밋 상세의 files[]';
