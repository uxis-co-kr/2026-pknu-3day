import { SummaryStatusBadge } from '@/components/common/StatusBadge'
import ActivityTypeIcon from '@/components/common/ActivityTypeIcon'
import DiffStat from '@/components/common/DiffStat'
import RepoBadge from '@/components/common/RepoBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { Activity } from '@/types/api'

function title(a: Activity): string {
  if (a.type === 'PR_OPENED') return `PR #${a.externalId} 열림: ${a.title}`
  if (a.type === 'PR_MERGED') return `PR #${a.externalId} 머지: ${a.title}`
  return a.title
}

/** 홈 타임라인과 초안 근거 패널이 같은 행을 쓴다. `dense` 는 근거 패널용. */
export default function ActivityRow({
  activity, dense = false, className, onClick,
}: {
  activity: Activity
  dense?: boolean
  className?: string
  onClick?: () => void
}) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 border-b px-4 text-left last:border-b-0',
        dense ? 'h-[29px] gap-2 px-3' : 'h-[37px]',
        onClick && 'hover:bg-muted/60',
        className,
      )}
    >
      <span className="w-[34px] shrink-0 text-[12px] tabular-nums text-muted-foreground">
        {formatTime(activity.occurredAt)}
      </span>
      <ActivityTypeIcon type={activity.type} />
      {!dense && <RepoBadge fullName={activity.repo.fullName} />}
      <span className="flex shrink-0 items-baseline gap-1.5">
        <span className="text-[13px] font-medium">{title(activity)}</span>
        {activity.sha && <span className="text-[12px] text-muted-foreground/70">{activity.sha}</span>}
      </span>

      <span className="flex min-w-0 flex-1 items-center gap-2">
        {!dense && activity.summaryStatus === 'PENDING' && (
          <>
            <Skeleton className="h-3 w-40" />
            <span className="text-[12px] text-muted-foreground">요약 생성 중…</span>
          </>
        )}
        {!dense && activity.summaryStatus === 'FAILED' && <SummaryStatusBadge status="FAILED" />}
        {!dense && activity.summaryStatus === 'DONE' && activity.summary && (
          <span className="truncate text-[12px] text-muted-foreground">{activity.summary}</span>
        )}
      </span>

      <DiffStat additions={activity.additions} deletions={activity.deletions} />
    </Tag>
  )
}
