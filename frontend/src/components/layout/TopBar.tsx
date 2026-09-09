import { ChevronLeft, ChevronRight } from 'lucide-react'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import { useMe } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatDateLabel } from '@/lib/date'

/** 상단 바 — 날짜 선택기(좌우 화살표로 하루씩) + 현재 사용자 아바타. */
export default function TopBar() {
  const { date, prev, next } = useSelectedDate()
  const { data: me } = useMe()

  return (
    <header className="flex h-[57px] shrink-0 items-center justify-between border-b bg-background px-6">
      <div className="flex items-center gap-2">
        <Button variant="outline" size="icon" className="size-[30px]" onClick={prev} aria-label="하루 전">
          <ChevronLeft />
        </Button>
        <div className="flex h-[30px] min-w-[160px] items-center justify-center rounded-md border px-3 text-[13px] font-medium tabular-nums">
          {formatDateLabel(date)}
        </div>
        <Button variant="outline" size="icon" className="size-[30px]" onClick={next} aria-label="하루 뒤">
          <ChevronRight />
        </Button>
      </div>
      <UserAvatar name={me?.name} login={me?.login} avatarUrl={me?.avatarUrl} />
    </header>
  )
}
