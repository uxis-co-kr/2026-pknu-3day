import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, qs } from './apiClient'
import type {
  Activity, ActivityDetail, ApiKey, DailyStats, Draft, DraftSummary, GeneratedDraft, GithubLink, IssuedApiKey,
  LlmSettings, LoginRequest, LoginResponse, Me, NotifySettings, Page, PeopleStats, Repo, VscodeSession,
} from '@/types/api'

/**
 * 목록 화면은 30초마다 스스로 다시 부른다 (9/10 결정).
 *
 * <p>VS Code 에서 전송하거나 수집기가 돌면 서버 쪽이 바뀌는데, 새로고침해야만 보이면
 * 화면이 멈춰 있는 것처럼 보인다.
 *
 * <p>초안 **상세**({@link useDraft})에는 절대 걸지 않는다. 편집 중에 다시 불러오면
 * 저장하지 않은 글이 서버 본문으로 덮인다.
 */
const LIVE = { refetchInterval: 30_000 } as const

/** 쿼리 키는 여기서만 만든다. 무효화할 때 경로를 헷갈리지 않기 위해서다. */
export const qk = {
  me: ['me'] as const,
  activities: (f: ActivityFilter) => ['activities', f] as const,
  activity: (id: number) => ['activities', id] as const,
  statsDaily: (date: string) => ['stats', 'daily', date] as const,
  statsPeople: (f: PeopleFilter) => ['stats', 'people', f] as const,
  sessions: (f: DayFilter | SessionRangeFilter) => ['vscode-sessions', f] as const,
  drafts: (f: DraftFilter) => ['drafts', f] as const,
  draftRange: (f: DraftRangeFilter) => ['drafts', 'range', f] as const,
  draftsByKind: (f: DraftKindFilter) => ['drafts', 'kind', f] as const,
  draft: (id: number) => ['drafts', id] as const,
  repos: ['repos'] as const,
  apiKeys: ['api-keys'] as const,
  notify: ['settings', 'notify'] as const,
  llm: ['settings', 'llm'] as const,
  github: ['me', 'github'] as const,
}

export interface DayFilter { date: string; userId?: number }
export interface ActivityFilter extends DayFilter { repoId?: number; type?: string }
export interface DraftFilter extends DayFilter { status?: string }
/** 업무 일지 목록 — 하루가 아니라 기간으로 본다. */
export interface DraftRangeFilter { from: string; to: string; userId?: number; status?: string }
/** 주간·저장소별 목록 (V15). kind 를 주지 않으면 서버가 하루치만 준다. */
export interface DraftKindFilter { from: string; to: string; kind: 'WEEKLY' | 'REPO'; userId?: number }
/** VSCode 내역 — 업무 일지처럼 달 단위로 본다 (BACKLOG2 §2-3). */
export interface SessionRangeFilter { from: string; to: string; userId?: number }
export interface PeopleFilter { from: string; to: string; userId?: number; granularity?: 'day' | 'week' }

export const useMe = () => useQuery({ queryKey: qk.me, queryFn: () => api.get<Me>('/me') })

/** 사원번호 로그인 (9/10 회의). 관리자도 같은 경로를 쓰고 role 로 갈린다. */
export const useLogin = () =>
  useMutation({ mutationFn: (req: LoginRequest) => api.post<LoginResponse>('/auth/login', req) })

export const useChangePassword = () => {
  const qc = useQueryClient()
  return useMutation({
    // 담당자 2 (9/11): 변경 뒤 서버가 새 토큰을 준다 — 이전 토큰은 그 순간 죽는다 (V10 password_changed_at).
    mutationFn: (req: { currentPassword: string; newPassword: string }) =>
      api.post<{ changed: boolean; token?: string } | null>('/me/password', req),
    onSuccess: () => void qc.invalidateQueries({ queryKey: qk.me }),
  })
}

export const useGithubLink = () =>
  useQuery({ queryKey: qk.github, queryFn: () => api.get<GithubLink>('/me/github') })

const useGithubMutation = <T,>(fn: () => Promise<T>) => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.github })
      void qc.invalidateQueries({ queryKey: qk.me })
    },
  })
}

export const useUnlinkGithub = () => useGithubMutation(() => api.delete<null>('/me/github'))

