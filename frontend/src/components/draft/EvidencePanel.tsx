import { useState } from 'react'
import { ChevronRight, Clock, FileDiff, GitCommitHorizontal, GitMerge, GitPullRequest, ListTodo, MessagesSquare, NotebookPen, Save } from 'lucide-react'
import ActivityRow from '@/components/activity/ActivityRow'
import DiffStat from '@/components/common/DiffStat'
import { Card } from '@/components/ui/card'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { Activity, ActivityType, VscodeSession } from '@/types/api'

/**
 * 초안 편집 화면 우측 "이 초안의 근거" (디자인 브리프 3.3).
 *
 * <p>근거는 **GitHub 활동**과 **VS 활동** 두 갈래이고, 각각을 세부 카테고리로 나눈다.
 * 생성·재생성 버튼을 누르면 서버가 이 둘을 함께 모아 초안을 만든다 (PRD F3) — 화면도
 * 그 구조를 그대로 보여 줘야 무엇을 근거로 썼는지 알 수 있다.
 *
 * <p>행을 누르면 에디터에서 그 근거가 적힌 줄로 이동한다.
 */
export default function EvidencePanel({
  activities, sessions, onJump,
}: {
  activities: Activity[]
  sessions: VscodeSession[]
  onJump: (needles: string[]) => void
}) {
  const byTime = [...activities].sort((x, y) => x.occurredAt.localeCompare(y.occurredAt))

  return (
    <Card className="flex h-full flex-col overflow-hidden rounded-lg shadow-none">
      <div className="flex h-[46px] shrink-0 items-center border-b px-[18px] text-sm font-semibold">
        이 초안의 근거
      </div>
      <div className="min-h-0 min-w-0 flex-1 overflow-y-auto overflow-x-hidden px-[18px] py-3">
        <Source label="GitHub 활동" count={activities.length} unit="건">
          {ACTIVITY_KINDS.map(({ type, label, Icon }) => {
            const rows = byTime.filter((a) => a.type === type)
            if (rows.length === 0) return null
            return (
              <Category key={type} label={label} count={rows.length} Icon={Icon}>
                <div className="-mx-1 min-w-0">
                  {rows.map((a) => (
                    <ActivityRow
                      key={a.id}
                      activity={a}
                      dense
                      className="rounded"
                      // 본문에는 짧은 sha 가 들어간다. 전체 sha 만 찾으면 늘 빗나간다.
                      onClick={() => onJump(jumpTargets(a))}
                    />
                  ))}
                </div>
              </Category>
            )
          })}
          {activities.length === 0 && <Empty />}
        </Source>

        <Source label="VS 활동" count={sessions.length} unit="세션">
          {sessions.map((s) => <SessionEvidence key={s.id} session={s} onJump={onJump} />)}
          {sessions.length === 0 && <Empty />}
        </Source>

        <p className="pt-3 text-[12px] text-muted-foreground/70">
          행을 클릭하면 에디터의 해당 줄로 이동합니다
        </p>
      </div>
    </Card>
  )
}

/**
 * 본문에서 이 활동을 가리킬 만한 문자열들. 앞의 것부터 찾는다.
 *
 * <p>템플릿은 `(commit c37005f)` 처럼 7자 sha 를 쓰고, AI 가 쓴 본문은 표기가 더 자유롭다.
 * 그래서 짧은 sha → 전체 sha → PR 번호 → 제목 순으로 후보를 준다.
 */
function jumpTargets(a: Activity): string[] {
  const out: string[] = []
  if (a.sha) {
    out.push(a.sha.slice(0, 7), a.sha)
  }
  if (a.type !== 'COMMIT') {
    out.push(`#${a.externalId}`, a.externalId)
  }
  if (a.title) out.push(a.title)
  return out
}

const ACTIVITY_KINDS: { type: ActivityType; label: string; Icon: typeof GitMerge }[] = [
  { type: 'COMMIT', label: '커밋', Icon: GitCommitHorizontal },
  { type: 'PR_OPENED', label: 'PR 열림', Icon: GitPullRequest },
  { type: 'PR_MERGED', label: 'PR 머지', Icon: GitMerge },
]

