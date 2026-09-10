import { useNavigate } from 'react-router-dom'
import UserCardHeader from '@/components/day/UserCardHeader'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useDailyStats, useDrafts, useGenerateDraft, useMe, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatTime } from '@/lib/date'

/**
 * 초안 작성 — **내** 초안을 만들고 연다.
 *
 * <p>초안은 여기서 버튼을 눌렀을 때만 만들어진다. 서버가 그날의 GitHub 활동과 VS 세션을
 * 함께 모아 쓴다 (PRD F3). 이미 있는 초안을 다시 만들면 확정본은 남고 새 버전이 생긴다.
 * <p>팀원 전체의 초안은 관리자 콘솔이 맡는다 (9/10 회의).
 */
export default function DraftsPage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const { data: me } = useMe()
  const stats = useDailyStats(date)
  const drafts = useDrafts({ date, userId: me?.id })
  const sessions = useSessions({ date, userId: me?.id })
  const generate = useGenerateDraft()

  const myStat = stats.data?.byUser.find((u) => u.userId === me?.id)
  const mySessions = (sessions.data ?? []).filter((s) => s.userId === me?.id)
  const draft = drafts.data?.[0]

  const summary = [
    `커밋 ${myStat?.commits ?? 0}`,
    (myStat?.prs ?? 0) > 0 && `PR ${myStat!.prs}`,
    mySessions.length > 0 && `VS 세션 ${mySessions.length}`,
  ].filter(Boolean).join(' · ')

  const nothing = (myStat?.commits ?? 0) === 0 && mySessions.length === 0

  async function onGenerate() {
    if (!me) return
    const created = await generate.mutateAsync({ date, userId: me.id })
    if (created && 'id' in created) navigate(`/drafts/${created.id}`)
  }

  if (stats.isLoading || !me) return <Skeleton className="h-[57px]" />

  return (
    <div className="space-y-3">
      <p className="text-[13px] text-muted-foreground">
        초안은 <strong className="font-medium text-foreground">생성</strong>을 눌렀을 때 만들어집니다.
        그날의 깃허브 내역과 VS 내역을 함께 모아 씁니다.
      </p>

      <Card className="overflow-hidden rounded-lg shadow-none">
        <UserCardHeader
          user={me}
          userId={me.id}
          summary={summary}
          draft={draft}
          busy={generate.isPending}
          onOpenDraft={(id) => navigate(`/drafts/${id}`)}
          onGenerate={() => void onGenerate()}
        />
        {draft ? (
          <div className="flex items-center gap-3 px-4 py-2.5 text-[12px] text-muted-foreground">
            <span>버전 {draft.version}</span>
            <span>수정 {formatTime(draft.updatedAt)}</span>
            {draft.confirmedAt && <span>확정 {formatTime(draft.confirmedAt)}</span>}
          </div>
        ) : (
          <p className="px-4 py-6 text-center text-[13px] text-muted-foreground">
            {nothing
              ? '이 날짜에는 초안을 만들 활동이 없습니다.'
              : '아직 초안이 없습니다. 위 버튼으로 만드세요.'}
          </p>
        )}
      </Card>
    </div>
  )
}
