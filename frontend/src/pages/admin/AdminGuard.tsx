import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'
import { ApiError } from '@/api/apiClient'
import { Button } from '@/components/ui/button'

/**
 * 콘솔의 403 을 사람이 읽을 수 있게 바꾼다.
 *
 * 서버가 /admin/** 전체를 ADMIN 권한으로 잠가 두었으므로 화면에서 막을 필요는 없다.
 * 다만 그냥 두면 "권한이 없습니다" 라는 오류 문구만 뜨고 왜인지 알 수 없다.
 */
export default function AdminGuard({
  error,
  children,
}: {
  error: unknown
  children: ReactNode
}) {
  const forbidden = error instanceof ApiError && error.status === 403

  if (!forbidden) return <>{children}</>

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <ShieldAlert className="size-10 text-muted-foreground" />
      <div>
        <p className="text-base font-medium">관리자만 볼 수 있는 화면입니다</p>
        <p className="mt-1 text-sm text-muted-foreground">
          로그인 화면 아래의 <b>관리자 콘솔</b>에서 관리자 계정(<code className="rounded bg-muted px-1">admin</code>)으로
          들어와 주세요. 일반 회원 계정으로는 볼 수 없습니다.
        </p>
      </div>
      <Button asChild variant="outline" size="sm">
        <Link to="/">서비스로 돌아가기</Link>
      </Button>
    </div>
  )
}
