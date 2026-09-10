import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { auth } from '@/api/apiClient'

/**
 * GitHub OAuth 콜백 착지점. 백엔드가 `${FRONTEND_URL}/auth/done?token=<jwt>` 로 보낸다
 * (docs/HANDOFF_day1_integration.md 1.).
 *
 * <p>`auth.save` 가 아니라 `signIn` 을 쓴다. save 만 부르면 이전 로그인이 남긴
 * mustChangePassword 플래그가 그대로 있어, RequireAuth 가 어느 화면을 열어도
 * /password 로 되돌려 보낸다. GitHub 으로 들어온 계정은 비밀번호를 바꿀 것이 없다.
 */
export default function AuthDonePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const token = params.get('token')
    if (token) {
      auth.signIn(token, false)
      navigate('/', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }, [params, navigate])

  return <div className="p-6 text-muted-foreground">로그인 처리 중…</div>
}
