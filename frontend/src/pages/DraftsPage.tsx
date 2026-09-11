import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import AutoBadge from '@/components/common/AutoBadge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import DraftWorkspace from '@/components/draft/DraftWorkspace'
import PeriodDraftWorkspace from '@/components/draft/PeriodDraftWorkspace'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActivities, useDraftRange, useDrafts, useMe, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import {
  endOfMonth, formatDateLabel, formatTime, monthLabel, shiftMonth, startOfMonth, todayKst,
} from '@/lib/date'

/**
 * 업무 일지 작성 — 오늘 것은 맨 위에서 **펼쳐 놓고** 바로 쓰고, 지난 것은 아래에서 고른다.
 *
 * <p>일지는 "생성" 을 눌렀을 때만 만들어진다. 서버가 그날의 깃허브 내역과 VSCode 내역을
 * 함께 모아 쓴다 (PRD F3).
 */
/** 일별·주간·저장소별 — 셋 다 AI 생성·저장·Mattermost 전송까지 같은 방식으로 쓴다 (V15). */
type Kind = 'daily' | 'weekly' | 'repo'

const KIND_TAB =
  'h-[32px] rounded-none border-b-2 border-transparent px-4 text-[13px] shadow-none'
  + ' data-[state=active]:border-primary data-[state=active]:bg-transparent'
  + ' data-[state=active]:text-primary data-[state=active]:shadow-none'

export default function DraftsPage() {
  const navigate = useNavigate()
  const [kind, setKind] = useState<Kind>('daily')
  const { date } = useSelectedDate()
  const { data: me } = useMe()

  const [month, setMonth] = useState(startOfMonth(date))
  const range = { from: startOfMonth(month), to: endOfMonth(month) }
  const list = useDraftRange({ ...range, userId: me?.id }, Boolean(me))

  const today = useDrafts({ date, userId: me?.id })
  const activities = useActivities({ date, userId: me?.id })
  const sessions = useSessions({ date, userId: me?.id })

  const todayDraft = today.data?.[0]
  // 내 활동에서 직접 센다. /stats/daily 응답에는 팀 전원의 숫자가 실려 온다.
  const myActivities = (activities.data?.items ?? []).filter((a) => a.user?.id === me?.id)
  const mySessions = (sessions.data ?? []).filter((s) => s.userId === me?.id)
  const material = myActivities.length + mySessions.length

  // 오늘 것은 위에서 이미 펼쳐 놓았다. 목록에서는 뺀다.
  const past = (list.data ?? []).filter((d) => d.workDate !== date)
  const thisMonth = startOfMonth(todayKst())


  return (
    <div className="space-y-4">
      <Tabs value={kind} onValueChange={(v) => setKind(v as Kind)}>
        <TabsList className="h-auto w-full justify-start rounded-none border-b bg-transparent p-0">
          <TabsTrigger value="daily" className={KIND_TAB}>일별</TabsTrigger>
          <TabsTrigger value="weekly" className={KIND_TAB}>주간 업무일지</TabsTrigger>
          <TabsTrigger value="repo" className={KIND_TAB}>저장소별 업무일지</TabsTrigger>
        </TabsList>
      </Tabs>

      {kind !== 'daily' && (
        <PeriodDraftWorkspace
          kind={kind}
          userId={me?.id}
          displayName={me?.name ?? me?.login}
        />
      )}

      {kind === 'daily' && (<>
      {/*
        * 일지가 없어도 편집기를 띄운다 — 이 화면에 들어온 목적이 쓰는 것이기 때문이다.
        * 저장하기 전에는 서버에 아무것도 만들지 않는다 (열어만 보고 나간 날에 빈 일지가
        * 쌓이지 않게).
        */}
      <DraftWorkspace
        draftId={todayDraft?.id}
        workDate={date}
        userId={me?.id}
        displayName={me?.name ?? me?.login}
        evidence={{ activities: myActivities, sessions: mySessions }}
        canGenerate={material > 0}
      />

      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-1.5 text-sm font-semibold">
          <CalendarDays className="size-4" />
          지난 업무 일지
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
        ) : past.length === 0 ? (
          <p className="px-6 py-12 text-center text-[13px] text-muted-foreground">
            이 달에 쓴 업무 일지가 없습니다.
          </p>
        ) : (
          past.map((d) => (
            <button
              key={d.id}
              type="button"
              onClick={() => navigate(`/drafts/${d.id}`)}
              className="flex w-full items-center gap-3 border-b px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-muted/60"
            >
              <span className="w-[120px] shrink-0 text-[13px] font-medium tabular-nums">
                {formatDateLabel(d.workDate)}
              </span>
              {d.autoGenerated && <AutoBadge />}
              <span className="text-[12px] text-muted-foreground">버전 {d.version}</span>
              <span className="min-w-0 flex-1" />
              <span className="shrink-0 text-[12px] text-muted-foreground">
                수정 {formatTime(d.updatedAt)}
              </span>
            </button>
          ))
        )}
      </Card>
      </>)}
    </div>
  )
}
