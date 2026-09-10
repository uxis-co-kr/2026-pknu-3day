#!/usr/bin/env bash
# VS Code 확장이 보낸 세션을 DB 에서 확인한다 (연동 테스트용).
#   scripts/check-session.sh            오늘 세션 요약
#   scripts/check-session.sh --files    미커밋 파일·TODO·저장 이벤트까지
set -euo pipefail
cd "$(dirname "$0")/.."

q() { docker compose exec -T postgres psql -U worklog -d worklog "$@"; }

echo "=== API Key ==="
q -c "select id, user_id, label, to_char(last_used_at,'MM-DD HH24:MI:SS') as 마지막사용 from api_keys order by id;"

echo "=== 오늘 세션 ==="
q -c "select s.id, r.full_name as repo, s.branch,
  jsonb_array_length(s.uncommitted_files) as 미커밋,
  jsonb_array_length(s.todos) as todo,
  jsonb_array_length(s.edit_timeline) as 저장이벤트,
  coalesce(s.plan_note,'—') as 계획,
  to_char(s.reported_at,'HH24:MI:SS') as 보고시각
  from vscode_sessions s left join repos r on r.id = s.repo_id
  where s.work_date = (now() at time zone 'Asia/Seoul')::date
  order by s.id desc;"

if [[ "${1:-}" == "--files" ]]; then
  echo "=== 미커밋 파일 ==="
  q -c "select s.id, f->>'path' as 파일, f->>'additions' as 추가, f->>'deletions' as 삭제,
    case when f->>'diff' is null then '없음'
         else (length(f->>'diff') - length(replace(f->>'diff', chr(10), '')) + 1)::text || '줄' end as diff
    from vscode_sessions s, jsonb_array_elements(s.uncommitted_files) f
    where s.work_date = (now() at time zone 'Asia/Seoul')::date order by s.id desc, 2;"

  echo "=== TODO ==="
  q -c "select s.id, t->>'path' as 파일, t->>'line' as 줄, t->>'text' as 내용
    from vscode_sessions s, jsonb_array_elements(s.todos) t
    where s.work_date = (now() at time zone 'Asia/Seoul')::date order by s.id desc;"

  echo "=== 저장 이벤트 (VS Code 안에서만 쌓인다) ==="
  q -c "select s.id, e->>'path' as 파일, e->>'saveCount' as 저장횟수,
    substring(e->>'firstSavedAt' from 12 for 8) as 처음,
    substring(e->>'lastSavedAt' from 12 for 8) as 마지막
    from vscode_sessions s, jsonb_array_elements(s.edit_timeline) e
    where s.work_date = (now() at time zone 'Asia/Seoul')::date order by s.id desc;"
fi
