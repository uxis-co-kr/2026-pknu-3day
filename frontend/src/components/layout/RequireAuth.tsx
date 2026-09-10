import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { auth } from '@/api/apiClient'

/**
 * 목업 모드에서도 로그인 화면을 지나게 한다. 9/10 회의로 로그인이 사원번호·비밀번호가
 * 되면서 로그인 자체가 눌러 볼 화면이 됐기 때문이다.
 *
 * <p>비밀번호를 아직 바꾸지 않은 계정은 /password 밖으로 나갈 수 없다. 최초 비밀번호가
 * 사원번호라 비밀이 아니기 때문이다 (TODO_0910 §1-1).
 */
export default function RequireAuth() {
  const location = useLocation()

  // 목업에서도 토큰을 요구한다. 로그인 화면이 넣어 준다.
  if (!auth.token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (auth.mustChangePassword && location.pathname !== '/password') {
    return <Navigate to="/password" replace />
  }

  return <Outlet />
}
