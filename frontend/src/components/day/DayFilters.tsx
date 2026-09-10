import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { Repo, UserRef } from '@/types/api'

/** 리포·사용자 필터. 깃허브 내역과 VS 내역이 같은 줄을 쓴다. */
export default function DayFilters({
  repos, users, repoFilter, userFilter, onRepo, onUser, children,
}: {
  repos: Repo[]
  users: UserRef[]
  repoFilter: string
  userFilter: string
  onRepo: (v: string) => void
  onUser: (v: string) => void
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

      <Select value={userFilter} onValueChange={onUser}>
        <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
          <SelectValue placeholder="사용자" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">사용자: 전체</SelectItem>
          {users.map((u) => <SelectItem key={u.id} value={String(u.id)}>{u.name ?? u.login}</SelectItem>)}
        </SelectContent>
      </Select>

      {children}
    </div>
  )
}
