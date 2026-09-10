import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import AdminGuard from './AdminGuard'
import { useGlobalNotify, useSaveGlobalNotify } from './api'

export default function AdminNotifyPage() {
  const { data, isLoading, error } = useGlobalNotify()
  const save = useSaveGlobalNotify()
  const [url, setUrl] = useState('')
  const [remind, setRemind] = useState(true)

  useEffect(() => {
    if (!data) return
    setUrl(data.mattermostWebhookUrl ?? '')
    setRemind(data.remindUncommitted ?? true)
  }, [data])

  return (
    <AdminGuard error={error}>
      <div className="space-y-5">
        <div>
          <h1 className="text-lg font-semibold tracking-tight">Mattermost</h1>
          <p className="mt-1 text-sm text-muted-foreground">
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
                    알림을 보내지 않습니다.
                  </p>
                </div>

                <div className="flex items-start justify-between gap-4 border-t pt-4">
                  <div>
                    <p className="text-sm font-medium">미커밋 리마인드</p>
                    <p className="mt-0.5 text-sm text-muted-foreground">
                      미커밋 변경이 300줄을 넘거나 마지막 커밋이 6시간 지나면 업무 시간에 매시
                      알립니다.
                    </p>
                  </div>
                  <Switch checked={remind} onCheckedChange={setRemind} />
                </div>

                <div className="flex justify-end border-t pt-4">
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

                <p className="text-xs text-muted-foreground">
                  보내는 것은 셋입니다 — 초안 생성 알림, 확정본 게시(사용자가 버튼을 누를 때),
                  미커밋 리마인드.
                </p>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminGuard>
  )
}
