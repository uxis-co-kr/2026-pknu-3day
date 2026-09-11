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
  /**
   * 고쳐 놓고 아직 저장하지 않은 파일.
   *
   * <p>git 에 아예 잡히지 않는 유일한 구간이다 — 디스크에 없으니 diff 에도 없다.
   */
  unsavedFiles: UnsavedFile[]
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
 * 이 폴더에서 오간 AI 대화 한 세션.
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
 *
 * <p>Claude Code 뿐 아니라 VS Code 내장 채팅(Copilot 등)·Codex·Gemini 에서도 모은다.
 * 어디서 왔는지는 {@link id} 의 접두사에 있다.
 */
export interface AiSessionSummary {
  /**
   * `<도구>:<그 도구의 세션 id>`. 예: `vscode:2cba0f08-…`, `codex:019fea63-…`.
   *
   * <p>Claude Code 만 접두사가 없다 — 서버가 id 하나로 대화를 합치는데, 이미 그 id 로 쌓인
   * 대화에 접두사를 붙이면 같은 대화가 남남이 되어 서버가 적어 둔 요약을 잃는다.
   */
  id: string
  /**
   * 세션 제목. 도구가 기록에 남긴 것(Claude 는 `ai-title`, 내장 채팅은 `customTitle`)을
   * 쓰고, 없으면 첫 질문에서 만든다.
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

/**
 * 저장하지 않은 채 열려 있는 파일 하나.
 *
 * <p>예전에는 <b>저장 이벤트</b>를 모았다. 그런데 `onDidSaveTextDocument` 는 편집기에서
 * 저장할 때만 온다 — AI 도구처럼 파일을 디스크에 곧바로 쓰는 변경은 이벤트가 없어,
 * 사람이 손으로 저장한 것만 남았다. 그런 목록은 그날 한 일을 대표하지 못한다.
 */
export interface UnsavedFile {
  path: string
  /** 고치기 시작해 아직 저장하지 않은 채 지난 시각. 확장을 다시 켜면 알 수 없어 생략한다. */
  dirtySince?: string
}
