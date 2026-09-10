import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, auth } from '@/api/apiClient'
import { useChangePassword, useMe } from '@/api/hooks'

/**
 * 비밀번호 변경 — 최초 로그인이면 여기를 지나야 다른 화면으로 갈 수 있다.
 *
 * <p>최초 비밀번호가 사원번호인데, 사원번호는 사원 목록 API 로 누구나 조회할 수 있다.
 * 바꾸기 전까지는 비밀이 아니므로 통과를 막는다 (TODO_0910 §1-1).
 */
export default function PasswordPage() {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const change = useChangePassword()

  // 강제 여부는 서버가 정한다. 브라우저에 남은 표시만 믿으면 바꿀 필요가 없는 계정에도 뜬다.
  const forced = me ? me.mustChangePassword === true : auth.mustChangePassword
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
      await change.mutateAsync({ currentPassword: current, newPassword: next })
      auth.clearMustChangePassword()
      if (forced) navigate(me?.role === 'ADMIN' ? '/admin' : '/github', { replace: true })
      else {
        setDone(true)
        setCurrent(''); setNext(''); setConfirm('')
      }
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

  // 최초 로그인 강제일 때는 사이드바 없이 이 화면만 보여 준다.
  if (forced) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-muted/40 py-10">
        <Card className="w-[474px] rounded-lg px-[37px] py-10 shadow-sm">
          <div className="text-center">
            <span className="mx-auto flex size-10 items-center justify-center rounded-lg bg-status-uncommitted/15 text-status-uncommitted">
              <KeyRound className="size-5" />
            </span>
            <h1 className="mt-6 text-[18px] font-semibold">비밀번호를 바꿔 주세요</h1>
            <p className="mt-2 text-[13px] text-muted-foreground">
              최초 비밀번호는 사원번호와 같습니다. 새 비밀번호를 정하면
              이 화면은 다시 나오지 않습니다.
            </p>
          </div>
          <div className="mt-7">{form}</div>
        </Card>
      </div>
    )
  }

  return (
    <Card className="max-w-[520px] rounded-lg p-6 shadow-none">
      <h2 className="text-sm font-semibold">비밀번호 변경</h2>
      <p className="mb-4 mt-1 text-[13px] text-muted-foreground">
        {me?.loginId ? `사원번호 ${me.loginId}` : '내 계정'}의 비밀번호를 바꿉니다.
      </p>
      {form}
    </Card>
  )
}
