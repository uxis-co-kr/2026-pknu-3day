import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { auth } from '@/api/apiClient'
import { useMe } from '@/api/hooks'

/**
 * 목업 모드에서도 로그인 화면을 지나게 한다. 9/10 회의로 로그인이 사원번호·비밀번호가
 * 되면서 로그인 자체가 눌러 볼 화면이 됐기 때문이다.
 *
 * <p>비밀번호를 아직 바꾸지 않은 계정은 /password 밖으로 나갈 수 없다. 최초 비밀번호가
 * 사원번호라 비밀이 아니기 때문이다 (TODO_0910 §1-1).
 *
 * <p>막을지 말지는 <b>서버(GET /me)</b> 가 정한다. 브라우저에 남은 표시만 믿으면, 이미 바꾼
 * 계정이나 애초에 바꿀 필요가 없는 계정(관리자)이 옛 값 때문에 변경 화면에 갇힌다.
 * 표시는 /me 가 오기 전 한 순간의 추측으로만 쓴다.
 */
export default function RequireAuth() {
  const location = useLocation()
  const { data: me } = useMe()

  // 서버가 "바꿀 필요 없다" 고 하면 남아 있던 표시를 지운다.
  useEffect(() => {
    if (me && !me.mustChangePassword) auth.clearMustChangePassword()
  }, [me])

  // 목업에서도 토큰을 요구한다. 로그인 화면이 넣어 준다.
  if (!auth.token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  const mustChange = me ? me.mustChangePassword === true : auth.mustChangePassword
  if (mustChange && location.pathname !== '/password') {
    return <Navigate to="/password" replace />
  }

  return <Outlet />
}
