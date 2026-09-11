import { useState } from 'react'
import { CalendarDays, ChevronLeft, ChevronRight, PanelRightClose, PanelRightOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import {
  endOfMonth, firstWeekdayOfMonth, monthLabel, shiftMonth, startOfMonth, todayKst,
} from '@/lib/date'
import { cn } from '@/lib/utils'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const
const OPEN_KEY = 'worklog.dateSidebar.open'

/** 브라우저가 막아 둔 환경(사생활 보호 창 등)에서도 화면은 떠야 한다. */
function readOpen(): boolean {
  try {
    return localStorage.getItem(OPEN_KEY) !== '0'
  } catch {
    return true
  }
}

/**
 * 오른쪽 날짜 사이드바 — 깃허브 내역과 VSCode 내역이 함께 쓴다.
 *
 * <p>날짜 이동은 `← 9월 11일 →` 하루씩뿐이었다. 지난주 화요일을 보려면 다섯 번 눌러야 하고,
 * 지금 보는 날이 무슨 요일인지도 알 수 없었다. 달력을 붙여 한 번에 고르게 한다.
 *
 * <p>접을 수 있게 둔다 — 목록을 넓게 보고 싶을 때가 있고, 날짜를 자주 바꾸지 않는 사람에게는
 * 늘 펼쳐져 있을 이유가 없다. 접었는지는 브라우저에 기억시킨다.
 */
export default function DateSidebar() {
  const { date, setDate } = useSelectedDate()
  const [open, setOpen] = useState(readOpen)
  // 보고 있는 달. 날짜를 고르면 그 달로 따라가고, 화살표로만 달을 넘길 수도 있다.
  const [month, setMonth] = useState(date)

  const today = todayKst()

  function toggle() {
    setOpen((prev) => {
      try {
        localStorage.setItem(OPEN_KEY, prev ? '0' : '1')
      } catch {
        // 기억하지 못할 뿐이다. 화면은 그대로 동작한다.
      }
      return !prev
    })
  }

  function pick(next: string) {
    setDate(next)
    setMonth(next)
  }

  if (!open) {
    return (
      <div className="shrink-0">
        <Button
          variant="outline" size="icon" className="size-[34px]"
          onClick={toggle}
          aria-label="날짜 사이드바 펼치기"
          title={`날짜 선택 — 지금 ${date}`}
        >
          <PanelRightOpen />
        </Button>
      </div>
    )
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
      <div className="flex items-center justify-between">
        <p className="flex items-center gap-1.5 text-[13px] font-medium">
          <CalendarDays className="size-3.5 text-muted-foreground" />
          날짜
        </p>
        <Button
          variant="ghost" size="icon" className="size-7 text-muted-foreground"
          onClick={toggle} aria-label="날짜 사이드바 접기"
        >
          <PanelRightClose />
        </Button>
      </div>

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
