import { Fragment, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import ActivityRow from '@/components/activity/ActivityRow'
import SessionRow from '@/components/activity/SessionRow'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import { Card } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useActivities, useMe, usePeopleStats, useSessions } from '@/api/hooks'
import { useSelectedDate } from '@/hooks/useSelectedDate'
import { addDays } from '@/lib/date'
import { cn } from '@/lib/utils'

/** 단일 계열이라 범례를 두지 않는다. 강조 막대는 선택한 날짜 하나뿐이고, 아래 표가 데이터 뷰를 겸한다. */
const BAR = '#93c5fd'
const BAR_ACTIVE = '#2563eb'

type Period = 'week' | 'month' | 'custom'

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <Card className="flex h-[101px] flex-col rounded-lg px-[19px] py-[17px] shadow-none">
      <p className="text-[13px] leading-4 text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-[26px] font-semibold leading-[33px] tabular-nums">{value}</p>
    </Card>
  )
}

export default function PeoplePage() {
  const navigate = useNavigate()
  const { date } = useSelectedDate()
  const { data: me } = useMe()

  const [period, setPeriod] = useState<Period>('week')
  const [userId, setUserId] = useState<number | undefined>(undefined)
  const [openDate, setOpenDate] = useState<string | null>(date)

  const span = period === 'month' ? 29 : 6
  const from = addDays(date, -span)
  const stats = usePeopleStats({ from, to: date, userId: userId ?? me?.id })
  const row = stats.data?.items[0]

  const dayActivities = useActivities({ date: openDate ?? date, userId: row?.user.id })
  const daySessions = useSessions({ date: openDate ?? date, userId: row?.user.id })

  const chart = (row?.series ?? []).map((p) => ({ ...p, label: p.date.slice(5).replace('-', '/') }))

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <Select value={String(userId ?? me?.id ?? '')} onValueChange={(v) => setUserId(Number(v))}>
          <SelectTrigger className="h-[34px] w-auto gap-2 text-[13px]">
            <SelectValue placeholder="사용자" />
          </SelectTrigger>
          <SelectContent>
            {(stats.data?.items ?? []).map((i) => (
              <SelectItem key={i.user.id} value={String(i.user.id)}>
                {i.user.name} ({i.user.login})
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Tabs value={period} onValueChange={(v) => setPeriod(v as Period)}>
          <TabsList className="h-[34px]">
            <TabsTrigger value="week" className="h-[26px] text-[13px]">이번 주</TabsTrigger>
            <TabsTrigger value="month" className="h-[26px] text-[13px]">이번 달</TabsTrigger>
            <TabsTrigger value="custom" className="h-[26px] text-[13px]">직접 선택</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div className="grid grid-cols-3 gap-3">
        {stats.isLoading || !row ? (
          <><Skeleton className="h-[101px]" /><Skeleton className="h-[101px]" /><Skeleton className="h-[101px]" /></>
        ) : (
          <>
            <StatCard label="커밋 합계" value={row.totals.commits} />
            <StatCard label="PR 합계" value={row.totals.prs} />
            <StatCard label="머지 합계" value={row.totals.merges} />
          </>
        )}
      </div>

      <Card className="rounded-lg p-5 shadow-none">
        <h2 className="text-[13px] font-medium">일별 커밋 수</h2>
        <div className="mt-4 h-[130px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chart} margin={{ top: 4, right: 4, bottom: 0, left: -20 }} barCategoryGap="30%">
              <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
              <XAxis dataKey="label" tickLine={false} axisLine={false}
                tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} />
              <YAxis width={40} tickLine={false} axisLine={false} allowDecimals={false}
                tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }} />
              <Tooltip
                cursor={{ fill: 'hsl(var(--muted))' }}
                contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid hsl(var(--border))' }}
                labelFormatter={(l) => `${l} 커밋`}
                formatter={(v: number) => [v, '건']}
              />
              <Bar dataKey="commits" radius={[4, 4, 0, 0]} maxBarSize={48}>
                {chart.map((p) => (
                  <Cell key={p.date} fill={p.date === date ? BAR_ACTIVE : BAR} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card className="overflow-hidden rounded-lg shadow-none">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="h-10 text-[12px]">날짜</TableHead>
              <TableHead className="h-10 w-[130px] text-[12px]">커밋</TableHead>
              <TableHead className="h-10 w-[130px] text-[12px]">PR</TableHead>
              <TableHead className="h-10 w-[130px] text-[12px]">머지</TableHead>
              <TableHead className="h-10 w-[160px] text-[12px]">초안 상태</TableHead>
              <TableHead className="h-10 w-[110px] text-right text-[12px]" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {[...(row?.series ?? [])].reverse().map((p) => (
              <Fragment key={p.date}>
                <TableRow
                  className="cursor-pointer"
                  onClick={() => setOpenDate(openDate === p.date ? null : p.date)}
                >
                  <TableCell className="text-table font-medium tabular-nums">{p.date}</TableCell>
                  <TableCell className="text-table tabular-nums">{p.commits}</TableCell>
                  <TableCell className="text-table tabular-nums">{p.prs}</TableCell>
                  <TableCell className="text-table tabular-nums">{p.merges}</TableCell>
                  <TableCell>
                    {p.draft ? <DraftStatusBadge status={p.draft.status} /> : <span className="text-table text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell className="text-right">
                    {p.draft && (
                      <button
                        type="button"
                        className="text-table text-primary underline-offset-2 hover:underline"
                        onClick={(e) => { e.stopPropagation(); navigate(`/drafts/${p.draft!.id}`) }}
                      >
                        초안 열기
                      </button>
                    )}
                  </TableCell>
                </TableRow>
                {openDate === p.date && (
                  <TableRow className="hover:bg-transparent">
                    <TableCell colSpan={6} className={cn('bg-muted/30 p-0')}>
                      {(dayActivities.data?.items ?? []).map((a) => <ActivityRow key={a.id} activity={a} />)}
                      {(daySessions.data ?? []).map((s) => <SessionRow key={s.id} session={s} />)}
                      {(dayActivities.data?.items ?? []).length === 0 && (daySessions.data ?? []).length === 0 && (
                        <p className="py-5 text-center text-[13px] text-muted-foreground">활동이 없습니다.</p>
                      )}
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            ))}
          </TableBody>
        </Table>
      </Card>
    </div>
  )
}
