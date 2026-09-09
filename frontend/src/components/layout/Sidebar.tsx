import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useDrafts, useMe } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { cn } from '@/lib/utils'

const item = (active: boolean) =>
  cn(
    'flex h-[33px] items-center rounded-md px-2.5 text-sm transition-colors',
    active ? 'bg-primary/10 font-medium text-primary' : 'text-foreground hover:bg-muted',
  )

/**
 * 좌측 고정 사이드바 — 로고 + 5개 메뉴 (디자인 브리프 2.). 아이콘 없이 글자만 쓴다.
 * "초안" 은 목록 화면이 없으므로 오늘 내 초안으로 보낸다 (App.tsx 주석 참고).
 */
export default function Sidebar() {
  const location = useLocation()
  const navigate = useNavigate()
  const { date } = useSelectedDate()
  const { data: me } = useMe()
  const { data: drafts } = useDrafts({ date, userId: me?.id })
  const myDraft = drafts?.[0]
  const onDraftPage = location.pathname.startsWith('/drafts')

  return (
    <aside className="flex w-[241px] shrink-0 flex-col border-r bg-background">
      <div className="flex h-[66px] items-center gap-2.5 px-[22px]">
        <span className="flex size-[26px] items-center justify-center rounded-md bg-primary text-[13px] font-bold text-primary-foreground">
          W
        </span>
        <span className="text-sm font-semibold">WorkLog Drafter</span>
      </div>

      <nav className="flex flex-col gap-0.5 px-3">
        <NavLink to="/" end className={({ isActive }) => item(isActive)}>홈</NavLink>
        <button
          type="button"
          disabled={!myDraft}
          onClick={() => myDraft && navigate(`/drafts/${myDraft.id}`)}
          className={cn(item(onDraftPage), 'text-left disabled:cursor-not-allowed disabled:opacity-40')}
          title={myDraft ? undefined : '이 날짜에 내 초안이 없습니다'}
        >
          초안
        </button>
        <NavLink to="/repos" className={({ isActive }) => item(isActive)}>리포</NavLink>
        <NavLink to="/people" className={({ isActive }) => item(isActive)}>인원</NavLink>
        <NavLink to="/settings" className={({ isActive }) => item(isActive)}>설정</NavLink>
      </nav>
    </aside>
  )
}
