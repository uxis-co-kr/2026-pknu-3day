import { useMemo, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
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
 * (1) 팀 합계, (2) 사람별 표, (3) 고른 사람의 일별 그래프와 그날 활동을 보여 준다.
 */
type Period = 'week' | 'month' | 'custom'
type Item = PeopleStats['items'][number]

const BAR = '#93c5fd'
const BAR_ACTIVE = '#2563eb'

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
  /** 어느 목록의 몇 쪽인지. 날짜나 사람이 바뀌면 그 자리에서 첫 쪽으로 돌아간다. */
  const [activityPage, setActivityPage] = useState({ key: '', page: 0 })

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

  // 전체를 골랐을 때의 일별 그래프는 사람별 시계열을 날짜로 합친다.
  const series = useMemo(() => {
    if (row) return row.series
    const byDate = new Map<string, { date: string; commits: number; prs: number; merges: number }>()
    items.forEach((i) => i.series.forEach((p) => {
      const cur = byDate.get(p.date) ?? { date: p.date, commits: 0, prs: 0, merges: 0 }
      cur.commits += p.commits; cur.prs += p.prs; cur.merges += p.merges
      byDate.set(p.date, cur)
    }))
    return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date))
  }, [row, items])
  const chart = series.map((p) => ({ ...p, label: p.date.slice(5).replace('-', '/') }))

  const dayActivities = useActivities({ date: openDate ?? today, userId: row?.user.id })
  const dayItems = dayActivities.data?.items ?? []
  const activityPageCount = Math.max(1, Math.ceil(dayItems.length / PER_PAGE))
  // 다른 날·다른 사람을 고르면 목록이 통째로 바뀐다. 3쪽을 보던 채로 남아 있으면 안 된다.
  const activityKey = `${openDate ?? today}|${selected}`
  const activityCurrent = activityPage.key === activityKey
    ? Math.min(activityPage.page, activityPageCount - 1)
    : 0
  const activityRows = dayItems.slice(activityCurrent * PER_PAGE, (activityCurrent + 1) * PER_PAGE)

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
                        {drafts.length === 0 ? '없음' : `${drafts.length}건`}
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

        {/* 일별 그래프 */}
        <Card className="rounded-lg p-5 shadow-none">
          <h2 className="text-[13px] font-medium">
            일별 커밋 수{row ? ` — ${nameOf(row)}` : ' — 전체'}
            <span className="ml-2 font-normal text-muted-foreground">막대를 누르면 그날 활동을 봅니다</span>
          </h2>
          <div className="mt-4 h-[130px]">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chart} margin={{ top: 4, right: 4, bottom: 0, left: -20 }} barCategoryGap="30%"
                onClick={(s) => {
                  const d = (s as { activePayload?: { payload: { date: string } }[] })?.activePayload?.[0]?.payload.date
                  if (d) setOpenDate(openDate === d ? null : d)
                }}>
                <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} />
                <YAxis width={40} tickLine={false} axisLine={false} allowDecimals={false} tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} />
                <Tooltip cursor={{ fill: 'hsl(var(--muted))' }}
                  contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid hsl(var(--border))' }}
                  labelFormatter={(l) => `${l}`}
                  formatter={(v: number, name: string) => [v, { commits: '커밋', prs: 'PR', merges: '머지' }[name] ?? name]} />
                <Bar dataKey="commits" radius={[4, 4, 0, 0]} maxBarSize={48} className="cursor-pointer">
                  {chart.map((p) => (
                    <Cell key={p.date} fill={p.date === (openDate ?? today) ? BAR_ACTIVE : BAR} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        {/* 그날 활동 — 사람을 골랐으면 그 사람, 전체면 전원 */}
        <Card className="rounded-lg shadow-none">
          <div className="flex items-center justify-between border-b px-4 py-3">
            <h2 className="text-[13px] font-medium">
              {openDate ?? today} 활동{row ? ` — ${nameOf(row)}` : ' — 전체'}
              <span className="ml-2 font-normal text-muted-foreground">행을 누르면 GitHub 에서 엽니다</span>
            </h2>
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
                {/*
                  커밋 하나가 실제로 무엇을 고쳤는지는 여기서 알 수 없다 — 제목과 요약뿐이다.
                  관리자가 "이건 뭐지" 할 때 GitHub 을 손으로 찾아 들어가고 있었다. 행이 그
                  주소를 이미 들고 있으므로(activity.url) 눌러서 바로 가게 한다.
                */}
                {activityRows.map((a) => (
                  <ActivityRow
                    key={a.id}
                    activity={a}
                    dense
                    onClick={a.url ? () => window.open(a.url, '_blank', 'noopener,noreferrer') : undefined}
                  />
                ))}
              </div>
              <Pagination
                page={activityCurrent}
                pageCount={activityPageCount}
                total={dayItems.length}
                onChange={(p) => setActivityPage({ key: activityKey, page: p })}
              />
            </>
          )}
        </Card>
      </div>
    </AdminGuard>
  )
}
