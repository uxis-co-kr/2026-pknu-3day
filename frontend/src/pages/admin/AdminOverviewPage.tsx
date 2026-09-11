import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, RefreshCw, Sparkles } from 'lucide-react'
import { ApiError } from '@/api/apiClient'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import AdminGuard from './AdminGuard'
import { useAdminOverview, useRunSummaries, useSyncAllRepos } from './api'

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-[13px] text-muted-foreground">{label}</p>
        <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  )
}

/** 설정이 됐는지를 문장으로 알린다. 안 된 것은 무엇을 하면 되는지까지 적는다. */
function Readiness({
  ok, title, okText, todoText, to,
}: { ok: boolean; title: string; okText: string; todoText: string; to?: string }) {
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
        <Link to={to} className="ml-auto shrink-0 text-[13px] text-primary hover:underline">
          설정하기
        </Link>
      )}
    </div>
  )
}

export default function AdminOverviewPage() {
  const { data, isLoading, error } = useAdminOverview()
  const syncAll = useSyncAllRepos()
  const runSummaries = useRunSummaries()
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  async function run(fn: () => Promise<string>) {
    setMessage(null)
    try {
      setMessage({ ok: true, text: await fn() })
    } catch (e) {
      setMessage({ ok: false, text: e instanceof ApiError ? e.message : '실패했습니다.' })
    }
  }

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">개요</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            {data ? `${data.adminLogin} 님으로 접속했습니다.` : '팀 전체 상태를 봅니다.'}
          </p>
        </div>

        {data?.defaultAdminPassword && (
          // 관리자 비밀번호가 배포 기본값 그대로다. 사원번호와 달리 설정 파일에만 있지만,
          // 저장소가 공개라 기본값은 누구나 안다 (BACKLOG2 §2-2).
          <div className="flex items-start gap-3 rounded border border-destructive/40 bg-destructive/5 p-3">
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
            <div className="min-w-0">
              <p className="text-[13px] font-medium text-destructive">관리자 비밀번호가 기본값입니다</p>
              <p className="mt-0.5 text-[13px] text-muted-foreground">
                누구나 아는 값으로 관리자 콘솔에 들어올 수 있습니다. 설정에서 바꿔 두세요.
              </p>
            </div>
            <Link to="/settings" className="ml-auto shrink-0 text-[13px] text-primary hover:underline">
              바꾸러 가기
            </Link>
          </div>
        )}

        <div className="grid grid-cols-4 gap-3">
          {isLoading || !data ? (
            <>
              <Skeleton className="h-[92px]" />
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
                hint={data.wapleConfigured ? '회원 조회 API 기준' : '임시 목록'}
              />
              <Stat label="등록 리포" value={data.repoCount} hint={`수집된 활동 ${data.activityCount}건`} />
              <Stat
                label="요약 대기"
                value={data.pendingSummaryCount}
                hint={data.failedSummaryCount > 0 ? `실패 ${data.failedSummaryCount}건` : '밀린 것 없음'}
              />
            </>
          )}
        </div>

        {/* ── 관리자가 손으로 밀어야 할 때 ── */}
        <Card>
          <CardContent className="p-4">
            <p className="text-[13px] font-medium">수동 실행</p>
            <p className="mt-0.5 text-[13px] text-muted-foreground">
              수집은 10분마다, 요약은 1분마다 자동으로 돕니다. 아래는 기다리지 않고 지금 확인할 때
              씁니다.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button
                variant="outline" size="sm" disabled={syncAll.isPending}
                onClick={() => void run(async () => {
                  const r = await syncAll.mutateAsync(false)
                  return `리포 ${r.repoCount}개의 동기화를 시작했습니다. 잠시 뒤 새로고침하세요.`
                })}
              >
                <RefreshCw className={syncAll.isPending ? 'animate-spin' : undefined} />
                전체 동기화
              </Button>
              <Button
                variant="outline" size="sm" disabled={syncAll.isPending}
                onClick={() => void run(async () => {
                  const r = await syncAll.mutateAsync(true)
                  return `리포 ${r.repoCount}개를 최근 7일까지 다시 훑습니다.`
                })}
              >
                전체 재수집 (7일)
              </Button>
              <Button
                variant="outline" size="sm" disabled={runSummaries.isPending}
                onClick={() => void run(async () => {
                  const r = await runSummaries.mutateAsync()
                  return r.summarized > 0
                    ? `${r.summarized}건을 요약했습니다.`
                    : '요약할 활동이 없습니다.'
                })}
              >
                <Sparkles />
                요약 지금 실행
              </Button>
            </div>
            {message && (
              <p className={
                message.ok
                  ? 'mt-3 rounded border border-emerald-200 bg-emerald-50 p-2.5 text-[13px] text-emerald-800'
                  : 'mt-3 rounded border border-destructive/30 bg-destructive/5 p-2.5 text-[13px] text-destructive'
              }>
                {message.text}
              </p>
            )}
            <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">
              <b>전체 재수집</b>은 마지막 동기화 시각을 무시하고 최근 7일을 다시 봅니다. 수집 대상을
              늘렸거나 빠진 커밋이 있을 때 씁니다. 이미 저장한 활동은 중복되지 않습니다.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="divide-y p-4">
            <p className="pb-2 text-[13px] font-medium">연동 상태</p>
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
                      ? `아직 설정되지 않아 임시 목록(${data.employeeCount}명)을 쓰고 있습니다.`
                      : 'backend/.env 의 WAPLE_API_BASE_URL · WAPLE_API_KEY 가 비어 있습니다.'
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
                  ok={!data.anyRepoSyncFailed}
                  title="리포 수집"
                  okText={
                    data.syncingRepoCount > 0
                      ? `${data.syncingRepoCount}개를 지금 수집하고 있습니다.`
                      : `${data.repoCount}개 리포가 정상입니다.`
                  }
                  todoText="마지막 수집이 실패한 리포가 있습니다. 등록자의 GitHub 토큰이 만료됐을 수 있습니다."
                />
                <Readiness
                  ok={data.pendingSummaryCount === 0 && data.failedSummaryCount === 0}
                  title="요약"
                  okText={`모든 활동이 ${data.llmProvider} 로 요약돼 있습니다.`}
                  todoText={`대기 ${data.pendingSummaryCount}건 · 실패 ${data.failedSummaryCount}건. 요약이 밀리면 업무 일지가 부실해집니다.`}
                  to="/admin/llm"
                />
                <Readiness
                  ok={data.unclaimedContributorCount === 0}
                  title="기여자 연결"
                  okText="모든 활동이 사람에 이어져 있습니다."
                  todoText={`${data.unclaimedContributorCount}명이 커밋만 올리고 로그인한 적이 없습니다. 그 활동은 업무 일지에 들어가지 않습니다.`}
                  to="/admin/people"
                />
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminGuard>
  )
}
