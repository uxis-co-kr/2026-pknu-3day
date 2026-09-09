/**
 * PRD 7. API 명세의 응답 타입. 목업 JSON(`src/mocks/`)과 실서버 응답이 같은 모양이어야 하므로
 * 필드명을 임의로 바꾸지 않는다. 목록은 배열, `/activities` 만 페이지 래퍼를 쓴다
 * (docs/HANDOFF_day1_integration.md 2.).
 */

export type ActivityType = 'COMMIT' | 'PR_OPENED' | 'PR_MERGED'
export type SummaryStatus = 'PENDING' | 'DONE' | 'FAILED'
export type DraftStatus = 'DRAFT' | 'CONFIRMED'
export type SyncStatus = 'OK' | 'SYNCING' | 'FAILED'

export interface Me {
  id: number
  login: string
  name: string | null
  avatarUrl: string | null
}

export interface UserRef extends Me {}

export interface RepoRef {
  id: number
  fullName: string
}

export interface Repo {
  id: number
  fullName: string
  defaultBranch: string | null
  lastSyncedAt: string | null
  registeredBy: { id: number; login: string }
  /** 그날 이 리포의 커밋 수 — 리포 관리 Table "오늘 활동 수" */
  todayActivityCount: number
  syncStatus: SyncStatus
}

export interface Activity {
  id: number
  type: ActivityType
  repo: RepoRef
  /** 가입하지 않은 GitHub 계정의 활동은 null 이고 externalLogin 만 채워진다 (PRD F1-5). */
  user: UserRef | null
  externalLogin: string | null
  /** COMMIT 은 sha, PR_OPENED/PR_MERGED 는 PR 번호 */
  externalId: string
  sha: string | null
  title: string
  url: string
  branch: string | null
  filesChanged: number | null
  additions: number | null
  deletions: number | null
  summary: string | null
  summaryStatus: SummaryStatus
  occurredAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface UncommittedFile {
  path: string
  additions: number
  deletions: number
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

export interface VscodeSession {
  id: number
  userId: number
  repo: RepoRef | null
  remoteUrl: string
  branch: string
  workDate: string
  uncommittedFiles: UncommittedFile[]
  todos: TodoItem[]
  planNote: string | null
  editTimeline: EditTimelineEntry[]
  summary: string | null
  lastCommitAt: string | null
  reportedAt: string
}

/** `GET /drafts` — 목록에는 본문을 싣지 않는다. */
export interface DraftSummary {
  id: number
  userId: number
  workDate: string
  version: number
  status: DraftStatus
  createdAt: string
  updatedAt: string
  confirmedAt: string | null
}

/** `GET /drafts/{id}` — 근거를 객체로 펼친 상세. */
export interface Draft extends DraftSummary {
  contentMd: string
  sourceActivities: Activity[]
  sourceSessions: VscodeSession[]
}

export interface DailyStats {
  date: string
  commits: number
  prs: number
  merges: number
  sessions: number
  /** "어제 대비 +3" */
  commitsDelta: number
  /** "⚠ 6시간 이상 1건" */
  staleSessions: number
  /**
   * 사용자에 연결되지 않은 활동 수. 총계 = byUser 합계 + unmapped 가 항상 성립한다.
   * 가입하지 않은 외부 기여자의 커밋이 여기 잡힌다.
   */
  unmapped: { commits: number; prs: number; merges: number }
  byUser: { userId: number; commits: number; prs: number; merges: number; sessions: number }[]
}

export interface PeopleSeriesPoint {
  date: string
  commits: number
  prs: number
  merges: number
  draft: { id: number; status: DraftStatus } | null
}

export interface PeopleStats {
  from: string
  to: string
  granularity: 'day' | 'week'
  items: {
    user: UserRef
    totals: { commits: number; prs: number; merges: number }
    series: PeopleSeriesPoint[]
  }[]
}

export interface ApiKey {
  id: number
  label: string
  createdAt: string
  lastUsedAt: string | null
}

/** 발급 직후 한 번만 평문 키가 온다. */
export interface IssuedApiKey extends ApiKey {
  key: string
}

export interface NotifySettings {
  mattermostWebhookUrl: string | null
  remindUncommitted: boolean
}

export interface LlmSettings {
  provider: 'gemma4' | 'qwen3'
  presets: { id: 'gemma4' | 'qwen3'; model: string; connected: boolean }[]
}

/** 오류 형식은 전부 이 모양이다 (HANDOFF 3.). */
export interface ApiErrorBody {
  code: string
  message: string
}
