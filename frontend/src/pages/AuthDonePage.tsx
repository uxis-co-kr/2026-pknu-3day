import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { auth } from '@/api/apiClient'

/**
 * GitHub OAuth 콜백 착지점. 백엔드가 `${FRONTEND_URL}/auth/done?token=<jwt>` 로 보낸다
 * (docs/HANDOFF_day1_integration.md 1.). 토큰만 저장하고 홈으로 넘긴다.
 */
export default function AuthDonePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const token = params.get('token')
    if (token) {
      auth.save(token)
      navigate('/', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }, [params, navigate])

  return <div className="p-6 text-muted-foreground">로그인 처리 중…</div>
}
