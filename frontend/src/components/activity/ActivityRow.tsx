import { SummaryStatusBadge } from '@/components/common/StatusBadge'
import ActivityTypeIcon from '@/components/common/ActivityTypeIcon'
import DiffStat from '@/components/common/DiffStat'
import RepoBadge from '@/components/common/RepoBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { Activity } from '@/types/api'

/**
 * 수집기가 PR_MERGED 의 title 에는 "PR #N 머지: " 를 이미 붙여 오고 PR_OPENED 에는 안 붙인다.
 * 표기 형식은 화면이 정하므로, 들어온 접두사는 떼고 여기서 다시 붙인다.
 */
const PR_PREFIX = /^PR\s*#\d+\s*(열림|머지)\s*:\s*/

function title(a: Activity): string {
  const plain = a.title.replace(PR_PREFIX, '')
  if (a.type === 'PR_OPENED') return `PR #${a.externalId} 열림: ${plain}`
  if (a.type === 'PR_MERGED') return `PR #${a.externalId} 머지: ${plain}`
  return plain
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
      <span className={cn('flex items-baseline gap-1.5', dense ? 'min-w-0 flex-1' : 'shrink-0')}>
        {/* 근거 패널은 폭이 좁아 실제 커밋 제목이 넘친다. 좁을 때만 줄인다. */}
        <span className={cn('text-[13px] font-medium', dense && 'truncate')}>{title(activity)}</span>
        {/* 실서버는 40자 전체 sha 를 준다. 아트보드는 abc1234 처럼 짧은 형태다. */}
        {activity.sha && (
          <span className="shrink-0 text-[12px] text-muted-foreground/70">{activity.sha.slice(0, 7)}</span>
        )}
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
