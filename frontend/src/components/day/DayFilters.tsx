import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatDateLabel } from '@/lib/date'
import type { Repo } from '@/types/api'

/**
 * 리포 필터 + 날짜 선택. 깃허브 내역과 VS 내역이 같은 줄을 쓴다.
 *
 * <p>날짜 선택기는 상단 바에 있었는데, 날짜와 무관한 화면에서도 늘 떠 있어 자리만
 * 차지했다. 날짜가 실제로 화면을 지배하는 이 두 곳으로 내렸다 (9/10 결정).
 *
 * <p>사용자 필터는 없다 — 일반 로그인은 **내 내역만** 본다. 팀원 전체는 관리자 콘솔이
 * 맡는다.
 */
export default function DayFilters({
  repos, repoFilter, onRepo, children,
}: {
  repos: Repo[]
  repoFilter: string
  onRepo: (v: string) => void
  /** 리포 필터 옆에 붙는 화면별 추가 필터 (타입 탭 등) */
  children?: React.ReactNode
}) {
  const { date, prev, next } = useSelectedDate()

  return (
    <div className="flex items-center gap-2.5">
      <Select value={repoFilter} onValueChange={onRepo}>
        <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
          <SelectValue placeholder="리포" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">리포: 전체</SelectItem>
          {repos.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.fullName}</SelectItem>)}
        </SelectContent>
      </Select>

      {children}

      <div className="ml-auto flex items-center gap-2">
        <Button variant="outline" size="icon" className="size-[34px]" onClick={prev} aria-label="하루 전">
          <ChevronLeft />
        </Button>
        <div className="flex h-[34px] min-w-[160px] items-center justify-center rounded-md border px-3 text-[13px] font-medium tabular-nums">
          {formatDateLabel(date)}
        </div>
        <Button variant="outline" size="icon" className="size-[34px]" onClick={next} aria-label="하루 뒤">
          <ChevronRight />
        </Button>
      </div>
    </div>
  )
}
