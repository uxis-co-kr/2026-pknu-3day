import { cn } from '@/lib/utils'

export default function RepoBadge({ fullName, className }: { fullName: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-[15px] shrink-0 items-center rounded bg-muted px-[7px] text-[12px] leading-[15px] text-muted-foreground',
        className,
      )}
    >
      {fullName}
    </span>
  )
}
