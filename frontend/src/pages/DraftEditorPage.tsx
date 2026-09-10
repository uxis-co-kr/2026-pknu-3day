import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import EvidencePanel from '@/components/draft/EvidencePanel'
import MarkdownPreview from '@/components/draft/MarkdownPreview'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { useDraft, useGenerateDraft, useNotifyDraft, useSaveDraft } from '@/api/hooks'
import { ApiError } from '@/api/apiClient'
import { formatTime } from '@/lib/date'
import { cn } from '@/lib/utils'

export default function DraftEditorPage() {
  const { id } = useParams()
  const draftId = Number(id)
  const navigate = useNavigate()

  const { data: draft, isLoading } = useDraft(Number.isFinite(draftId) ? draftId : undefined)
  const save = useSaveDraft()
  const notify = useNotifyDraft()
  const regenerate = useGenerateDraft()

  const [tab, setTab] = useState<'edit' | 'preview'>('edit')
  const [content, setContent] = useState('')
  const [message, setMessage] = useState<{ text: string; failed: boolean } | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (!draft) return
    setContent(draft.contentMd)
    // 완료 개념을 없앴다 (9/10 결정). 늘 편집 탭으로 연다.
    setTab(draft.status === 'CONFIRMED' ? 'preview' : 'edit')
  }, [draft])

  useEffect(() => {
    if (!message) return
    // 실패 안내는 읽을 시간이 더 필요하다 (webhook URL 을 등록하라는 안내가 온다).
    const t = setTimeout(() => setMessage(null), message.failed ? 6000 : 2500)
    return () => clearTimeout(t)
  }, [message])

  const dirty = draft !== undefined && content !== draft.contentMd

  /** 근거 패널의 행을 누르면 본문에서 그 근거가 적힌 줄을 찾아 선택해 준다. */
  function jumpTo(needle: string) {
    setTab('edit')
    requestAnimationFrame(() => {
      const el = textareaRef.current
      if (!el) return
      const at = content.indexOf(needle)
      if (at < 0) return
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
      setMessage({
        text: e instanceof ApiError ? e.message : '요청에 실패했습니다.',
        failed: true,
      })
    }
  }

  if (isLoading || !draft) {
    return (
      <div className="grid grid-cols-[672px_1fr] gap-4">
        <Skeleton className="h-[795px] rounded-lg" />
        <Skeleton className="h-[795px] rounded-lg" />
      </div>
    )
  }

  const sourceActivities = draft.sourceActivities ?? []
  const sourceSessions = draft.sourceSessions ?? []
  const author = sourceActivities[0]?.user?.name
  const busy = save.isPending || notify.isPending || regenerate.isPending

  return (
    <div className="grid h-[795px] grid-cols-[672px_1fr] gap-4">
      <Card className="flex flex-col overflow-hidden rounded-lg shadow-none">
        <div className="flex h-[52px] shrink-0 items-center gap-3 border-b px-5">
          <h1 className="text-[15px] font-semibold">
            {draft.workDate} 업무 일지{author ? ` — ${author}` : ''}
          </h1>
          <span className="text-[12px] text-muted-foreground">
            v{draft.version} ·{' '}
            {`마지막 저장 ${formatTime(draft.updatedAt)}`}
          </span>
          {message && (
            <span
              className={cn(
                'text-[12px] font-medium',
                message.failed ? 'text-status-failed' : 'text-primary',
              )}
            >
              {message.text}
            </span>
          )}
        </div>

        <Tabs value={tab} onValueChange={(v) => setTab(v as 'edit' | 'preview')} className="flex min-h-0 flex-1 flex-col">
          <div className="h-[41px] shrink-0 border-b px-5">
            <TabsList className="h-[41px] gap-1 bg-transparent p-0">
              <TabsTrigger value="edit" className="h-[30px] rounded-none border-b-2 border-transparent px-3.5 text-[13px] shadow-none data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:text-primary data-[state=active]:shadow-none">
                편집
              </TabsTrigger>
              <TabsTrigger value="preview" className="h-[30px] rounded-none border-b-2 border-transparent px-3.5 text-[13px] shadow-none data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:text-primary data-[state=active]:shadow-none">
                미리보기
              </TabsTrigger>
            </TabsList>
          </div>

          <div className="min-h-0 flex-1 p-5">
            {tab === 'edit' ? (
              <Textarea
                ref={textareaRef}
                value={content}
                
                onChange={(e) => setContent(e.target.value)}
                className="h-full resize-none text-[13px] leading-6 read-only:bg-muted/30"
              />
            ) : (
              <div className="h-full overflow-y-auto rounded-md border p-5">
                <MarkdownPreview source={content} />
              </div>
            )}
          </div>
        </Tabs>

        <div className="flex h-[59px] shrink-0 items-center justify-between border-t px-5">
          <Button
            variant="outline" size="sm" className="h-[34px] gap-1.5"
            disabled={busy}
            onClick={() => void run(async () => {
              const next = await regenerate.mutateAsync({ date: draft.workDate, userId: draft.userId })
              if (next && 'id' in next) navigate(`/drafts/${next.id}`)
            }, 'AI 가 다시 썼습니다')}
          >
            <Sparkles /> AI 생성
          </Button>

          <div className="flex items-center gap-2">
            <Button
              size="sm" className="h-[34px]"
              disabled={!dirty || busy}
              onClick={() => void run(() => save.mutateAsync({ id: draft.id, contentMd: content }), '저장했습니다')}
            >
              저장
            </Button>
            <Button
              variant="outline" size="sm" className="h-[34px]"
              disabled={busy || !draft.userEdited || dirty}
              title={
                !draft.userEdited
                  ? '한 번 저장한 뒤에 보낼 수 있습니다'
                  : dirty
                    ? '먼저 저장해 주세요'
                    : undefined
              }
              onClick={() => void run(() => notify.mutateAsync({ id: draft.id }), 'Mattermost로 보냈습니다')}
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
