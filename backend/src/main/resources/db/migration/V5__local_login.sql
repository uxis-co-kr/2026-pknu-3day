-- 자체 로그인 (TODO_0910 §1-1, §1-3).
--
-- 지금은 GitHub OAuth 가 유일한 로그인 수단이라 users.github_id 가 NOT NULL 이다.
-- 그래서 GitHub 계정이 없는 관리자 계정을 만들 수 없다 — 개인 GitHub 계정에 관리자
-- 권한을 얹는 수밖에 없었고, 그건 계정 하나가 두 역할을 겸하는 상태다.
--
-- login_id + password_hash 를 더해 GitHub 없이도 계정이 설 수 있게 한다.
-- 회원 조회 API 가 붙으면 login_id 에 사원 번호가 들어간다 (§1-1).

ALTER TABLE users ALTER COLUMN github_id DROP NOT NULL;

-- GitHub 로그인 이름. 자체 계정은 GitHub 이 없으므로 비어 있을 수 있다.
ALTER TABLE users ALTER COLUMN login DROP NOT NULL;

-- 자체 로그인 아이디. 관리자는 'admin', 사원은 사원 번호가 들어간다.
ALTER TABLE users ADD COLUMN login_id VARCHAR(100);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(200);

-- 최초 비밀번호는 발급자가 알고 있으므로 비밀이 아니다. 처음 로그인하면 바꾸게 한다.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_users_login_id ON users (login_id) WHERE login_id IS NOT NULL;

-- 로그인 수단이 하나도 없는 행은 만들 수 없다.
ALTER TABLE users ADD CONSTRAINT ck_users_has_credential
    CHECK (github_id IS NOT NULL OR login_id IS NOT NULL);
