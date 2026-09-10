import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, auth } from '@/api/apiClient'
import { useLogin } from '@/api/hooks'
import { cn } from '@/lib/utils'

/**
 * 로그인 — 사원번호와 비밀번호 (9/10 회의).
 *
 * <p>GitHub 로그인은 없어졌다. GitHub 은 설정에서 **내 계정에 붙이는 연동 수단**이 된다.
 * <p>화면 아래에 관리자 콘솔 진입을 둔다. 같은 폼에서 아이디만 `admin` 으로 바뀐다.
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const login = useLogin()

  const [admin, setAdmin] = useState(false)
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const res = await login.mutateAsync({ loginId: loginId.trim(), password })
      auth.signIn(res.token, res.mustChangePassword)
      // 비밀번호를 아직 안 바꿨으면 그 화면부터. RequireAuth 가 다른 경로를 막는다.
      if (res.mustChangePassword) navigate('/password', { replace: true })
      else navigate(res.role === 'ADMIN' ? '/admin' : '/github', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
    }
  }

  function switchMode(next: boolean) {
    setAdmin(next)
    setLoginId(next ? 'admin' : '')
    setPassword('')
    setError(null)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 py-10">
      <div className="w-[474px] space-y-3">
        <Card className="rounded-lg px-[37px] py-10 shadow-sm">
          <div className="text-center">
            <span className={cn(
              'mx-auto flex size-10 items-center justify-center rounded-lg text-[18px] font-bold text-primary-foreground',
              admin ? 'bg-foreground' : 'bg-primary',
            )}>
              {admin ? <ShieldCheck className="size-5" /> : 'W'}
            </span>
            <h1 className="mt-6 text-[18px] font-semibold">
              {admin ? '관리자 콘솔' : 'WorkLog Drafter'}
            </h1>
            <p className="mt-2 text-[13px] text-muted-foreground">
              {admin
                ? '팀원 내역·LLM 모델·알림을 관리합니다'
                : 'GitHub 활동과 VS Code 작업으로 업무 일지 초안을 만듭니다'}
            </p>
          </div>

          <form className="mt-7 space-y-4" onSubmit={(e) => void onSubmit(e)}>
            <div className="space-y-1.5">
              <Label htmlFor="loginId" className="text-[13px]">
                {admin ? '관리자 아이디' : '사원번호'}
              </Label>
              <Input
                id="loginId"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                placeholder={admin ? 'admin' : '0042'}
                autoComplete="username"
                className="h-10"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password" className="text-[13px]">비밀번호</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                className="h-10"
              />
              {!admin && (
                <p className="text-[12px] text-muted-foreground">
                  처음 로그인한다면 비밀번호는 사원번호와 같습니다
                </p>
              )}
            </div>

            {error && <p className="text-[13px] text-status-failed">{error}</p>}

            <Button type="submit" className="h-10 w-full" disabled={login.isPending || !loginId || !password}>
              {login.isPending ? '확인 중…' : '로그인'}
            </Button>
          </form>
        </Card>

        {/*
          * 사원번호 로그인 서버(POST /auth/login)가 아직 없다. 실서버 모드에서 목업 토큰으로
          * 들어가면 다른 API 가 401 을 돌려주므로, 그때까지 GitHub 로그인 길을 남겨 둔다.
          * 담당자 2 의 로그인 API 가 붙으면 이 버튼은 지운다 (TODO_0910 §1-1).
          */}
        {!admin && (
          <button
            type="button"
            onClick={() => auth.startGithubLogin()}
            className="w-full rounded-lg border border-dashed bg-background py-2.5 text-[12px] text-muted-foreground transition-colors hover:bg-muted"
          >
            임시 — GitHub 로 로그인 (사원번호 로그인 서버가 붙기 전까지)
          </button>
        )}

        {/* 회의: "로그인화면 아래에다가 관리자 콘솔" */}
        <button
          type="button"
          onClick={() => switchMode(!admin)}
          className="flex w-full items-center justify-center gap-1.5 rounded-lg border bg-background py-3 text-[13px] text-muted-foreground transition-colors hover:bg-muted"
        >
          {admin ? '← 일반 로그인으로' : <><ShieldCheck className="size-3.5" /> 관리자 콘솔</>}
        </button>
      </div>
    </div>
  )
}