/**
 * 연동을 시작할 GitHub 주소를 받아 그리로 이동한다.
 *
 * <p>주소에 사용자 id 를 적어 보내던 것을 없앴다 — 그 경로는 인증 없이 열려 있어 남의 id 를
 * 적으면 그 계정에 자기 GitHub 이 붙었다. id 는 이제 서버가 토큰에서 꺼낸다.
 */
export const useStartGithubLink = () =>
  useMutation({
    mutationFn: () => api.post<{ url: string }>('/me/github/start', {}),
    onSuccess: (r) => window.location.assign(r.url),
  })

export const useActivities = (f: ActivityFilter, enabled = true) =>
  useQuery({
    queryKey: qk.activities(f),
    queryFn: () => api.get<Page<Activity>>(`/activities${qs({ ...f })}`),
    enabled,
    ...LIVE,
  })

/** 행을 펼칠 때만 부른다 — 목록에 없는 커밋 메시지가 여기 있다. */
export const useActivityDetail = (id: number | undefined, enabled: boolean) =>
  useQuery({
    queryKey: qk.activity(id ?? 0),
    queryFn: () => api.get<ActivityDetail>(`/activities/${id}`),
    enabled: enabled && id !== undefined,
  })

export const useDailyStats = (date: string) =>
  useQuery({ queryKey: qk.statsDaily(date), queryFn: () => api.get<DailyStats>(`/stats/daily${qs({ date })}`) })

export const usePeopleStats = (f: PeopleFilter) =>
  useQuery({ queryKey: qk.statsPeople(f), queryFn: () => api.get<PeopleStats>(`/stats/people${qs({ ...f })}`) })

export const useSessions = (f: DayFilter, enabled = true) =>
  useQuery({
    queryKey: qk.sessions(f),
    queryFn: () => api.get<VscodeSession[]>(`/vscode/sessions${qs({ ...f })}`),
    enabled,
    ...LIVE,
  })

/** 기간 안의 내 VSCode 내역. 같은 날 안에서는 늦게 보고한 것이 먼저 온다 (서버 정렬). */
export const useSessionRange = (f: SessionRangeFilter, enabled = true) =>
  useQuery({
    queryKey: qk.sessions(f),
    queryFn: () => api.get<VscodeSession[]>(`/vscode/sessions${qs({ ...f })}`),
    enabled,
    ...LIVE,
  })

export const useDrafts = (f: DraftFilter) =>
  useQuery({ queryKey: qk.drafts(f), queryFn: () => api.get<DraftSummary[]>(`/drafts${qs({ ...f })}`), ...LIVE })

/** 기간 안의 내 업무 일지. 최근 날짜가 먼저 온다 (서버 정렬). */
export const useDraftRange = (f: DraftRangeFilter, enabled = true) =>
  useQuery({
    queryKey: qk.draftRange(f),
    queryFn: () => api.get<DraftSummary[]>(`/drafts${qs({ ...f })}`),
    enabled,
  })

export const useDraft = (id: number | undefined) =>
  useQuery({ queryKey: qk.draft(id!), queryFn: () => api.get<Draft>(`/drafts/${id}`), enabled: id !== undefined })

export const useRepos = () => useQuery({ queryKey: qk.repos, queryFn: () => api.get<Repo[]>('/repos'), ...LIVE })
export const useApiKeys = () => useQuery({ queryKey: qk.apiKeys, queryFn: () => api.get<ApiKey[]>('/me/api-keys') })
export const useNotifySettings = () =>
  useQuery({ queryKey: qk.notify, queryFn: () => api.get<NotifySettings>('/settings/notify') })
export const useLlmSettings = () =>
  useQuery({ queryKey: qk.llm, queryFn: () => api.get<LlmSettings>('/settings/llm') })

/** 초안이 바뀌면 상세와 목록(홈 카드의 배지·버튼)을 같이 새로 읽는다. */
function useDraftMutation<TVars>(fn: (v: TVars) => Promise<Draft | { sent: boolean } | null>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (data) => {
      if (data && 'id' in data) qc.setQueryData(qk.draft(data.id), data)
      void qc.invalidateQueries({ queryKey: ['drafts'] })
    },
  })
}

/** 활동이 없어 AI 생성을 쓸 수 없을 때 빈 일지를 만든다 (9/10). */
export const useCreateBlankDraft = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (date: string) => api.post<Draft>(`/drafts/blank?date=${date}`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['drafts'] }),
  })
}

