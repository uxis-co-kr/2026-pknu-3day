-- 업무 일지에 종류를 둔다 — 하루치(DAILY) 말고 주간(WEEKLY)과 저장소별(REPO) 도 쓴다.
--
-- 셋은 쓰는 방식이 같다. AI 가 초안을 만들고, 사람이 고쳐 저장하고, Mattermost 로 보낸다.
-- 다른 것은 "무엇을 모아 쓰느냐" 뿐이라 표를 새로 만들지 않고 이 표에 종류를 더한다.
-- 조회·저장·전송 경로(GET /drafts, PATCH /drafts/{id}, POST /drafts/{id}/notify)를 그대로 쓴다.
ALTER TABLE drafts
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'DAILY';

-- 주간·저장소별은 하루가 아니라 기간이다. work_date 에는 기간 시작일을 넣어 기존 조회가 그대로 돌고,
-- 실제 기간은 이 두 칸으로 본다. DAILY 는 둘 다 NULL.
ALTER TABLE drafts
    ADD COLUMN IF NOT EXISTS period_start DATE;
ALTER TABLE drafts
    ADD COLUMN IF NOT EXISTS period_end DATE;

-- 저장소별 일지가 어느 저장소 것인지. 리포를 지워도 일지는 남긴다 — 쓴 글이 사라지면 안 된다.
ALTER TABLE drafts
    ADD COLUMN IF NOT EXISTS repo_id BIGINT REFERENCES repos(id) ON DELETE SET NULL;

ALTER TABLE drafts
    ADD CONSTRAINT ck_drafts_kind CHECK (kind IN ('DAILY', 'WEEKLY', 'REPO'));

-- 버전 유니크에 종류와 저장소를 더한다. 같은 날 시작하는 하루치와 주간이 부딪히면 안 된다.
--
-- UNIQUE 제약 대신 인덱스를 쓰는 이유: Postgres 는 NULL 을 서로 다른 값으로 보므로 repo_id 가
-- NULL 인 행끼리는 제약이 걸리지 않는다. coalesce 로 0 을 채워 DAILY·WEEKLY 도 막는다.
ALTER TABLE drafts DROP CONSTRAINT IF EXISTS uq_drafts_user_date_version;
CREATE UNIQUE INDEX IF NOT EXISTS uq_drafts_kind_date_version
    ON drafts (user_id, kind, work_date, coalesce(repo_id, 0), version);

COMMENT ON COLUMN drafts.kind IS 'DAILY 하루치 · WEEKLY 기간 · REPO 저장소별';
COMMENT ON COLUMN drafts.period_start IS 'WEEKLY·REPO 의 시작일. DAILY 는 NULL';
COMMENT ON COLUMN drafts.period_end IS 'WEEKLY·REPO 의 종료일. DAILY 는 NULL';
