-- 대표 채널 (9/11). 사원이 업무 일지를 저장하고 [Mattermost 전송] 을 누르면
-- "OOO의 오늘 업무일지가 요약되었습니다" 알림이 가는 채널 하나. 봇 계정이 그 채널에 쓴다.
-- 비어 있으면 전과 같이 전역 웹훅으로 간다.

ALTER TABLE chat_bot_settings
    ADD COLUMN IF NOT EXISTS primary_channel_id VARCHAR(100);

COMMENT ON COLUMN chat_bot_settings.primary_channel_id IS
    '업무 일지 요약 알림을 받는 대표 채널. NULL 이면 전역 웹훅으로';
