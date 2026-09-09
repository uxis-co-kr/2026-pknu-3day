import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ActivityRow from '@/components/activity/ActivityRow'
import SessionRow from '@/components/activity/SessionRow'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useDailyStats, useDrafts, useGenerateDraft, useRepos, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { cn } from '@/lib/utils'
import type { Activity, UserRef, VscodeSession } from '@/types/api'

type TypeKey = 'COMMIT' | 'PR_OPENED' | 'PR_MERGED' | 'SESSION'
const TYPE_TABS: { key: TypeKey; label: string }[] = [
  { key: 'COMMIT', label: '커밋' },
  { key: 'PR_OPENED', label: 'PR' },
  { key: 'PR_MERGED', label: '머지' },
  { key: 'SESSION', label: '미커밋' },
]

function SummaryCard({ label, value, hint, warn }: { label: string; value: number | string; hint: string; warn?: boolean }) {
  return (
    <Card className="flex h-[101px] flex-col rounded-lg px-[19px] py-[17px] shadow-none">
      <p className="text-[13px] leading-4 text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-[26px] font-semibold leading-[33px] tabular-nums">{value}</p>
      <p className={cn('mt-0.5 text-[12px] leading-[14px]', warn ? 'text-status-uncommitted' : 'text-muted-foreground')}>
        {hint}
      </p>
    </Card>
  )
}

export default function HomePage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const stats = useDailyStats(date)
  const activities = useActivities({ date })
  const sessions = useSessions({ date })
  const drafts = useDrafts({ date })
  const repos = useRepos()
  const generate = useGenerateDraft()

  const [repoFilter, setRepoFilter] = useState('all')
  const [userFilter, setUserFilter] = useState('all')
  const [types, setTypes] = useState<TypeKey[]>(TYPE_TABS.map((t) => t.key))

  const allActivities = activities.data?.items ?? []
  const allSessions = sessions.data ?? []

  /** 사용자 이름은 활동에만 실려 오므로 여기서 모아 둔다. */
  const users = useMemo(() => {
    const map = new Map<number, UserRef>()
    for (const a of allActivities) if (!map.has(a.user.id)) map.set(a.user.id, a.user)
    return map
  }, [allActivities])

  const shownActivities = allActivities.filter((a) =>
    types.includes(a.type) &&
    (repoFilter === 'all' || a.repo.id === Number(repoFilter)) &&
    (userFilter === 'all' || a.user.id === Number(userFilter)))

  const shownSessions = allSessions.filter((s) =>
    types.includes('SESSION') &&
    (repoFilter === 'all' || s.repo?.id === Number(repoFilter)) &&
    (userFilter === 'all' || s.userId === Number(userFilter)))

  /** 카드 순서는 일별 통계의 사용자 순서를 따른다. */
  const rows = (stats.data?.byUser ?? []).map((u) => ({
    stat: u,
    user: users.get(u.userId),
    activities: shownActivities.filter((a) => a.user.id === u.userId),
    sessions: shownSessions.filter((s) => s.userId === u.userId),
    draft: drafts.data?.find((d) => d.userId === u.userId),
  })).filter((r) => (userFilter === 'all' || r.stat.userId === Number(userFilter)))

  const toggleType = (key: TypeKey) =>
    setTypes((prev) => (prev.includes(key) ? prev.filter((t) => t !== key) : [...prev, key]))

  async function onGenerate(userId: number) {
    const draft = await generate.mutateAsync({ date, userId })
    if (draft && 'id' in draft) navigate(`/drafts/${draft.id}`)
  }

  const s = stats.data

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-4 gap-3">
        {stats.isLoading || !s ? (
          TYPE_TABS.map((t) => <Skeleton key={t.key} className="h-[101px]" />)
        ) : (
          <>
            <SummaryCard label="커밋" value={s.commits}
              hint={s.commitsDelta === 0 ? '어제와 같음' : `어제 대비 ${s.commitsDelta > 0 ? '+' : ''}${s.commitsDelta}`} />
            <SummaryCard label="PR" value={s.prs} hint={`열림 ${s.prs} · 머지 ${s.merges}`} />
            <SummaryCard label="머지" value={s.merges} hint="—" />
            <SummaryCard label="미커밋 세션" value={s.sessions}
              hint={s.staleSessions > 0 ? `⚠ 6시간 이상 ${s.staleSessions}건` : '—'} warn={s.staleSessions > 0} />
          </>
        )}
      </div>

      <div className="flex items-center gap-2.5">
        <Select value={repoFilter} onValueChange={setRepoFilter}>
          <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
            <SelectValue placeholder="리포" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">리포: 전체</SelectItem>
            {(repos.data ?? []).map((r) => (
              <SelectItem key={r.id} value={String(r.id)}>{r.fullName}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={userFilter} onValueChange={setUserFilter}>
          <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
            <SelectValue placeholder="사용자" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">사용자: 전체</SelectItem>
            {[...users.values()].map((u) => (
              <SelectItem key={u.id} value={String(u.id)}>{u.name ?? u.login}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex h-[34px] overflow-hidden rounded-md border">
          {TYPE_TABS.map((t, i) => (
            <button
              key={t.key}
              type="button"
              onClick={() => toggleType(t.key)}
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
      </div>

      <div className="space-y-3">
        {rows.map(({ stat, user, activities: acts, sessions: sess, draft }) => (
          <Card key={stat.userId} className="overflow-hidden rounded-lg shadow-none">
            <div className="flex h-[57px] items-center justify-between border-b px-4">
              <div className="flex items-center gap-2.5">
                <UserAvatar name={user?.name} login={user?.login} avatarUrl={user?.avatarUrl} />
                <span className="text-sm font-semibold">{user?.name ?? `#${stat.userId}`}</span>
                <span className="text-[13px] text-muted-foreground">{user?.login}</span>
                <span className="ml-2.5 text-[13px] text-muted-foreground">
                  {[
                    `커밋 ${stat.commits}`,
                    stat.prs > 0 && `PR ${stat.prs}`,
                    stat.merges > 0 && `머지 ${stat.merges}`,
                    stat.sessions > 0 && `미커밋 ${stat.sessions}`,
                  ].filter(Boolean).join(' · ')}
                </span>
              </div>
              <div className="flex items-center gap-3">
                {draft && <DraftStatusBadge status={draft.status} />}
                {draft ? (
                  <Button variant="outline" size="sm" className="h-8" onClick={() => navigate(`/drafts/${draft.id}`)}>
                    초안 열기
                  </Button>
                ) : (
                  <Button size="sm" className="h-8" disabled={generate.isPending} onClick={() => void onGenerate(stat.userId)}>
                    초안 생성
                  </Button>
                )}
              </div>
            </div>

            {acts.length === 0 && sess.length === 0 ? (
              <p className="px-4 py-6 text-center text-[13px] text-muted-foreground">표시할 활동이 없습니다.</p>
            ) : (
              <>
                {acts.map((a: Activity) => <ActivityRow key={a.id} activity={a} />)}
                {sess.map((s2: VscodeSession) => <SessionRow key={s2.id} session={s2} />)}
              </>
            )}
          </Card>
        ))}

        {!stats.isLoading && rows.length === 0 && (
          <Card className="rounded-lg p-10 text-center text-[13px] text-muted-foreground shadow-none">
            이 날짜에는 기록된 활동이 없습니다.
          </Card>
        )}
      </div>
    </div>
  )
}
