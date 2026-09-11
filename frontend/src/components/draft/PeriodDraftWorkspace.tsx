import { useEffect, useRef, useState } from 'react'
import { Loader2, Sparkles } from 'lucide-react'
import MarkdownPreview from '@/components/draft/MarkdownPreview'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/api/apiClient'
import {
  useDraft, useDraftsByKind, useGeneratePeriodDraft, useNotifyDraft, useRepos, useSaveDraft,
} from '@/api/hooks'
import { addDays, formatTime, mondayOf, todayKst, weekLabel } from '@/lib/date'
import type { DraftSummary } from '@/types/api'

const TAB =
  'h-[30px] rounded-none border-b-2 border-transparent px-3.5 text-[13px] shadow-none'
  + ' data-[state=active]:border-primary data-[state=active]:bg-transparent'
  + ' data-[state=active]:text-primary data-[state=active]:shadow-none'

/**
 * 주간·저장소별 업무일지 작성.
 *
 * <p>하루치({@link DraftWorkspace})와 쓰는 방식이 같다 — AI 로 만들고, 고쳐 저장하고,
 * Mattermost 로 보낸다. 다른 것은 <b>무엇을 골라 만드느냐</b>뿐이라 그 부분만 위에 둔다.
 *
 * <p>하루치와 파일을 나눈 이유: 그쪽은 날짜 하나를 축으로 도는 화면이고 담당자 1 영역이다.
 * 기간과 저장소를 끼워 넣으면 양쪽이 다 복잡해진다.
 */
