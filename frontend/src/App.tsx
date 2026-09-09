import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import RequireAuth from '@/components/layout/RequireAuth'
import AuthDonePage from '@/pages/AuthDonePage'
import LoginPage from '@/pages/LoginPage'
import HomePage from '@/pages/HomePage'
import ReposPage from '@/pages/ReposPage'
import SettingsPage from '@/pages/SettingsPage'

// recharts 와 react-markdown 은 이 두 화면에서만 쓴다. 첫 로딩에서 떼어 낸다.
const DraftEditorPage = lazy(() => import('@/pages/DraftEditorPage'))
const PeoplePage = lazy(() => import('@/pages/PeoplePage'))

/**
 * 디자인 브리프 3. 의 6개 화면. 여기에 없는 페이지는 추가하지 않는다.
 *
 * 사이드바 "초안" 메뉴: 초안 목록 화면은 기획하지 않았으므로 새로 만들지 않는다.
 * 클릭하면 오늘 내 초안(/drafts/:id)으로 보내고, 오늘 초안이 없으면 홈에 머문다.
 * (아트보드 3·4 에서 /drafts/:id 일 때 이 메뉴가 활성으로 그려져 있다.)
 */
export default function App() {
  return (
    <Suspense fallback={<div className="p-6 text-muted-foreground">불러오는 중…</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/done" element={<AuthDonePage />} />
        <Route element={<RequireAuth />}>
            <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/drafts/:id" element={<DraftEditorPage />} />
          <Route path="/repos" element={<ReposPage />} />
          <Route path="/people" element={<PeoplePage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
