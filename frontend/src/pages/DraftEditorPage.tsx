import { useNavigate, useParams } from 'react-router-dom'
import DraftWorkspace from '@/components/draft/DraftWorkspace'

/**
 * 초안 편집 화면 — 지난 일지를 목록에서 눌러 들어온다.
 *
 * <p>화면 본체는 {@link DraftWorkspace} 다. 업무 일지 작성 탭의 "오늘 일지" 와 같은 것을 쓴다.
 */
export default function DraftEditorPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const draftId = Number(id)

  if (!Number.isFinite(draftId)) {
    return <p className="text-[13px] text-muted-foreground">초안을 찾을 수 없습니다.</p>
  }

  // AI 생성은 새 버전을 만든다. 그 초안으로 옮겨 간다.
  return (
    <DraftWorkspace
      draftId={draftId}
      onGenerated={(next) => navigate(`/drafts/${next}`)}
      onBack={() => navigate('/drafts')}
    />
  )
}
