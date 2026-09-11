-- 고쳐 놓고 아직 저장하지 않은 파일.
--
-- git 에 잡히지 않는 유일한 구간이다 — 저장하지 않은 내용은 디스크에 없으니 diff 에도 없다.
--
-- 자리를 물려받은 edit_timeline(저장 이벤트)은 남겨 둔다. 지난 기록을 지우지 않기 위해서다.
-- 확장은 더 이상 보내지 않는다 — VS Code 의 저장 이벤트는 편집기에서 저장할 때만 와서,
-- 파일을 디스크에 곧바로 쓰는 AI 도구의 변경이 한 건도 남지 않았다.
ALTER TABLE vscode_sessions
    ADD COLUMN unsaved_files JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN vscode_sessions.unsaved_files IS
    '[{path, dirtySince}] — 편집기에 열려 있고 저장되지 않은 파일';

COMMENT ON COLUMN vscode_sessions.edit_timeline IS
    '[{path, firstSavedAt, lastSavedAt, saveCount}] — 2026-09-11 부터 수집하지 않는다 (unsaved_files 로 대체)';
