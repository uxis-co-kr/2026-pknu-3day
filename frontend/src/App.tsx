import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import LoginPage from '@/pages/LoginPage'
import HomePage from '@/pages/HomePage'
import DraftEditorPage from '@/pages/DraftEditorPage'
import ReposPage from '@/pages/ReposPage'
import PeoplePage from '@/pages/PeoplePage'
import SettingsPage from '@/pages/SettingsPage'

/**
 * 디자인 브리프 3. 의 6개 화면. 여기에 없는 페이지는 추가하지 않는다.
 * 사이드바 "초안" 메뉴의 목적지(/drafts 목록)는 아트보드가 없어 아직 비워 둔다.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AppLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/drafts/:id" element={<DraftEditorPage />} />
        <Route path="/repos" element={<ReposPage />} />
        <Route path="/people" element={<PeoplePage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
