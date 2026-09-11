import { useEffect, useRef, useState } from 'react'
import {
  CalendarDays, ChevronLeft, ChevronRight, Clock, FileDiff, GitCommitHorizontal, ListTodo,
  MessagesSquare, NotebookPen, Save,
} from 'lucide-react'
import DayFilters from '@/components/day/DayFilters'
import PlanMarkdown from '@/components/common/PlanMarkdown'
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
  const { date } = useSelectedDate()

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

      <MonthList selectedDate={date} userId={me?.id} />
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
function MonthList({ selectedDate, userId }: {
  selectedDate: string
  userId: number | undefined
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
          rows.map(([workDate, sessions]) => (
            <DayRow key={workDate} workDate={workDate} sessions={sessions} />
          ))
        )}
      </Card>
    </>
  )
}

/** 세션 하나를 네 갈래로 편다 — 초안 근거 패널과 같은 구분이다. */
function SessionDetail({ session }: { session: VscodeSession }) {
  // 계획은 확장이 markdown 문서 한 통으로 보낸다 — 하루에 하나다. 건수를 세지 않는다.
  const plan = (session.planNote ?? '').trim()
  // 미푸시는 빈 배열(없음)과 값이 없는 것이 다르다. null 을 0 으로 뭉개지 않는다.
  //
  // 값이 없는 까닭은 둘인데 서버 기록만으로는 가릴 수 없다 — 업스트림이 없어 확장이 셀 수
  // 없었거나(한 번도 push 하지 않은 브랜치), 미푸시를 보내기 전 확장·서버가 남긴 기록이거나.
  // 그래서 "셀 수 없음" 이라고 단정하지 않고 "알 수 없음" 으로 적는다.
  const unpushed = session.unpushedCommits ?? null
  const unsaved = session.unsavedFiles ?? []

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

      {/* 확장 사이드바와 같은 순서로 둔다 — 계획, 미커밋 파일, 미푸시 커밋, AI 대화, TODO,
          저장 이벤트. 두 화면을 오가며 볼 때 눈이 같은 자리를 짚어야 한다. */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-3">
        <Group label="계획" empty={!plan} Icon={NotebookPen}>
          {/* 확장에서 markdown 으로 적은 문서다. 적은 양식 그대로 그린다. */}
          <PlanMarkdown source={plan} />
        </Group>

        <Group label="미커밋 파일" count={session.uncommittedFiles.length} Icon={FileDiff}>
          {session.uncommittedFiles.map((f) => (
            <div key={f.path} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{f.path}</span>
              <DiffStat additions={f.additions} deletions={f.deletions} />
            </div>
          ))}
        </Group>

        {/* 커밋했지만 아직 push 하지 않은 것. GitHub 활동에는 안 잡히므로 여기서만 보인다. */}
        <Group
          label="미푸시 커밋"
          count={unpushed?.length}
          empty={!unpushed?.length}
          emptyLabel={unpushed ? '없음' : '알 수 없음'}
          Icon={GitCommitHorizontal}
        >
          {(unpushed ?? []).map((c) => (
            <div key={c.sha} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{c.subject}</span>
              <span className="shrink-0 tabular-nums text-muted-foreground/70">
                {c.sha} · {formatTime(c.at)}
              </span>
            </div>
          ))}
        </Group>

        <Group label="AI 대화" count={session.aiSessions?.length ?? 0} Icon={MessagesSquare}>
          {(session.aiSessions ?? []).map((a) => <AiSession key={a.id} ai={a} />)}
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

        {/* 고쳐 놓고 저장하지 않은 파일. 디스크에 없으니 diff 에도 없다 — 여기가 유일한 출처다. */}
        <Group label="미저장 파일" count={unsaved.length} Icon={Save}>
          {unsaved.map((f) => (
            <div key={f.path} className="flex items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{f.path}</span>
              {f.dirtySince && (
                <span className="shrink-0 tabular-nums text-muted-foreground/70">
                  {formatTime(f.dirtySince)}부터
                </span>
              )}
            </div>
          ))}
        </Group>

        {/* 2026-09-11 부터 모으지 않는다. 그전 기록에만 남아 있어, 있을 때만 보여 준다. */}
        {session.editTimeline.length > 0 && (
          <Group label="저장 이벤트 (지난 기록)" count={session.editTimeline.length} Icon={Clock}>
            {session.editTimeline.map((e) => (
              <div key={e.path} className="flex items-center gap-2">
                <span className="min-w-0 flex-1 truncate">{e.path}</span>
                <span className="shrink-0 tabular-nums text-muted-foreground/70">
                  {e.saveCount}회 · {formatTime(e.lastSavedAt)}
                </span>
              </div>
            ))}
          </Group>
        )}
      </div>
    </div>
  )
}

