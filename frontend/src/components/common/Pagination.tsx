import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * 목록 아래 페이지 바. 경로는 바꾸지 않고 표시만 넘긴다 (9/10 결정).
 *
 * <p>페이지가 많아도 번호를 다 그리지 않는다. 현재 쪽 주변 다섯 개만 두고 나머지는 화살표로
 * 넘긴다 — 목록 아래가 번호로 길어지면 읽기 어렵다.
 */
export default function Pagination({
  page, pageCount, total, onChange,
}: {
  /** 0부터 */
  page: number
  pageCount: number
  total: number
  onChange: (page: number) => void
}) {
  if (pageCount <= 1) return null

  const start = Math.max(0, Math.min(page - 2, pageCount - 5))
  const numbers = Array.from({ length: Math.min(5, pageCount) }, (_, i) => start + i)

  return (
    <div className="flex items-center justify-between border-t px-4 py-2.5">
      <span className="text-[12px] text-muted-foreground tabular-nums">
        전체 {total}건 · {page + 1}/{pageCount} 쪽
      </span>
      <div className="flex items-center gap-1">
        <Button variant="outline" size="icon" className="size-[28px]"
          disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="이전 쪽">
          <ChevronLeft />
        </Button>
        {numbers.map((n) => (
          <button
            key={n}
            type="button"
            onClick={() => onChange(n)}
            aria-current={n === page ? 'page' : undefined}
            className={cn(
              'h-[28px] min-w-[28px] rounded-md px-2 text-[12px] tabular-nums transition-colors',
              n === page ? 'bg-primary/10 font-medium text-primary' : 'text-muted-foreground hover:bg-muted',
            )}
          >
            {n + 1}
          </button>
        ))}
        <Button variant="outline" size="icon" className="size-[28px]"
          disabled={page >= pageCount - 1} onClick={() => onChange(page + 1)} aria-label="다음 쪽">
          <ChevronRight />
        </Button>
      </div>
    </div>
  )
}
