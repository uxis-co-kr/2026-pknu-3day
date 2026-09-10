import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Check } from 'lucide-react'
import UserAvatar from '@/components/common/UserAvatar'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { auth } from '@/api/apiClient'
import { useGithubLink, useMe, useUnlinkGithub } from '@/api/hooks'
import { formatDateLabel } from '@/lib/date'
import { cn } from '@/lib/utils'

/**
 * 설정 > 깃허브 연동 (9/10 회의).
 *
 * <p>GitHub 은 더 이상 로그인 수단이 아니다. 사원번호로 로그인한 **내 계정에 붙이는** 것이다.
 * 실서버에서는 GitHub OAuth 로 넘어갔다 돌아오지만, 목업은 바로 붙은 것으로 친다.
 */
/** 콜백이 `?github=` 로 알려 주는 결과. 코드 그대로 보여 주면 읽을 수 없다. */
const RESULT: Record<string, { text: string; failed: boolean }> = {
  linked: { text: 'GitHub 을 연결했습니다.', failed: false },
  GITHUB_ALREADY_LINKED: {
    text: '이 GitHub 계정은 이미 다른 사용자에 연결돼 있습니다. 관리자에게 문의해 주세요.',
    failed: true,
  },
  USER_NOT_FOUND: { text: '계정을 찾을 수 없습니다. 다시 로그인해 주세요.', failed: true },
}

export default function GithubLinkCard() {
  const link = useGithubLink()
  const { data: me } = useMe()
  const disconnect = useUnlinkGithub()

  /**
   * OAuth 는 브라우저가 넘어갔다 돌아오는 흐름이라 결과를 주소로 받는다.
   * 아무 말도 없으면 눌렀는데 아무 일도 안 난 것처럼 보인다.
   */
  const [params, setParams] = useSearchParams()
  const result = params.get('github')
  useEffect(() => {
    if (!result) return
    // 한 번 보여 주고 주소를 정리한다 — 새로고침할 때마다 다시 뜨면 안 된다.
    const next = new URLSearchParams(params)
    next.delete('github')
    setParams(next, { replace: true })
  }, [result, params, setParams])

  const [notice, setNotice] = useState<{ text: string; failed: boolean } | null>(null)
  useEffect(() => {
    if (result) {
      setNotice(RESULT[result] ?? { text: `연결에 실패했습니다 (${result})`, failed: true })
    }
  }, [result])

  if (link.isLoading) return <Skeleton className="h-10 w-full" />

  const banner = notice && (
    <p className={cn('mb-3 text-[13px]', notice.failed ? 'text-status-failed' : 'text-primary')}>
      {notice.text}
    </p>
  )

  const data = link.data
  if (!data?.linked) {
    return (
      <div>
      {banner}
      <div className="flex items-center justify-between gap-4">
        <p className="text-[13px] text-muted-foreground">
          아직 연결하지 않았습니다. 연결해야 리포를 등록하고 활동을 가져올 수 있습니다.
        </p>
        {/* OAuth 라 fetch 가 아니라 브라우저가 GitHub 으로 넘어갔다 돌아온다. */}
        <Button className="h-9 shrink-0 gap-2" disabled={!me} onClick={() => me && auth.linkGithub(me.id)}>
          <svg viewBox="0 0 16 16" aria-hidden className="size-4 fill-current">
            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
          </svg>
          GitHub 연결
        </Button>
      </div>
      </div>
    )
  }

  return (
    <div>
    {banner}
    <div className="flex items-center justify-between gap-4">
      <div className="flex items-center gap-2.5">
        <UserAvatar login={data.login ?? undefined} avatarUrl={data.avatarUrl} />
        <div>
          <p className="flex items-center gap-1.5 text-[13px] font-medium">
            <Check className="size-3.5 text-status-confirmed" />
            {data.login}
          </p>
          {data.linkedAt && (
            <p className="text-[12px] text-muted-foreground">{formatDateLabel(data.linkedAt.slice(0, 10))} 연결됨</p>
          )}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {/* 연결만으로는 활동이 들어오지 않는다. 다음에 무엇을 할지 알려 준다. */}
        <span className="text-[12px] text-muted-foreground">
          아래 <strong className="font-medium text-foreground">전체 등록</strong>으로 내 리포를 한 번에 가져올 수 있습니다
        </span>
        <Button variant="outline" size="sm" className="h-9"
          disabled={disconnect.isPending} onClick={() => disconnect.mutate()}>
          연결 해제
        </Button>
      </div>
    </div>
    </div>
  )
}
