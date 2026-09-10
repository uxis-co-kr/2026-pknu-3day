import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { auth } from '@/api/apiClient'
import { useMe } from '@/api/hooks'

/**
 * 목업 모드에서도 로그인 화면을 지나게 한다. 9/10 회의로 로그인이 사원번호·비밀번호가
 * 되면서 로그인 자체가 눌러 볼 화면이 됐기 때문이다.
 *
 * <p>비밀번호 변경은 **강제하지 않는다** (9/10 결정). 최초 비밀번호가 사원번호라 비밀이
 * 아니지만, 막지 않고 설정에서 안내만 한다.
 *
 * <p>담당자 2 가 서버(GET /me)가 정하는 강제 흐름을 넣었는데, 그 사이 "강제하지 않는다" 로
 * 정해져 여기서는 쓰지 않는다. 서버의 mustChangePassword 는 설정 화면의 안내 조건으로만 쓴다.
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


  return <Outlet />
}
