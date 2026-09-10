import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import AdminGuard from './AdminGuard'
import EmployeeTable from './EmployeeTable'
import { useAdminPeople } from './api'

/**
 * 사내 직원과 서비스 이용 현황 (TODO_0910 §1-3).
 *
 * <p>표는 하나다. "사원 정보가 없는 계정" 과 "아직 로그인하지 않은 기여자" 는 뺐다 —
 * 사원 기준 로그인(§1-1)이 붙으면 계정과 사원이 로그인 시점에 이어지므로, 관리자가 손으로
 * 짝을 맞추는 자리가 필요 없어진다.
 */
export default function AdminPeoplePage() {
  const { data, isLoading, error } = useAdminPeople()

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">직원 · 계정</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            사내 직원이 이 서비스를 쓸 준비가 됐는지 봅니다. 서비스 계정이 있어야 VS Code 와
            GitHub 을 연결할 수 있고, 그래야 업무 일지가 만들어집니다.
          </p>
        </div>

        {isLoading || !data ? (
          <Skeleton className="h-64" />
        ) : (
          <Card>
            <CardContent className="p-4">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-[13px] font-medium">사내 직원</p>
                <span className="text-xs text-muted-foreground">
                  {data.employees.length}명
                  {!data.wapleConfigured && data.employees.length > 0 && ' · 임시 목록'}
                </span>
              </div>

              {data.employees.length === 0 ? (
                <p className="rounded border border-dashed p-4 text-[13px] text-muted-foreground">
                  회원 조회 API가 설정되지 않아 직원 목록을 불러올 수 없습니다.{' '}
                  <code className="rounded bg-muted px-1 text-xs">backend/.env</code> 의{' '}
                  <code className="rounded bg-muted px-1 text-xs">WAPLE_API_BASE_URL</code>,{' '}
                  <code className="rounded bg-muted px-1 text-xs">WAPLE_API_KEY</code>,{' '}
                  <code className="rounded bg-muted px-1 text-xs">WAPLE_COMPANY_SEQ</code> 를 채우면
                  이 표가 채워집니다.
                </p>
              ) : (
                <>
                  {!data.wapleConfigured && (
                    <p className="mb-3 rounded border border-dashed p-2.5 text-[13px] text-muted-foreground">
                      사내 회원 조회 API가 아직 설정되지 않아{' '}
                      <code className="rounded bg-muted px-1 text-xs">WAPLE_FALLBACK_EMPLOYEES</code>{' '}
                      의 임시 목록을 쓰고 있습니다. 실제 API가 붙으면 그쪽으로 자동 전환됩니다.
                    </p>
                  )}
                  <p className="mb-2 text-[12px] text-muted-foreground">
                    행을 클릭하면 그 사원이 등록한 리포지터리가 펼쳐집니다.
                  </p>
                  <EmployeeTable employees={data.employees} />
                </>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </AdminGuard>
  )
}
