-- 사용자가 한 번이라도 저장한 일지는 18:00 스케줄러가 덮지 않는다 (9/10 결정).
--
-- 지금까지는 CONFIRMED 만 건너뛰었다. 그래서 낮에 고쳐 저장해 두어도 완료로 표시하지
-- 않았으면 저녁에 자동 생성이 새 버전을 만들어, 사람이 쓴 내용이 뒤로 밀렸다.
ALTER TABLE drafts ADD COLUMN user_edited BOOLEAN NOT NULL DEFAULT FALSE;

-- 이미 완료한 일지는 사람이 손댄 것으로 본다.
UPDATE drafts SET user_edited = TRUE WHERE status = 'CONFIRMED';

COMMENT ON COLUMN drafts.user_edited IS '사용자가 저장한 적이 있으면 true. 스케줄러가 건너뛰는 기준';
