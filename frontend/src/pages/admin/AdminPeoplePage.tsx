import { useState } from 'react'
import { Link2, Link2Off, UserRoundX } from 'lucide-react'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import AdminGuard from './AdminGuard'
import { useAdminPeople, useLinkEmployee } from './api'
import type { AdminAccount } from './types'

function AccountCell({ account }: { account: AdminAccount }) {
  return (
    <div className="flex items-center gap-2">
      <Avatar className="size-6">
        {account.avatarUrl && <AvatarImage src={account.avatarUrl} alt="" />}
        <AvatarFallback className="text-[10px]">
          {(account.name ?? account.login).slice(0, 2)}
        </AvatarFallback>
      </Avatar>
      <div className="min-w-0">
        <p className="truncate text-sm">{account.name ?? account.login}</p>
        <p className="truncate text-xs text-muted-foreground">{account.login}</p>
      </div>
      {account.role === 'ADMIN' && (
        <Badge variant="outline" className="ml-1 border-amber-300 text-amber-700">
          관리자
        </Badge>
      )}
    </div>
  )
}

/** GitHub 활성화 상태 — 토큰이 있어야 수집기가 그 사람 권한으로 리포를 읽는다. */
function GithubBadge({ linked }: { linked: boolean }) {
  return linked ? (
    <Badge variant="outline" className="gap-1 border-emerald-300 text-emerald-700">
      <Link2 className="size-3" /> 연결됨
    </Badge>
  ) : (
    <Badge variant="outline" className="gap-1 text-muted-foreground">
      <Link2Off className="size-3" /> 미연결
    </Badge>
  )
}

/** 계정에 사원 번호를 붙이는 입력. 동명이인이 있어 이름이 아니라 번호로 잇는다. */
function LinkEmployeeForm({ account, companySeq }: { account: AdminAccount; companySeq: number | null }) {
  const [value, setValue] = useState(account.empSeq?.toString() ?? '')
  const link = useLinkEmployee()

  const save = () => {
    const empSeq = value.trim() === '' ? null : Number(value.trim())
    if (empSeq !== null && Number.isNaN(empSeq)) return
    link.mutate({ userId: account.userId, coSeq: empSeq === null ? null : companySeq, empSeq })
  }

  return (
    <div className="flex items-center gap-1.5">
      <Input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="사원 번호"
        className="h-8 w-28"
        inputMode="numeric"
      />
      <Button size="sm" variant="outline" className="h-8" onClick={save} disabled={link.isPending}>
        <Link2 className="size-3.5" />
        연결
      </Button>
    </div>
  )
}

export default function AdminPeoplePage() {
  const { data, isLoading, error } = useAdminPeople()

  return (
    <AdminGuard error={error}>
      <div className="space-y-5">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">직원 · 계정</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            사내 직원과 서비스 계정이 어떻게 이어져 있는지, 누구의 GitHub 이 붙어 있는지 봅니다.
          </p>
        </div>

        {isLoading || !data ? (
          <Skeleton className="h-64" />
        ) : (
          <>
            {/* ── 사내 직원 ── */}
            <Card>
              <CardContent className="p-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-sm font-medium">사내 직원</p>
                  <span className="text-xs text-muted-foreground">
                    회원 조회 API 기준 {data.employees.length}명
                  </span>
                </div>

                {!data.wapleConfigured ? (
                  <p className="rounded border border-dashed p-4 text-sm text-muted-foreground">
                    회원 조회 API가 설정되지 않아 직원 목록을 불러올 수 없습니다.
                    <br />
                    <code className="rounded bg-muted px-1 text-xs">backend/.env</code> 의{' '}
                    <code className="rounded bg-muted px-1 text-xs">WAPLE_API_BASE_URL</code>,{' '}
                    <code className="rounded bg-muted px-1 text-xs">WAPLE_API_KEY</code>,{' '}
                    <code className="rounded bg-muted px-1 text-xs">WAPLE_COMPANY_SEQ</code> 를 채우면
                    이 표가 채워집니다. 아래 목록은 그와 무관하게 동작합니다.
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-24">사원 번호</TableHead>
                        <TableHead>이름</TableHead>
                        <TableHead>서비스 계정</TableHead>
                        <TableHead className="w-28">GitHub</TableHead>
                        <TableHead className="w-20 text-right">활동</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.employees.map((e) => (
                        <TableRow key={e.empSeq}>
                          <TableCell className="tabular-nums text-muted-foreground">{e.empSeq}</TableCell>
                          <TableCell>{e.empNm}</TableCell>
                          <TableCell>
                            {e.account ? (
                              <AccountCell account={e.account} />
                            ) : (
                              <span className="text-sm text-muted-foreground">— 아직 사용하지 않음</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {e.account ? <GithubBadge linked={e.account.githubLinked} /> : null}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {e.account?.activityCount ?? 0}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            {/* ── 사원과 잇지 않은 계정 ── */}
            <Card>
              <CardContent className="p-4">
                <div className="mb-1 flex items-center justify-between">
                  <p className="text-sm font-medium">사원과 연결되지 않은 계정</p>
                  <span className="text-xs text-muted-foreground">{data.unlinkedAccounts.length}건</span>
                </div>
                <p className="mb-3 text-sm text-muted-foreground">
                  로그인은 했지만 사내 사원과 이어지지 않은 계정입니다. 사원 번호를 넣어 이어 줍니다.
                </p>

                {data.unlinkedAccounts.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted-foreground">모두 연결돼 있습니다.</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>계정</TableHead>
                        <TableHead className="w-28">GitHub</TableHead>
                        <TableHead className="w-20 text-right">활동</TableHead>
                        <TableHead className="w-56">사원 연결</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.unlinkedAccounts.map((a) => (
                        <TableRow key={a.userId}>
                          <TableCell><AccountCell account={a} /></TableCell>
                          <TableCell><GithubBadge linked={a.githubLinked} /></TableCell>
                          <TableCell className="text-right tabular-nums">{a.activityCount}</TableCell>
                          <TableCell>
                            <LinkEmployeeForm account={a} companySeq={data.companySeq} />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            {/* ── 미연결 기여자 (BACKLOG §3-4) ── */}
            <Card id="contributors">
              <CardContent className="p-4">
                <div className="mb-1 flex items-center justify-between">
                  <p className="text-sm font-medium">사람에 연결되지 않은 기여자</p>
                  <span className="text-xs text-muted-foreground">
                    {data.unclaimedContributors.length}건
                  </span>
                </div>
                <p className="mb-3 text-sm text-muted-foreground">
                  커밋은 수집되는데 이 서비스에 로그인한 적이 없는 GitHub 계정입니다. 이 활동은
                  총계에는 들어가지만 사용자 카드나 업무 일지에는 나오지 않습니다. 본인이 한 번
                  로그인하면 지난 활동까지 자동으로 이어집니다.
                </p>

                {data.unclaimedContributors.length === 0 ? (
                  <p className="py-4 text-center text-sm text-muted-foreground">
                    모든 활동이 사람에 연결돼 있습니다.
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>GitHub 계정</TableHead>
                        <TableHead className="w-24 text-right">활동</TableHead>
                        <TableHead className="w-32">마지막 활동</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.unclaimedContributors.map((c) => (
                        <TableRow key={c.externalLogin}>
                          <TableCell>
                            <span className="flex items-center gap-2">
                              <UserRoundX className="size-4 text-muted-foreground" />
                              {c.externalLogin}
                            </span>
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{c.activityCount}</TableCell>
                          <TableCell className="text-muted-foreground">
                            {c.lastSeenAt?.slice(0, 10) ?? '—'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </AdminGuard>
  )
}
