import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import { useMe } from '@/api/hooks'
import { cn } from '@/lib/utils'

/**
 * 관리자 콘솔 껍데기 (TODO_0910 §1-3).
 *
 * 서비스 화면(AppLayout)과 <b>같은 양식</b>을 쓴다 — 사이드바 241 + 상단 바 57,
 * 아이콘 없이 글자만 쓰는 메뉴, 본문 최대 1200 폭. 회의에서 "또 다른 페이지"로 정해진 것은
 * 메뉴 구성이 다르다는 뜻이지 다른 디자인을 쓰라는 뜻이 아니어서, 같은 제품으로 보이도록
 * 맞췄다. 관리자 화면임은 로고 옆 배지와 "서비스로 돌아가기" 로만 구분한다.
 */
const item = (active: boolean) =>
  cn(
    'flex h-[33px] items-center rounded-md px-2.5 text-sm transition-colors',
    active ? 'bg-primary/10 font-medium text-primary' : 'text-foreground hover:bg-muted',
  )

export default function AdminShell() {
  const navigate = useNavigate()
  const { data: me } = useMe()

  return (
    <div className="flex h-screen bg-background">
      <aside className="flex w-[241px] shrink-0 flex-col border-r bg-background">
        <div className="flex h-[66px] items-center gap-2.5 px-[22px]">
          <span className="flex size-[26px] items-center justify-center rounded-md bg-primary text-[13px] font-bold text-primary-foreground">
            W
          </span>
          <span className="text-sm font-semibold">WorkLog Drafter</span>
        </div>

        <div className="px-3 pb-2">
          <span className="inline-flex h-[22px] items-center rounded bg-muted px-2 text-[11px] font-medium text-muted-foreground">
            관리자 콘솔
          </span>
        </div>

        <nav className="flex flex-col gap-0.5 px-3">
          <NavLink to="/admin" end className={({ isActive }) => item(isActive)}>개요</NavLink>
          <NavLink to="/admin/people" className={({ isActive }) => item(isActive)}>직원 · 계정</NavLink>
          <NavLink to="/admin/activity" className={({ isActive }) => item(isActive)}>팀원 내역</NavLink>
          <NavLink to="/admin/llm" className={({ isActive }) => item(isActive)}>LLM 모델</NavLink>
          <NavLink to="/admin/notify" className={({ isActive }) => item(isActive)}>Mattermost</NavLink>
        </nav>

        <div className="mt-auto px-3 pb-3">
          <Button
            variant="ghost"
            size="sm"
            className="h-[33px] w-full justify-start px-2.5 text-sm font-normal text-muted-foreground"
            onClick={() => navigate('/')}
          >
            서비스로 돌아가기
          </Button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-[57px] shrink-0 items-center justify-between border-b bg-background px-6">
          <span className="text-[13px] font-medium text-muted-foreground">
            팀 전체 설정과 현황을 관리합니다
          </span>
          <UserAvatar name={me?.name} login={me?.login} avatarUrl={me?.avatarUrl} />
        </header>

        <main className="flex-1 overflow-y-auto bg-muted/40">
          <div className="mx-auto w-full max-w-[1200px] px-10 py-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