export const useSaveDraft = () =>
  useDraftMutation(({ id, contentMd }: { id: number; contentMd: string }) =>
    api.patch<Draft>(`/drafts/${id}`, { contentMd }))

export const useConfirmDraft = () =>
  useDraftMutation(({ id }: { id: number }) => api.post<Draft>(`/drafts/${id}/confirm`))

/**
 * 새 초안 생성·재생성. 204 면 그날 활동이 없다는 뜻이라 null 이 온다 (PRD 7.).
 *
 * 응답이 상세와 모양이 달라 상세 캐시에 넣지 않는다. 목록만 무효화하고, 화면은 새 id 로
 * 이동해 GET /drafts/{id} 를 다시 읽는다.
 */
export const useGenerateDraft = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ date, userId }: { date: string; userId?: number }) =>
      api.post<GeneratedDraft | null>('/drafts/generate', { date, userId }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['drafts'] }),
  })
}

/**
 * 주간·저장소별 업무일지 AI 생성 (V15).
 *
 * <p>하루치와 달리 재료가 없으면 400 으로 이유가 온다 — 빈 일지를 만들지 않는다.
 * 모델 호출이라 사내 gemma4 기준 10~30초 걸린다.
 */
export const useGeneratePeriodDraft = () => {
  const qc = useQueryClient()
  return useMutation({
    // date 는 그 주의 아무 날. 서버가 월~일로 맞춘다 — 같은 주면 버전만 올라간다.
    mutationFn: ({ kind, ...body }: {
      kind: 'weekly' | 'repo'
      date: string
      userId?: number
      repoId?: number
      mineOnly?: boolean
    }) => api.post<Draft>(`/drafts/generate/${kind}`, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['drafts'] }),
  })
}

/** 종류별 일지 목록. kind 를 안 주면 서버가 하루치만 준다. */
export const useDraftsByKind = (f: DraftKindFilter, enabled = true) =>
  useQuery({
    queryKey: qk.draftsByKind(f),
    queryFn: () => api.get<DraftSummary[]>(`/drafts${qs({ ...f })}`),
    enabled,
  })

export const useNotifyDraft = () =>
  useDraftMutation(({ id }: { id: number }) => api.post<{ sent: boolean }>(`/drafts/${id}/notify`))

function useReposMutation<TVars, TData>(fn: (v: TVars) => Promise<TData>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => void qc.invalidateQueries({ queryKey: qk.repos }) })
}

export const useRegisterRepo = () => useReposMutation((fullName: string) => api.post<Repo>('/repos', { fullName }))
export const useDeleteRepo = () => useReposMutation((id: number) => api.delete<null>(`/repos/${id}`))
export const useSyncRepo = () => useReposMutation((id: number) => api.post<null>(`/repos/${id}/sync`))

/** 내 GitHub 리포를 한 번에 등록하고 바로 수집을 건다 (9/10 "전체 등록"). */
export const useImportRepos = () =>
  useReposMutation(() => api.post<{ count: number; repos: string[] }>('/repos/import', {}))

/** 내 리포를 한 번에 동기화한다. full 이면 최근 며칠을 다시 훑는다. */
export const useSyncAllRepos = () =>
  useReposMutation((full: boolean) =>
    api.post<{ count: number; repos: string[] }>(`/repos/sync-all?full=${full}`, {}))

function useApiKeyMutation<TVars, TData>(fn: (v: TVars) => Promise<TData>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => void qc.invalidateQueries({ queryKey: qk.apiKeys }) })
}

export const useIssueApiKey = () =>
  useApiKeyMutation((label: string) => api.post<IssuedApiKey>('/me/api-keys', { label }))
export const useRevokeApiKey = () =>
  useApiKeyMutation((id: number) => api.delete<null>(`/me/api-keys/${id}`))

export const useUpdateNotifySettings = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: NotifySettings) => api.put<NotifySettings>('/settings/notify', v),
    onSuccess: (data) => qc.setQueryData(qk.notify, data),
  })
}

export const useUpdateLlmSettings = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { provider: LlmSettings['provider'] }) => api.put<LlmSettings>('/settings/llm', v),
    onSuccess: (data) => qc.setQueryData(qk.llm, data),
  })
}
