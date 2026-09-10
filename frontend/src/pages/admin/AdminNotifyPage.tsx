import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/api/apiClient'
import AdminGuard from './AdminGuard'
import { useChatBotStatus, useGlobalNotify, useSaveGlobalNotify, useTestWebhook } from './api'

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
 * 채널에서 "OOO의 오늘 업무일지 요약해줘" 라고 물으면 답하는 봇의 상태와 설정 안내.
 *
 * 방향이 알림과 반대다 — 알림은 우리가 Mattermost 로 보내고, 이것은 우리가 Mattermost 에
 * 회원으로 로그인해 채널을 읽고 답을 쓴다. Outgoing Webhook 은 Mattermost 관리자가 켜 줘야
 * 해서, 일반 회원 계정 하나면 되는 이 방식을 기본으로 한다.
 */
function ChatQueryGuide() {
  const { data: bot } = useChatBotStatus()

  const state = !bot
    ? null
    : !bot.enabled
      ? { tone: 'muted', text: '꺼짐 — 서버 .env 에 봇 계정이 없습니다' }
      : !bot.connected
        ? { tone: 'bad', text: `로그인 안 됨 — ${bot.baseUrl} 에 ${'계정으로 들어가지 못했습니다. 아이디·비밀번호를 확인해 주세요'}` }
        : bot.channels.length === 0
          ? { tone: 'warn', text: `@${bot.botUsername} 로 로그인됨 — 아직 들어가 있는 채널이 없습니다. 답할 채널에 이 계정을 초대해 주세요` }
          : { tone: 'good', text: `@${bot.botUsername} 로 로그인됨 — 읽는 채널: ${bot.channels.join(', ')}` }

  const toneClass: Record<string, string> = {
    good: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    warn: 'border-amber-200 bg-amber-50 text-amber-800',
    bad: 'border-destructive/30 bg-destructive/5 text-destructive',
    muted: 'border bg-muted/40 text-muted-foreground',
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

        {state && (
          <p className={`rounded border p-2.5 text-[13px] ${toneClass[state.tone]}`}>{state.text}</p>
        )}

        <ol className="list-decimal space-y-1 pl-5 text-[13px] text-muted-foreground">
          <li>Mattermost 에 봇으로 쓸 <b>회원 계정</b>을 하나 만듭니다 (사이드바 "회원 초대"). 본인 계정으로 시험해도 됩니다</li>
          <li>서버 <code className="rounded bg-muted px-1">backend/.env</code> 에{' '}
            <code className="rounded bg-muted px-1">MATTERMOST_BOT_LOGIN_ID</code> ·{' '}
            <code className="rounded bg-muted px-1">MATTERMOST_BOT_PASSWORD</code> 를 적고 재시작</li>
          <li>답을 받을 채널에 그 계정을 <b>초대</b>합니다 — 들어가 있는 채널만 읽습니다</li>
        </ol>
        <p className="text-xs text-muted-foreground">
          서버를 다시 띄운 뒤에 올라온 글에만 답합니다. 봇이 쓴 글과 시스템 메시지는 무시합니다.
        </p>
      </CardContent>
    </Card>
  )
}
