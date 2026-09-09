import { endOfMonth, firstWeekdayOfMonth, monthLabel, startOfMonth } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { PeopleSeriesPoint } from '@/types/api'

/**
 * GitHub 잔디 형식의 월간 히트맵.
 *
 * 색은 파랑 한 계열을 밝음→어두움으로 쓴다(순차형). 0건은 계열 색이 아니라 중립 회색이라
 * "적음" 과 "없음" 이 헷갈리지 않는다. 값은 아래 표가 그대로 보여 주므로 칸에 숫자를 넣지 않는다.
 */
const LEVELS = ['#f1f5f9', '#bfdbfe', '#93c5fd', '#3b82f6', '#1d4ed8'] as const
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

function level(commits: number): number {
  if (commits <= 0) return 0
  if (commits <= 2) return 1
  if (commits <= 4) return 2
  if (commits <= 6) return 3
  return 4
}

export default function ContributionCalendar({
  series, month, selected, maxDate, onSelect,
}: {
  series: PeopleSeriesPoint[]
  /** 이 달을 그린다. 'YYYY-MM-DD' 아무 날짜나 주면 된다. */
  month: string
  selected: string | null
  /** 이 날짜보다 뒤는 아직 오지 않은 날로 비워 둔다. */
  maxDate: string
  onSelect: (date: string) => void
}) {
  const first = startOfMonth(month)
  const last = endOfMonth(month)
  const lead = firstWeekdayOfMonth(month)
  const days = Number(last.slice(8))
  const byDate = new Map(series.map((s) => [s.date, s]))

  const cells: (string | null)[] = [
    ...Array.from({ length: lead }, () => null),
    ...Array.from({ length: days }, (_, i) => `${first.slice(0, 8)}${String(i + 1).padStart(2, '0')}`),
  ]
  while (cells.length % 7 !== 0) cells.push(null)

  return (
    <div className="mx-auto w-fit">
      <div className="flex items-center justify-between gap-8">
        <h2 className="text-[13px] font-medium">{monthLabel(month)} 커밋</h2>
        <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <span>적음</span>
          {LEVELS.map((c) => (
            <span key={c} className="size-[11px] rounded-[2px]" style={{ backgroundColor: c }} />
          ))}
          <span>많음</span>
        </div>
      </div>

      <div className="mt-4 grid grid-cols-[repeat(7,56px)] gap-1.5">
        {WEEKDAYS.map((w) => (
          <div key={w} className="pb-1 text-center text-[11px] text-muted-foreground">{w}</div>
        ))}

        {cells.map((date, i) => {
          if (!date) return <div key={`pad-${i}`} />
          const point = byDate.get(date)
          const future = date > maxDate
          const commits = point?.commits ?? 0
          return (
            <button
              key={date}
              type="button"
              disabled={future}
              onClick={() => onSelect(date)}
              title={future ? date : `${date} · 커밋 ${commits}건`}
              className={cn(
                'flex size-14 flex-col justify-between rounded-md border p-1.5 text-left transition-shadow',
                future ? 'border-dashed bg-transparent' : 'hover:ring-2 hover:ring-primary/30',
                selected === date && 'ring-2 ring-primary',
              )}
              style={future ? undefined : { backgroundColor: LEVELS[level(commits)] }}
            >
              <span
                className={cn(
                  'text-[11px] tabular-nums',
                  future ? 'text-muted-foreground/40' : level(commits) >= 3 ? 'text-white' : 'text-muted-foreground',
                )}
              >
                {Number(date.slice(8))}
              </span>
              {!future && commits > 0 && (
                <span className={cn('text-[11px] font-medium tabular-nums',
                  level(commits) >= 3 ? 'text-white' : 'text-foreground')}>
                  {commits}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
