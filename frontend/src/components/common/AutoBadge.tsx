import { Clock3 } from 'lucide-react'

/**
 * 18:00 스케줄러가 만든 초안 표시 (V4).
 *
 * <p>내가 만들지 않은 초안이 왜 있는지 알 수 있어야 한다. 재생성하면 사용자가 만든
 * 새 버전이 되므로 이 표시가 사라진다.
 */
export default function AutoBadge() {
  return (
    <span
      className="inline-flex shrink-0 items-center gap-1 rounded bg-muted px-1.5 py-px text-[11px] text-muted-foreground"
      title="18:00 스케줄러가 자동으로 만든 초안입니다. 재생성하면 직접 만든 버전이 됩니다."
    >
      <Clock3 className="size-3" />
      자동 생성됨
    </span>
  )
}
