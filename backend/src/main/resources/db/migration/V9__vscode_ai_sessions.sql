-- 그 폴더에서 오간 AI 대화 (Claude Code).
--
-- 커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
-- 업무 일지가 "그날 한 일" 을 모으는 것이라면 이것도 재료다.
ALTER TABLE vscode_sessions
    ADD COLUMN ai_sessions JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN vscode_sessions.ai_sessions IS
    '[{id, firstAt, lastAt, promptCount, prompts[]}] — 사용자가 친 말만 담는다';
