import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, Loader2, Sparkles } from 'lucide-react'
import EvidencePanel from '@/components/draft/EvidencePanel'
import MarkdownPreview from '@/components/draft/MarkdownPreview'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { useCreateBlankDraft, useDraft, useGenerateDraft, useNotifyDraft, useSaveDraft } from '@/api/hooks'
import { ApiError } from '@/api/apiClient'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'
import type { Activity, VscodeSession } from '@/types/api'

const TAB =
  'h-[30px] rounded-none border-b-2 border-transparent px-3.5 text-[13px] shadow-none'
  + ' data-[state=active]:border-primary data-[state=active]:bg-transparent'
  + ' data-[state=active]:text-primary data-[state=active]:shadow-none'

/** 서버의 DraftTemplate.blank 와 같은 모양. 저장하면 서버가 만든 것과 이어진다. */
function blankTemplate(workDate: string, displayName?: string | null): string {
  return `# ${workDate} 업무 일지 — ${displayName ?? ''}\n\n`
    + '## 완료한 작업\n- \n\n## 진행 중 / 미커밋\n- \n\n## 계획 / TODO\n- \n\n## 메모\n'
}

/**
 * 업무 일지 작업 화면 — 왼쪽 편집기, 오른쪽 근거.
 *
 * <p>업무 일지 작성 탭의 "오늘 일지" 와 편집 화면(/drafts/:id)이 **같은 컴포넌트**를 쓴다.
 * 두 곳을 따로 만들었더니 탭 모양·버튼 자리·여백이 조금씩 어긋났다 (9/10 지적).
 */
