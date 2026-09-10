import { Fragment, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { cn } from '@/lib/utils'
import type { AdminAccount, AdminEmployee } from './types'

/** 연결 여부를 같은 모양으로 보여 준다. 칸마다 표현이 다르면 훑어보기 어렵다. */
function LinkBadge({ linked, note }: { linked: boolean; note?: string }) {
  return linked ? (
    <Badge variant="outline" className="border-emerald-300 font-normal text-emerald-700">
      연결됨{note ? ` · ${note}` : ''}
    </Badge>
  ) : (
    <Badge variant="outline" className="font-normal text-muted-foreground">
      아직 연결되지 않음
    </Badge>
  )
}

function AccountCell({ account }: { account: AdminAccount }) {
  return (
    <div className="flex items-center gap-2">
      <Avatar className="size-6">
        {account.avatarUrl && <AvatarImage src={account.avatarUrl} alt="" />}
        <AvatarFallback className="text-[10px]">
          {(account.name ?? account.login).slice(0, 2)}
        </AvatarFallback>
      </Avatar>
      <span className="truncate text-[13px]">{account.login}</span>
      {account.role === 'ADMIN' && (
        <Badge variant="outline" className="border-amber-300 font-normal text-amber-700">
          관리자
        </Badge>
      )}
    </div>
  )
}

/** 펼쳤을 때 보이는 리포 목록. 그 사람이 등록한 리포다 (PRD F1 — 등록자 토큰으로 수집). */
function RepoList({ account }: { account: AdminAccount }) {
  if (account.repos.length === 0) {
    return (
      <p className="px-4 py-3 text-[13px] text-muted-foreground">
        등록한 리포지터리가 없습니다. 리포를 등록해야 그 사람 커밋이 수집됩니다.
      </p>
    )
  }
  return (
    <div className="px-4 py-3">
      <p className="mb-2 text-[12px] text-muted-foreground">
        {account.login} 님이 등록한 리포지터리 {account.repos.length}개
      </p>
      <div className="space-y-1">
        {account.repos.map((r) => (
          <div
            key={r.repoId}
            className="flex items-center gap-3 rounded border bg-background px-3 py-2 text-[13px]"
          >
            <span className="font-medium">{r.fullName}</span>
            {r.defaultBranch && (
              <span className="text-[12px] text-muted-foreground">{r.defaultBranch}</span>
            )}
            {r.syncStatus !== 'OK' && (
              <Badge variant="outline" className="font-normal text-muted-foreground">
                {r.syncStatus === 'SYNCING' ? '동기화 중' : '동기화 실패'}
              </Badge>
            )}
            <span className="ml-auto tabular-nums text-muted-foreground">활동 {r.activityCount}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * 사내 직원 표 (TODO_0910 §1-3).
 *
 * <p>한 줄이 "이 사람이 서비스를 쓸 준비가 됐는가"를 말한다. 서비스 계정이 없으면
 * VS Code 도 GitHub 도 붙일 자리가 없으므로 나머지 칸은 비운다.
 */
export default function EmployeeTable({ employees }: { employees: AdminEmployee[] }) {
  const [open, setOpen] = useState<number | null>(null)

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-8" />
          <TableHead className="w-24">사원 번호</TableHead>
          <TableHead className="w-28">이름</TableHead>
          <TableHead>서비스 계정</TableHead>
          <TableHead className="w-40">VS Code 연동</TableHead>
          <TableHead className="w-40">GitHub 연동</TableHead>
          <TableHead className="w-20 text-right">활동 수</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {employees.map((e) => {
          const expanded = open === e.empSeq
          const acc = e.account
          return (
            <Fragment key={e.empSeq}>
              <TableRow
                className={cn('cursor-pointer', expanded && 'bg-muted/50')}
                onClick={() => setOpen(expanded ? null : e.empSeq)}
              >
                <TableCell className="text-muted-foreground">
                  {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                </TableCell>
                <TableCell className="tabular-nums text-muted-foreground">{e.empSeq}</TableCell>
                <TableCell>{e.empNm}</TableCell>
                <TableCell>
                  {acc ? (
                    <AccountCell account={acc} />
                  ) : (
                    <span className="text-[13px] text-muted-foreground">
                      아직 로그인한 적 없음
                    </span>
                  )}
                </TableCell>
                <TableCell>
                  {acc ? (
                    <LinkBadge
                      linked={acc.vscodeLinked}
                      note={acc.sessionCount > 0 ? `세션 ${acc.sessionCount}` : undefined}
                    />
                  ) : (
                    <span className="text-[13px] text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell>
                  {acc ? (
                    <LinkBadge linked={acc.githubLinked} />
                  ) : (
                    <span className="text-[13px] text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">{acc?.activityCount ?? 0}</TableCell>
              </TableRow>

              {expanded && (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={7} className="bg-muted/30 p-0">
                    {acc ? (
                      <RepoList account={acc} />
                    ) : (
                      <p className="px-4 py-3 text-[13px] text-muted-foreground">
                        이 사원은 아직 서비스에 로그인한 적이 없습니다. 로그인하면 계정이 생기고,
                        그때부터 리포지터리를 등록해 활동을 모을 수 있습니다.
                      </p>
                    )}
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          )
        })}
      </TableBody>
    </Table>
  )
}
