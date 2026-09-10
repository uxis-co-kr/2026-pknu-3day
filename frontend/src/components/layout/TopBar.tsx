import { useLocation } from 'react-router-dom'
import { ChevronLeft, ChevronRight, LogOut } from 'lucide-react'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { auth } from '@/api/apiClient'
import { useMe } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { formatDateLabel } from '@/lib/date'
import { cn } from '@/lib/utils'

/**
 * 상단 바 — 날짜 선택기(좌우 화살표로 하루씩) + 현재 사용자 아바타.
 *
 * 아바타를 누르면 사용자 이름과 로그아웃이 나온다. 디자인 브리프 2. 에는 아바타만 있고
 * 로그아웃 수단이 없는데, 그러면 한 기기에서 사람을 바꿀 방법이 화면에 없다 (BACKLOG §3-3).
 */
/**
 * 날짜 선택기가 뜻을 갖는 화면에서만 보여 준다.
 *
 * <p>업무 일지 작성은 기간 목록이고, 설정은 날짜와 무관하다. 거기서 날짜를 넘기면
 * 아무 일도 일어나지 않아 고장처럼 보인다.
 */
const DATELESS = ['/drafts', '/settings', '/admin']

export default function TopBar() {
  const { date, prev, next } = useSelectedDate()
  const { data: me } = useMe()
  const { pathname } = useLocation()
  // /drafts/:id 편집 화면은 그 초안의 날짜가 따로 있어 선택기가 필요 없다.
  const showDate = !DATELESS.some((p) => pathname === p || pathname.startsWith(p + '/'))

  return (
    <header className="flex h-[57px] shrink-0 items-center justify-between border-b bg-background px-6">
      <div className={cn('flex items-center gap-2', !showDate && 'invisible')}>
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

      <DropdownMenu>
        <DropdownMenuTrigger
          className="rounded-full outline-none ring-offset-background focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          aria-label="내 계정"
        >
          <UserAvatar name={me?.name} login={me?.login} avatarUrl={me?.avatarUrl} />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="min-w-[180px]">
          <DropdownMenuLabel className="font-normal">
            <div className="text-[13px] font-medium leading-tight">{me?.name ?? me?.login ?? '—'}</div>
            {me?.login ? (
              <div className="mt-0.5 text-xs font-normal text-muted-foreground">@{me.login}</div>
            ) : null}
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem onSelect={() => auth.logout()}>
            <LogOut />
            로그아웃
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}