export default function DraftWorkspace({
  draftId, workDate, userId, displayName, evidence, canGenerate = true, onGenerated, onBack,
}: {
  /** 없으면 아직 만들어지지 않은 일지다. 빈 편집기를 띄우고, 저장할 때 서버에 만든다. */
  draftId?: number
  /** draftId 가 없을 때 필요하다. 있으면 초안이 자기 날짜를 안다. */
  workDate?: string
  userId?: number
  displayName?: string | null
  /** 일지가 없을 때 근거 패널에 보여 줄 그날의 기록. */
  evidence?: { activities: Activity[]; sessions: VscodeSession[] }
  /** 그날 기록이 없으면 AI 가 쓸 재료가 없다. */
  canGenerate?: boolean
  /** AI 생성으로 새 버전이 생겼을 때. 편집 화면은 그 초안으로 이동한다. */
  onGenerated?: (nextId: number) => void
  /** 주면 머리에 뒤로 가기가 생긴다. 지난 일지를 열었을 때 목록으로 돌아가는 길이다. */
  onBack?: () => void
}) {
  const { data: draft, isLoading } = useDraft(draftId)
  const save = useSaveDraft()
  const notify = useNotifyDraft()
  const regenerate = useGenerateDraft()
  const createBlank = useCreateBlankDraft()

  const [tab, setTab] = useState<'edit' | 'preview'>('edit')
  const [content, setContent] = useState('')
  const [message, setMessage] = useState<{ text: string; failed: boolean } | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (draft) setContent(draft.contentMd)
    // 아직 일지가 없으면 머리말만 둔 뼈대에서 시작한다. 저장하기 전에는 서버에 아무것도 없다.
    else if (!draftId && workDate) setContent(blankTemplate(workDate, displayName))
  }, [draft, draftId, workDate, displayName])

  useEffect(() => {
    if (!message) return
    // 실패 안내는 읽을 시간이 더 필요하다 (webhook URL 을 등록하라는 안내가 온다).
    const t = setTimeout(() => setMessage(null), message.failed ? 6000 : 2500)
    return () => clearTimeout(t)
  }, [message])

  const dirty = draft
    ? content !== draft.contentMd
    : content.trim() !== blankTemplate(workDate ?? '', displayName).trim()

  /**
   * AI 가 쓴 초안은 아직 <b>사람이 받아들인 적이 없다</b> (`userEdited === false`).
   *
   * <p>서버는 사람이 한 번 저장한 일지만 Mattermost 로 내보낸다 — 자동 생성 그대로를 채널에
   * 흘리지 않기 위해서다 (V6). 그런데 저장 버튼을 "고친 데가 있을 때" 로만 열어 두면, AI 생성
   * 직후에는 저장도 전송도 잠긴다. 고칠 데가 없는 초안은 아무 글자나 쳤다 지워야 풀렸다.
   *
   * <p>그래서 아직 받아들이지 않은 초안은 <b>고친 데가 없어도 한 번은 저장할 수 있다.</b>
   * 그 저장이 곧 "이대로 쓰겠다" 는 표시다.
   */
  const needsAccept = Boolean(draft && !draft.userEdited)

  /** 일지가 없으면 저장할 때 만든다 — 열어만 보고 나간 날에 빈 일지가 쌓이지 않게. */
  async function saveContent() {
    const id = draft?.id ?? (await createBlank.mutateAsync(workDate ?? '')).id
    await save.mutateAsync({ id, contentMd: content })
  }

  /**
   * 근거 행을 누르면 본문에서 그 줄을 찾아 선택한다.
   *
   * <p>후보를 여러 개 받아 **먼저 걸리는 것**을 쓴다. 본문에는 짧은 sha 가 들어가는데
   * 근거 목록은 40자 전체 sha 를 들고 있어, 하나만 찾으면 커밋 행이 늘 빗나갔다.
   * AI 가 쓴 본문은 표기가 더 자유로워 후보가 여럿 필요하다.
   */
  function jumpTo(needles: string[]) {
    const at = needles.reduce(
      (found, n) => (found >= 0 ? found : n ? content.indexOf(n) : -1),
      -1,
    )
    if (at < 0) {
      setMessage({ text: '본문에서 그 근거를 찾지 못했습니다', failed: true })
      return
    }
    setTab('edit')
    requestAnimationFrame(() => {
      const el = textareaRef.current
      if (!el) return
      const lineStart = content.lastIndexOf('\n', at) + 1
      let lineEnd = content.indexOf('\n', at)
      if (lineEnd < 0) lineEnd = content.length
      el.focus()
      el.setSelectionRange(lineStart, lineEnd)
      const ratio = lineStart / Math.max(1, content.length)
      el.scrollTop = ratio * el.scrollHeight - el.clientHeight / 2
    })
  }

  async function run(action: () => Promise<unknown>, ok: string) {
    try {
      await action()
      setMessage({ text: ok, failed: false })
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : '요청에 실패했습니다.', failed: true })
    }
  }

  if (draftId && (isLoading || !draft)) {
    return (
      <div className="grid grid-cols-[672px_1fr] gap-4">
        <Skeleton className="h-[795px] rounded-lg" />
        <Skeleton className="h-[795px] rounded-lg" />
      </div>
    )
  }

  const sourceActivities = draft?.sourceActivities ?? evidence?.activities ?? []
  const sourceSessions = draft?.sourceSessions ?? evidence?.sessions ?? []
  const author = draft ? sourceActivities[0]?.user?.name : displayName
  const busy = save.isPending || notify.isPending || regenerate.isPending || createBlank.isPending

  return (
    <div className="grid h-[795px] grid-cols-[672px_1fr] gap-4">
      <Card className="flex flex-col overflow-hidden rounded-lg shadow-none">
        <div className="flex h-[52px] shrink-0 items-center gap-3 border-b px-5">
          {onBack && (
            <Button variant="ghost" size="icon" className="-ml-2 size-8 shrink-0"
              onClick={onBack} aria-label="지난 업무 일지 목록으로">
              <ArrowLeft />
            </Button>
          )}
          <h1 className="text-[15px] font-semibold">
            {draft?.workDate ?? workDate} 업무 일지{author ? ` — ${author}` : ''}
          </h1>
          <span className="text-[12px] text-muted-foreground">
            {draft
              ? `v${draft.version} · ${dirty ? '저장하지 않은 변경' : `마지막 저장 ${formatTime(draft.updatedAt)}`}`
              : '아직 저장하지 않았습니다'}
          </span>
          {message && (
            <span className={cn('text-[12px] font-medium', message.failed ? 'text-status-failed' : 'text-primary')}>
              {message.text}
            </span>
          )}
        </div>

        <Tabs value={tab} onValueChange={(v) => setTab(v as 'edit' | 'preview')} className="flex min-h-0 flex-1 flex-col">
          <div className="h-[41px] shrink-0 border-b px-5">
            <TabsList className="h-[41px] gap-1 bg-transparent p-0">
              <TabsTrigger value="edit" className={TAB}>편집</TabsTrigger>
              <TabsTrigger value="preview" className={TAB}>미리보기</TabsTrigger>
            </TabsList>
          </div>

          <div className="relative min-h-0 flex-1 p-5">
            {regenerate.isPending && (
              <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 rounded-md bg-background/70 text-[13px] text-muted-foreground backdrop-blur-[1px]">
                <Loader2 className="size-4 animate-spin" />
                AI 가 오늘 기록을 읽고 일지를 쓰는 중입니다…
              </div>
            )}
            {tab === 'edit' ? (
              <Textarea
                ref={textareaRef}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                className="h-full resize-none text-[13px] leading-6"
              />
            ) : (
              <div className="h-full overflow-y-auto rounded-md border p-5">
                <MarkdownPreview source={content} />
              </div>
            )}
          </div>
        </Tabs>

        <div className="flex h-[59px] shrink-0 items-center justify-between border-t px-5">
          <div className="flex items-center gap-2">
          <Button
            variant="outline" size="sm" className="h-[34px] gap-1.5"
            disabled={busy || !canGenerate}
            title={canGenerate
              ? '지금까지의 깃허브·VS 기록으로 AI 가 다시 씁니다. 쓴 내용은 새 버전으로 남습니다'
              : undefined}
            onClick={() => void run(async () => {
              const next = await regenerate.mutateAsync({
                date: draft?.workDate ?? workDate ?? '',
                userId: draft?.userId ?? userId ?? 0,
              })
              if (next && 'id' in next) onGenerated?.(next.id)
            }, 'AI 가 다시 썼습니다')}
          >
            {/* 모델이 하루치를 읽고 쓰는 데 시간이 걸린다. 도는 중인지 보이지 않으면 다시 누른다. */}
            {regenerate.isPending
              ? <><Loader2 className="animate-spin" /> AI 가 쓰는 중…</>
              : <><Sparkles /> AI 생성</>}
          </Button>
          {/* 왜 못 누르는지 버튼 옆에 둔다. 잠긴 버튼만 있으면 고장으로 보인다. */}
          {!canGenerate && (
            <span className="text-[12px] text-muted-foreground">
              오늘 커밋도 VS 기록도 없어 AI 가 쓸 재료가 없습니다
            </span>
          )}
          </div>

          <div className="flex items-center gap-2">
            <Button
              size="sm" className="h-[34px]"
              disabled={(!dirty && !needsAccept) || busy}
              title={!dirty && needsAccept
                ? 'AI 가 쓴 그대로 저장합니다. 저장해야 Mattermost 로 보낼 수 있습니다'
                : undefined}
              onClick={() => void run(saveContent, '저장했습니다')}
            >
              {/* 고친 데가 없는데 눌리는 이유를 글자로 말해 준다 — "저장" 만 있으면 고장으로 보인다. */}
              {!dirty && needsAccept ? '이대로 저장' : '저장'}
            </Button>
            <Button
              variant="outline" size="sm" className="h-[34px]"
              disabled={busy || !draft?.userEdited || dirty}
              title={
                !draft?.userEdited ? '왼쪽 저장을 누른 뒤에 보낼 수 있습니다'
                  : dirty ? '먼저 저장해 주세요' : undefined
              }
              onClick={() => draft && void run(() => notify.mutateAsync({ id: draft.id }), 'Mattermost로 보냈습니다')}
            >
              Mattermost 전송
            </Button>
          </div>
        </div>
      </Card>

      <EvidencePanel activities={sourceActivities} sessions={sourceSessions} onJump={jumpTo} />
    </div>
  )
}
