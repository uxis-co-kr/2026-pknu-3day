import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, qs } from '@/api/apiClient'
import type { Draft, DraftSummary } from '@/types/api'
import type {
  AdminLlmSettings,
  AdminOverview,
  ChatBotSettings,
  ChatBotStatus,
  GlobalNotifySettings,
  PeopleDirectory,
} from './types'

/** 관리자 콘솔 쿼리 키. 일반 화면 키와 섞이지 않게 admin 으로 시작한다. */
export const adminQk = {
  chatStatus: ['admin', 'chat', 'status'] as const,
  chatSettings: ['admin', 'chat', 'settings'] as const,
  overview: ['admin', 'overview'] as const,
  drafts: ['admin', 'drafts'] as const,
  people: ['admin', 'people'] as const,
  notify: ['admin', 'settings', 'notify'] as const,
  llm: ['admin', 'settings', 'llm'] as const,
}

/**
 * 기간 안의 <b>전원</b> 업무 일지 (직원 업무일지 페이지).
 *
 * 같은 `GET /drafts` 를 쓰지만 관리자 토큰이라 전원이 온다 — 일반 회원은 DataScope 가
 * 자기 것으로 좁힌다. (사용자, 날짜)당 최신 버전만 오고 최근 날짜가 먼저다.
 */
export const useAdminDrafts = (f: { from: string; to: string; userId?: number }) =>
  useQuery({
    queryKey: [...adminQk.drafts, f],
    queryFn: () => api.get<DraftSummary[]>(`/drafts${qs({ ...f })}`),
  })

/** 일지 한 건의 본문. 목록에는 본문이 없어 고른 뒤에 따로 부른다. */
export const useAdminDraft = (id: number | undefined) =>
  useQuery({
    queryKey: [...adminQk.drafts, 'one', id],
    queryFn: () => api.get<Draft>(`/drafts/${id}`),
    enabled: id !== undefined,
  })

export const useAdminOverview = () =>
  useQuery({ queryKey: adminQk.overview, queryFn: () => api.get<AdminOverview>('/admin/overview') })

export const useAdminPeople = () =>
  useQuery({ queryKey: adminQk.people, queryFn: () => api.get<PeopleDirectory>('/admin/people') })

export const useGlobalNotify = () =>
  useQuery({
    queryKey: adminQk.notify,
    queryFn: () => api.get<GlobalNotifySettings>('/admin/settings/notify'),
  })

export const useSaveGlobalNotify = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: GlobalNotifySettings) =>
      api.put<GlobalNotifySettings>('/admin/settings/notify', body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: adminQk.notify })
      void qc.invalidateQueries({ queryKey: adminQk.overview })
    },
  })
}

/**
 * 비밀번호를 사원번호로 되돌린다 (DAY3_plan C-3). 되돌리면 그 사람의 이전 토큰이 전부 죽고,
 * 다음 로그인에서 비밀번호를 바꾸게 된다. 계정을 남이 선점했을 때 되찾는 길이다.
 */
export const useResetPassword = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) =>
      api.post<{ userId: number; loginId: string }>(`/admin/users/${userId}/password-reset`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: adminQk.people }),
  })
}

/** 사원과 계정을 잇는다. 동명이인이 있어 이름이 아니라 번호로 잇는다. */
export const useLinkEmployee = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, coSeq, empSeq }: { userId: number; coSeq: number | null; empSeq: number | null }) =>
      api.put(`/admin/users/${userId}/employee`, { coSeq, empSeq }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: adminQk.people })
      void qc.invalidateQueries({ queryKey: adminQk.overview })
    },
  })
}

export const useAdminLlm = () =>
  useQuery({ queryKey: adminQk.llm, queryFn: () => api.get<AdminLlmSettings>('/settings/llm') })

