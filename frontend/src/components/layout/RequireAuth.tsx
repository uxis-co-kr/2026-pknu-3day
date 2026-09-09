import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { USE_MOCK, auth } from '@/api/apiClient'

/**
 * 목업 모드에서는 검사하지 않는다. 1일차에는 백엔드 로그인 없이 화면을 그려야 하고,
 * 실서버로 붙는 순간(VITE_USE_MOCK=false)부터 JWT 가 없으면 /login 으로 보낸다.
 */
export default function RequireAuth() {
  const location = useLocation()
  if (!USE_MOCK && !auth.token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