/**
 * 지난 날 하루치. **그 자리에서 펼친다.**
 *
 * <p>누르면 위 상세가 그 날짜로 바뀌게 했더니 화면이 통째로 갈아엎어져, 목록을 훑다가
 * 제자리를 잃었다. 날짜 선택기는 그대로 두고 여기서는 펼쳐 보기만 한다.
 */
function DayRow({ workDate, sessions }: { workDate: string; sessions: VscodeSession[] }) {
  const [open, setOpen] = useState(false)
  const files = sessions.reduce((n, x) => n + x.uncommittedFiles.length, 0)
  const ai = new Set(sessions.flatMap((x) => (x.aiSessions ?? []).map((a) => a.id))).size

  return (
    <div className="border-b last:border-b-0">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-muted/60"
      >
        <ChevronRight className={cn('size-3.5 shrink-0 transition-transform', open && 'rotate-90')} />
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

      {open && (
        <div className="border-t bg-muted/30">
          {sessions.map((session) => <SessionDetail key={session.id} session={session} />)}
        </div>
      )}
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
        {/* 서버가 붙인 요약. 질문 원문보다 먼저 읽히도록 위에 둔다 — 접은 채로도 무슨
            대화였는지 알 수 있어야 한다. 전송 직후에는 잠깐 없다. */}
        {ai.summary && <p className="text-foreground/80">{ai.summary}</p>}
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

/**
 * 접었을 때의 높이(px). 12px 글씨로 여덟 줄쯤 — 미커밋 파일 수십 개나 긴 계획 문서 하나가
 * 하루치 칸을 세로로 밀어내지 않을 만큼이면서, 대부분의 갈래는 접히지도 않을 높이다.
 */
const COLLAPSED_HEIGHT = 132

function Group({ label, count, empty, emptyLabel, Icon, children }: {
  label: string
  /** 몇 건인지. 세는 것이 뜻이 있을 때만 준다 — 계획은 문서 한 통이라 세지 않는다. */
  count?: number
  /** 셀 수 없는 갈래에서 "없음" 을 가리는 값. count 를 주면 필요 없다. */
  empty?: boolean
  /** 비었을 때 적을 말. 미푸시처럼 "0개" 와 "셀 수 없음" 이 다른 갈래에서 쓴다. */
  emptyLabel?: string
  Icon: typeof Clock
  children: React.ReactNode
}) {
  const isEmpty = empty ?? count === 0
  const [open, setOpen] = useState(false)
  const content = useRef<HTMLDivElement>(null)
  /** 접은 높이를 넘는지. 넘을 때만 "더 보기" 를 붙인다. */
  const [long, setLong] = useState(false)

  // 갈래마다 안에 든 것이 달라(목록, markdown 문서, 대화 묶음) 개수로는 길이를 알 수 없다.
  // 실제로 그려진 높이를 잰다. 내용이 바뀌거나 창이 좁아지면 다시 잰다 — 관찰 대상은
  // **잘리지 않은 안쪽**이라, 바깥을 max-height 로 막아도 참값이 들어온다.
  useEffect(() => {
    const el = content.current
    if (!el) return
    const observer = new ResizeObserver(() => setLong(el.scrollHeight > COLLAPSED_HEIGHT))
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return (
    <div className="min-w-0">
      <p className="mb-1 flex items-center gap-1.5 text-[12px] font-medium text-muted-foreground">
        <Icon className="size-3.5" />
        {label}
        {count !== undefined && <span className="tabular-nums">{count}</span>}
      </p>
      {isEmpty ? (
        <p className="text-[12px] text-muted-foreground/60">{emptyLabel ?? '없음'}</p>
      ) : (
        <>
          <div
            className={cn(!open && long && 'overflow-hidden')}
            style={{ maxHeight: open || !long ? undefined : COLLAPSED_HEIGHT }}
          >
            <div ref={content} className="space-y-0.5 text-[12px]">{children}</div>
          </div>
          {long && (
            <button
              type="button"
              onClick={() => setOpen(!open)}
              className="mt-1 text-[12px] text-muted-foreground/70 underline underline-offset-2 hover:text-foreground"
            >
              {open ? '접기' : '더 보기'}
            </button>
          )}
        </>
      )}
    </div>
  )
}
