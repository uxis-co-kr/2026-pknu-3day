import { useState } from 'react'
import { ChevronRight, ExternalLink } from 'lucide-react'
import ActivityTypeIcon from '@/components/common/ActivityTypeIcon'
import DiffStat from '@/components/common/DiffStat'
import { SummaryStatusBadge } from '@/components/common/StatusBadge'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivityDetail } from '@/api/hooks'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { Activity } from '@/types/api'

/** 서버는 GitHub 이 준 제목만 저장한다. 표기는 읽는 쪽이 type 과 externalId 로 붙인다. */
function title(a: Activity): string {
  if (a.type === 'PR_OPENED') return `PR #${a.externalId} 열림: ${a.title}`
  if (a.type === 'PR_MERGED') return `PR #${a.externalId} 머지: ${a.title}`
  return a.title
}

/**
 * 깃허브 내역의 한 행 — 펼치면 설명, 제목을 누르면 GitHub 으로 간다.
 *
 * <p>회의에서 "상세 내역(드롭다운 — 설명), 클릭 시 상세 페이지 이동" 으로 정한 행이다.
 * 자체 상세 페이지는 아직 만들지 않았다 — `GET /activities/{id}` 의 `rawDiff` 가 비어 있어
 * 보여 줄 코드 변경 내용이 없다 (TODO_0910 §4 F-2). 그때까지는 GitHub 원본으로 보낸다.
 */
export default function ActivityDetailRow({ activity }: { activity: Activity }) {
  const [open, setOpen] = useState(false)
  // 커밋 메시지는 목록 응답에 없다. 펼칠 때만 상세를 받아 온다.
  const detail = useActivityDetail(activity.id, open)
  const message = detail.data?.message

  return (
    <div className="border-b last:border-b-0">
      <div className="flex h-[37px] items-center gap-3 px-4">
        <button
          type="button"
          onClick={() => setOpen(!open)}
          aria-label={open ? '설명 접기' : '설명 펼치기'}
          className="shrink-0 rounded p-0.5 hover:bg-muted"
        >
          <ChevronRight className={cn('size-3.5 transition-transform', open && 'rotate-90')} />
        </button>

        <span className="w-[34px] shrink-0 text-[12px] tabular-nums text-muted-foreground">
          {formatTime(activity.occurredAt)}
        </span>
        <ActivityTypeIcon type={activity.type} />

        <a
          href={activity.url ?? undefined}
          target="_blank"
          rel="noreferrer"
          className="group flex min-w-0 shrink-0 items-baseline gap-1.5"
        >
          <span className="truncate text-[13px] font-medium group-hover:underline">{title(activity)}</span>
          {activity.sha && (
            <span className="shrink-0 text-[12px] text-muted-foreground/70">{activity.sha.slice(0, 7)}</span>
          )}
          <ExternalLink className="size-3 shrink-0 text-muted-foreground/50" />
        </a>

        <span className="flex min-w-0 flex-1 items-center gap-2">
          {activity.summaryStatus === 'PENDING' && (
            <>
              <Skeleton className="h-3 w-40" />
              <span className="text-[12px] text-muted-foreground">요약 생성 중…</span>
            </>
          )}
          {activity.summaryStatus === 'FAILED' && <SummaryStatusBadge status="FAILED" />}
          {activity.summaryStatus === 'DONE' && activity.summary && !open && (
            <span className="truncate text-[12px] text-muted-foreground">{activity.summary}</span>
          )}
        </span>

        <DiffStat additions={activity.additions} deletions={activity.deletions} />
      </div>

      {open && (
        <div className="space-y-2 bg-muted/40 px-4 py-3 pl-[62px]">
          {activity.summary && (
            <div>
              <p className="mb-0.5 text-[11px] font-medium text-muted-foreground">요약</p>
              <p className="text-[13px] leading-relaxed">{activity.summary}</p>
            </div>
          )}
          {detail.isLoading && <Skeleton className="h-4 w-64" />}
          {message && message !== activity.title && (
            <div>
              <p className="mb-0.5 text-[11px] font-medium text-muted-foreground">커밋 메시지</p>
              <pre className="whitespace-pre-wrap break-words font-mono text-[12px] leading-relaxed text-foreground/80">
                {message}
              </pre>
            </div>
          )}
          <p className="text-[11px] text-muted-foreground/70">
            {activity.repo.fullName}
            {activity.branch && ` · ${activity.branch}`}
            {(activity.filesChanged ?? 0) > 0 && ` · ${activity.filesChanged}개 파일`}
          </p>
        </div>
      )}
    </div>
  )
}
