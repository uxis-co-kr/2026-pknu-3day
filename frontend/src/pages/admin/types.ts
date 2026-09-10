/**
 * 관리자 콘솔 전용 타입 (TODO_0910 §1-3).
 *
 * 공용 `types/api.ts` 를 건드리지 않고 여기 둔다 — 담당자 1 이 같은 파일에서
 * 메뉴 재구성을 하고 있어 충돌을 피한다.
 */

export interface AdminOverview {
  adminLogin: string
  accountCount: number
  employeeCount: number
  unclaimedContributorCount: number
  wapleConfigured: boolean
  globalWebhookConfigured: boolean
  llmProvider: string
  repoCount: number
  /** 지금 수집 중인 리포 수 */
  syncingRepoCount: number
  anyRepoSyncFailed: boolean
  activityCount: number
  /** 아직 요약되지 않은 활동. 밀려 있으면 초안이 부실해진다 */
  pendingSummaryCount: number
  /** 3회까지 실패해 포기한 활동 */
  failedSummaryCount: number
}

/** 그 사람이 등록한 리포. 사원 행을 펼치면 보인다. */
export interface AdminRepo {
  repoId: number
  fullName: string
  defaultBranch: string | null
  lastSyncedAt: string | null
  syncStatus: 'OK' | 'SYNCING' | 'FAILED'
  /** 이 리포에서 그 사람 앞으로 잡힌 활동 수 */
  activityCount: number
}

/** 우리 서비스 계정 — 그 사람이 WorkLog Drafter 에 로그인해서 생긴 것. */
export interface AdminAccount {
  userId: number
  login: string
  name: string | null
  avatarUrl: string | null
  role: 'MEMBER' | 'ADMIN'
  /** 수집기가 이 사람 권한으로 리포를 읽을 수 있는지 */
  githubLinked: boolean
  /** 확장이 실제로 보낸 적이 있는지. 키 유무가 아니라 세션이 들어왔는지로 본다 */
  vscodeLinked: boolean
  /** 발급한 API Key 수. 키는 있는데 세션이 없으면 "발급만 함" 이다 */
  apiKeyCount: number
  sessionCount: number
  empSeq: number | null
  activityCount: number
  joinedAt: string
  repos: AdminRepo[]
}

/** 사내 회원(와플) 사원. account 가 null 이면 아직 서비스를 쓰지 않는 사람이다. */
export interface AdminEmployee {
  empSeq: number
  empNm: string
  account: AdminAccount | null
}

/** 커밋은 있는데 로그인한 적이 없는 GitHub 계정 (BACKLOG §3-4). */
export interface UnclaimedContributor {
  externalLogin: string
  activityCount: number
  lastSeenAt: string | null
}

export interface PeopleDirectory {
  wapleConfigured: boolean
  companySeq: number | null
  employees: AdminEmployee[]
  unlinkedAccounts: AdminAccount[]
  unclaimedContributors: UnclaimedContributor[]
}

export interface GlobalNotifySettings {
  mattermostWebhookUrl: string | null
  remindUncommitted: boolean | null
}

/**
 * `/settings/llm` 응답 (PRD 7.).
 *
 * 공용 `types/api.ts` 의 LlmSettings 는 이 API 가 생기기 전에 만들어져 `available`·`model` 이
 * 없다. 공용 타입을 고치면 담당자 1 의 설정 화면까지 영향을 받으므로 콘솔 전용으로 둔다.
 */
export interface AdminLlmSettings {
  provider: string
  model: string | null
  /** 사용자가 직접 고른 값인지. false 면 전역 설정을 보여 주는 중이다. */
  overridden: boolean
  available: string[]
}

/** 봇이 들어가 있는 채널 하나. */
export interface ChatBotChannel {
  id: string
  name: string
  displayName: string
  /** O 공개 · P 비공개 · D 개인 메시지 · G 그룹 메시지 */
  type: 'O' | 'P' | 'D' | 'G' | string
  /** 이 채널의 글을 읽고 답하는지 (관리자가 끌 수 있다) */
  watching: boolean
  answeredCount: number
  lastAnsweredAt: string | null
}

/** `GET /admin/chat/status` — 채널에서 물어보면 답하는 봇의 상태. */
export interface ChatBotStatus {
  /** 연결 설정이 있고 켜져 있는지 */
  enabled: boolean
  /** Mattermost 로그인이 된 상태인지 */
  connected: boolean
  botUsername: string | null
  baseUrl: string
  loginId: string
  /** 설정 출처 — db(콘솔에서 저장) / env(서버 .env) / none */
  source: 'db' | 'env' | 'none'
  lastPollAt: string | null
  lastError: string | null
  channels: ChatBotChannel[]
  checkedAt: string
}

/** `GET /admin/chat/settings` — 저장된 연결 설정. 비밀번호는 있는지만 알려 준다. */
export interface ChatBotSettings {
  baseUrl: string
  loginId: string
  passwordSet: boolean
  enabled: boolean
  source: 'db' | 'env' | 'none'
}
