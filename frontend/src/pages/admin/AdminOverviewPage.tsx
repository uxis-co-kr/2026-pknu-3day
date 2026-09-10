import { Link } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, GitBranch, Users2 } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import AdminGuard from './AdminGuard'
import { useAdminOverview } from './api'

/** 값 하나짜리 카드. 화면이 무엇을 보여 줄 수 있는 상태인지 한눈에 알리는 용도다. */
function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

/** 설정이 됐는지 안 됐는지를 문장으로 알리는 줄. 안 된 것은 무엇을 하면 되는지까지 적는다. */
function Readiness({
  ok,
  title,
  okText,
  todoText,
  to,
}: {
  ok: boolean
  title: string
  okText: string
  todoText: string
  to?: string
}) {
  return (
    <div className="flex items-start gap-3 py-3">
      {ok ? (
        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
      ) : (
        <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-500" />
      )}
      <div className="min-w-0">
        <p className="text-[13px] font-medium">{title}</p>
        <p className="mt-0.5 text-[13px] text-muted-foreground">{ok ? okText : todoText}</p>
      </div>
      {!ok && to && (
        <Link to={to} className="ml-auto shrink-0 text-sm text-primary hover:underline">
          설정하기
        </Link>
      )}
    </div>
  )
}

export default function AdminOverviewPage() {
  const { data, isLoading, error } = useAdminOverview()

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">개요</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            {data ? `${data.adminLogin} 님으로 접속했습니다.` : '팀 전체 상태를 봅니다.'}
          </p>
        </div>

        <div className="grid grid-cols-3 gap-3">
          {isLoading || !data ? (
            <>
              <Skeleton className="h-[92px]" />
              <Skeleton className="h-[92px]" />
              <Skeleton className="h-[92px]" />
            </>
          ) : (
            <>
              <Stat label="서비스 계정" value={data.accountCount} hint="로그인한 적이 있는 사람" />
              <Stat
                label="사내 직원"
                value={data.employeeCount}
                hint={
                  data.wapleConfigured
                    ? '회원 조회 API 기준'
                    : data.employeeCount > 0
                      ? '임시 목록 (API 미설정)'
                      : '회원 조회 API 미설정'
                }
              />
              <Stat
                label="로그인 안 한 기여자"
                value={data.unclaimedContributorCount}
                hint="커밋은 있으나 이 서비스를 쓴 적 없는 GitHub 계정"
              />
            </>
          )}
        </div>

        <Card>
          <CardContent className="divide-y p-4">
            <p className="pb-2 text-sm font-medium">연동 상태</p>
            {isLoading || !data ? (
              <Skeleton className="my-3 h-12" />
            ) : (
              <>
                <Readiness
                  ok={data.wapleConfigured}
                  title="사내 회원 조회 API"
                  okText="직원 목록을 불러올 수 있습니다."
                  todoText={
                    data.employeeCount > 0
                      ? `아직 설정되지 않아 임시 목록(${data.employeeCount}명)을 쓰고 있습니다. 실제 API가 붙으면 자동으로 전환됩니다.`
                      : 'backend/.env 의 WAPLE_API_BASE_URL · WAPLE_API_KEY 가 비어 있습니다. 직원 목록만 비고 나머지는 정상 동작합니다.'
                  }
                />
                <Readiness
                  ok={data.globalWebhookConfigured}
                  title="Mattermost 웹훅 (전역)"
                  okText="초안 생성·확정본·미커밋 리마인드가 채널로 갑니다."
                  todoText="웹훅 주소가 없어 알림이 전송되지 않습니다."
                  to="/admin/notify"
                />
                <Readiness
                  ok
                  title="요약 모델"
                  okText={`현재 ${data.llmProvider} 로 요약합니다.`}
                  todoText=""
                  to="/admin/llm"
                />
              </>
            )}
          </CardContent>
        </Card>

        <div className="grid grid-cols-2 gap-3">
          <Link to="/admin/people">
            <Card className="transition-colors hover:border-primary/40">
              <CardContent className="flex items-center gap-3 p-4">
                <Users2 className="size-5 text-muted-foreground" />
                <div>
                  <p className="text-[13px] font-medium">직원 · 계정</p>
                  <p className="text-[13px] text-muted-foreground">
                    누가 GitHub 을 연결했는지, 누가 빠져 있는지
                  </p>
                </div>
              </CardContent>
            </Card>
          </Link>
          <Link to="/admin/people#contributors">
            <Card className="transition-colors hover:border-primary/40">
              <CardContent className="flex items-center gap-3 p-4">
                <GitBranch className="size-5 text-muted-foreground" />
                <div>
                  <p className="text-[13px] font-medium">로그인 안 한 기여자</p>
                  <p className="text-[13px] text-muted-foreground">
                    커밋은 올라오는데 누구인지 알 수 없는 계정
                  </p>
                </div>
              </CardContent>
            </Card>
          </Link>
        </div>
      </div>
    </AdminGuard>
  )
}
