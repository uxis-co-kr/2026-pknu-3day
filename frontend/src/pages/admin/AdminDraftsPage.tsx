import { useEffect, useMemo, useState } from 'react'
import { FileText } from 'lucide-react'
import MarkdownPreview from '@/components/draft/MarkdownPreview'
import AutoBadge from '@/components/common/AutoBadge'
import { DraftStatusBadge } from '@/components/common/StatusBadge'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { addDays, endOfMonth, formatDateLabel, startOfMonth, todayKst } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { DraftSummary } from '@/types/api'
import AdminGuard from './AdminGuard'
import { useAdminDraft, useAdminDrafts, useAdminPeople } from './api'

const ALL = '__all__'
type Period = 'week' | 'month' | 'custom'

/**
 * 직원 업무일지 (관리자 콘솔).
 *
 * <p>팀원 내역은 "무엇을 얼마나 했는지" 숫자를 본다. 이 화면은 "무엇이라고 썼는지" 글을 본다 —
 * 관리자가 사람마다 채널로 물어보지 않고도 그날 일지를 읽을 수 있어야 한다.
 *
 * <p>서버는 같은 {@code GET /drafts} 다. 관리자 토큰이라 전원이 오고, 일반 회원은 DataScope 가
 * 자기 것으로 좁힌다. 목록에는 본문이 없어 고른 뒤에 한 건만 따로 부른다.
 */
