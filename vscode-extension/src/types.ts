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

export interface EditTimelineEntry {
  path: string
  firstSavedAt: string
  lastSavedAt: string
  saveCount: number
}
