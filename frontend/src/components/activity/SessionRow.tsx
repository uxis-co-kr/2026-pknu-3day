import { TriangleAlert } from 'lucide-react'
import { formatRelative } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { VscodeSession } from '@/types/api'

/** 미커밋 세션 행은 배경을 연한 주황으로 구분한다 (디자인 브리프 3.2 C). */
export default function SessionRow({
  session, dense = false, className,
}: {
  session: VscodeSession
  dense?: boolean
  className?: string
}) {
  const files = session.uncommittedFiles.length
  return (
    <div className={cn('flex gap-3 border-b bg-status-uncommitted/[0.07] px-4 py-2.5 last:border-b-0',
      dense && 'gap-2 rounded-md border px-3', className)}>
      <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-status-uncommitted" />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex items-center gap-4">
          <span className="rounded bg-status-uncommitted/15 px-[7px] py-px text-table text-status-uncommitted">
            {session.branch}
          </span>
          <span className="text-[12px] text-muted-foreground">
            {dense ? `${files}파일` : `미커밋 ${files}파일 · 마지막 커밋 ${formatRelative(session.lastCommitAt)}`}
          </span>
        </div>
        {session.todos.length > 0 && (dense ? (
          <ul className="space-y-0.5">
            {session.todos.map((t) => (
              <li key={`${t.path}:${t.line}`} className="text-[12px] text-foreground/80">
                TODO · {t.text}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-[12px] text-foreground/80">
            TODO {session.todos.length}건 — {session.todos[0].text}
          </p>
        ))}
        {!dense && session.planNote && (
          <p className="border-l-2 border-status-uncommitted/40 pl-2 text-[12px] italic text-muted-foreground">
            &quot;{session.planNote}&quot;
          </p>
        )}
      </div>
    </div>
  )
}
