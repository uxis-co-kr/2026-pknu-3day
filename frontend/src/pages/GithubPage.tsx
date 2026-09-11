import { useMemo, useState } from 'react'
import ActivityDetailRow from '@/components/activity/ActivityDetailRow'
import DateSidebar from '@/components/day/DateSidebar'
import Pagination from '@/components/common/Pagination'
import DayFilters from '@/components/day/DayFilters'
import SummaryCard from '@/components/common/SummaryCard'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useMe, useRepos } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { cn } from '@/lib/utils'
import type { ActivityType } from '@/types/api'

/** 한 페이지에 보여 줄 활동 수. */
const PER_PAGE = 10

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

  const { data: me } = useMe()
  const activities = useActivities({ date, userId: me?.id })
  const repos = useRepos()

  const [repoFilter, setRepoFilter] = useState('all')
  const [types, setTypes] = useState<ActivityType[]>(TYPE_TABS.map((t) => t.key))
  const [page, setPage] = useState(0)

  // 최신이 위로 온다 (9/10 결정). 방금 한 일을 찾으려고 아래로 스크롤하지 않게.
  const mine = [...(activities.data?.items ?? [])]
    .filter((a) => a.user?.id === me?.id)
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))

  const shown = useMemo(
    () => mine.filter((a) =>
      types.includes(a.type) && (repoFilter === 'all' || a.repo.id === Number(repoFilter))),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [activities.data, types, repoFilter],
  )

  // 필터를 바꾸면 있던 페이지가 사라질 수 있다. 범위를 벗어나면 마지막 페이지로 당긴다.
  const pageCount = Math.max(1, Math.ceil(shown.length / PER_PAGE))
  const current = Math.min(page, pageCount - 1)
  const rows = shown.slice(current * PER_PAGE, (current + 1) * PER_PAGE)

  /**
   * 요약은 내 활동에서 직접 센다.
   *
   * <p>전에는 /stats/daily 의 byUser 에서 내 몫을 골라 썼는데, 그 응답에는 팀 전원의 숫자가
   * 함께 실려 온다. 화면에 안 그려도 브라우저까지는 오는 것이라 아예 부르지 않는다
   * (9/10 결정 — 일반 로그인은 내 것만 본다).
   */
  const myStat = {
    // 저장소를 맨 앞에 둔다 — 하루에 여러 저장소를 오간 날에 그 사실이 먼저 보여야 한다.
    repos: new Set(mine.map((a) => a.repo.id)).size,
    commits: mine.filter((a) => a.type === 'COMMIT').length,
    prs: mine.filter((a) => a.type === 'PR_OPENED').length,
    merges: mine.filter((a) => a.type === 'PR_MERGED').length,
  }


  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <DayFilters
          repos={repos.data ?? []}
          repoFilter={repoFilter}
          onRepo={(v) => { setRepoFilter(v); setPage(0) }}
        />
      </div>

      {/* 달력은 요약 박스와 같은 줄에서 시작한다. 필터 줄은 위에 통째로 둔다. */}
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1 space-y-4">
          <div className="grid grid-cols-4 gap-3">
            {activities.isLoading ? (
              [0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-[101px]" />)
            ) : (
              <>
                <SummaryCard label="저장소" value={myStat.repos} />
                <SummaryCard label="커밋" value={myStat.commits} />
                <SummaryCard label="열린 PR" value={myStat.prs} />
                <SummaryCard label="머지된 PR" value={myStat.merges} />
              </>
            )}
          </div>

          <div className="flex h-[34px] w-fit overflow-hidden rounded-md border">
            {TYPE_TABS.map((t, i) => (
              <button
                key={t.key}
                type="button"
                onClick={() => {
                  setTypes((prev) => prev.includes(t.key) ? prev.filter((x) => x !== t.key) : [...prev, t.key])
                  setPage(0)
                }}
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

          <Card className="overflow-hidden rounded-lg shadow-none">
            {activities.isLoading ? (
              <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div>
            ) : shown.length === 0 ? (
              <EmptyHint hasAny={mine.length > 0} />
            ) : (
              <>
                {rows.map((a) => <ActivityDetailRow key={a.id} activity={a} />)}
                <Pagination
                  page={current}
                  pageCount={pageCount}
                  total={shown.length}
                  onChange={setPage}
                />
              </>
            )}
          </Card>
        </div>

        <DateSidebar />
      </div>
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
          수집기는 기본 브랜치의 커밋만 가져옵니다. 작업 중인 내용은 <strong>VSCode 내역</strong>에서 볼 수 있습니다.
        </p>
      )}
    </div>
  )
}
