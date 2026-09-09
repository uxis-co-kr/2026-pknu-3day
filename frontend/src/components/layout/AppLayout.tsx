import { Outlet } from 'react-router-dom'

/**
 * 공통 레이아웃 — 좌측 고정 사이드바(로고 + 홈/초안/리포/인원/설정) + 상단 바(날짜 선택기, 아바타).
 * 콘텐츠 영역 최대 폭 1200. 1-3 에서 Figma 아트보드 2 기준으로 채운다.
 */
export default function AppLayout() {
  return (
    <div className="min-h-screen bg-muted/30">
      <Outlet />
    </div>
  )
}