export default function PeriodDraftWorkspace({
  kind, userId, displayName,
}: {
  kind: 'weekly' | 'repo'
  userId?: number
  displayName?: string | null
}) {
  const today = todayKst()
  // 주를 단위로 쓴다 — 같은 주를 조금씩 다르게 잡으면 일지가 쌓인다. 고른 날이 든 주(월~일)다.
  const [week, setWeek] = useState(mondayOf(today))
  const [repoId, setRepoId] = useState<string>('')
  const [mineOnly, setMineOnly] = useState(true)

  const [openId, setOpenId] = useState<number | undefined>(undefined)
  const [tab, setTab] = useState<'edit' | 'preview'>('edit')
  const [content, setContent] = useState('')
  const [message, setMessage] = useState<{ text: string; failed: boolean } | null>(null)
  const [confirmNotify, setConfirmNotify] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const { data: repos } = useRepos()
  const listKind = kind === 'weekly' ? 'WEEKLY' : 'REPO'
  const list = useDraftsByKind({ from: addDays(today, -120), to: today, kind: listKind, userId }, Boolean(userId))
  const { data: draft } = useDraft(openId)

  const generate = useGeneratePeriodDraft()
  const save = useSaveDraft()
  const notify = useNotifyDraft()
  const busy = generate.isPending || save.isPending || notify.isPending

  useEffect(() => {
    if (draft) setContent(draft.contentMd)
  }, [draft])

  useEffect(() => {
    if (!message) return
    const t = setTimeout(() => setMessage(null), message.failed ? 6000 : 2500)
    return () => clearTimeout(t)
  }, [message])

  // 저장소를 처음 고를 때 첫 번째를 채워 둔다. 빈 값으로 두면 만들기를 눌러야 이유를 안다.
  useEffect(() => {
    if (kind === 'repo' && !repoId && repos && repos.length > 0) setRepoId(String(repos[0].id))
  }, [kind, repoId, repos])

  const dirty = draft ? content !== draft.contentMd : false

  async function run(action: () => Promise<unknown>, ok: string) {
    setMessage(null)
    try {
      await action()
      setMessage({ text: ok, failed: false })
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : '요청에 실패했습니다.', failed: true })
    }
  }

  async function onGenerate() {
    const made = await generate.mutateAsync({
      kind,
      date: week,
      userId,
      repoId: kind === 'repo' ? Number(repoId) : undefined,
      mineOnly: kind === 'repo' ? mineOnly : undefined,
    })
    setOpenId(made.id)
    setContent(made.contentMd)
  }

  /**
   * 전송 — 아직 저장 전이거나 고친 것이 남아 있으면 <b>저장부터</b> 한다.
   *
   * <p>서버는 한 번도 저장하지 않은 일지를 409 로 막는다 (자동 생성 그대로를 채널에 흘리지
   * 않으려는 규칙이다). 그 규칙은 그대로 두고, 사람이 버튼을 두 번 누르지 않아도 되게 여기서 잇는다.
   */
  async function sendNow() {
    if (!draft) return
    if (dirty || !draft.userEdited) {
      await save.mutateAsync({ id: draft.id, contentMd: content })
    }
    await notify.mutateAsync({ id: draft.id })
  }

  const rows = list.data ?? []

  return (
    <div className="space-y-4">
      {/* 무엇을 모아 만들지 */}
      <Card className="flex flex-wrap items-end gap-2 p-3">
        <label className="flex flex-col gap-1 text-[12px] text-muted-foreground">
          주 고르기 (그 주의 아무 날)
          <Input type="date" value={week} max={today} className="h-[34px] w-[170px] text-[13px]"
            onChange={(e) => setWeek(mondayOf(e.target.value))} />
        </label>
        <div className="flex flex-col gap-1 text-[12px] text-muted-foreground">
          기간
          <div className="flex h-[34px] items-center gap-1">
            <Button variant="outline" size="sm" className="h-[34px] px-2"
              onClick={() => setWeek(addDays(week, -7))}>◀</Button>
            <span className="w-[210px] text-center text-[13px] tabular-nums text-foreground">
              {weekLabel(week)}
            </span>
            <Button variant="outline" size="sm" className="h-[34px] px-2"
              disabled={addDays(week, 7) > today}
              onClick={() => setWeek(addDays(week, 7))}>▶</Button>
          </div>
        </div>

        {kind === 'repo' && (
          <>
            <label className="flex flex-col gap-1 text-[12px] text-muted-foreground">
              저장소
              <Select value={repoId} onValueChange={setRepoId}>
                <SelectTrigger className="h-[34px] w-[260px] text-[13px]">
                  <SelectValue placeholder="저장소를 고르세요" />
                </SelectTrigger>
                <SelectContent>
                  {(repos ?? []).map((r) => (
                    <SelectItem key={r.id} value={String(r.id)}>{r.fullName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <label className="flex h-[34px] items-center gap-1.5 text-[13px] text-muted-foreground">
              <input type="checkbox" checked={mineOnly} onChange={(e) => setMineOnly(e.target.checked)} />
              내 활동만
            </label>
          </>
        )}

        <Button
          size="sm"
          className="h-[34px]"
          disabled={busy || (kind === 'repo' && !repoId)}
          onClick={() => void run(onGenerate, 'AI 로 만들었습니다')}
        >
          {generate.isPending ? <Loader2 className="animate-spin" /> : <Sparkles />}
          {generate.isPending ? 'AI 가 쓰는 중…' : 'AI 생성'}
        </Button>

        <span className="ml-auto text-[12px] text-muted-foreground">
          {kind === 'weekly'
            ? '그 주(월~일)의 하루치 일지를 묶어 다시 씁니다. 같은 주에 다시 만들면 버전만 올라갑니다'
            : '그 주(월~일) 그 저장소의 커밋·PR 을 묶어 씁니다. 같은 주에 다시 만들면 버전만 올라갑니다'}
        </span>
      </Card>

      {message && (
        <p className={message.failed
          ? 'rounded border border-destructive/30 bg-destructive/5 p-2.5 text-[13px] text-destructive'
          : 'rounded border border-emerald-200 bg-emerald-50 p-2.5 text-[13px] text-emerald-800'}>
          {message.text}
        </p>
      )}

      {/* 편집기 */}
      {openId !== undefined && draft && (
        <Card className="p-0">
          <div className="flex items-center justify-between border-b px-4 py-2.5">
            <div className="min-w-0">
              <p className="truncate text-[13px] font-medium">
                {draft.repoFullName ? `${draft.repoFullName} · ` : ''}
                {draft.periodStart} ~ {draft.periodEnd}
                <span className="ml-2 font-normal text-muted-foreground">v{draft.version}</span>
              </p>
              <p className="text-[12px] text-muted-foreground">
                {displayName ?? ''} · 마지막 저장 {formatTime(draft.updatedAt)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Tabs value={tab} onValueChange={(v) => setTab(v as 'edit' | 'preview')}>
                <TabsList className="h-auto rounded-none border-0 bg-transparent p-0">
                  <TabsTrigger value="edit" className={TAB}>편집</TabsTrigger>
                  <TabsTrigger value="preview" className={TAB}>미리보기</TabsTrigger>
                </TabsList>
              </Tabs>
              {/*
                * 고친 것이 없어도 저장할 수 있다. AI 가 쓴 그대로를 "내 일지로 받아들이는" 것도
                * 뜻이 있는 행동이고, 그래야 전송이 열린다 (서버는 userEdited 를 본다).
                */}
              <Button size="sm" className="h-[34px]" disabled={busy}
                onClick={() => void run(() => save.mutateAsync({ id: draft.id, contentMd: content }), '저장했습니다')}>
                저장
              </Button>
              <Button
                variant="outline" size="sm" className="h-[34px]"
                disabled={busy}
                onClick={() => setConfirmNotify(true)}
              >
                Mattermost 전송
              </Button>
            </div>
          </div>
          {/*
            * AI 생성만으로는 전송할 수 없다 — 서버가 409 로 막고(DRAFT_NOT_EDITED) 버튼도 잠근다.
            * 왜 잠겼는지 적어 두지 않으면 "눌러도 안 된다" 로 읽히고, 반대로 전송이 저절로
            * 나간 것처럼 오해하기도 한다.
            */}
          {(dirty || !draft.userEdited) && (
            <p className="border-b bg-muted/40 px-4 py-2 text-[12px] text-muted-foreground">
              {dirty ? '고친 내용이 아직 저장되지 않았습니다. ' : 'AI 가 쓴 그대로입니다. '}
              전송을 누르면 <b>저장한 뒤에</b> 보냅니다.
            </p>
          )}
          <div className="p-4">
            {tab === 'edit' ? (
              <Textarea
                ref={textareaRef}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                className="min-h-[420px] font-mono text-[13px]"
              />
            ) : (
              <MarkdownPreview source={content} />
            )}
          </div>
        </Card>
      )}

      {/* 지난 것 */}
      <Card className="p-0">
        <p className="border-b px-4 py-2.5 text-[13px] font-medium">
          {kind === 'weekly' ? '주간' : '저장소별'} 업무일지 — 주마다 한 건
        </p>
        {rows.length === 0 ? (
          <p className="px-4 py-6 text-center text-[13px] text-muted-foreground">
            아직 만든 것이 없습니다. 위에서 기간을 고르고 AI 생성을 눌러 보세요.
          </p>
        ) : (
          <div className="divide-y">
            {rows.map((d) => <PastRow key={d.id} draft={d} active={d.id === openId} onClick={() => setOpenId(d.id)} />)}
          </div>
        )}
      </Card>

      <Dialog open={confirmNotify} onOpenChange={setConfirmNotify}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle className="text-base">관리자에게 알리기</DialogTitle>
            <DialogDescription>
              관리자에게 업무 일지 요약이 완료되었음을 알리시겠습니까?
            </DialogDescription>
          </DialogHeader>
          <p className="text-[13px] text-muted-foreground">
            관리자가 요약본을 받도록 정해 둔 채팅방으로 알림이 갑니다. 일지 본문은 보내지 않습니다.
          </p>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setConfirmNotify(false)}>취소</Button>
            <Button size="sm" disabled={busy} onClick={() => {
              setConfirmNotify(false)
              if (draft) void run(sendNow, '관리자에게 알렸습니다')
            }}>
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PastRow({ draft, active, onClick }: { draft: DraftSummary; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-2 px-4 py-2.5 text-left transition-colors hover:bg-muted/50${active ? ' bg-muted' : ''}`}
    >
      <span className="text-[13px] font-medium">
        {draft.repoFullName ? `${draft.repoFullName} · ` : ''}
        {draft.periodStart} ~ {draft.periodEnd}
      </span>
      <span className="text-[12px] text-muted-foreground">v{draft.version}</span>
      <span className="ml-auto text-[12px] text-muted-foreground">
        {draft.userEdited ? '저장함' : '생성만 함'}
      </span>
    </button>
  )
}
