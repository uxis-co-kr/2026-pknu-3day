import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { Repo } from '@/types/api'

/**
 * 리포 필터. 깃허브 내역과 VS 내역이 같은 줄을 쓴다.
 *
 * <p>사용자 필터는 없다 — 일반 로그인은 **내 내역만** 본다. 팀원 전체는 관리자 콘솔이
 * 맡는다 (9/10 회의).
 */
export default function DayFilters({
  repos, repoFilter, onRepo, children,
}: {
  repos: Repo[]
  repoFilter: string
  onRepo: (v: string) => void
  /** 화면별 추가 필터 (타입 탭 등) */
  children?: React.ReactNode
}) {
  return (
    <div className="flex items-center gap-2.5">
      <Select value={repoFilter} onValueChange={onRepo}>
        <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
          <SelectValue placeholder="리포" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">리포: 전체</SelectItem>
          {repos.map((r) => <SelectItem key={r.id} value={String(r.id)}>{r.fullName}</SelectItem>)}
        </SelectContent>
      </Select>

      {children}
    </div>
  )
}
