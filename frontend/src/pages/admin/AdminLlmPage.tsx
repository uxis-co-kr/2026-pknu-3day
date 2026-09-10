import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import AdminGuard from './AdminGuard'
import { useAdminLlm, useSaveAdminLlm } from './api'

/** 프리셋 이름만으로는 무엇인지 알기 어려워 한 줄씩 붙인다. */
const DESCRIPTIONS: Record<string, string> = {
  mock: '실제 호출 없이 커밋 메시지를 그대로 돌려줍니다. 개발·점검용입니다.',
  gemma4: '사내 기본 모델. 응답이 빠릅니다.',
  qwen3: '사내 대체 모델. 품질을 비교할 때 씁니다 — diff 한 건에 50초 이상 걸립니다.',
}

export default function AdminLlmPage() {
  const { data, isLoading, error } = useAdminLlm()
  const save = useSaveAdminLlm()
  const [provider, setProvider] = useState<string>('')

  useEffect(() => {
    if (data?.provider) setProvider(data.provider)
  }, [data?.provider])

  return (
    <AdminGuard error={error}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">LLM 모델</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            커밋 요약과 업무 일지 초안을 만드는 모델입니다. 프리셋 중에서만 고를 수 있습니다.
          </p>
        </div>

        <Card>
          <CardContent className="p-4">
            {isLoading || !data ? (
              <Skeleton className="h-32" />
            ) : (
              <>
                <RadioGroup value={provider} onValueChange={setProvider} className="gap-3">
                  {data.available.map((id: string) => (
                    <div key={id} className="flex items-start gap-3 rounded border p-3">
                      <RadioGroupItem value={id} id={`llm-${id}`} className="mt-0.5" />
                      <Label htmlFor={`llm-${id}`} className="cursor-pointer font-normal">
                        <span className="text-[13px] font-medium">{id}</span>
                        {id === data.provider && (
                          <span className="ml-2 text-xs text-muted-foreground">사용 중</span>
                        )}
                        <p className="mt-0.5 text-[13px] text-muted-foreground">
                          {DESCRIPTIONS[id] ?? '설정에 추가된 프리셋입니다.'}
                        </p>
                      </Label>
                    </div>
                  ))}
                </RadioGroup>

                <div className="mt-4 flex items-center justify-between">
                  <p className="text-sm text-muted-foreground">
                    {data.model ? `모델: ${data.model}` : '이 프리셋은 모델명이 없습니다.'}
                  </p>
                  <Button
                    size="sm"
                    disabled={save.isPending || provider === data.provider}
                    onClick={() => save.mutate({ provider })}
                  >
                    저장
                  </Button>
                </div>

                <p className="mt-3 border-t pt-3 text-xs text-muted-foreground">
                  이미 만들어진 요약은 다시 만들지 않습니다. 바꾼 모델은 새로 수집되는 활동부터
                  적용됩니다.
                </p>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </AdminGuard>
  )
}
