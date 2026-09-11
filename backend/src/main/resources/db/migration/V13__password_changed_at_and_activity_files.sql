-- 담당자 2 의 컬럼 둘 (9/11).
--
-- 원래 V11 에 미푸시 커밋과 함께 넣었는데, 담당자 1 도 미푸시 커밋을 V11 로 만들었다
-- (스탠드업을 못 해 번호도 기능도 겹쳤다). 미푸시 커밋은 그쪽 것을 쓰기로 하고 —
-- 업스트림이 없는 경우를 NULL 로 구분하는 편이 낫고, 확장이 이미 그 형식으로 보낸다 —
-- 남은 두 컬럼만 이리로 옮긴다.
--
-- 옮기기 전 번호로 이미 적용된 개발 DB 가 있어 IF NOT EXISTS 로 다시 돌아도 되게 한다.

-- (1) 비밀번호를 바꾼 시각. 이보다 먼저 발급된 JWT 는 거절한다 (BACKLOG2 §2-2 "JWT").
--     로그아웃이 클라이언트에서 버리는 것뿐이라, 유출된 토큰을 죽일 방법이 이것 하나다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN users.password_changed_at IS
    '비밀번호를 마지막으로 바꾼 시각. 이보다 앞서 발급된 토큰은 401';

-- (2) 커밋의 변경 파일 목록 (BACKLOG F-2). raw_diff 는 한 덩어리 텍스트라 화면이 파일 단위로
--     그리기 어렵다. 목록·통계는 여기, 본문은 raw_diff 그대로.
ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS files JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN activities.files IS
    '[{path, status, additions, deletions}] — GitHub 커밋 상세의 files[]';
