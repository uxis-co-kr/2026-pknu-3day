-- 관리자 콘솔 접근 제어와 사내 회원(와플) 연결 (TODO_0910 §1-1, §1-3).
--
-- 회의에서 자체 회원가입 대신 "이미 있는 회원 조회 API 를 쓴다"로 정해졌으므로
-- email·password_hash 는 넣지 않는다. 필요한 것은 둘이다.
--   1) role      — 관리자 콘솔에 들어갈 수 있는 사람을 가린다
--   2) emp_seq   — 와플 사원과 우리 users 를 잇는 열쇠. co_seq 와 함께 있어야 사원을 특정한다
--                  (같은 이름의 사원이 실제로 있고, 회사가 둘인데 이름이 같다)

ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('MEMBER', 'ADMIN'));

ALTER TABLE users ADD COLUMN co_seq  BIGINT;
ALTER TABLE users ADD COLUMN emp_seq BIGINT;

-- 한 사원이 두 계정에 연결되면 활동이 갈린다. 회사 안에서 사원은 유일해야 한다.
CREATE UNIQUE INDEX uq_users_employee ON users (co_seq, emp_seq)
    WHERE co_seq IS NOT NULL AND emp_seq IS NOT NULL;

CREATE INDEX idx_users_role ON users (role);
