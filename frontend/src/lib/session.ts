import type { VscodeSession } from '@/types/api'

/**
 * 이 세션에 **적을 것이 있는가**. 서버의 `VscodeSession.hasContent()` 와 같은 기준이다.
 *
 * <p>확장은 10분마다 보낸다. 열어만 두고 아무것도 하지 않은 날에도 저장소·브랜치·날짜만 담긴
 * 빈 세션이 쌓인다. "세션이 있다" 를 "일한 기록이 있다" 로 읽으면, 아무것도 하지 않은 날에도
 * AI 생성 버튼이 열려 일지를 지어내게 된다 (9/11 확인).
 *
 * <p>미푸시 커밋의 `null` 은 "셀 수 없음"(업스트림 없는 브랜치)이지 기록이 아니다.
 */
export function sessionHasContent(s: VscodeSession): boolean {
  return (s.uncommittedFiles?.length ?? 0) > 0
    || (s.todos?.length ?? 0) > 0
    || (s.unsavedFiles?.length ?? 0) > 0
    || (s.aiSessions?.length ?? 0) > 0
    || (s.editTimeline?.length ?? 0) > 0
    || (s.unpushedCommits?.length ?? 0) > 0
    || !!s.planNote?.trim()
}
