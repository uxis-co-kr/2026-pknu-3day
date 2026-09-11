import { Fragment, useMemo, useState } from 'react'
import { ChevronDown, ChevronRight, ExternalLink, KeyRound } from 'lucide-react'
import { ApiError } from '@/api/apiClient'
import Pagination from '@/components/common/Pagination'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useResetPassword } from './api'
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
      {note ?? '아직 연결되지 않음'}
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

/**
 * 비밀번호를 사원번호로 되돌리는 버튼 (DAY3_plan C-3).
 *
 * 최초 비밀번호가 사원번호라 남이 먼저 들어갈 수 있다는 것은 회의가 알고 유지한 결정이다.
 * 정책을 뒤집지 않고, 사고가 났을 때 되돌릴 수단만 둔다.
 */
function ResetPasswordButton({ account, empSeq }: { account: AdminAccount; empSeq: number }) {
  const reset = useResetPassword()
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  async function onClick() {
    if (!window.confirm(`${account.login} 의 비밀번호를 사원번호(${empSeq})로 되돌립니다.\n지금 로그인된 세션은 모두 끊깁니다. 계속할까요?`)) return
    setMessage(null)
    try {
      const r = await reset.mutateAsync(account.userId)
      setMessage({ ok: true, text: `되돌렸습니다. 이제 비밀번호는 ${r.loginId} 이고, 다음 로그인에서 바꾸게 됩니다.` })
    } catch (e) {
      setMessage({ ok: false, text: e instanceof ApiError ? e.message : '초기화에 실패했습니다.' })
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-3 border-t px-4 py-3">
      <Button variant="outline" size="sm" disabled={reset.isPending} onClick={() => void onClick()}>
        <KeyRound />
        비밀번호 초기화
      </Button>
      <span className="text-[12px] text-muted-foreground">
        계정을 남이 먼저 차지했거나 비밀번호를 잊었을 때. 사원번호로 되돌리고 이전 로그인을 모두 끊습니다.
      </span>
      {message && (
        <span className={message.ok ? 'text-[12px] text-emerald-700' : 'text-[12px] text-destructive'}>
          {message.text}
        </span>
      )}
    </div>
  )
}

/**
 * 펼쳤을 때 보이는 리포 목록 — 그 사람이 **연결된** 리포다.
 *
 * <p>등록한 것만 보여 주면 팀원은 늘 "없습니다" 가 된다. 리포는 한 사람만 등록할 수 있어
 * (수집이 등록자 토큰으로 돈다) 두 번째 사람은 등록할 길이 없다. 활동이 잡혔거나 VS 기록을
 * 보낸 리포도 함께 세고, 등록한 것에는 표를 단다 (BACKLOG2 §2-4).
 */
function RepoList({ account }: { account: AdminAccount }) {
  if (account.repos.length === 0) {
    return (
      <p className="px-4 py-3 text-[13px] text-muted-foreground">
        연결된 리포지터리가 없습니다. 팀에서 한 사람이 리포를 등록하고 이 사람이 GitHub 을
        연결하면, 그때부터 이 사람 커밋이 여기 잡힙니다.
      </p>
    )
  }
  const registered = account.repos.filter((r) => r.registered).length
  return (
    <div className="px-4 py-3">
      <p className="mb-2 text-[12px] text-muted-foreground">
        {account.login} 님이 연결된 리포지터리 {account.repos.length}개
        {registered > 0 && ` · 그중 ${registered}개를 등록했습니다`}
      </p>
      <div className="space-y-1">
        {account.repos.map((r) => (
          // 리포 이름을 누르면 GitHub 으로 간다. 관리자가 "이 리포가 뭐지" 할 때 바로 볼 수 있게.
          <a
            key={r.repoId}
            href={`https://github.com/${r.fullName}`}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-3 rounded border bg-background px-3 py-2 text-[13px] transition-colors hover:border-primary/40 hover:bg-muted/40"
          >
            <span className="inline-flex items-center gap-1.5 font-medium">
              {r.fullName}
              <ExternalLink className="size-3 text-muted-foreground" />
            </span>
            {/* 등록자는 수집이 그 사람 토큰으로 돈다는 뜻이라, 남과 구분해 둔다. */}
            <Badge variant="outline" className="font-normal text-muted-foreground">
              {r.registered ? '등록자' : '참여'}
            </Badge>
            {r.defaultBranch && (
              <span className="text-[12px] text-muted-foreground">{r.defaultBranch}</span>
            )}
            {r.syncStatus !== 'OK' && (
              <Badge variant="outline" className="font-normal text-muted-foreground">
                {r.syncStatus === 'SYNCING' ? '동기화 중' : '동기화 실패'}
              </Badge>
            )}
            <span className="ml-auto tabular-nums text-muted-foreground">
              활동 {r.activityCount} · VS {r.sessionCount}
            </span>
          </a>
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
const PER_PAGE = 10

export default function EmployeeTable({ employees }: { employees: AdminEmployee[] }) {
  const [open, setOpen] = useState<number | null>(null)
  const [page, setPage] = useState(0)

  // 사원 번호 순. 명부가 오는 순서는 API 사정이라 사람이 찾을 때 기준이 되지 못한다.
  const sorted = useMemo(
    () => [...employees].sort((a, b) => a.empSeq - b.empSeq),
    [employees],
  )
  // 명단이 줄어 있던 쪽이 사라질 수 있다. 범위를 벗어나면 마지막 쪽으로 당긴다.
  const pageCount = Math.max(1, Math.ceil(sorted.length / PER_PAGE))
  const current = Math.min(page, pageCount - 1)
  const rows = sorted.slice(current * PER_PAGE, (current + 1) * PER_PAGE)

  return (
    <>
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-8" />
          <TableHead className="w-24">사원 번호</TableHead>
          <TableHead className="w-28">이름</TableHead>
          <TableHead>서비스 계정</TableHead>
          <TableHead className="w-40">VS Code 연동</TableHead>
          <TableHead className="w-40">GitHub 연동</TableHead>
          <TableHead className="w-24 text-right">연결된 리포</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((e) => {
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
                      note={
                        acc.vscodeLinked
                          ? `세션 ${acc.sessionCount}`
                          : acc.apiKeyCount > 0
                            ? '키만 발급됨'
                            : undefined
                      }
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
                <TableCell className="text-right tabular-nums">
                  {acc ? acc.repos.length : <span className="text-muted-foreground">—</span>}
                </TableCell>
              </TableRow>

              {expanded && (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={7} className="bg-muted/30 p-0">
                    {acc ? (
                      <>
                        <RepoList account={acc} />
                        <ResetPasswordButton account={acc} empSeq={e.empSeq} />
                      </>
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
    <Pagination
      page={current}
      pageCount={pageCount}
      total={sorted.length}
      onChange={(p) => {
        setPage(p)
        // 펼친 행은 이 쪽에만 있던 것이다. 넘어가서도 열려 있으면 엉뚱한 자리가 펼쳐진다.
        setOpen(null)
      }}
    />
    </>
  )
}
