import ActivityRow from '@/components/activity/ActivityRow'
import SessionRow from '@/components/activity/SessionRow'
import { Card } from '@/components/ui/card'
import type { Activity, VscodeSession } from '@/types/api'

/**
 * 초안 편집 화면 우측 "이 초안의 근거" (디자인 브리프 3.3).
 * 행을 누르면 에디터의 해당 줄로 이동한다.
 */
export default function EvidencePanel({
  activities, sessions, onJump,
}: {
  activities: Activity[]
  sessions: VscodeSession[]
  onJump: (needle: string) => void
}) {
  return (
    <Card className="flex h-full flex-col overflow-hidden rounded-lg shadow-none">
      <div className="flex h-[46px] shrink-0 items-center border-b px-[18px] text-sm font-semibold">
        이 초안의 근거
      </div>
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-[18px] py-3">
        <section>
          <h3 className="mb-1 text-[12px] font-medium text-muted-foreground">
            GitHub 활동 ({activities.length})
          </h3>
          <div className="-mx-1">
            {activities.map((a) => (
              <ActivityRow
                key={a.id}
                activity={a}
                dense
                className="rounded"
                onClick={() => onJump(a.sha ?? `#${a.externalId}`)}
              />
            ))}
            {activities.length === 0 && <p className="py-2 text-[12px] text-muted-foreground">없음</p>}
          </div>
        </section>

        <section>
          <h3 className="mb-1.5 text-[12px] font-medium text-muted-foreground">
            미커밋 세션 ({sessions.length})
          </h3>
          <div className="space-y-2">
            {sessions.map((s) => <SessionRow key={s.id} session={s} dense />)}
            {sessions.length === 0 && <p className="text-[12px] text-muted-foreground">없음</p>}
          </div>
        </section>

        <p className="pt-1 text-[12px] text-muted-foreground/70">
          행을 클릭하면 에디터의 해당 줄로 이동합니다
        </p>
      </div>
    </Card>
  )
}
