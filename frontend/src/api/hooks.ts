import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, qs } from './apiClient'
import type {
  Activity, ApiKey, DailyStats, Draft, DraftSummary, GeneratedDraft, IssuedApiKey,
  LlmSettings, Me, NotifySettings, Page, PeopleStats, Repo, VscodeSession,
} from '@/types/api'

/** 쿼리 키는 여기서만 만든다. 무효화할 때 경로를 헷갈리지 않기 위해서다. */
export const qk = {
  me: ['me'] as const,
  activities: (f: ActivityFilter) => ['activities', f] as const,
  statsDaily: (date: string) => ['stats', 'daily', date] as const,
  statsPeople: (f: PeopleFilter) => ['stats', 'people', f] as const,
  sessions: (f: DayFilter) => ['vscode-sessions', f] as const,
  drafts: (f: DraftFilter) => ['drafts', f] as const,
  draft: (id: number) => ['drafts', id] as const,
  repos: ['repos'] as const,
  apiKeys: ['api-keys'] as const,
  notify: ['settings', 'notify'] as const,
  llm: ['settings', 'llm'] as const,
}

export interface DayFilter { date: string; userId?: number }
export interface ActivityFilter extends DayFilter { repoId?: number; type?: string }
export interface DraftFilter extends DayFilter { status?: string }
export interface PeopleFilter { from: string; to: string; userId?: number; granularity?: 'day' | 'week' }

export const useMe = () => useQuery({ queryKey: qk.me, queryFn: () => api.get<Me>('/me') })

export const useActivities = (f: ActivityFilter) =>
  useQuery({ queryKey: qk.activities(f), queryFn: () => api.get<Page<Activity>>(`/activities${qs({ ...f })}`) })

export const useDailyStats = (date: string) =>
  useQuery({ queryKey: qk.statsDaily(date), queryFn: () => api.get<DailyStats>(`/stats/daily${qs({ date })}`) })

export const usePeopleStats = (f: PeopleFilter) =>
  useQuery({ queryKey: qk.statsPeople(f), queryFn: () => api.get<PeopleStats>(`/stats/people${qs({ ...f })}`) })

export const useSessions = (f: DayFilter) =>
  useQuery({ queryKey: qk.sessions(f), queryFn: () => api.get<VscodeSession[]>(`/vscode/sessions${qs({ ...f })}`) })

export const useDrafts = (f: DraftFilter) =>
  useQuery({ queryKey: qk.drafts(f), queryFn: () => api.get<DraftSummary[]>(`/drafts${qs({ ...f })}`) })

export const useDraft = (id: number | undefined) =>
  useQuery({ queryKey: qk.draft(id!), queryFn: () => api.get<Draft>(`/drafts/${id}`), enabled: id !== undefined })

export const useRepos = () => useQuery({ queryKey: qk.repos, queryFn: () => api.get<Repo[]>('/repos') })
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

export const useNotifyDraft = () =>
  useDraftMutation(({ id }: { id: number }) => api.post<{ sent: boolean }>(`/drafts/${id}/notify`))

function useReposMutation<TVars, TData>(fn: (v: TVars) => Promise<TData>) {
  const qc = useQueryClient()
  return useMutation({ mutationFn: fn, onSuccess: () => void qc.invalidateQueries({ queryKey: qk.repos }) })
}

export const useRegisterRepo = () => useReposMutation((fullName: string) => api.post<Repo>('/repos', { fullName }))
export const useDeleteRepo = () => useReposMutation((id: number) => api.delete<null>(`/repos/${id}`))
export const useSyncRepo = () => useReposMutation((id: number) => api.post<null>(`/repos/${id}/sync`))

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
