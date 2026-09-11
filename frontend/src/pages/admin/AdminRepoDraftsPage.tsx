import PeriodDraftWorkspace from '@/components/draft/PeriodDraftWorkspace'
import { useMe } from '@/api/hooks'
import AdminGuard from './AdminGuard'

/**
 * 저장소별 업무일지 (관리자 콘솔).
 *
 * <p>사원 개인 화면에 있던 것을 여기로 옮겼다 (9/11). 저장소별은 한 사람의 일지가 아니라
 * <b>그 저장소에서 팀이 무엇을 했는지</b>를 본다. 사원 화면에 두면 남의 활동까지 묶어 보게 된다.
 * 서버도 만들기·목록·본문 모두 관리자만 받는다.
 *
 * <p>쓰는 방식은 주간과 같다 — 주(월~일)와 저장소를 고르고 AI 생성, 고쳐 저장, Mattermost 전송.
 * 같은 주에 다시 만들면 버전만 올라간다.
 */
export default function AdminRepoDraftsPage() {
  const { data: me } = useMe()

  return (
    <AdminGuard error={null}>
      <div className="space-y-4">
        <div>
          <h1 className="text-[15px] font-semibold">저장소별 업무일지</h1>
          <p className="mt-1 text-[13px] text-muted-foreground">
            주와 저장소를 고르면 그 주 그 저장소의 커밋·PR 을 AI 가 묶어 씁니다. 고쳐 저장한 뒤
            Mattermost 로 보낼 수 있습니다. 같은 주에 다시 만들면 새 일지가 아니라 버전이 올라갑니다.
          </p>
        </div>

        <PeriodDraftWorkspace
          kind="repo"
          userId={me?.id}
          displayName={me?.name ?? me?.login}
        />
      </div>
    </AdminGuard>
  )
}
