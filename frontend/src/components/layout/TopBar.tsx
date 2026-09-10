import { LogOut } from 'lucide-react'
import UserAvatar from '@/components/common/UserAvatar'
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

/**
 * 상단 바 — 로고 줄과 내 계정.
 *
 * <p>날짜 선택기는 깃허브 내역·VSCode 내역의 필터 줄로 내려갔다 (9/10 결정). 날짜와 무관한
 * 화면에서도 늘 떠 있어 자리만 차지했기 때문이다.
 */
export default function TopBar() {
  const { data: me } = useMe()

  return (
    <header className="flex h-[57px] shrink-0 items-center justify-end border-b bg-background px-6">

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
