-- WorkLog Drafter — 초기 스키마 (PRD 6. 데이터 모델)
--
-- ENUM 표기는 PostgreSQL 네이티브 enum 타입 대신 VARCHAR + CHECK 제약으로 구현한다.
-- (JPA @Enumerated(STRING) 과 그대로 맞물리고, 값 추가 시 마이그레이션이 단순하다.)

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id               BIGSERIAL PRIMARY KEY,
    github_id        BIGINT       NOT NULL UNIQUE,
    login            VARCHAR(100) NOT NULL UNIQUE,
    name             VARCHAR(200),
    avatar_url       TEXT,
    github_token_enc TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------- api_keys
CREATE TABLE api_keys (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    key_hash     VARCHAR(128) NOT NULL UNIQUE,
    label        VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ
);

CREATE INDEX idx_api_keys_user ON api_keys (user_id);

-- ---------------------------------------------------------------- repos
CREATE TABLE repos (
    id             BIGSERIAL PRIMARY KEY,
    owner          VARCHAR(120) NOT NULL,
    name           VARCHAR(200) NOT NULL,
    full_name      VARCHAR(320) NOT NULL UNIQUE,
    default_branch VARCHAR(200),
    registered_by  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    last_synced_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------- activities
CREATE TABLE activities (
    id              BIGSERIAL PRIMARY KEY,
    repo_id         BIGINT       NOT NULL REFERENCES repos (id) ON DELETE CASCADE,
    user_id         BIGINT REFERENCES users (id) ON DELETE SET NULL,
    external_login  VARCHAR(100),
    type            VARCHAR(20)  NOT NULL,
    external_id     VARCHAR(200) NOT NULL,
    sha             VARCHAR(64),
    title           TEXT,
    message         TEXT,
    url             TEXT,
    branch          VARCHAR(200),
    files_changed   INTEGER      NOT NULL DEFAULT 0,
    additions       INTEGER      NOT NULL DEFAULT 0,
    deletions       INTEGER      NOT NULL DEFAULT 0,
    raw_diff        TEXT,
    summary         TEXT,
    summary_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    summary_retries INTEGER      NOT NULL DEFAULT 0,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_activities_type CHECK (type IN ('COMMIT', 'PR_OPENED', 'PR_MERGED')),
    CONSTRAINT ck_activities_summary_status CHECK (summary_status IN ('PENDING', 'DONE', 'FAILED')),
    CONSTRAINT uq_activities_repo_type_external UNIQUE (repo_id, type, external_id)
);

CREATE INDEX idx_activities_occurred_at ON activities (occurred_at DESC);
CREATE INDEX idx_activities_user_occurred ON activities (user_id, occurred_at DESC);
CREATE INDEX idx_activities_repo_occurred ON activities (repo_id, occurred_at DESC);
-- 요약 파이프라인(2-7)이 훑을 대상만 좁게 인덱싱
CREATE INDEX idx_activities_summary_pending ON activities (summary_status)
    WHERE summary_status <> 'DONE';

-- ------------------------------------------------------ vscode_sessions
CREATE TABLE vscode_sessions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    repo_id           BIGINT REFERENCES repos (id) ON DELETE SET NULL,
    remote_url        TEXT         NOT NULL,
    branch            VARCHAR(200) NOT NULL,
    work_date         DATE         NOT NULL,
    uncommitted_files JSONB        NOT NULL DEFAULT '[]'::jsonb, -- [{path, additions, deletions, diff}]
    todos             JSONB        NOT NULL DEFAULT '[]'::jsonb, -- [{path, line, text}]
    plan_note         TEXT,
    edit_timeline     JSONB        NOT NULL DEFAULT '[]'::jsonb, -- [{path, firstSavedAt, lastSavedAt, saveCount}]
    summary           TEXT,
    last_commit_at    TIMESTAMPTZ,
    reported_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_vscode_sessions UNIQUE (user_id, remote_url, branch, work_date)
);

CREATE INDEX idx_vscode_sessions_work_date ON vscode_sessions (work_date DESC);
CREATE INDEX idx_vscode_sessions_user_date ON vscode_sessions (user_id, work_date DESC);

-- --------------------------------------------------------------- drafts
CREATE TABLE drafts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    work_date           DATE        NOT NULL,
    version             INTEGER     NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content_md          TEXT        NOT NULL,
    source_activity_ids BIGINT[]    NOT NULL DEFAULT '{}',
    source_session_ids  BIGINT[]    NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at        TIMESTAMPTZ,

    CONSTRAINT ck_drafts_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
    CONSTRAINT uq_drafts_user_date_version UNIQUE (user_id, work_date, version)
);

CREATE INDEX idx_drafts_user_date ON drafts (user_id, work_date DESC);
CREATE INDEX idx_drafts_work_date ON drafts (work_date DESC);

-- ------------------------------------------------------- notify_settings
-- user_id IS NULL 인 행은 전역 기본값 1건만 존재한다.
CREATE TABLE notify_settings (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    mattermost_webhook_url TEXT,
    remind_uncommitted     BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uq_notify_settings_global ON notify_settings ((user_id IS NULL))
    WHERE user_id IS NULL;

-- ----------------------------------------------------- user_llm_settings (P2)
CREATE TABLE user_llm_settings (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(50) NOT NULL,
    model       VARCHAR(200),
    endpoint    TEXT,
    api_key_enc TEXT
);
