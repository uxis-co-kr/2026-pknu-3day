import { lazy, Suspense } from 'react'
import { Skeleton } from '@/components/ui/skeleton'

/**
 * 팀원 전체 내역 (TODO_0910 §1-3 첫째).
 *
 * 담당자 1 이 만든 `/people` 화면을 그대로 쓴다 — 회의록 §1-3 에 "화면은 이미 만들어져
 * 있으니 관리자 콘솔에서 재사용할 수 있다"고 적혀 있고, 실제로 같은 `/stats/people` 을
 * 부른다. 같은 화면을 두 벌 만들 이유가 없다.
 *
 * recharts 를 쓰므로 콘솔 첫 로딩에서 떼어 낸다.
 */
const PeoplePage = lazy(() => import('@/pages/PeoplePage'))

export default function AdminActivityPage() {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-[15px] font-semibold">팀원 내역</h1>
        <p className="mt-1 text-[13px] text-muted-foreground">
          기간별 커밋 · PR · 머지와 그날의 업무 일지 상태를 봅니다.
        </p>
      </div>
      <Suspense fallback={<Skeleton className="h-96" />}>
        <PeoplePage />
      </Suspense>
    </div>
  )
}
