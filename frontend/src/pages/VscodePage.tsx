import { useState } from 'react'
import {
  CalendarDays, ChevronLeft, ChevronRight, Clock, FileDiff, ListTodo, MessagesSquare, NotebookPen,
} from 'lucide-react'
import DayFilters from '@/components/day/DayFilters'
import SummaryCard from '@/components/common/SummaryCard'
import DiffStat from '@/components/common/DiffStat'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useMe, useRepos, useSessionRange, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import {
  endOfMonth, formatDateLabel, formatRelative, formatTime, monthLabel, shiftMonth, startOfMonth,
  todayKst,
} from '@/lib/date'
import { cn } from '@/lib/utils'
import type { AiSessionSummary, VscodeSession } from '@/types/api'

/**
 * VSCode 내역 — 확장이 보낸 **내** 작업.
 *
 * <p>GitHub 활동과 나란한 **초안의 다른 한 갈래**다 (PRD F3). 커밋 전 작업이라 GitHub 쪽에는
 * 아무 흔적이 없다. 팀원 전체는 관리자 콘솔이 맡는다 (9/10 회의).
 */
export default function VscodePage() {
  const { date, setDate } = useSelectedDate()

  const { data: me } = useMe()
  const sessions = useSessions({ date, userId: me?.id })
  const repos = useRepos()

  const [repoFilter, setRepoFilter] = useState('all')

  const shown = (sessions.data ?? [])
    .filter((s) => s.userId === me?.id && (repoFilter === 'all' || s.repo?.id === Number(repoFilter)))
    // 최신 보고가 위로 (9/10 결정).
    .sort((a, b) => b.reportedAt.localeCompare(a.reportedAt))

  const files = shown.reduce((n, x) => n + x.uncommittedFiles.length, 0)
  /**
   * 세션 행 여럿에 같은 대화가 들어 있을 수 있다 — 세션 키는 브랜치별인데 AI 대화는
   * 폴더 단위다. 그대로 더하면 브랜치를 바꾼 날 두 배로 세어진다 (BACKLOG2_client C-1).
   */
  const aiSessions = new Set(shown.flatMap((x) => (x.aiSessions ?? []).map((a) => a.id))).size

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <DayFilters repos={repos.data ?? []} repoFilter={repoFilter} onRepo={setRepoFilter} />
      </div>

      <div className="grid grid-cols-3 gap-3">
        {sessions.isLoading ? (
          [0, 1, 2].map((i) => <Skeleton key={i} className="h-[101px]" />)
        ) : (
          <>
            {/* 팀 전체 숫자는 관리자 콘솔이 맡는다 (9/10 결정). 여기는 내 것만 본다. */}
            <SummaryCard label="저장소" value={shown.length} />
            <SummaryCard label="미커밋 파일" value={files} />
            <SummaryCard label="AI 대화 세션" value={aiSessions} />
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

      <MonthList selectedDate={date} userId={me?.id} onPick={setDate} />
    </div>
  )
}

/**
 * 지난 VSCode 내역 — 달 단위 목록 (BACKLOG2 §2-3).
 *
 * <p>날짜 선택기만 있으면 지난주에 무엇을 했는지 보려고 하루씩 일곱 번 눌러야 한다.
 * 기록이 있는 날만 줄로 보여 주고, 누르면 위 상세가 그 날짜로 바뀐다. 업무 일지 목록과
 * 같은 방식이다.
 */
function MonthList({ selectedDate, userId, onPick }: {
  selectedDate: string
  userId: number | undefined
  onPick: (date: string) => void
}) {
  const [month, setMonth] = useState(startOfMonth(selectedDate))
  const range = { from: startOfMonth(month), to: endOfMonth(month) }
  const list = useSessionRange({ ...range, userId }, Boolean(userId))

  // 위에서 이미 펼쳐 놓은 날은 목록에서 뺀다.
  const days = new Map<string, VscodeSession[]>()
  for (const s of list.data ?? []) {
    if (s.userId !== userId || s.workDate === selectedDate) continue
    days.set(s.workDate, [...(days.get(s.workDate) ?? []), s])
  }
  const rows = [...days.entries()].sort((a, b) => b[0].localeCompare(a[0]))
  const thisMonth = startOfMonth(todayKst())

  return (
    <>
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-1.5 text-sm font-semibold">
          <CalendarDays className="size-4" />
          지난 VSCode 내역
        </h2>
        <div className="flex items-center gap-1">
          <Button variant="outline" size="icon" className="size-[30px]"
            onClick={() => setMonth(shiftMonth(month, -1))} aria-label="이전 달">
            <ChevronLeft />
          </Button>
          <span className="min-w-[92px] text-center text-[13px] font-medium tabular-nums">
            {monthLabel(month)}
          </span>
          <Button variant="outline" size="icon" className="size-[30px]"
            disabled={month >= thisMonth}
            onClick={() => setMonth(shiftMonth(month, 1))} aria-label="다음 달">
            <ChevronRight />
          </Button>
        </div>
      </div>

      <Card className="overflow-hidden rounded-lg shadow-none">
        {list.isLoading ? (
          <div className="space-y-2 p-4"><Skeleton className="h-9" /><Skeleton className="h-9" /></div>
        ) : rows.length === 0 ? (
          <p className="px-6 py-12 text-center text-[13px] text-muted-foreground">
            이 달에 VS Code 에서 보낸 다른 날의 작업이 없습니다.
          </p>
        ) : (
          rows.map(([workDate, sessions]) => {
            const files = sessions.reduce((n, x) => n + x.uncommittedFiles.length, 0)
            const ai = new Set(sessions.flatMap((x) => (x.aiSessions ?? []).map((a) => a.id))).size
            return (
              <button
                key={workDate}
                type="button"
                onClick={() => onPick(workDate)}
                className="flex w-full items-center gap-3 border-b px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-muted/60"
              >
                <span className="w-[150px] shrink-0 text-[13px] font-medium tabular-nums">
                  {formatDateLabel(workDate)}
                </span>
                <span className="text-[12px] text-muted-foreground">저장소 {sessions.length}</span>
                <span className="text-[12px] text-muted-foreground">미커밋 {files}파일</span>
                <span className="text-[12px] text-muted-foreground">AI 대화 {ai}세션</span>
                <span className="min-w-0 flex-1 truncate text-[12px] text-muted-foreground/70">
                  {sessions.map((x) => x.repo?.fullName ?? x.remoteUrl).join(' · ')}
                </span>
              </button>
            )
          })
        )}
      </Card>
    </>
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
          {(session.aiSessions ?? []).map((a) => <AiSession key={a.id} ai={a} />)}
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

/**
 * AI 대화 한 세션.
 *
 * <p>예전에는 `02:35–05:49` 처럼 시각만 보여 줬다. 무슨 대화였는지 알 수 없다. 제목을
 * 앞에 세우고, 펼치면 질문마다 무엇이라 답했는지 본다 (BACKLOG2 §2-3).
 */
function AiSession({ ai }: { ai: AiSessionSummary }) {
  const [open, setOpen] = useState(false)
  const turns = ai.turns ?? []
  const shown = open ? turns : turns.slice(0, 2)

  return (
    <div className="rounded border border-border/60 px-2 py-1.5">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center gap-1.5 text-left"
      >
        <ChevronRight className={cn('size-3 shrink-0 transition-transform', open && 'rotate-90')} />
        <span className="min-w-0 flex-1 truncate font-medium">{ai.title}</span>
        <span className="shrink-0 tabular-nums text-muted-foreground/70">
          {formatTime(ai.firstAt)}–{formatTime(ai.lastAt)} · {ai.promptCount}개
        </span>
      </button>

      <div className="mt-1 space-y-1 pl-[18px]">
        {shown.map((t, i) => (
          <div key={`${t.at}:${i}`}>
            <p className={open ? '' : 'truncate'}>· {t.prompt}</p>
            {t.answer && (
              <p className={cn('pl-2 text-muted-foreground/70', !open && 'truncate')}>↳ {t.answer}</p>
            )}
          </div>
        ))}
        {!open && turns.length > shown.length && (
          <p className="text-muted-foreground/60">… 그 외 {turns.length - shown.length}개</p>
        )}
        {/* 담은 것은 12개까지다. 실제로 물어본 횟수와 다르면 그렇다고 말한다 (C-1 ①). */}
        {open && ai.promptCount > turns.length && (
          <p className="text-muted-foreground/60">
            {ai.promptCount}개 중 최근 {turns.length}개만 보관합니다
          </p>
        )}
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
