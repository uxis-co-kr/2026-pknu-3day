/**
 * POST /api/vscode/sessions 요청 본문. PRD 7. "대표 응답 스키마" 와 필드명이 정확히 같아야 한다.
 * 백엔드는 (user, remoteUrl, branch, workDate) 로 UPSERT 한다.
 */
export interface SessionPayload {
  remoteUrl: string
  branch: string
  /** YYYY-MM-DD, KST 기준 */
  workDate: string
  uncommittedFiles: UncommittedFile[]
  todos: TodoItem[]
  planNote?: string
  editTimeline: EditTimelineEntry[]
  /** ISO-8601. 마지막 커밋 시각을 못 읽으면 생략한다. */
  lastCommitAt?: string
  /** 이 폴더에서 오늘 오간, 또는 지금 열어 둔 AI 대화. 기록이 없으면 빈 배열. */
  aiSessions: AiSessionSummary[]
  /**
   * 커밋했지만 아직 push 하지 않은 커밋.
   *
   * <p>업스트림이 없어 <b>셀 수 없으면</b> 생략한다 — 빈 배열(미푸시 없음)과 뜻이 다르다.
   */
  unpushedCommits?: UnpushedCommit[]
}

/** 미푸시 커밋 하나. GitHub 수집기가 보지 못하는 구간이다 (원격에 없으니 API 에 안 나온다). */
export interface UnpushedCommit {
  /** 짧은 해시 */
  sha: string
  subject: string
  /** ISO-8601 커밋 시각 */
  at: string
}

export interface UncommittedFile {
  path: string
  additions: number
  deletions: number
  /** worklog.collectDiff 가 false 면 비워 보낸다. 파일당 200줄로 자른다. */
  diff?: string
}

export interface TodoItem {
  path: string
  line: number
  text: string
}

/**
 * 이 폴더에서 오간 AI 대화 한 세션 (Claude Code).
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
 */
export interface AiSessionSummary {
  id: string
  /**
   * 세션 제목. Claude Code 가 기록에 남긴 것(`ai-title`)을 쓰고, 없으면 첫 질문에서 만든다.
   *
   * <p>시각으로만 구분하면(`02:35–05:49`) 무슨 대화였는지 알 수 없다 (BACKLOG2 §2-3).
   */
  title: string
  /** ISO-8601 */
  firstAt: string
  lastAt: string
  /** 실제로 물어본 횟수. {@link turns} 는 잘리지만 이 값은 전부 센다. */
  promptCount: number
  /** 질문과 그 답변. 최근 것부터 일정 개수까지만 담는다. */
  turns: AiTurn[]
}

/** 질문 하나와 그에 대한 답변 (BACKLOG2 §2-3 "질의별 답변 기록"). */
export interface AiTurn {
  /** ISO-8601. 물어본 시각 */
  at: string
  prompt: string
  /** 그 질문에 대한 마지막 답변. 아직 답하는 중이면 없다. */
  answer?: string
}

export interface EditTimelineEntry {
  path: string
  firstSavedAt: string
  lastSavedAt: string
  saveCount: number
}
