import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ActivityDetailRow from '@/components/activity/ActivityDetailRow'
import DayFilters from '@/components/day/DayFilters'
import UserCardHeader from '@/components/day/UserCardHeader'
import SummaryCard from '@/components/common/SummaryCard'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useDailyStats, useDrafts, useGenerateDraft, useRepos } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { cn } from '@/lib/utils'
import type { ActivityType, UserRef } from '@/types/api'

const TYPE_TABS: { key: ActivityType; label: string }[] = [
  { key: 'COMMIT', label: '커밋' },
  { key: 'PR_OPENED', label: 'PR' },
  { key: 'PR_MERGED', label: '머지' },
]

/**
 * 깃허브 내역 — 그날의 GitHub 활동을 사용자별로 본다.
 *
 * <p>VS 활동은 별도 메뉴로 나갔다. 초안은 여기서 "생성" 을 눌렀을 때 서버가 GitHub 활동과
 * VS 세션을 **함께** 모아 만든다 (PRD F3).
 */
export default function GithubPage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const stats = useDailyStats(date)
  const activities = useActivities({ date })
  const drafts = useDrafts({ date })
  const repos = useRepos()
  const generate = useGenerateDraft()

  const [repoFilter, setRepoFilter] = useState('all')
  const [userFilter, setUserFilter] = useState('all')
  const [types, setTypes] = useState<ActivityType[]>(TYPE_TABS.map((t) => t.key))

  // 서버는 최신순으로 주는데 타임라인은 시간순이다 (아트보드 2: 10:12 → 16:30).
  const all = [...(activities.data?.items ?? [])].sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))

  /** 사용자 이름은 활동에만 실려 온다. 가입하지 않은 계정의 활동은 user 가 null 이다 (PRD F1-5). */
  const users = useMemo(() => {
    const map = new Map<number, UserRef>()
    for (const a of all) if (a.user && !map.has(a.user.id)) map.set(a.user.id, a.user)
    return map
  }, [all])

  const shown = all.filter((a) =>
    a.user !== null &&
    types.includes(a.type) &&
    (repoFilter === 'all' || a.repo.id === Number(repoFilter)) &&
    (userFilter === 'all' || a.user.id === Number(userFilter)))

  const rows = (stats.data?.byUser ?? [])
    .map((u) => ({
      stat: u,
      user: users.get(u.userId),
      activities: shown.filter((a) => a.user?.id === u.userId),
      draft: drafts.data?.find((d) => d.userId === u.userId),
    }))
    .filter((r) => userFilter === 'all' || r.stat.userId === Number(userFilter))

  async function onGenerate(userId: number) {
    const draft = await generate.mutateAsync({ date, userId })
    if (draft && 'id' in draft) navigate(`/drafts/${draft.id}`)
  }

  const s = stats.data

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        {stats.isLoading || !s ? (
          TYPE_TABS.map((t) => <Skeleton key={t.key} className="h-[101px]" />)
        ) : (
          <>
            <SummaryCard label="커밋" value={s.commits}
              hint={s.commitsDelta === 0 ? '어제와 같음' : `어제 대비 ${s.commitsDelta > 0 ? '+' : ''}${s.commitsDelta}`} />
            <SummaryCard label="PR" value={s.prs} hint={`열림 ${s.prs} · 머지 ${s.merges}`} />
            <SummaryCard label="머지" value={s.merges} hint="—" />
          </>
        )}
      </div>

      <DayFilters
        repos={repos.data ?? []}
        users={[...users.values()]}
        repoFilter={repoFilter}
        userFilter={userFilter}
        onRepo={setRepoFilter}
        onUser={setUserFilter}
      >
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

      <div className="space-y-3">
        {rows.map(({ stat, user, activities: acts, draft }) => (
          <Card key={stat.userId} className="overflow-hidden rounded-lg shadow-none">
            <UserCardHeader
              user={user}
              userId={stat.userId}
              summary={[`커밋 ${stat.commits}`, stat.prs > 0 && `PR ${stat.prs}`, stat.merges > 0 && `머지 ${stat.merges}`]
                .filter(Boolean).join(' · ')}
              draft={draft}
              busy={generate.isPending}
              onOpenDraft={(id) => navigate(`/drafts/${id}`)}
              onGenerate={(id) => void onGenerate(id)}
            />
            {acts.length === 0 ? (
              <p className="px-4 py-6 text-center text-[13px] text-muted-foreground">표시할 활동이 없습니다.</p>
            ) : (
              acts.map((a) => <ActivityDetailRow key={a.id} activity={a} />)
            )}
          </Card>
        ))}

        {/*
          * 총계 = byUser 합계 + unmapped 다. 가입하지 않은 외부 기여자의 활동은 어느 사용자
          * 카드에도 붙지 않아 요약 카드 숫자와 타임라인이 어긋나 보인다. 있을 때만 설명한다.
          */}
        {(s?.unmapped?.commits ?? 0) > 0 && (
          <p className="px-1 text-[12px] text-muted-foreground">
            사용자에 연결되지 않은 활동 {s!.unmapped.commits}건은 타임라인에 표시되지 않습니다.
          </p>
        )}

        {!stats.isLoading && rows.length === 0 && (
          <Card className="rounded-lg p-10 text-center text-[13px] text-muted-foreground shadow-none">
            이 날짜에는 기록된 GitHub 활동이 없습니다.
          </Card>
        )}
      </div>
    </div>
  )
}
