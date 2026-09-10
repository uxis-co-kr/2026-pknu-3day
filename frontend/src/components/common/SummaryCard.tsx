import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** 화면 상단 요약 카드 (디자인 브리프 2.). 깃허브 내역·VS 내역이 같은 카드를 쓴다. */
export default function SummaryCard({
  label, value, hint, warn,
}: {
  label: string
  value: number | string
  hint: string
  warn?: boolean
}) {
  return (
    <Card className="flex h-[101px] flex-col rounded-lg px-[19px] py-[17px] shadow-none">
      <p className="text-[13px] leading-4 text-muted-foreground">{label}</p>
      <p className="mt-0.5 text-[26px] font-semibold leading-[33px] tabular-nums">{value}</p>
      <p className={cn('mt-0.5 text-[12px] leading-[14px]', warn ? 'text-status-uncommitted' : 'text-muted-foreground')}>
        {hint}
      </p>
    </Card>
  )
}
