import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarDays, ChevronLeft, ChevronRight, ExternalLink } from 'lucide-react'
import AutoBadge from '@/components/common/AutoBadge'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import {
  useConfirmDraft, useDailyStats, useDraft, useDraftRange, useDrafts, useGenerateDraft, useMe,
  useSaveDraft, useSessions,
} from '@/api/hooks'
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
  const stats = useDailyStats(date)
  const sessions = useSessions({ date, userId: me?.id })
  const generate = useGenerateDraft()

  const todayDraft = today.data?.[0]
  const myStat = stats.data?.byUser.find((u) => u.userId === me?.id)
  const mySessions = (sessions.data ?? []).filter((s) => s.userId === me?.id)
  const material = (myStat?.commits ?? 0) + (myStat?.prs ?? 0) + mySessions.length

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
        <TodayEditor draftId={todayDraft.id} onRegenerate={() => void onGenerate()} busy={generate.isPending} />
      ) : (
        <Card className="flex items-center justify-between gap-4 rounded-lg p-4 shadow-none">
          <div className="min-w-0">
            <p className="text-sm font-semibold">{formatDateLabel(date)}</p>
            <p className="mt-0.5 text-[12px] text-muted-foreground">
              {material > 0
                ? `근거 ${material}건 — 깃허브 내역과 VS 내역을 모아 씁니다`
                : '이 날짜에는 일지를 만들 활동이 없습니다'}
            </p>
          </div>
          <Button size="sm" className="h-[34px] shrink-0"
            disabled={generate.isPending || material === 0} onClick={() => void onGenerate()}>
            업무 일지 생성
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
              {d.status === 'CONFIRMED' && <DraftStatusBadge status="CONFIRMED" />}
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

/**
 * 오늘 일지 — 접지 않고 늘 펼쳐 둔다. 이 화면에 들어온 목적이 이것이기 때문이다.
 *
 * <p>근거 패널·Mattermost 전송은 편집 화면에 있다. 여기서는 쓰고 저장하고 완료한다.
 */
function TodayEditor({ draftId, onRegenerate, busy }: {
  draftId: number
  onRegenerate: () => void
  busy: boolean
}) {
  const navigate = useNavigate()
  const detail = useDraft(draftId)
  const save = useSaveDraft()
  const confirm = useConfirmDraft()

  const [text, setText] = useState('')
  const [touched, setTouched] = useState(false)

  // 재생성하면 새 본문이 내려온다. 손대지 않았다면 그것으로 갈아 끼운다.
  useEffect(() => {
    if (detail.data && !touched) setText(detail.data.contentMd)
  }, [detail.data, touched])

  const done = detail.data?.status === 'CONFIRMED'
  const dirty = touched && text !== detail.data?.contentMd

  async function onSave() {
    await save.mutateAsync({ id: draftId, contentMd: text })
    setTouched(false)
  }

  if (detail.isLoading || !detail.data) return <Skeleton className="h-[360px] rounded-lg" />

  return (
    <Card className="overflow-hidden rounded-lg shadow-none">
      <div className="flex items-center justify-between gap-4 border-b px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <span className="text-sm font-semibold">{formatDateLabel(detail.data.workDate)}</span>
          {detail.data.autoGenerated && <AutoBadge />}
          {done && <DraftStatusBadge status="CONFIRMED" />}
          <span className="text-[12px] text-muted-foreground">버전 {detail.data.version}</span>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Button size="sm" className="h-8" disabled={!dirty || save.isPending || done}
            onClick={() => void onSave()}>
            {save.isPending ? '저장 중…' : '저장'}
          </Button>
          <Button variant="outline" size="sm" className="h-8"
            disabled={done || confirm.isPending || dirty}
            title={dirty ? '먼저 저장해 주세요' : undefined}
            onClick={() => confirm.mutate({ id: draftId })}>
            완료
          </Button>
          <Button variant="outline" size="sm" className="h-8" disabled={busy || done} onClick={onRegenerate}>
            재생성
          </Button>
          <Button variant="outline" size="sm" className="h-8 gap-1.5"
            onClick={() => navigate(`/drafts/${draftId}`)}>
            <ExternalLink className="size-3.5" />
            근거 보기
          </Button>
        </div>
      </div>

      <Textarea
        value={text}
        onChange={(e) => { setText(e.target.value); setTouched(true) }}
        readOnly={done}
        className="min-h-[420px] rounded-none border-0 font-mono text-[13px] leading-relaxed focus-visible:ring-0"
      />
    </Card>
  )
}
