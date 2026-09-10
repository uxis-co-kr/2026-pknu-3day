import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/api/apiClient'
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
  people: ['admin', 'people'] as const,
  notify: ['admin', 'settings', 'notify'] as const,
  llm: ['admin', 'settings', 'llm'] as const,
}

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

export const useSetChannelWatching = () => {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: { channelId: string; watching: boolean }) =>
      api.put<ChatBotStatus>(`/admin/chat/channels/${req.channelId}`, { watching: req.watching }),
    onSuccess: (status) => qc.setQueryData(adminQk.chatStatus, status),
  })
}
