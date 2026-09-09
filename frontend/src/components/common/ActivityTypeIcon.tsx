import { GitCommitHorizontal, GitMerge, GitPullRequest } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { ActivityType } from '@/types/api'

/** 디자인 브리프 2. — 커밋 회색, PR 초록, 머지 보라. */
export default function ActivityTypeIcon({ type, className }: { type: ActivityType; className?: string }) {
  const common = cn('size-3.5 shrink-0', className)
  if (type === 'PR_OPENED') return <GitPullRequest className={cn(common, 'text-status-confirmed')} />
  if (type === 'PR_MERGED') return <GitMerge className={cn(common, 'text-violet-600')} />
  return <GitCommitHorizontal className={cn(common, 'text-muted-foreground')} />
}
