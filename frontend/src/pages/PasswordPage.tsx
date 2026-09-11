import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, auth } from '@/api/apiClient'
import { useChangePassword, useMe } from '@/api/hooks'

/**
 * 비밀번호 변경 — 설정 안의 카드.
 *
 * <p>최초 비밀번호가 사원번호인데, 사원번호는 사원 목록 API 로 누구나 조회할 수 있다.
 * 그래도 **변경을 강제하지는 않는다** (9/10 결정). 아직 최초 비밀번호를 쓰고 있으면
 * 한 줄로 알리기만 한다.
 */
export default function PasswordPage() {
  const { data: me } = useMe()
  const change = useChangePassword()

  // 서버(GET /me)가 답이다. 브라우저에 남은 표시만 믿으면 이미 바꾼 계정에도 안내가 뜬다.
  // 강제하지는 않는다 (9/10 결정) — 안내 문구의 조건으로만 쓴다.
  const usingInitial = me ? me.mustChangePassword === true : auth.mustChangePassword
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const mismatch = confirm.length > 0 && next !== confirm

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (next !== confirm) return
    try {
      const res = await change.mutateAsync({ currentPassword: current, newPassword: next })
      // 담당자 2 (9/11): 이전 토큰은 방금 죽었다. 새 토큰으로 갈아 끼워야 다음 요청이 401 이 안 난다.
      if (res?.token) auth.save(res.token)
      auth.clearMustChangePassword()
      setDone(true)
      setCurrent(''); setNext(''); setConfirm('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '변경에 실패했습니다.')
    }
  }

  const form = (
    <form className="space-y-4" onSubmit={(e) => void onSubmit(e)}>
      <div className="space-y-1.5">
        <Label htmlFor="current" className="text-[13px]">현재 비밀번호</Label>
        <Input id="current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)}
          autoComplete="current-password" className="h-10" />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="next" className="text-[13px]">새 비밀번호</Label>
        <Input id="next" type="password" value={next} onChange={(e) => setNext(e.target.value)}
          autoComplete="new-password" className="h-10" />
        <p className="text-[12px] text-muted-foreground">4자 이상. 사원번호와 같은 값은 쓸 수 없습니다</p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="confirm" className="text-[13px]">새 비밀번호 확인</Label>
        <Input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)}
          autoComplete="new-password" className="h-10" />
        {mismatch && <p className="text-[12px] text-status-failed">두 값이 다릅니다</p>}
      </div>

      {error && <p className="text-[13px] text-status-failed">{error}</p>}
      {done && <p className="text-[13px] text-status-confirmed">비밀번호를 바꿨습니다.</p>}

      <Button type="submit" className="h-10 w-full"
        disabled={change.isPending || !current || !next || mismatch}>
        {change.isPending ? '변경 중…' : '비밀번호 변경'}
      </Button>
    </form>
  )

  // 설정의 Section 이 이미 카드다. 여기서 또 감싸면 상자가 겹친다.
  return (
    <div className="max-w-[520px]">
      <p className="mb-4 text-[13px] text-muted-foreground">
        {usingInitial
          ? '아직 최초 비밀번호(사원번호)를 쓰고 있습니다. 사원번호는 사원 목록에서 조회할 수 있으니 바꾸는 편이 안전합니다.'
          : `${me?.loginId ? `사원번호 ${me.loginId}` : '내 계정'}의 비밀번호를 바꿉니다.`}
      </p>
      {form}
    </div>
  )
}
