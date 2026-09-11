-- 대표 채널 (9/11). 사원이 업무 일지를 저장하고 [Mattermost 전송] 을 누르면
-- "OOO의 오늘 업무일지가 요약되었습니다" 알림이 가는 채널 하나. 봇 계정이 그 채널에 쓴다.
-- 비어 있으면 전과 같이 전역 웹훅으로 간다.
--
-- 번호가 10.1 인 이유: V11 은 담당자 1 (AI 세션 형식 변경) 몫이다.
ALTER TABLE chat_bot_settings
    ADD COLUMN primary_channel_id VARCHAR(100);

COMMENT ON COLUMN chat_bot_settings.primary_channel_id IS
    '업무 일지 요약 알림을 받는 대표 채널. NULL 이면 전역 웹훅으로';