export const useSaveAdminLlm = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { provider: string }) => api.put<AdminLlmSettings>('/settings/llm', body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: adminQk.llm })
      void qc.invalidateQueries({ queryKey: adminQk.overview })
      // 담당자 1 의 설정 화면도 같은 값을 본다.
      void qc.invalidateQueries({ queryKey: ['settings', 'llm'] })
    },
  })
}

/** 지금 입력한 주소로 시험 전송한다. 저장하지 않으므로 맞는지 먼저 볼 수 있다. */
export const useTestWebhook = () =>
  useMutation({
    mutationFn: (mattermostWebhookUrl: string | null) =>
      api.post<{ sent: boolean; text: string }>('/admin/settings/notify/test', {
        mattermostWebhookUrl,
      }),
  })

/** 등록된 리포를 한꺼번에 동기화한다 (TODO_0910 §1-2 "전체 동기화"). */
export const useSyncAllRepos = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (full: boolean) =>
      api.post<{ repoCount: number; full: boolean }>(`/admin/repos/sync-all?full=${full}`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: adminQk.overview })
      void qc.invalidateQueries({ queryKey: adminQk.people })
    },
  })
}

/** 요약을 손으로 한 번 더 돌린다. 모델을 바꾼 직후 기다리지 않고 확인할 때 쓴다. */
export const useRunSummaries = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<{ summarized: number }>('/admin/summaries/run'),
    onSuccess: () => void qc.invalidateQueries({ queryKey: adminQk.overview }),
  })
}

/**
 * 요약이 빠진 AI 대화를 채운다.
 *
 * <p>대화 요약은 확장이 보낼 때 채우므로, 다시 전송될 일이 없는 지난 세션은 영영 빈 채로
 * 남는다. VS 내역 탭이 요약만 보여 주게 된 뒤로는 그 날들이 빈칸이 된다.
 */
export const useRunAiSummaries = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<{ sessions: number }>('/admin/ai-summaries/run'),
    onSuccess: () => void qc.invalidateQueries({ queryKey: adminQk.overview }),
  })
}

/** 봇 상태는 몇 초마다 다시 본다 — 연결하거나 채널에 초대하면 화면이 따라 바뀐다. */
export const useChatBotStatus = () =>
  useQuery({
    queryKey: adminQk.chatStatus,
    queryFn: () => api.get<ChatBotStatus>('/admin/chat/status'),
    refetchInterval: 5000,
  })

export const useChatBotSettings = () =>
  useQuery({ queryKey: adminQk.chatSettings, queryFn: () => api.get<ChatBotSettings>('/admin/chat/settings') })

/** 저장하고 바로 로그인해 본다. 실패하면 서버가 이유를 400 으로 돌려준다. */
export const useConnectChatBot = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: { baseUrl: string; loginId: string; password: string | null }) =>
      api.put<ChatBotStatus>('/admin/chat/settings', req),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: adminQk.chatStatus })
      void qc.invalidateQueries({ queryKey: adminQk.chatSettings })
    },
  })
}

export const useDisconnectChatBot = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.post<ChatBotStatus>('/admin/chat/disconnect', {}),
    onSettled: () => {
      void qc.invalidateQueries({ queryKey: adminQk.chatStatus })
      void qc.invalidateQueries({ queryKey: adminQk.chatSettings })
    },
  })
}

/** 대표 채널 — 사원이 [Mattermost 전송] 을 누르면 "요약되었습니다" 알림이 가는 채널. null 이면 해제. */
export const useSetPrimaryChannel = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (channelId: string | null) =>
      api.put<ChatBotStatus>('/admin/chat/primary-channel', { channelId }),
    onSuccess: (status) => qc.setQueryData(adminQk.chatStatus, status),
  })
}

export const useSetChannelWatching = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: { channelId: string; watching: boolean }) =>
      api.put<ChatBotStatus>(`/admin/chat/channels/${req.channelId}`, { watching: req.watching }),
    onSuccess: (status) => qc.setQueryData(adminQk.chatStatus, status),
  })
}
