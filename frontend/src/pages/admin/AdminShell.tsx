import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { ArrowLeft, BarChart3, Bot, Send, Users2, LayoutGrid } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * 관리자 콘솔 껍데기 (TODO_0910 §1-3).
 *
 * 일반 화면의 사이드바(AppLayout)를 쓰지 않는다. 회의에서 "지금 구상한 페이지 말고
 * 또 다른 페이지"로 정해졌고, 관리 대상이 개인이 아니라 팀 전체라 다른 자리에 있다는 것이
 * 한눈에 보여야 한다. 그래서 어두운 상단 바 + 가로 탭으로 서비스 화면과 구분한다.
 */
const TABS = [
  { to: '/admin', end: true, label: '개요', icon: LayoutGrid },
  { to: '/admin/people', end: false, label: '직원 · 계정', icon: Users2 },
  { to: '/admin/activity', end: false, label: '팀원 내역', icon: BarChart3 },
  { to: '/admin/llm', end: false, label: 'LLM 모델', icon: Bot },
  { to: '/admin/notify', end: false, label: 'Mattermost', icon: Send },
]

export default function AdminShell() {
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-muted/30">
      <header className="bg-slate-900 text-slate-100">
        <div className="mx-auto flex h-14 max-w-[1200px] items-center gap-3 px-6">
          <Button
            variant="ghost"
            size="sm"
            className="h-8 gap-1.5 px-2 text-slate-300 hover:bg-slate-800 hover:text-slate-50"
            onClick={() => navigate('/')}
          >
            <ArrowLeft className="size-4" />
            서비스로
          </Button>
          <div className="h-4 w-px bg-slate-700" />
          <span className="text-sm font-semibold tracking-tight">관리자 콘솔</span>
          <span className="rounded bg-amber-500/15 px-1.5 py-0.5 text-[11px] font-medium text-amber-400">
            ADMIN
          </span>
        </div>

        <nav className="mx-auto flex max-w-[1200px] gap-1 px-6">
          {TABS.map(({ to, end, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'border-amber-400 text-slate-50'
                    : 'border-transparent text-slate-400 hover:text-slate-200',
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-[1200px] px-6 py-6">
        <Outlet />
      </main>
    </div>
  )
}
