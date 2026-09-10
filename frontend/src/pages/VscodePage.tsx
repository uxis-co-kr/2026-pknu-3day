import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Clock, FileDiff, ListTodo, NotebookPen } from 'lucide-react'
import DayFilters from '@/components/day/DayFilters'
import UserCardHeader from '@/components/day/UserCardHeader'
import SummaryCard from '@/components/common/SummaryCard'
import DiffStat from '@/components/common/DiffStat'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivities, useDailyStats, useDrafts, useGenerateDraft, useRepos, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatRelative, formatTime } from '@/lib/date'
import type { UserRef, VscodeSession } from '@/types/api'

/**
 * VS 내역 — VS Code 확장이 보낸 그날의 작업.
 *
 * <p>GitHub 활동과 나란한 **초안의 다른 한 갈래**다 (PRD F3). 커밋 전 작업이라 GitHub 쪽에는
 * 아무 흔적이 없다.
 */
export default function VscodePage() {
  const { date } = useSelectedDate()
  const navigate = useNavigate()

  const stats = useDailyStats(date)
  const sessions = useSessions({ date })
  const drafts = useDrafts({ date })
  const repos = useRepos()
  // 사용자 이름은 활동에만 실려 온다. 세션에는 userId 뿐이라 여기서 이름을 얻는다.
  const activities = useActivities({ date })
  const generate = useGenerateDraft()

  const [repoFilter, setRepoFilter] = useState('all')
  const [userFilter, setUserFilter] = useState('all')

  const users = new Map<number, UserRef>()
  for (const a of activities.data?.items ?? []) if (a.user) users.set(a.user.id, a.user)

  const shown = (sessions.data ?? []).filter((s) =>
    (repoFilter === 'all' || s.repo?.id === Number(repoFilter)) &&
    (userFilter === 'all' || s.userId === Number(userFilter)))

  /** 세션은 사용자별로 묶는다. 한 사람이 저장소를 여럿 열어 두면 세션도 여럿이다. */
  const byUser = new Map<number, VscodeSession[]>()
  for (const s of shown) byUser.set(s.userId, [...(byUser.get(s.userId) ?? []), s])

  async function onGenerate(userId: number) {
    const draft = await generate.mutateAsync({ date, userId })
    if (draft && 'id' in draft) navigate(`/drafts/${draft.id}`)
  }

  const s = stats.data

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        {stats.isLoading || !s ? (
          [0, 1, 2].map((i) => <Skeleton key={i} className="h-[101px]" />)
        ) : (
          <>
            <SummaryCard label="세션" value={s.sessions} hint={`${byUser.size}명`} />
            <SummaryCard label="미커밋 파일" value={shown.reduce((n, x) => n + x.uncommittedFiles.length, 0)} hint="—" />
            <SummaryCard label="6시간 이상 미커밋" value={s.staleSessions}
              hint={s.staleSessions > 0 ? '⚠ 커밋을 권합니다' : '—'} warn={s.staleSessions > 0} />
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
      />

      <div className="space-y-3">
        {[...byUser.entries()].map(([userId, list]) => (
          <Card key={userId} className="overflow-hidden rounded-lg shadow-none">
            <UserCardHeader
              user={users.get(userId)}
              userId={userId}
              summary={`세션 ${list.length} · 미커밋 ${list.reduce((n, x) => n + x.uncommittedFiles.length, 0)}파일`}
              draft={drafts.data?.find((d) => d.userId === userId)}
              busy={generate.isPending}
              onOpenDraft={(id) => navigate(`/drafts/${id}`)}
              onGenerate={(id) => void onGenerate(id)}
            />
            {list.map((session) => <SessionDetail key={session.id} session={session} />)}
          </Card>
        ))}

        {!sessions.isLoading && byUser.size === 0 && (
          <Card className="rounded-lg p-10 text-center text-[13px] text-muted-foreground shadow-none">
            이 날짜에는 VS Code 에서 보낸 작업이 없습니다.
          </Card>
        )}
      </div>
    </div>
  )
}

/** 세션 하나를 네 갈래로 편다 — 초안 근거 패널과 같은 구분이다. */
function SessionDetail({ session }: { session: VscodeSession }) {
  // 계획은 확장이 여러 건을 줄바꿈으로 이어 보낸다 (서버 계약은 문자열 한 칸).
  const plans = (session.planNote ?? '').split('\n').map((p) => p.trim()).filter(Boolean)

  return (
    <div className="border-b px-4 py-3 last:border-b-0">
      <div className="mb-2 flex items-center gap-2.5 text-[13px]">
        <span className="font-medium">{session.repo?.fullName ?? session.remoteUrl}</span>
        <span className="rounded bg-status-uncommitted/15 px-[7px] py-px text-[12px] text-status-uncommitted">
          {session.branch}
        </span>
        <span className="text-[12px] text-muted-foreground">
          마지막 커밋 {formatRelative(session.lastCommitAt)} · 보고 {formatTime(session.reportedAt)}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-x-6 gap-y-3">
        <Group label="미커밋 파일" count={session.uncommittedFiles.length} Icon={FileDiff}>
          {session.uncommittedFiles.map((f) => (
            <div key={f.path} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{f.path}</span>
              <DiffStat additions={f.additions} deletions={f.deletions} />
            </div>
          ))}
        </Group>

        <Group label="TODO" count={session.todos.length} Icon={ListTodo}>
          {session.todos.map((t) => (
            <div key={`${t.path}:${t.line}`} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{t.text}</span>
              <span className="shrink-0 text-muted-foreground/70">
                {t.path.slice(t.path.lastIndexOf('/') + 1)}:{t.line}
              </span>
            </div>
          ))}
        </Group>

        <Group label="계획" count={plans.length} Icon={NotebookPen}>
          {plans.map((p) => <p key={p} className="truncate italic">{p}</p>)}
        </Group>

        <Group label="저장 이벤트" count={session.editTimeline.length} Icon={Clock}>
          {session.editTimeline.map((e) => (
            <div key={e.path} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{e.path}</span>
              <span className="shrink-0 tabular-nums text-muted-foreground/70">
                {e.saveCount}회 · {formatTime(e.lastSavedAt)}
              </span>
            </div>
          ))}
        </Group>
      </div>
    </div>
  )
}

function Group({ label, count, Icon, children }: {
  label: string
  count: number
  Icon: typeof Clock
  children: React.ReactNode
}) {
  return (
    <div className="min-w-0">
      <p className="mb-1 flex items-center gap-1.5 text-[12px] font-medium text-muted-foreground">
        <Icon className="size-3.5" />
        {label}
        <span className="tabular-nums">{count}</span>
      </p>
      {count === 0
        ? <p className="text-[12px] text-muted-foreground/60">없음</p>
        : <div className="space-y-0.5 text-[12px]">{children}</div>}
    </div>
  )
}
