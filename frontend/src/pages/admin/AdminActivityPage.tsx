import { useEffect, useMemo, useState } from 'react'
import ActivityRow from '@/components/activity/ActivityRow'
import Pagination from '@/components/common/Pagination'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActivities, usePeopleStats } from '@/api/hooks'
import { addDays, endOfMonth, startOfMonth, todayKst } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { PeopleStats } from '@/types/api'
import AdminGuard from './AdminGuard'
import { useAdminPeople } from './api'

/**
 * 팀원 내역 (TODO_0910 §1-3 첫째) — 관리자가 보는 전원 집계.
 *
 * 사용자 화면(/people)은 "내 것"을 보는 화면이라 한 사람만 불러오고, 선택기도 그 응답으로 채운다.
 * 그걸 그대로 콘솔에 넣으면 관리자 본인 하나만 보인다. 여기서는 전원을 한 번에 불러
 * (1) 팀 합계, (2) 사람별 표, (3) 고른 날의 활동을 보여 준다.
 */
type Period = 'week' | 'month' | 'custom'
type Item = PeopleStats['items'][number]


const ALL = 'all'

const PER_PAGE = 10

function StatCard({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <Card className="flex h-[101px] flex-col rounded-lg px-[19px] py-[17px] shadow-none">
      <p className="text-[13px] leading-4 text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-[26px] font-semibold leading-[33px] tabular-nums">{value}</p>
      {hint && <p className="text-[12px] text-muted-foreground">{hint}</p>}
    </Card>
  )
}

