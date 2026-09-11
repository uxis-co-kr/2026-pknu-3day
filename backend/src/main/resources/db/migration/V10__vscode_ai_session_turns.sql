-- AI 대화에 제목과 질의별 답변을 담는다 (BACKLOG2 §2-3).
--
-- V9 는 {id, firstAt, lastAt, promptCount, prompts[]} 였다. 화면에서 세션을 시각으로만
-- 구분해서(02:35–05:49) 무슨 대화였는지 알 수 없었고, 무엇을 시켰는지만 있고 그래서
-- 무엇을 했는지가 없었다.
--
-- 새 모양: {id, title, firstAt, lastAt, promptCount, turns[{at, prompt, answer}]}
--
-- 이미 쌓인 행의 답변은 되살릴 수 없다 — 원본 기록은 각자 PC 의 ~/.claude 에만 있다.
-- 질문만 turns 로 옮기고, 제목은 첫 질문에서 만든다. 답변은 다음 전송부터 채워진다.
UPDATE vscode_sessions
SET ai_sessions = (
    SELECT coalesce(
        jsonb_agg(
            jsonb_build_object(
                'id', s ->> 'id',
                'title', coalesce(
                    nullif(s ->> 'title', ''),
                    -- 첫 질문 40자. 확장·서버의 제목 규칙과 같게 잘린 표시를 붙인다.
                    (SELECT CASE
                        WHEN first IS NULL THEN '제목 없는 대화'
                        WHEN length(first) > 40 THEN left(first, 40) || '…'
                        ELSE first END
                     FROM (SELECT s -> 'prompts' ->> 0) AS f(first))),
                'firstAt', s ->> 'firstAt',
                'lastAt', s ->> 'lastAt',
                'promptCount', s -> 'promptCount',
                'turns', coalesce(
                    s -> 'turns',
                    (SELECT jsonb_agg(jsonb_build_object('at', s ->> 'firstAt', 'prompt', p))
                     FROM jsonb_array_elements_text(coalesce(s -> 'prompts', '[]'::jsonb)) AS p),
                    '[]'::jsonb))
            ORDER BY idx),
        '[]'::jsonb)
    FROM jsonb_array_elements(ai_sessions) WITH ORDINALITY AS t(s, idx))
WHERE jsonb_array_length(ai_sessions) > 0;

COMMENT ON COLUMN vscode_sessions.ai_sessions IS
    '[{id, title, firstAt, lastAt, promptCount, turns[{at, prompt, answer}]}] — V10';
