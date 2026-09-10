import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const item = (active: boolean) =>
  cn(
    'flex h-[33px] items-center rounded-md px-2.5 text-sm transition-colors',
    active ? 'bg-primary/10 font-medium text-primary' : 'text-foreground hover:bg-muted',
  )

/**
 * 좌측 고정 사이드바 — 9/10 회의에서 정한 메뉴 넷.
 *
 * <p>"홈" 은 깃허브 내역으로 갈렸고, "리포" 는 설정 안으로 들어갔다. "인원" 은 관리자
 * 콘솔의 팀원 전체 내역으로 흡수된다 (담당자 2). 초안 편집(/drafts/:id)은 초안 작성에서 연다.
 */
const MENU = [
  { to: '/github', label: '깃허브 내역' },
  { to: '/vscode', label: 'VSCode 내역' },
  { to: '/drafts', label: '업무 일지 작성' },
  { to: '/settings', label: '설정' },
]

export default function Sidebar() {
  return (
    <aside className="flex w-[241px] shrink-0 flex-col border-r bg-background">
      <div className="flex h-[66px] items-center gap-2.5 px-[22px]">
        <span className="flex size-[26px] items-center justify-center rounded-md bg-primary text-[13px] font-bold text-primary-foreground">
          W
        </span>
        <span className="text-sm font-semibold">WorkLog Drafter</span>
      </div>

      <nav className="flex flex-col gap-0.5 px-3">
        {MENU.map((m) => (
          <NavLink key={m.to} to={m.to} className={({ isActive }) => item(isActive)}>
            {m.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}
