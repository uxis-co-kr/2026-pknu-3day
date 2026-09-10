import { useNavigate } from 'react-router-dom'
import UserCardHeader from '@/components/day/UserCardHeader'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useDailyStats, useDrafts, useGenerateDraft, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatTime } from '@/lib/date'
import type { UserRef } from '@/types/api'

/**
 * 초안 작성 — 그날 초안을 사람별로 만들고 연다.
 *
 * <p>초안은 여기서 버튼을 눌렀을 때만 만들어진다. 서버가 GitHub 활동과 VS 세션을 함께
 * 모아 쓴다 (PRD F3). 이미 있는 초안을 다시 만들면 확정본은 남고 새 버전이 생긴다.
 */
export default function DraftsPage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const stats = useDailyStats(date)
  const drafts = useDrafts({ date })
  const sessions = useSessions({ date })
  const activities = useActivities({ date })
  const generate = useGenerateDraft()

  const users = new Map<number, UserRef>()
  for (const a of activities.data?.items ?? []) if (a.user) users.set(a.user.id, a.user)

  /** 그날 활동이 있는 사람 + 세션만 있는 사람 모두가 초안 대상이다. */
  const userIds = new Set<number>([
    ...(stats.data?.byUser ?? []).map((u) => u.userId),
    ...(sessions.data ?? []).map((s) => s.userId),
  ])

  async function onGenerate(userId: number) {
    const draft = await generate.mutateAsync({ date, userId })
    if (draft && 'id' in draft) navigate(`/drafts/${draft.id}`)
  }

  return (
    <div className="space-y-3">
      <p className="text-[13px] text-muted-foreground">
        초안은 <strong className="font-medium text-foreground">생성</strong>을 눌렀을 때 만들어집니다.
        그날의 GitHub 활동과 VS 활동을 함께 모아 씁니다.
      </p>

      {stats.isLoading && <Skeleton className="h-[57px]" />}

      {[...userIds].map((userId) => {
        const stat = stats.data?.byUser.find((u) => u.userId === userId)
        const session = (sessions.data ?? []).filter((s) => s.userId === userId)
        const draft = drafts.data?.find((d) => d.userId === userId)
        const summary = [
          stat && `커밋 ${stat.commits}`,
          stat && stat.prs > 0 && `PR ${stat.prs}`,
          session.length > 0 && `VS 세션 ${session.length}`,
        ].filter(Boolean).join(' · ') || '활동 없음'

        return (
          <Card key={userId} className="overflow-hidden rounded-lg shadow-none">
            <UserCardHeader
              user={users.get(userId)}
              userId={userId}
              summary={summary}
              draft={draft}
              busy={generate.isPending}
              onOpenDraft={(id) => navigate(`/drafts/${id}`)}
              onGenerate={(id) => void onGenerate(id)}
            />
            {draft && (
              <div className="flex items-center gap-3 px-4 py-2.5 text-[12px] text-muted-foreground">
                <span>버전 {draft.version}</span>
                <span>수정 {formatTime(draft.updatedAt)}</span>
                {draft.confirmedAt && <span>확정 {formatTime(draft.confirmedAt)}</span>}
              </div>
            )}
          </Card>
        )
      })}

      {!stats.isLoading && userIds.size === 0 && (
        <Card className="rounded-lg p-10 text-center text-[13px] text-muted-foreground shadow-none">
          이 날짜에는 초안을 만들 활동이 없습니다.
        </Card>
      )}
    </div>
  )
}