export default function AdminDraftsPage() {
  const today = todayKst()
  const [period, setPeriod] = useState<Period>('week')
  const [customFrom, setCustomFrom] = useState(addDays(today, -13))
  const [customTo, setCustomTo] = useState(today)
  const [who, setWho] = useState<string>(ALL)
  const [openId, setOpenId] = useState<number | undefined>(undefined)

  const { from, to } = useMemo(() => {
    if (period === 'week') return { from: addDays(today, -6), to: today }
    if (period === 'month') return { from: startOfMonth(today), to: endOfMonth(today) }
    return { from: customFrom, to: customTo }
  }, [period, customFrom, customTo, today])

  const { data: people } = useAdminPeople()
  const { data: drafts, isLoading, error } = useAdminDrafts({
    from,
    to,
    userId: who === ALL ? undefined : Number(who),
  })
  const { data: opened, isLoading: openLoading } = useAdminDraft(openId)

  /** userId → 보여 줄 이름. 사원 명단 이름이 계정 로그인보다 낫다 — "ungsikJo" 보다 "조웅식". */
  const nameOf = useMemo(() => {
    const map = new Map<number, string>()
    for (const e of people?.employees ?? []) {
      if (e.account) map.set(e.account.userId, e.empNm)
    }
    for (const a of people?.unlinkedAccounts ?? []) {
      if (!map.has(a.userId)) map.set(a.userId, a.name ?? a.login)
    }
    return map
  }, [people])

  /** 고를 수 있는 사람 — 계정이 있는 사람만. 계정이 없으면 일지가 있을 수 없다. */
  const choices = useMemo(() => {
    const rows = [...nameOf.entries()].map(([userId, name]) => ({ userId, name }))
    rows.sort((a, b) => a.name.localeCompare(b.name))
    return rows
  }, [nameOf])

  const rows = drafts ?? []

  // 목록이 바뀌면 첫 건을 열어 둔다. 빈 화면보다 뭐라도 보이는 편이 낫다.
  useEffect(() => {
    if (rows.length === 0) {
      setOpenId(undefined)
      return
    }
    if (!rows.some((d) => d.id === openId)) setOpenId(rows[0].id)
  }, [rows, openId])

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">직원 업무일지</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            직원들이 쓴 업무 일지를 그대로 읽습니다. 사람과 기간을 골라 왼쪽에서 날짜를 누르면
            오른쪽에 본문이 나옵니다.
          </p>
        </div>

        {/* 고르기 */}
        <Card className="flex flex-wrap items-center gap-2 p-3">
          <Select value={who} onValueChange={(v) => setWho(v)}>
            <SelectTrigger className="h-[34px] w-[200px] text-[13px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>전체 직원</SelectItem>
              {choices.map((c) => (
                <SelectItem key={c.userId} value={String(c.userId)}>{c.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={period} onValueChange={(v) => setPeriod(v as Period)}>
            <SelectTrigger className="h-[34px] w-[150px] text-[13px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="week">최근 7일</SelectItem>
              <SelectItem value="month">이번 달</SelectItem>
              <SelectItem value="custom">직접 고르기</SelectItem>
            </SelectContent>
          </Select>

          {period === 'custom' && (
            <>
              <Input type="date" value={customFrom} max={customTo} className="h-[34px] w-[150px] text-[13px]"
                onChange={(e) => setCustomFrom(e.target.value)} />
              <span className="text-[13px] text-muted-foreground">~</span>
              <Input type="date" value={customTo} min={customFrom} max={today} className="h-[34px] w-[150px] text-[13px]"
                onChange={(e) => setCustomTo(e.target.value)} />
            </>
          )}

          <span className="ml-auto text-[13px] text-muted-foreground">
            {from} ~ {to} · 일지 {rows.length}건
          </span>
        </Card>

        <div className="grid grid-cols-[320px_1fr] gap-4">
          {/* 목록 */}
          <Card className="overflow-hidden p-0">
            {isLoading ? (
              <div className="space-y-2 p-3">
                <Skeleton className="h-12" /><Skeleton className="h-12" /><Skeleton className="h-12" />
              </div>
            ) : rows.length === 0 ? (
              <p className="p-4 text-[13px] text-muted-foreground">
                이 기간에 쓴 업무 일지가 없습니다. 기간을 넓혀 보세요.
              </p>
            ) : (
              <div className="max-h-[680px] divide-y overflow-y-auto">
                {rows.map((d) => (
                  <DraftRow
                    key={d.id}
                    draft={d}
                    name={nameOf.get(d.userId) ?? `사용자 ${d.userId}`}
                    active={d.id === openId}
                    onClick={() => setOpenId(d.id)}
                  />
                ))}
              </div>
            )}
          </Card>

          {/* 본문 */}
          <Card className="min-h-[320px] p-4">
            {openId === undefined ? (
              <p className="text-[13px] text-muted-foreground">왼쪽에서 일지를 고르세요.</p>
            ) : openLoading || !opened ? (
              <div className="space-y-2"><Skeleton className="h-6 w-1/3" /><Skeleton className="h-40" /></div>
            ) : (
              <>
                <div className="mb-3 flex flex-wrap items-center gap-2 border-b pb-3">
                  <FileText className="size-4 text-muted-foreground" />
                  <span className="text-[13px] font-medium">
                    {nameOf.get(opened.userId) ?? `사용자 ${opened.userId}`} · {formatDateLabel(opened.workDate)}
                  </span>
                  <DraftStatusBadge status={opened.status} />
                  {opened.autoGenerated && <AutoBadge />}
                  {!opened.userEdited && (
                    <span className="text-[12px] text-muted-foreground">아직 본인이 손대지 않음</span>
                  )}
                  <span className="ml-auto text-[12px] text-muted-foreground">v{opened.version}</span>
                </div>
                <MarkdownPreview source={opened.contentMd} />
              </>
            )}
          </Card>
        </div>
      </div>
    </AdminGuard>
  )
}

/** 목록 한 줄 — 날짜와 이름이 먼저다. 관리자는 "누가 언제" 로 찾는다. */
function DraftRow({
  draft, name, active, onClick,
}: { draft: DraftSummary; name: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex w-full flex-col gap-1 px-3 py-2.5 text-left transition-colors hover:bg-muted/50',
        active && 'bg-muted',
      )}
    >
      <span className="flex items-center gap-2">
        <span className="text-[13px] font-medium">{name}</span>
        <span className="text-[12px] text-muted-foreground">{formatDateLabel(draft.workDate)}</span>
        <DraftStatusBadge status={draft.status} className="ml-auto" />
      </span>
      <span className="flex items-center gap-1.5 text-[12px] text-muted-foreground">
        {draft.autoGenerated && <AutoBadge />}
        {draft.userEdited ? '본인이 저장함' : '자동 생성 그대로'}
      </span>
    </button>
  )
}
