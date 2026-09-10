-- 채널에서 물어보면 답하는 봇의 연결 설정 (TODO_0910 — Mattermost 질의 응답).
--
-- 처음엔 .env 로만 정했다. 그런데 연결은 관리자가 콘솔에서 하고 싶은 일이다 — 계정을 넣고,
-- 붙었는지 보고, 어느 채널을 읽는지 고르는 것까지. 그래서 DB 에 1건 둔다. 행이 없으면
-- .env 값을 그대로 쓰므로 기존 설정은 깨지지 않는다.
CREATE TABLE chat_bot_settings (
    id                  BIGSERIAL PRIMARY KEY,
    base_url            TEXT NOT NULL,
    login_id            VARCHAR(200) NOT NULL,
    -- GitHub 토큰과 같은 키(ENCRYPTION_KEY)로 AES-GCM 암호화한다.
    password_enc        TEXT NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    -- 읽을 채널 id 를 쉼표로 이어 둔다. NULL 이면 봇이 들어가 있는 채널 전부.
    watched_channel_ids TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
