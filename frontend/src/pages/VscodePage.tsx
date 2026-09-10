import { useState } from 'react'
import { Clock, FileDiff, ListTodo, MessagesSquare, NotebookPen } from 'lucide-react'
import DayFilters from '@/components/day/DayFilters'
import SummaryCard from '@/components/common/SummaryCard'
import DiffStat from '@/components/common/DiffStat'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useDailyStats, useMe, useRepos, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatRelative, formatTime } from '@/lib/date'
import type { VscodeSession } from '@/types/api'

/**
 * VS 내역 — VS Code 확장이 보낸 **내** 작업.
 *
 * <p>GitHub 활동과 나란한 **초안의 다른 한 갈래**다 (PRD F3). 커밋 전 작업이라 GitHub 쪽에는
 * 아무 흔적이 없다. 팀원 전체는 관리자 콘솔이 맡는다 (9/10 회의).
 */
export default function VscodePage() {
  const { date } = useSelectedDate()

  const { data: me } = useMe()
  const stats = useDailyStats(date)
  const sessions = useSessions({ date, userId: me?.id })
  const repos = useRepos()

  const [repoFilter, setRepoFilter] = useState('all')

  const shown = (sessions.data ?? [])
    .filter((s) => s.userId === me?.id && (repoFilter === 'all' || s.repo?.id === Number(repoFilter)))
    // 최신 보고가 위로 (9/10 결정).
    .sort((a, b) => b.reportedAt.localeCompare(a.reportedAt))

  const files = shown.reduce((n, x) => n + x.uncommittedFiles.length, 0)


  const s = stats.data

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <DayFilters repos={repos.data ?? []} repoFilter={repoFilter} onRepo={setRepoFilter} />
      </div>

      <div className="grid grid-cols-3 gap-3">
        {stats.isLoading || !s ? (
          [0, 1, 2].map((i) => <Skeleton key={i} className="h-[101px]" />)
        ) : (
          <>
            <SummaryCard label="내 세션" value={shown.length} hint={`팀 전체 ${s.sessions}`} />
            <SummaryCard label="미커밋 파일" value={files} hint={files > 0 ? '커밋 전 작업입니다' : '—'} />
            <SummaryCard label="6시간 이상 미커밋" value={s.staleSessions}
              hint={s.staleSessions > 0 ? '⚠ 커밋을 권합니다' : '—'} warn={s.staleSessions > 0} />
          </>
        )}
      </div>

      <Card className="overflow-hidden rounded-lg shadow-none">
        {shown.length === 0 ? (
          <div className="px-6 py-12 text-center">
            <p className="text-[13px] text-muted-foreground">이 날짜에는 VS Code 에서 보낸 작업이 없습니다.</p>
            <p className="mx-auto mt-2 max-w-[420px] text-[12px] leading-relaxed text-muted-foreground/70">
              VS Code 에서 <strong>WorkLog: 지금 전송</strong> 을 누르거나, 확장 사이드바의 전송 버튼을 쓰면
              여기에 나타납니다.
            </p>
          </div>
        ) : (
          shown.map((session) => <SessionDetail key={session.id} session={session} />)
        )}
      </Card>
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

        <Group label="AI 대화" count={session.aiSessions?.length ?? 0} Icon={MessagesSquare}>
          {(session.aiSessions ?? []).map((a) => (
            <div key={a.id}>
              <p className="tabular-nums text-muted-foreground/70">
                {formatTime(a.firstAt)}–{formatTime(a.lastAt)} · {a.promptCount}개
              </p>
              {a.prompts.slice(0, 3).map((q) => (
                <p key={q} className="truncate pl-2">· {q}</p>
              ))}
            </div>
          ))}
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
