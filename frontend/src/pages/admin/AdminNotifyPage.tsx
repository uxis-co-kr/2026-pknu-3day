import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/api/apiClient'
import AdminGuard from './AdminGuard'
import { useGlobalNotify, useSaveGlobalNotify, useTestWebhook } from './api'

export default function AdminNotifyPage() {
  const { data, isLoading, error } = useGlobalNotify()
  const save = useSaveGlobalNotify()
  const test = useTestWebhook()
  const [url, setUrl] = useState('')
  const [remind, setRemind] = useState(true)
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null)

  async function onTest() {
    setResult(null)
    try {
      await test.mutateAsync(url.trim() || null)
      setResult({ ok: true, message: '채널로 시험 메시지를 보냈습니다. 확인해 주세요.' })
    } catch (e) {
      setResult({
        ok: false,
        message: e instanceof ApiError ? e.message : '전송에 실패했습니다.',
      })
    }
  }

  useEffect(() => {
    if (!data) return
    setUrl(data.mattermostWebhookUrl ?? '')
    setRemind(data.remindUncommitted ?? true)
  }, [data])

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">Mattermost</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            팀 전체가 쓰는 기본 웹훅입니다. 개인이 설정에서 따로 지정하면 그 값이 우선합니다.
          </p>
        </div>

        <Card>
          <CardContent className="space-y-4 p-4">
            {isLoading ? (
              <Skeleton className="h-28" />
            ) : (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="webhook">Incoming Webhook URL</Label>
                  <Input
                    id="webhook"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://mattermost.example.com/hooks/..."
                  />
                  <p className="text-xs text-muted-foreground">
                    Mattermost 메인 메뉴 → 통합 → Incoming Webhooks 에서 만듭니다. 비워 두면
                    알림을 보내지 않습니다. <b>시험 전송</b>은 저장하지 않고 지금 입력한 주소로만
                    보내므로, 저장 전에 주소가 맞는지 확인할 수 있습니다.
                  </p>
                </div>

                <div className="flex items-start justify-between gap-4 border-t pt-4">
                  <div>
                    <p className="text-[13px] font-medium">미커밋 리마인드</p>
                    <p className="mt-0.5 text-[13px] text-muted-foreground">
                      미커밋 변경이 300줄을 넘거나 마지막 커밋이 6시간 지나면 업무 시간에 매시
                      알립니다.
                    </p>
                  </div>
                  <Switch checked={remind} onCheckedChange={setRemind} />
                </div>

                <div className="flex items-center justify-end gap-2 border-t pt-4">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={test.isPending}
                    onClick={() => void onTest()}
                  >
                    {test.isPending ? '보내는 중…' : '시험 전송'}
                  </Button>
                  <Button
                    size="sm"
                    disabled={save.isPending}
                    onClick={() =>
                      save.mutate({ mattermostWebhookUrl: url.trim() || null, remindUncommitted: remind })
                    }
                  >
                    저장
                  </Button>
                </div>

                {result && (
                  <p
                    className={
                      result.ok
                        ? 'rounded border border-emerald-200 bg-emerald-50 p-2.5 text-[13px] text-emerald-800'
                        : 'rounded border border-destructive/30 bg-destructive/5 p-2.5 text-[13px] text-destructive'
                    }
                  >
                    {result.message}
                  </p>
                )}

                <p className="text-xs text-muted-foreground">
                  보내는 것은 셋입니다 — 초안 생성 알림, 확정본 게시(사용자가 버튼을 누를 때),
                  미커밋 리마인드.
                </p>
              </>
            )}
          </CardContent>
        </Card>

        <ChatQueryGuide />
      </div>
    </AdminGuard>
  )
}

/**
 * 채널에서 "OOO의 오늘 업무일지 요약해줘" 라고 물으면 답하는 기능의 설정 안내.
 *
 * 방향이 반대다 — 위의 웹훅은 우리가 Mattermost 로 보내고, 이것은 Mattermost 가 우리를
 * 부른다(Outgoing Webhook). 그래서 서버 쪽 설정은 없고, Mattermost 에 우리 주소를 알려
 * 주기만 하면 된다. 관리자가 복사할 주소를 여기서 보여 준다.
 */
function ChatQueryGuide() {
  // 화면을 연 주소가 곧 사내에서 닿는 주소다. /api 는 프록시가 백엔드로 넘긴다.
  const callbackUrl = `${window.location.origin}/api/chat/mattermost`
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(callbackUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* 클립보드가 막힌 환경이면 그냥 직접 긁어 복사하면 된다 */
    }
  }

  return (
    <Card>
      <CardContent className="space-y-3 p-4">
        <div>
          <p className="text-[13px] font-medium">채널에서 물어보기</p>
          <p className="mt-0.5 text-[13px] text-muted-foreground">
            채널에 <code className="rounded bg-muted px-1">조웅식의 오늘 업무일지 요약해서 보내줘</code> 처럼
            쓰면 그 사람의 그날 업무 일지를 답합니다. 확정본이 있으면 확정본을, 없으면 활동으로
            그 자리에서 만든 요약을 줍니다. 이름 뒤 말투는 자유이고, 날짜는{' '}
            <code className="rounded bg-muted px-1">어제</code>·
            <code className="rounded bg-muted px-1">9월 9일</code>·
            <code className="rounded bg-muted px-1">2026-09-09</code> 를 알아듣습니다.
          </p>
        </div>

        <div className="space-y-1.5">
          <Label>Mattermost 에 알려 줄 주소</Label>
          <div className="flex gap-2">
            <Input readOnly value={callbackUrl} className="font-mono text-[12px]" onFocus={(e) => e.target.select()} />
            <Button size="sm" variant="outline" onClick={() => void copy()}>
              {copied ? '복사됨' : '복사'}
            </Button>
          </div>
        </div>

        <ol className="list-decimal space-y-1 pl-5 text-[13px] text-muted-foreground">
          <li>Mattermost 메인 메뉴 → 통합 → <b>Outgoing Webhooks</b> → 추가</li>
          <li>채널: 답을 받을 채널 · 트리거 단어: 비워 둠(채널을 골랐으면 그 채널의 모든 글이 옵니다) · Callback URL: 위 주소</li>
          <li>만들어지면 나오는 <b>token</b> 을 서버 <code className="rounded bg-muted px-1">.env</code> 의{' '}
            <code className="rounded bg-muted px-1">MATTERMOST_OUTGOING_TOKEN</code> 에 넣고 재시작 — 비워 두면 누구나 부를 수 있습니다</li>
        </ol>
        <p className="text-xs text-muted-foreground">
          위 주소가 <code className="rounded bg-muted px-1">192.168.</code> 같은 사내 주소이면 Mattermost 가 기본으로
          막습니다. 시스템 콘솔 → 환경 → 웹 서버 → <b>신뢰할 수 없는 내부 연결 허용</b>에 이 서버의 IP 를
          적어야 합니다.
        </p>
      </CardContent>
    </Card>
  )
}
