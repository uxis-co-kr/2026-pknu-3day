import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarDays, ChevronLeft, ChevronRight, Sparkles } from 'lucide-react'
import AutoBadge from '@/components/common/AutoBadge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import DraftWorkspace from '@/components/draft/DraftWorkspace'
import { useActivities, useDraftRange, useDrafts, useGenerateDraft, useMe, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatDateLabel, formatTime } from '@/lib/date'

/** YYYY-MM 의 첫날·마지막날. 목록은 달 단위로 넘긴다. */
function monthRange(ym: string): { from: string; to: string } {
  const [y, m] = ym.split('-').map(Number)
  const last = new Date(y, m, 0).getDate()
  return { from: `${ym}-01`, to: `${ym}-${String(last).padStart(2, '0')}` }
}

function shiftMonth(ym: string, by: number): string {
  const [y, m] = ym.split('-').map(Number)
  const d = new Date(y, m - 1 + by, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/**
 * 업무 일지 작성 — 오늘 것은 맨 위에서 **펼쳐 놓고** 바로 쓰고, 지난 것은 아래에서 고른다.
 *
 * <p>일지는 "생성" 을 눌렀을 때만 만들어진다. 서버가 그날의 깃허브 내역과 VS 내역을
 * 함께 모아 쓴다 (PRD F3).
 */
export default function DraftsPage() {
  const navigate = useNavigate()
  const { date } = useSelectedDate()
  const { data: me } = useMe()

  const [month, setMonth] = useState(date.slice(0, 7))
  const range = monthRange(month)
  const list = useDraftRange({ ...range, userId: me?.id }, Boolean(me))

  const today = useDrafts({ date, userId: me?.id })
  const activities = useActivities({ date, userId: me?.id })
  const sessions = useSessions({ date, userId: me?.id })
  const generate = useGenerateDraft()

  const todayDraft = today.data?.[0]
  // 내 활동에서 직접 센다. /stats/daily 응답에는 팀 전원의 숫자가 실려 온다.
  const myActivities = (activities.data?.items ?? []).filter((a) => a.user?.id === me?.id)
  const mySessions = (sessions.data ?? []).filter((s) => s.userId === me?.id)
  const material = myActivities.length + mySessions.length

  // 오늘 것은 위에서 이미 펼쳐 놓았다. 목록에서는 뺀다.
  const past = (list.data ?? []).filter((d) => d.workDate !== date)
  const thisMonth = new Date().toISOString().slice(0, 7)

  async function onGenerate() {
    if (!me) return
    await generate.mutateAsync({ date, userId: me.id })
  }

  return (
    <div className="space-y-4">
      {todayDraft ? (
        <DraftWorkspace draftId={todayDraft.id} />
      ) : (
        <Card className="flex items-center justify-between gap-4 rounded-lg p-4 shadow-none">
          <div className="min-w-0">
            <p className="text-sm font-semibold">{formatDateLabel(date)}</p>
            <p className="mt-0.5 text-[12px] text-muted-foreground">
              {material > 0
                ? `아직 일지가 없습니다. 근거 ${material}건을 모아 AI 가 초안을 써 줍니다`
                : '이 날짜에는 일지를 만들 활동이 없습니다'}
            </p>
          </div>
          <Button size="sm" className="h-[34px] shrink-0 gap-1.5"
            disabled={generate.isPending || material === 0} onClick={() => void onGenerate()}>
            <Sparkles className="size-3.5" />
            {generate.isPending ? 'AI 가 쓰는 중…' : 'AI 생성'}
          </Button>
        </Card>
      )}

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
            {month.replace('-', '년 ')}월
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
    </div>
  )
}
