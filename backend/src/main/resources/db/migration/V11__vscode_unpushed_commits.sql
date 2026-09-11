-- 커밋했지만 아직 push 하지 않은 커밋.
--
-- GitHub 수집기가 보지 못하는 구간이다 — 원격에 없으니 API 로는 안 나온다. 그동안은
-- 확장 사이드바에서만 보였고 서버에는 자리가 없어, 대시보드에서 "커밋까지 해 둔 일" 이
-- 통째로 빠져 있었다.
--
-- NULL 을 허용한다. 빈 배열(= 미푸시 없음)과 "셀 수 없음" 은 다르다 — 한 번도 push 하지
-- 않은 브랜치는 비교할 업스트림이 없어 셀 수가 없다.
ALTER TABLE vscode_sessions
    ADD COLUMN unpushed_commits JSONB;

COMMENT ON COLUMN vscode_sessions.unpushed_commits IS
    '[{sha, subject, at}] — NULL 이면 업스트림이 없어 셀 수 없음, [] 면 미푸시 없음';
