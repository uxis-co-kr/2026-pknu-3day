import { useState } from 'react'
import { RefreshCw, Trash2 } from 'lucide-react'
import { SyncStatusBadge } from '@/components/common/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ApiError } from '@/api/apiClient'
import { useDeleteRepo, useRegisterRepo, useRepos, useSyncRepo } from '@/api/hooks'
import { formatRelative } from '@/lib/date'

/** 디자인 브리프 3.4 — 등록·삭제·동기화. 빈 상태는 일러스트 없이 텍스트만. */
/**
 * 설정 > 리포지터리 — 등록·삭제·동기화.
 *
 * <p>9/10 회의로 독립 메뉴(/repos)에서 설정 안으로 들어왔다. 회의에서 나온
 * "전체 등록 / 전체 동기화" 는 서버에 해당 엔드포인트가 없어 아직 없다
 * (TODO_0910 §5-2).
 */
export default function RepoSection() {
  const { data: repos, isLoading } = useRepos()
  const register = useRegisterRepo()
  const remove = useDeleteRepo()
  const sync = useSyncRepo()

  const [fullName, setFullName] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function onRegister() {
    if (!fullName.trim()) return
    setError(null)
    try {
      await register.mutateAsync(fullName.trim())
      setFullName('')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '등록에 실패했습니다.')
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2.5">
        <Input
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && void onRegister()}
          placeholder="owner/repo"
          className="h-[34px] w-[260px] text-[13px]"
        />
        <Button size="sm" className="h-[34px]" disabled={register.isPending} onClick={() => void onRegister()}>
          등록
        </Button>
        <span className="text-[12px] text-muted-foreground">GitHub에서 접근 가능한 리포만 등록됩니다</span>
      </div>

      {error && <p className="text-[12px] text-status-failed">{error}</p>}

      <Card className="overflow-hidden rounded-lg shadow-none">
        {isLoading ? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-8" /><Skeleton className="h-8" />
          </div>
        ) : (repos ?? []).length === 0 ? (
          <div className="px-6 py-16 text-center">
            <p className="text-sm font-medium">등록된 리포지터리가 없습니다</p>
            <p className="mt-1.5 text-[13px] text-muted-foreground">
              위 입력창에 <span className="font-mono">owner/repo</span> 를 넣어 첫 리포를 등록해 주세요.
            </p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="h-10 text-[12px]">리포 이름</TableHead>
                <TableHead className="h-10 w-[140px] text-[12px]">기본 브랜치</TableHead>
                <TableHead className="h-10 w-[110px] text-[12px]">오늘 활동 수</TableHead>
                <TableHead className="h-10 w-[140px] text-[12px]">마지막 동기화</TableHead>
                <TableHead className="h-10 w-[110px] text-[12px]">상태</TableHead>
                <TableHead className="h-10 w-[90px] text-right text-[12px]">액션</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(repos ?? []).map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-table">
                    <a
                      href={`https://github.com/${r.fullName}`}
                      target="_blank"
                      rel="noreferrer"
                      className="font-medium text-primary underline-offset-2 hover:underline"
                    >
                      {r.fullName}
                    </a>
                  </TableCell>
                  <TableCell className="text-table text-muted-foreground">{r.defaultBranch ?? '—'}</TableCell>
                  <TableCell className="text-table tabular-nums">{r.todayActivityCount}</TableCell>
                  <TableCell className="text-table text-muted-foreground">{formatRelative(r.lastSyncedAt)}</TableCell>
                  <TableCell><SyncStatusBadge status={r.syncStatus} /></TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost" size="icon" className="size-8"
                      aria-label="동기화"
                      disabled={r.syncStatus === 'SYNCING'}
                      onClick={() => sync.mutate(r.id)}
                    >
                      <RefreshCw />
                    </Button>
                    <Button
                      variant="ghost" size="icon" className="size-8 text-muted-foreground hover:text-status-failed"
                      aria-label="삭제"
                      onClick={() => remove.mutate(r.id)}
                    >
                      <Trash2 />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  )
}