/** 세션 하나를 네 갈래로 펼친다. 서버가 초안에 쓰는 재료와 같은 구분이다. */
function SessionEvidence({ session, onJump }: { session: VscodeSession; onJump: (needles: string[]) => void }) {
  // 계획은 확장이 markdown 문서 한 통으로 보낸다 — 하루에 하나라 건수를 세지 않는다.
  // 초안으로 건너뛰려면 줄 단위로 잡아야 해서, 보여 줄 때만 줄로 나눈다.
  const plan = (session.planNote ?? '').trim()
  const planLines = plan.split('\n').map((p) => p.trim()).filter(Boolean)

  return (
    <div className="mb-1 last:mb-0">
      <div className="mb-1 flex items-center gap-2 text-[12px]">
        <span className="truncate font-medium">{session.repo?.fullName ?? session.remoteUrl}</span>
        <span className="shrink-0 rounded bg-status-uncommitted/15 px-[7px] py-px text-[11px] text-status-uncommitted">
          {session.branch}
        </span>
      </div>

      <Category label="미커밋 파일" count={session.uncommittedFiles.length} Icon={FileDiff}>
        {session.uncommittedFiles.map((f) => (
          <Row key={f.path} onClick={() => onJump([f.path, basename(f.path)])}>
            <span className="min-w-0 flex-1 truncate">{f.path}</span>
            <DiffStat additions={f.additions} deletions={f.deletions} />
          </Row>
        ))}
      </Category>

      <Category label="TODO" count={session.todos.length} Icon={ListTodo}>
        {session.todos.map((t) => (
          <Row key={`${t.path}:${t.line}`} onClick={() => onJump([t.text, `${t.path}:${t.line}`, basename(t.path)])}>
            <span className="min-w-0 flex-1 truncate">{t.text}</span>
            <span className="shrink-0 text-muted-foreground/70">{basename(t.path)}:{t.line}</span>
          </Row>
        ))}
      </Category>

      <Category label="계획" empty={!plan} Icon={NotebookPen}>
        {planLines.map((p) => (
          <Row key={p} onClick={() => onJump([p])}>
            <span className="min-w-0 flex-1 truncate italic">{p}</span>
          </Row>
        ))}
      </Category>

      <Category label="AI 대화" count={session.aiSessions?.length ?? 0} Icon={MessagesSquare}>
        {(session.aiSessions ?? []).flatMap((a) =>
          (a.turns ?? []).map((t, i) => (
            <Row key={`${a.id}:${i}`} onClick={() => onJump([t.prompt.slice(0, 40)])}>
              {/* 답변은 좁은 패널에 다 들어가지 않는다. 마우스를 올리면 보이게 둔다. */}
              <span className="min-w-0 flex-1 truncate" title={t.answer ? `${a.title}\n\n${t.answer}` : a.title}>
                {t.prompt}
              </span>
              <span className="shrink-0 text-muted-foreground/70">{formatTime(t.at)}</span>
            </Row>
          )))}
      </Category>

      {/* 미저장 파일은 초안 본문에 직접 쓰이지 않는다. 아직 디스크에도 없는 것이라 눌러도 이동하지 않는다. */}
      <Category label="미저장 파일" count={(session.unsavedFiles ?? []).length} Icon={Save}>
        {(session.unsavedFiles ?? []).map((f) => (
          <Row key={f.path}>
            <span className="min-w-0 flex-1 truncate">{f.path}</span>
            {f.dirtySince && (
              <span className="shrink-0 tabular-nums text-muted-foreground/70">
                {formatTime(f.dirtySince)}부터
              </span>
            )}
          </Row>
        ))}
      </Category>

      {/* 2026-09-11 부터 모으지 않는다. 그전 기록에만 남아 있다. */}
      {session.editTimeline.length > 0 && (
        <Category label="저장 이벤트 (지난 기록)" count={session.editTimeline.length} Icon={Clock}>
          {session.editTimeline.map((e) => (
            <Row key={e.path}>
              <span className="min-w-0 flex-1 truncate">{e.path}</span>
              <span className="shrink-0 tabular-nums text-muted-foreground/70">
                {e.saveCount}회 · {formatTime(e.lastSavedAt)}
              </span>
            </Row>
          ))}
        </Category>
      )}
    </div>
  )
}

/** 근거의 갈래 — GitHub / VS. */
function Source({ label, count, unit, children }: {
  label: string
  count: number
  unit: string
  children: React.ReactNode
}) {
  return (
    <section className="mb-4 last:mb-0">
      <h3 className="mb-1.5 flex items-baseline gap-1.5 text-[12px] font-semibold">
        {label}
        <span className="font-normal text-muted-foreground">{count}{unit}</span>
      </h3>
      {children}
    </section>
  )
}

/** 갈래 안의 세부 카테고리. 비어 있으면 접힌 채 개수 0 만 보인다. */
function Category({ label, count, empty, Icon, children }: {
  label: string
  /** 몇 건인지. 세는 것이 뜻이 있을 때만 준다 — 계획은 문서 한 통이라 세지 않는다. */
  count?: number
  /** 셀 수 없는 갈래에서 접힘·"없음" 을 가리는 값. count 를 주면 필요 없다. */
  empty?: boolean
  Icon: typeof GitMerge
  children: React.ReactNode
}) {
  const has = !(empty ?? count === 0)
  const [open, setOpen] = useState(has)
  return (
    <div className="mb-1 last:mb-0">
      <button
        type="button"
        onClick={() => has && setOpen(!open)}
        disabled={!has}
        className={cn(
          'flex w-full items-center gap-1.5 rounded py-0.5 text-[12px] text-muted-foreground',
          has ? 'hover:bg-muted/60' : 'cursor-default opacity-50',
        )}
      >
        <ChevronRight className={cn('size-3 shrink-0 transition-transform', open && has && 'rotate-90')} />
        <Icon className="size-3 shrink-0" />
        <span>{label}</span>
        {count !== undefined && <span className="tabular-nums">{count}</span>}
      </button>
      {open && has && <div className="pl-[18px]">{children}</div>}
    </div>
  )
}

function Row({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag
      type={onClick ? 'button' : undefined}
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-2 rounded px-1.5 py-1 text-left text-[12px]',
        onClick && 'hover:bg-muted/60',
      )}
    >
      {children}
    </Tag>
  )
}

function Empty() {
  return <p className="py-1 text-[12px] text-muted-foreground">없음</p>
}

function basename(p: string): string {
  return p.slice(p.lastIndexOf('/') + 1)
}
