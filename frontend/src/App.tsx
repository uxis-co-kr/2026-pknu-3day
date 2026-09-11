import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import RequireAuth from '@/components/layout/RequireAuth'
import AuthDonePage from '@/pages/AuthDonePage'
import LoginPage from '@/pages/LoginPage'
import GithubPage from '@/pages/GithubPage'
import PasswordPage from '@/pages/PasswordPage'
import SettingsPage from '@/pages/SettingsPage'
import VscodePage from '@/pages/VscodePage'

// recharts 와 react-markdown 은 이 두 화면에서만 쓴다. 첫 로딩에서 떼어 낸다.
const DraftEditorPage = lazy(() => import('@/pages/DraftEditorPage'))
const DraftsPage = lazy(() => import('@/pages/DraftsPage'))
// 메뉴에서는 빠졌지만 경로는 남긴다 — 관리자 콘솔의 "팀원 전체 내역" 이 이 화면을 재사용한다.
const PeoplePage = lazy(() => import('@/pages/PeoplePage'))

// 관리자 콘솔 (TODO_0910 1-3, 담당자 2). 서비스 화면과 레이아웃이 다르고
// 관리자만 들어가므로 통째로 떼어 낸다.
const AdminShell = lazy(() => import('@/pages/admin/AdminShell'))
const AdminOverviewPage = lazy(() => import('@/pages/admin/AdminOverviewPage'))
const AdminPeoplePage = lazy(() => import('@/pages/admin/AdminPeoplePage'))
const AdminDraftsPage = lazy(() => import('@/pages/admin/AdminDraftsPage'))
const AdminRepoDraftsPage = lazy(() => import('@/pages/admin/AdminRepoDraftsPage'))
const AdminActivityPage = lazy(() => import('@/pages/admin/AdminActivityPage'))
const AdminLlmPage = lazy(() => import('@/pages/admin/AdminLlmPage'))
const AdminNotifyPage = lazy(() => import('@/pages/admin/AdminNotifyPage'))

/**
 * 9/10 회의에서 정한 메뉴 넷 — 깃허브 내역 · VSCode 내역 · 업무 일지 작성 · 설정.
 *
 * <p>이전 경로는 그대로 두지 않고 새 자리로 보낸다. 북마크나 화면 안의 오래된 링크가
 * 빈 화면으로 떨어지지 않게 하기 위해서다.
 * <p>인원별 이력(/people)은 메뉴에서 빠졌지만 경로는 남긴다. 관리자 콘솔의 "팀원 전체
 * 내역" 이 이 화면을 재사용할 수 있다 (담당자 2).
 */
export default function App() {
  return (
    <Suspense fallback={<div className="p-6 text-muted-foreground">불러오는 중…</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/done" element={<AuthDonePage />} />
        <Route element={<RequireAuth />}>
          {/* 비밀번호 변경 강제 화면은 사이드바 없이 단독으로 뜬다. */}
          <Route path="/password" element={<PasswordPage />} />
            <Route element={<AppLayout />}>
          <Route path="/github" element={<GithubPage />} />
          <Route path="/vscode" element={<VscodePage />} />
          <Route path="/drafts" element={<DraftsPage />} />
          <Route path="/drafts/:id" element={<DraftEditorPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          {/* 옛 경로 → 새 자리 */}
          <Route path="/" element={<Navigate to="/github" replace />} />
          <Route path="/repos" element={<Navigate to="/settings" replace />} />
          <Route path="/people" element={<PeoplePage />} />
        </Route>

          {/* 관리자 콘솔 — AppLayout 을 쓰지 않는 별도 화면 (TODO_0910 1-3).
              담당자 1 이 만들어 둔 뼈대(AdminPage)를 이 콘솔이 대신한다. */}
          <Route path="/admin" element={<AdminShell />}>
            <Route index element={<AdminOverviewPage />} />
            <Route path="people" element={<AdminPeoplePage />} />
            <Route path="drafts" element={<AdminDraftsPage />} />
            <Route path="repo-drafts" element={<AdminRepoDraftsPage />} />
            <Route path="activity" element={<AdminActivityPage />} />
            <Route path="llm" element={<AdminLlmPage />} />
            <Route path="notify" element={<AdminNotifyPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/github" replace />} />
      </Routes>
    </Suspense>
  )
}
