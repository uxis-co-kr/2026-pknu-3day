import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ActivityDetailRow from '@/components/activity/ActivityDetailRow'
import DayFilters from '@/components/day/DayFilters'
import SummaryCard from '@/components/common/SummaryCard'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useDailyStats, useDrafts, useGenerateDraft, useMe, useRepos } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { cn } from '@/lib/utils'
import type { ActivityType } from '@/types/api'

const TYPE_TABS: { key: ActivityType; label: string }[] = [
  { key: 'COMMIT', label: '커밋' },
  { key: 'PR_OPENED', label: 'PR' },
  { key: 'PR_MERGED', label: '머지' },
]

/**
 * 깃허브 내역 — **내** GitHub 활동을 날짜별로 본다.
 *
 * <p>팀원 전체 내역은 관리자 콘솔이 맡는다 (9/10 회의). 그래서 사용자 필터도, 사람별
 * 카드도 없다.
 */
export default function GithubPage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const { data: me } = useMe()
  const stats = useDailyStats(date)
  const activities = useActivities({ date, userId: me?.id })
  const drafts = useDrafts({ date, userId: me?.id })
  const repos = useRepos()
  const generate = useGenerateDraft()

  const [repoFilter, setRepoFilter] = useState('all')
  const [types, setTypes] = useState<ActivityType[]>(TYPE_TABS.map((t) => t.key))

  // 서버는 최신순으로 주는데 타임라인은 시간순이다 (아트보드 2: 10:12 → 16:30).
  const mine = [...(activities.data?.items ?? [])]
    .filter((a) => a.user?.id === me?.id)
    .sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))

  const shown = mine.filter((a) =>
    types.includes(a.type) && (repoFilter === 'all' || a.repo.id === Number(repoFilter)))

  /** 요약 카드는 팀 총계가 아니라 내 것이다. */
  const myStat = stats.data?.byUser.find((u) => u.userId === me?.id)
  const draft = drafts.data?.[0]

  async function onGenerate() {
    if (!me) return
    const created = await generate.mutateAsync({ date, userId: me.id })
    if (created && 'id' in created) navigate(`/drafts/${created.id}`)
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        {stats.isLoading ? (
          TYPE_TABS.map((t) => <Skeleton key={t.key} className="h-[101px]" />)
        ) : (
          <>
            <SummaryCard label="내 커밋" value={myStat?.commits ?? 0} hint={`팀 전체 ${stats.data?.commits ?? 0}`} />
            <SummaryCard label="내 PR" value={myStat?.prs ?? 0} hint={`팀 전체 ${stats.data?.prs ?? 0}`} />
            <SummaryCard label="내 머지" value={myStat?.merges ?? 0} hint={`팀 전체 ${stats.data?.merges ?? 0}`} />
          </>
        )}
      </div>

      <div className="flex items-center justify-between gap-3">
        <DayFilters repos={repos.data ?? []} repoFilter={repoFilter} onRepo={setRepoFilter}>
          <div className="flex h-[34px] overflow-hidden rounded-md border">
            {TYPE_TABS.map((t, i) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setTypes((prev) => prev.includes(t.key) ? prev.filter((x) => x !== t.key) : [...prev, t.key])}
                className={cn(
                  'px-3 text-[13px] transition-colors',
                  i > 0 && 'border-l',
                  types.includes(t.key) ? 'bg-primary/10 font-medium text-primary' : 'text-muted-foreground hover:bg-muted',
                )}
              >
                {t.label}
              </button>
            ))}
          </div>
        </DayFilters>

        <div className="flex items-center gap-3">
          {draft && <DraftStatusBadge status={draft.status} />}
          {draft ? (
            <Button variant="outline" size="sm" className="h-[34px]" onClick={() => navigate(`/drafts/${draft.id}`)}>
              초안 열기
            </Button>
          ) : (
            <Button size="sm" className="h-[34px]" disabled={generate.isPending} onClick={() => void onGenerate()}>
              초안 생성
            </Button>
          )}
        </div>
      </div>

      <Card className="overflow-hidden rounded-lg shadow-none">
        {activities.isLoading ? (
          <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div>
        ) : shown.length === 0 ? (
          <EmptyHint hasAny={mine.length > 0} />
        ) : (
          shown.map((a) => <ActivityDetailRow key={a.id} activity={a} />)
        )}
      </Card>
    </div>
  )
}

/**
 * 비어 있을 때의 안내.
 *
 * <p>수집기는 **기본 브랜치의 커밋만** 가져온다 (`GET /repos/{o}/{r}/commits` 에 sha 를
 * 주지 않는다). 작업 브랜치에만 있는 커밋은 머지되기 전까지 여기 뜨지 않는다.
 * 빈 화면을 보고 "수집이 고장났나" 로 오해하지 않도록 적어 둔다 (TODO_0910 §3-5).
 */
function EmptyHint({ hasAny }: { hasAny: boolean }) {
  return (
    <div className="px-6 py-12 text-center">
      <p className="text-[13px] text-muted-foreground">
        {hasAny ? '이 필터에 맞는 활동이 없습니다.' : '이 날짜에는 내 GitHub 활동이 없습니다.'}
      </p>
      {!hasAny && (
        <p className="mx-auto mt-2 max-w-[440px] text-[12px] leading-relaxed text-muted-foreground/70">
          커밋을 했는데도 비어 있다면, 아직 <strong>기본 브랜치에 머지되지 않았기</strong> 때문일 수 있습니다.
          수집기는 기본 브랜치의 커밋만 가져옵니다. 작업 중인 내용은 <strong>VS 내역</strong>에서 볼 수 있습니다.
        </p>
      )}
    </div>
  )
}
