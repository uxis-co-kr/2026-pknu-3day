-- 18:00 스케줄러가 만든 초안과 사람이 버튼을 눌러 만든 초안을 구분한다.
-- 화면이 "자동 생성됨" 을 표시해, 내가 만들지 않은 초안이 왜 있는지 알 수 있게 한다.
ALTER TABLE drafts ADD COLUMN auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN drafts.auto_generated IS '스케줄러가 만들었으면 true, 사용자가 생성/재생성했으면 false';
