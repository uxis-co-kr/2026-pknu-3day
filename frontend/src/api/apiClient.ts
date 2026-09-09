import { MockHttpError, handleMock } from './mockServer'
import type { ApiErrorBody } from '@/types/api'

/**
 * VITE_USE_MOCK=true 면 src/mocks 를, false 면 실서버를 부른다 (PRD 9. 1-2 / 1-9).
 * 두 경로가 같은 타입을 돌려주므로 화면 코드는 어느 쪽인지 몰라도 된다.
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

const TOKEN_KEY = 'worklog.token'

export const auth = {
  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  save(token: string) {
    localStorage.setItem(TOKEN_KEY, token)
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
  },
  /** GitHub 로그인은 fetch 가 아니라 브라우저 이동이다 (HANDOFF 1.). */
  startGithubLogin() {
    if (USE_MOCK) {
      auth.save('mock-token')
      window.location.assign('/')
      return
    }
    window.location.assign(`${API_BASE}/auth/github`)
  },
}

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type Method = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  if (USE_MOCK) {
    try {
      return (await handleMock(method, path, body)) as T
    } catch (e) {
      if (e instanceof MockHttpError) throw new ApiError(e.status, e.code, e.message)
      throw e
    }
  }

  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth.token
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (res.status === 401) {
    // JWT 는 12시간이면 만료된다. 토큰을 버리고 로그인부터 다시 시킨다.
    auth.clear()
    if (window.location.pathname !== '/login') window.location.assign('/login')
    throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.')
  }

  if (!res.ok) {
    // 오류 본문은 전부 {code, message} 다 (HANDOFF 3.).
    const fallback: ApiErrorBody = { code: 'UNKNOWN', message: `요청에 실패했습니다 (${res.status}).` }
    const problem = await res.json().catch(() => fallback) as ApiErrorBody
    throw new ApiError(res.status, problem.code ?? fallback.code, problem.message ?? fallback.message)
  }

  if (res.status === 204) return null as T
  const text = await res.text()
  return (text ? JSON.parse(text) : null) as T
}

export const api = {
  get: <T,>(path: string) => request<T>('GET', path),
  post: <T,>(path: string, body?: unknown) => request<T>('POST', path, body),
  patch: <T,>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  put: <T,>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: <T,>(path: string) => request<T>('DELETE', path),
}

/** `?date=...&userId=...` 를 만들 때 빈 값은 빼 준다. */
export function qs(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}
