import { DraftStatusBadge } from '@/components/common/StatusBadge'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import type { DraftSummary, UserRef } from '@/types/api'

/**
 * 사용자별 카드의 머리 — 아바타·이름·요약 + 초안 버튼.
 *
 * <p>초안은 생성/재생성을 눌렀을 때만 만들어진다 (PRD F3). 이 버튼이 그 지점이다.
 */
export default function UserCardHeader({
  user, userId, summary, draft, busy, onOpenDraft, onGenerate,
}: {
  user: UserRef | undefined
  userId: number
  summary: string
  draft: DraftSummary | undefined
  busy?: boolean
  onOpenDraft: (id: number) => void
  onGenerate: (userId: number) => void
}) {
  return (
    <div className="flex h-[57px] items-center justify-between border-b px-4">
      <div className="flex min-w-0 items-center gap-2.5">
        <UserAvatar name={user?.name} login={user?.login} avatarUrl={user?.avatarUrl} />
        <span className="shrink-0 text-sm font-semibold">{user?.name ?? `#${userId}`}</span>
        <span className="shrink-0 text-[13px] text-muted-foreground">{user?.login}</span>
        <span className="ml-2.5 truncate text-[13px] text-muted-foreground">{summary}</span>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        {draft && <DraftStatusBadge status={draft.status} />}
        {draft ? (
          <Button variant="outline" size="sm" className="h-8" onClick={() => onOpenDraft(draft.id)}>
            초안 열기
          </Button>
        ) : (
          <Button size="sm" className="h-8" disabled={busy} onClick={() => onGenerate(userId)}>
            초안 생성
          </Button>
        )}
      </div>
    </div>
  )
}
