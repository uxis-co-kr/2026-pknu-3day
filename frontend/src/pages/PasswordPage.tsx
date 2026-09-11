import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, auth } from '@/api/apiClient'
import { cn } from '@/lib/utils'
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
    /*
     * 세 칸을 가로로 편다.
     *
     * <p>예전에는 좁은 한 줄(520px)에 세로로 쌓았다. 설정의 다른 단락은 카드 너비를 다 쓰는데
     * 여기만 왼쪽 절반이 차고 오른쪽이 비어, 덜 만든 화면처럼 보였다. 그렇다고 입력칸을 카드
     * 너비만큼 늘이면 여덟 글자 적을 자리가 1000px 이 된다 — 칸 길이는 적을 내용의 길이를
     * 알려 주는 신호라, 길면 긴 값을 기대하게 만든다.
     *
     * <p>좁은 화면에서는 한 칸씩 쌓인다.
     */
    <form className="space-y-4" onSubmit={(e) => void onSubmit(e)}>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field id="current" label="현재 비밀번호" value={current} onChange={setCurrent}
          autoComplete="current-password" />
        <Field id="next" label="새 비밀번호" value={next} onChange={setNext}
          autoComplete="new-password" hint="4자 이상. 사원번호와 같은 값은 쓸 수 없습니다" />
        <Field id="confirm" label="새 비밀번호 확인" value={confirm} onChange={setConfirm}
          autoComplete="new-password"
          hint={mismatch ? '두 값이 다릅니다' : undefined} hintTone={mismatch ? 'error' : undefined} />
      </div>

      {/* 결과 문구와 버튼을 한 줄에 둔다 — 버튼만 있는 줄이 하나 더 생기지 않게. */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-[13px]">
          {error && <span className="text-status-failed">{error}</span>}
          {done && <span className="text-status-confirmed">비밀번호를 바꿨습니다.</span>}
        </p>
        <Button type="submit" className="h-[34px] w-full sm:ml-auto sm:w-auto sm:min-w-[132px]"
          disabled={change.isPending || !current || !next || mismatch}>
          {change.isPending ? '변경 중…' : '비밀번호 변경'}
        </Button>
      </div>
    </form>
  )

  // 설정의 Section 이 이미 카드다. 여기서 또 감싸면 상자가 겹친다.
  return (
    <div>
      {/* 아래 입력 줄과 같은 너비를 쓴다. 폭을 좁혀 두 줄로 접으면 한 문장이 토막 나 보인다. */}
      <p className="mb-4 text-[13px] text-muted-foreground">
        {usingInitial
          ? '아직 최초 비밀번호(사원번호)를 쓰고 있습니다. 사원번호는 사원 목록에서 조회할 수 있으니 바꾸는 편이 안전합니다.'
          : `${me?.loginId ? `사원번호 ${me.loginId}` : '내 계정'}의 비밀번호를 바꿉니다.`}
      </p>
      {form}
    </div>
  )
}

/**
 * 입력 한 칸. 라벨·입력·도움말이 한 묶음이다.
 *
 * <p>도움말 자리는 비어 있어도 남겨 둔다. "두 값이 다릅니다" 가 떴다 사라질 때마다 세 칸의
 * 높이가 들썩이면 눈이 쫓아가지 못한다.
 */
function Field({ id, label, value, onChange, autoComplete, hint, hintTone }: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  autoComplete: string
  hint?: string
  hintTone?: 'error'
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="text-[13px]">{label}</Label>
      <Input id={id} type="password" value={value} onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete} className="h-[34px] text-[13px]" />
      <p className={cn('min-h-[16px] text-[12px]',
        hintTone === 'error' ? 'text-status-failed' : 'text-muted-foreground')}>
        {hint}
      </p>
    </div>
  )
}