export default function AdminActivityPage() {
  const today = todayKst()
  const [period, setPeriod] = useState<Period>('week')
  const [customFrom, setCustomFrom] = useState(addDays(today, -13))
  const [customTo, setCustomTo] = useState(today)
  const [selected, setSelected] = useState<string>(ALL)
  const [openDate, setOpenDate] = useState<string | null>(null)
  const [peoplePage, setPeoplePage] = useState(0)
  const [activityPage, setActivityPage] = useState(0)

  const range =
    period === 'week' ? { from: addDays(today, -6), to: today }
    : period === 'month' ? { from: startOfMonth(today), to: endOfMonth(today) }
    : { from: customFrom, to: customTo }

  // userId 를 주지 않으면 전원이다. 선택기도 이 목록으로 채운다.
  const stats = usePeopleStats({ from: range.from, to: range.to })
  const { data: people, error } = useAdminPeople()

  // 사원 명단 이름이 있으면 그걸 앞세운다 — "ungsikJo" 보다 "조웅식" 이 관리자에게 익숙하다.
  const nameOf = useMemo(() => {
    const byUserId = new Map<number, string>()
    people?.employees.forEach((e) => {
      if (e.account) byUserId.set(e.account.userId, e.empNm)
    })
    return (item: Item) => byUserId.get(item.user.id) ?? item.user.name ?? item.user.login
  }, [people])

  // 커밋이 많은 사람부터. 같으면 이름 순으로 묶어 둔다 — 동점이 여럿이면 쪽을 넘길 때마다
  // 순서가 흔들려 같은 사람이 두 쪽에 보일 수 있다.
  const items = useMemo(
    () => [...(stats.data?.items ?? [])].sort(
      (a, b) => b.totals.commits - a.totals.commits
        || (a.user.name ?? a.user.login).localeCompare(b.user.name ?? b.user.login),
    ),
    [stats.data],
  )
  const team = items.reduce(
    (t, i) => ({ commits: t.commits + i.totals.commits, prs: t.prs + i.totals.prs, merges: t.merges + i.totals.merges }),
    { commits: 0, prs: 0, merges: 0 },
  )
  const active = items.filter((i) => i.totals.commits + i.totals.prs + i.totals.merges > 0).length

  const row = selected === ALL ? null : items.find((i) => String(i.user.id) === selected) ?? null

  // 기간이나 사람을 바꾸면 보던 쪽이 사라질 수 있다. 범위를 벗어나면 마지막 쪽으로 당긴다.
  const peoplePageCount = Math.max(1, Math.ceil(items.length / PER_PAGE))
  const peopleCurrent = Math.min(peoplePage, peoplePageCount - 1)
  const peopleRows = items.slice(peopleCurrent * PER_PAGE, (peopleCurrent + 1) * PER_PAGE)

  const dayActivities = useActivities({ date: openDate ?? today, userId: row?.user.id })
  const dayItems = dayActivities.data?.items ?? []
  const activityPageCount = Math.max(1, Math.ceil(dayItems.length / PER_PAGE))
  const activityCurrent = Math.min(activityPage, activityPageCount - 1)
  const activityRows = dayItems.slice(activityCurrent * PER_PAGE, (activityCurrent + 1) * PER_PAGE)

  // 다른 날·다른 사람을 고르면 목록이 통째로 바뀐다. 3쪽을 보던 채로 남아 있으면 안 된다.
  useEffect(() => setActivityPage(0), [openDate, selected])

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">팀원 내역</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            기간별 커밋 · PR · 머지와 그날의 업무 일지 상태를 봅니다. 전체를 보거나 한 사람을 골라 봅니다.
          </p>
        </div>

        <div className="flex items-center justify-between">
          <Select value={selected} onValueChange={setSelected}>
            <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
              <SelectValue placeholder="사용자" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>전체 팀원 ({items.length}명)</SelectItem>
              {items.map((i) => (
                <SelectItem key={i.user.id} value={String(i.user.id)}>
                  {nameOf(i)} ({i.user.login}) · 커밋 {i.totals.commits}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <div className="flex items-center gap-2.5">
            {period === 'custom' && (
              <div className="flex items-center gap-1.5">
                <Input type="date" value={customFrom} max={customTo} onChange={(e) => setCustomFrom(e.target.value)}
                  className="h-[34px] w-[150px] text-[13px]" />
                <span className="text-[13px] text-muted-foreground">~</span>
                <Input type="date" value={customTo} min={customFrom} onChange={(e) => setCustomTo(e.target.value)}
                  className="h-[34px] w-[150px] text-[13px]" />
              </div>
            )}
            <Tabs value={period} onValueChange={(v) => setPeriod(v as Period)}>
              <TabsList className="h-[34px]">
                <TabsTrigger value="week" className="h-[26px] text-[13px]">이번 주</TabsTrigger>
                <TabsTrigger value="month" className="h-[26px] text-[13px]">이번 달</TabsTrigger>
                <TabsTrigger value="custom" className="h-[26px] text-[13px]">직접 선택</TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </div>

        {/* 합계 — 전체면 팀 합계, 사람을 골랐으면 그 사람 */}
        <div className="grid grid-cols-3 gap-3">
          {stats.isLoading ? (
            <><Skeleton className="h-[101px]" /><Skeleton className="h-[101px]" /><Skeleton className="h-[101px]" /></>
          ) : row ? (
            <>
              <StatCard label={`${nameOf(row)} · 커밋 합계`} value={row.totals.commits} />
              <StatCard label="PR 합계" value={row.totals.prs} />
              <StatCard label="머지 합계" value={row.totals.merges} />
            </>
          ) : (
            <>
              <StatCard label="팀 커밋 합계" value={team.commits} hint={`${active}명이 활동`} />
              <StatCard label="팀 PR 합계" value={team.prs} />
              <StatCard label="팀 머지 합계" value={team.merges} />
            </>
          )}
        </div>

        {/* 사람별 표 — 전체를 볼 때만. 행을 누르면 그 사람으로 좁혀진다 */}
        {!row && (
          <Card className="rounded-lg shadow-none">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>팀원</TableHead>
                  <TableHead className="w-24 text-right">커밋</TableHead>
                  <TableHead className="w-24 text-right">PR</TableHead>
                  <TableHead className="w-24 text-right">머지</TableHead>
                  <TableHead className="w-40">업무 일지 (기간 내)</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {stats.isLoading ? (
                  <TableRow><TableCell colSpan={5}><Skeleton className="h-8" /></TableCell></TableRow>
                ) : items.length === 0 ? (
                  <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground">계정이 없습니다.</TableCell></TableRow>
                ) : peopleRows.map((i) => {
                  const drafts = i.series.filter((p) => p.draft)
                  const confirmed = drafts.filter((p) => p.draft?.status === 'CONFIRMED').length
                  return (
                    <TableRow key={i.user.id} className="cursor-pointer" onClick={() => setSelected(String(i.user.id))}>
                      <TableCell>
                        <span className="font-medium">{nameOf(i)}</span>
                        <span className="ml-2 text-[12px] text-muted-foreground">{i.user.login}</span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{i.totals.commits}</TableCell>
                      <TableCell className="text-right tabular-nums">{i.totals.prs}</TableCell>
                      <TableCell className="text-right tabular-nums">{i.totals.merges}</TableCell>
                      <TableCell className="text-[12px] text-muted-foreground">
                        {drafts.length === 0 ? '없음' : `${drafts.length}건 · 확정 ${confirmed}`}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
            <Pagination
              page={peopleCurrent}
              pageCount={peoplePageCount}
              total={items.length}
              onChange={setPeoplePage}
            />
          </Card>
        )}

        {/* 그날 활동 — 사람을 골랐으면 그 사람, 전체면 전원 */}
        <Card className="rounded-lg shadow-none">
          <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3">
            <h2 className="text-[13px] font-medium">
              {openDate ?? today} 활동{row ? ` — ${nameOf(row)}` : ' — 전체'}
            </h2>
            {/* 막대그래프를 없애면서 날짜를 고를 길이 사라졌다. 날짜 입력으로 대신한다. */}
            <Input
              type="date"
              value={openDate ?? today}
              max={today}
              className="h-[30px] w-[150px] text-[13px]"
              onChange={(e) => setOpenDate(e.target.value)}
            />
            <span className="ml-auto" />
            {row && (() => {
              const p = row.series.find((x) => x.date === (openDate ?? today))
              return p?.draft ? <DraftStatusBadge status={p.draft.status} /> : <span className="text-[12px] text-muted-foreground">업무 일지 없음</span>
            })()}
          </div>
          {dayActivities.isLoading ? (
            <div className="p-4"><Skeleton className="h-16" /></div>
          ) : dayItems.length === 0 ? (
            <p className="px-4 py-6 text-center text-[13px] text-muted-foreground">이날 활동이 없습니다.</p>
          ) : (
            <>
              <div className={cn('divide-y')}>
                {activityRows.map((a) => <ActivityRow key={a.id} activity={a} dense />)}
              </div>
              <Pagination
                page={activityCurrent}
                pageCount={activityPageCount}
                total={dayItems.length}
                onChange={setActivityPage}
              />
            </>
          )}
        </Card>
      </div>
    </AdminGuard>
  )
}
