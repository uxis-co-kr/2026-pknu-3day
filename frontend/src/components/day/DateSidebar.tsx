import { useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import {
  endOfMonth, firstWeekdayOfMonth, monthLabel, shiftMonth, startOfMonth, todayKst,
} from '@/lib/date'
import { cn } from '@/lib/utils'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

/**
 * 오른쪽 날짜 사이드바 — 깃허브 내역과 VSCode 내역이 함께 쓴다.
 *
 * <p>날짜 이동은 `← 9월 11일 →` 하루씩뿐이었다. 지난주 화요일을 보려면 다섯 번 눌러야 하고,
 * 지금 보는 날이 무슨 요일인지도 알 수 없었다. 달력을 붙여 한 번에 고르게 한다.
 *
 * <p>옆의 요약 박스와 윗줄을 맞춘다 — 필터 줄은 본문 위에 통째로 두고, 그 아래에서 본문과
 * 나란히 시작한다. 여백을 숫자로 맞추면 필터 줄 높이가 바뀔 때마다 어긋난다.
 */
export default function DateSidebar() {
  const { date, setDate } = useSelectedDate()
  // 보고 있는 달. 날짜를 고르면 그 달로 따라가고, 화살표로만 달을 넘길 수도 있다.
  const [month, setMonth] = useState(date)

  const today = todayKst()

  function pick(next: string) {
    setDate(next)
    setMonth(next)
  }

  const first = startOfMonth(month)
  const lead = firstWeekdayOfMonth(month)
  const days = Number(endOfMonth(month).slice(8))
  const cells: (string | null)[] = [
    ...Array.from({ length: lead }, () => null),
    ...Array.from({ length: days }, (_, i) => `${first.slice(0, 8)}${String(i + 1).padStart(2, '0')}`),
  ]
  while (cells.length % 7 !== 0) cells.push(null)

  return (
    <Card className="h-fit w-[236px] shrink-0 rounded-lg p-3 shadow-none">
      <p className="flex items-center gap-1.5 text-[13px] font-medium">
        <CalendarDays className="size-3.5 text-muted-foreground" />
        날짜
      </p>

      <div className="mt-2 flex items-center justify-between">
        <Button
          variant="ghost" size="icon" className="size-7"
          onClick={() => setMonth(shiftMonth(month, -1))} aria-label="이전 달"
        >
          <ChevronLeft />
        </Button>
        <span className="text-[13px] font-medium tabular-nums">{monthLabel(month)}</span>
        <Button
          variant="ghost" size="icon" className="size-7"
          onClick={() => setMonth(shiftMonth(month, 1))} aria-label="다음 달"
        >
          <ChevronRight />
        </Button>
      </div>

      <div className="mt-2 grid grid-cols-7 gap-0.5">
        {WEEKDAYS.map((w) => (
          <div key={w} className="pb-1 text-center text-[11px] text-muted-foreground">{w}</div>
        ))}
        {cells.map((d, i) => {
          if (!d) return <div key={`pad-${i}`} />
          // 아직 오지 않은 날은 누를 수 없다 — 눌러도 빈 목록만 나온다.
          const future = d > today
          return (
            <button
              key={d}
              type="button"
              disabled={future}
              onClick={() => pick(d)}
              aria-current={d === date ? 'date' : undefined}
              className={cn(
                'h-[28px] rounded-md text-[12px] tabular-nums transition-colors',
                future && 'cursor-default text-muted-foreground/30',
                !future && d !== date && 'text-foreground hover:bg-muted',
                d === date && 'bg-primary font-medium text-primary-foreground',
                d === today && d !== date && 'font-medium text-primary',
              )}
            >
              {Number(d.slice(8))}
            </button>
          )
        })}
      </div>

      <Button
        variant="outline" size="sm" className="mt-3 h-8 w-full text-[12px]"
        disabled={date === today}
        onClick={() => pick(today)}
      >
        오늘로
      </Button>
    </Card>
  )
}
