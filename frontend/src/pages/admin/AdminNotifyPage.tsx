import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { ApiError } from '@/api/apiClient'
import AdminGuard from './AdminGuard'
import {
  useChatBotSettings,
  useChatBotStatus,
  useConnectChatBot,
  useDisconnectChatBot,
  useGlobalNotify,
  useSaveGlobalNotify,
  useSetChannelWatching,
  useSetPrimaryChannel,
  useTestWebhook,
} from './api'
import type { ChatBotChannel, ChatBotStatus } from './types'

/** Select 는 빈 문자열 값을 받지 않는다 — '정하지 않음' 의 자리표시 값. */
const NONE = '__none__'

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

        <ChatBotSection />
      </div>
    </AdminGuard>
  )
}

/**
 * 채널에서 "OOO의 오늘 업무일지 요약해줘" 라고 물으면 답하는 봇 — 연결과 채널.
 *
 * 방향이 알림과 반대다. 알림은 우리가 Mattermost 로 보내고, 이것은 우리가 Mattermost 에
 * 회원으로 로그인해 채널을 읽고 답을 쓴다. Outgoing Webhook 은 Mattermost 관리자가 켜 줘야
 * 해서, 일반 회원 계정 하나면 되는 이 방식을 기본으로 한다.
 *
 * 관리자가 여기서 하는 일은 셋이다 — 계정을 넣고 [연결], 붙었는지 보기, 어느 채널을 읽을지 고르기.
 */
