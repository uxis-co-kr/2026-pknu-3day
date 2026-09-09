import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import TopBar from './TopBar'

/** 사이드바 241 + 본문. 콘텐츠는 최대 1120 폭으로 가운데 정렬한다 (아트보드 2 기준). */
export default function AppLayout() {
  return (
    <div className="flex h-screen bg-background">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <main className="flex-1 overflow-y-auto bg-muted/40">
          <div className="mx-auto w-full max-w-[1200px] px-10 py-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
