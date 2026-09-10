import { cn } from '@/lib/utils'
import type { DraftStatus, SummaryStatus, SyncStatus } from '@/types/api'

/**
 * 의미색은 여기서만 쓴다 (디자인 브리프 2.).
 * DRAFT 회색 · CONFIRMED(=완료) 초록 · FAILED 빨강 · 미커밋 주황.
 *
 * <p>화면 문구는 "완료" 다 (9/10 결정). 서버 상태값은 CONFIRMED 그대로다.
 */
const base = 'inline-flex items-center rounded px-2 py-0.5 text-[11px] font-medium leading-[15px]'

export function DraftStatusBadge({ status, className }: { status: DraftStatus; className?: string }) {
  return (
    <span
      className={cn(
        base,
        status === 'CONFIRMED'
          ? 'bg-status-confirmed/10 text-status-confirmed'
          : 'bg-muted text-status-draft',
        className,
      )}
    >
      {status === 'CONFIRMED' ? '완료' : '작성 중'}
    </span>
  )
}

export function SummaryStatusBadge({ status }: { status: SummaryStatus }) {
  if (status !== 'FAILED') return null
  return <span className={cn(base, 'bg-status-failed/10 text-status-failed')}>요약 실패</span>
}

const SYNC_LABEL: Record<SyncStatus, string> = { OK: '정상', SYNCING: '진행 중', FAILED: '실패' }

export function SyncStatusBadge({ status }: { status: SyncStatus }) {
  return (
    <span
      className={cn(
        base,
        status === 'OK' && 'bg-status-confirmed/10 text-status-confirmed',
        status === 'SYNCING' && 'bg-primary/10 text-primary',
        status === 'FAILED' && 'bg-status-failed/10 text-status-failed',
      )}
    >
      {SYNC_LABEL[status]}
    </span>
  )
}