function ChatBotSection() {
  const { data: bot } = useChatBotStatus()
  const { data: saved } = useChatBotSettings()
  const connect = useConnectChatBot()
  const disconnect = useDisconnectChatBot()
  const setWatching = useSetChannelWatching()
  const setPrimary = useSetPrimaryChannel()
  const [primaryResult, setPrimaryResult] = useState<{ ok: boolean; message: string } | null>(null)

  async function onPickPrimary(value: string) {
    setPrimaryResult(null)
    try {
      const status = await setPrimary.mutateAsync(value === NONE ? null : value)
      const picked = status.channels.find((c) => c.primary)
      setPrimaryResult({
        ok: true,
        message: picked
          ? `${channelLabel(picked)} 을(를) 대표 채널로 정했습니다. 사원이 [Mattermost 전송]을 누르면 이 채널로 알림이 갑니다.`
          : '대표 채널을 해제했습니다. 알림은 위의 웹훅 주소로 갑니다.',
      })
    } catch (e) {
      setPrimaryResult({ ok: false, message: e instanceof ApiError ? e.message : '대표 채널을 정하지 못했습니다.' })
    }
  }

  const [baseUrl, setBaseUrl] = useState('')
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null)

  useEffect(() => {
    if (!saved) return
    setBaseUrl(saved.baseUrl ?? '')
    setLoginId(saved.loginId ?? '')
  }, [saved])

  async function onConnect() {
    setResult(null)
    try {
      const status = await connect.mutateAsync({
        baseUrl: baseUrl.trim(),
        loginId: loginId.trim(),
        password: password || null,
      })
      setPassword('')
      setResult({
        ok: true,
        message: `@${status.botUsername} 로 로그인했습니다. 채널 ${status.channels.length}곳을 읽습니다.`,
      })
    } catch (e) {
      setResult({ ok: false, message: e instanceof ApiError ? e.message : '연결에 실패했습니다.' })
    }
  }

  async function onDisconnect() {
    setResult(null)
    await disconnect.mutateAsync()
    setResult({ ok: true, message: '연결을 끊었습니다. 설정은 남아 있어 다시 [연결]하면 됩니다.' })
  }

  return (
    <Card>
      <CardContent className="space-y-4 p-4">
        <div>
          <p className="text-[13px] font-medium">채널에서 물어보기</p>
          <p className="mt-0.5 text-[13px] text-muted-foreground">
            채널에 <code className="rounded bg-muted px-1">조웅식의 오늘 업무일지 요약해서 보내줘</code> 처럼
            쓰면 그 사람의 그날 업무 일지를 답합니다. 확정본이 있으면 확정본을, 없으면 활동으로
            그 자리에서 만든 요약을 줍니다. 날짜는{' '}
            <code className="rounded bg-muted px-1">어제</code>·
            <code className="rounded bg-muted px-1">9월 9일</code>·
            <code className="rounded bg-muted px-1">2026-09-09</code> 를 알아듣습니다.
          </p>
        </div>

        <BotStatusBanner bot={bot} />

        {/* 연결 */}
        <div className="space-y-3 border-t pt-4">
          <div className="grid gap-3 sm:grid-cols-[1fr_1fr]">
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="mm-url">Mattermost 서버 주소</Label>
              <Input id="mm-url" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="http://mattermost.example.com:8065" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="mm-id">봇으로 쓸 계정 아이디</Label>
              <Input id="mm-id" value={loginId} onChange={(e) => setLoginId(e.target.value)}
                placeholder="worklog-bot" autoComplete="off" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="mm-pw">비밀번호</Label>
              <Input id="mm-pw" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder={saved?.passwordSet ? '저장돼 있음 — 바꿀 때만 입력' : ''} autoComplete="new-password" />
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            일반 회원 계정이면 됩니다 — Mattermost 관리자 권한이나 통합 메뉴가 필요 없습니다.
            비밀번호는 암호화해 저장하고 화면에 다시 보여 주지 않습니다.
            {saved?.source === 'env' && ' 지금 값은 서버 .env 에서 왔습니다. 여기서 저장하면 그 값을 대신합니다.'}
          </p>

          <div className="flex items-center justify-end gap-2">
            {bot?.enabled && (
              <Button size="sm" variant="outline" disabled={disconnect.isPending} onClick={() => void onDisconnect()}>
                연결 끊기
              </Button>
            )}
            <Button size="sm" disabled={connect.isPending || !baseUrl.trim() || !loginId.trim()}
              onClick={() => void onConnect()}>
              {connect.isPending ? '로그인 중…' : bot?.connected ? '다시 연결' : '연결'}
            </Button>
          </div>

          {result && (
            <p className={result.ok
              ? 'rounded border border-emerald-200 bg-emerald-50 p-2.5 text-[13px] text-emerald-800'
              : 'rounded border border-destructive/30 bg-destructive/5 p-2.5 text-[13px] text-destructive'}>
              {result.message}
            </p>
          )}
        </div>

        {/* 대표 채널 — 사원이 [Mattermost 전송] 을 누르면 "요약되었습니다" 알림이 가는 곳 */}
        <div className="space-y-2 border-t pt-4">
          <p className="text-[13px] font-medium">대표 채널</p>
          <p className="text-[13px] text-muted-foreground">
            사원이 업무 일지를 저장하고 <b>Mattermost 전송</b>을 누르면
            &quot;OOO의 오늘 업무일지가 요약되었습니다&quot; 알림이 <b>이 채널 하나</b>로 갑니다.
            봇 계정이 들어가 있는 채널 중에서 고릅니다. 정하지 않으면 위의 웹훅 주소로 갑니다.
          </p>
          {!bot?.connected ? (
            <p className="text-[13px] text-muted-foreground">봇을 먼저 연결하면 고를 수 있습니다.</p>
          ) : (
            <Select
              value={bot.primaryChannelId ?? NONE}
              onValueChange={(v) => void onPickPrimary(v)}
              disabled={setPrimary.isPending}
            >
              <SelectTrigger className="h-[34px] w-full max-w-md text-[13px]">
                <SelectValue placeholder="대표 채널을 고르세요" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>정하지 않음 — 웹훅 주소로 보냄</SelectItem>
                {bot.channels.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {channelLabel(c)} · {channelType(c.type)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          {primaryResult && (
            <p className={primaryResult.ok
              ? 'rounded border border-emerald-200 bg-emerald-50 p-2.5 text-[13px] text-emerald-800'
              : 'rounded border border-destructive/30 bg-destructive/5 p-2.5 text-[13px] text-destructive'}>
              {primaryResult.message}
            </p>
          )}
        </div>

        {/* 채널 */}
        <div className="space-y-2 border-t pt-4">
          <div className="flex items-baseline justify-between">
            <p className="text-[13px] font-medium">연결된 채널</p>
            {bot?.lastPollAt && (
              <p className="text-xs text-muted-foreground">마지막 확인 {timeOf(bot.lastPollAt)}</p>
            )}
          </div>
          {!bot?.connected ? (
            <p className="text-[13px] text-muted-foreground">연결되면 봇 계정이 들어가 있는 채널이 여기에 보입니다.</p>
          ) : bot.channels.length === 0 ? (
            <p className="rounded border border-amber-200 bg-amber-50 p-2.5 text-[13px] text-amber-800">
              @{bot.botUsername} 계정이 아직 어느 채널에도 들어가 있지 않습니다. Mattermost 에서 답을 받을 채널에 이 계정을 초대해 주세요.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>채널</TableHead>
                  <TableHead className="w-24">종류</TableHead>
                  <TableHead className="w-20 text-right">응답</TableHead>
                  <TableHead className="w-28">마지막 응답</TableHead>
                  <TableHead className="w-24 text-right">읽기</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {bot.channels.map((c) => (
                  <TableRow key={c.id} className={c.watching ? '' : 'text-muted-foreground'}>
                    <TableCell className="font-medium">
                      <span className="inline-flex items-center gap-2">
                        <span className={`size-2 rounded-full ${c.watching ? 'bg-emerald-500' : 'bg-muted-foreground/40'}`} />
                        {channelLabel(c)}
                        {c.primary && (
                          <span className="rounded border border-primary/40 px-1.5 text-[11px] font-normal text-primary">대표</span>
                        )}
                      </span>
                    </TableCell>
                    <TableCell>{channelType(c.type)}</TableCell>
                    <TableCell className="text-right tabular-nums">{c.answeredCount}</TableCell>
                    <TableCell className="text-[12px]">{c.lastAnsweredAt ? timeOf(c.lastAnsweredAt) : '—'}</TableCell>
                    <TableCell className="text-right">
                      <Switch checked={c.watching} disabled={setWatching.isPending}
                        onCheckedChange={(v) => setWatching.mutate({ channelId: c.id, watching: v })} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <p className="text-xs text-muted-foreground">
            봇 계정이 들어가 있는 채널만 읽습니다. 새 채널에 초대하면 1분 안에 나타납니다.
            서버를 다시 띄운 뒤에 올라온 글에만 답하고, 봇이 쓴 글과 시스템 메시지는 무시합니다.
          </p>
        </div>
      </CardContent>
    </Card>
  )
}

function BotStatusBanner({ bot }: { bot: ChatBotStatus | undefined }) {
  if (!bot) return null
  const watching = bot.channels.filter((c) => c.watching)
  const state = !bot.enabled
    ? { cls: 'border bg-muted/40 text-muted-foreground', dot: 'bg-muted-foreground/40', text: '연결 안 됨 — 아래에 계정을 넣고 [연결]을 누르세요' }
    : !bot.connected
      ? { cls: 'border-destructive/30 bg-destructive/5 text-destructive', dot: 'bg-destructive', text: `로그인 안 됨${bot.lastError ? ` — ${bot.lastError}` : ''}` }
      : bot.lastError
        ? { cls: 'border-amber-200 bg-amber-50 text-amber-800', dot: 'bg-amber-500', text: `@${bot.botUsername} 로그인됨 · 마지막 확인에 오류 — ${bot.lastError}` }
        : { cls: 'border-emerald-200 bg-emerald-50 text-emerald-800', dot: 'bg-emerald-500', text: `@${bot.botUsername} 로 연결됨 · 채널 ${watching.length}곳을 읽는 중${watching.length ? ` — ${watching.map(channelLabel).join(', ')}` : ''}` }
  return (
    <p className={`flex items-center gap-2 rounded border p-2.5 text-[13px] ${state.cls}`}>
      <span className={`size-2 shrink-0 rounded-full ${state.dot}`} />
      {state.text}
    </p>
  )
}

function channelLabel(c: ChatBotChannel): string {
  if (c.type === 'D') return '개인 메시지'
  if (c.type === 'G') return '그룹 메시지'
  return c.displayName || c.name
}

function channelType(type: string): string {
  return { O: '공개', P: '비공개', D: '개인', G: '그룹' }[type] ?? type
}

function timeOf(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
