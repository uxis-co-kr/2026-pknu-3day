import { MockHttpError, handleMock } from './mockServer'
import type { ApiErrorBody } from '@/types/api'

/**
 * VITE_USE_MOCK=true 면 src/mocks 를, 아니면 실서버를 부른다 (PRD 9. 1-2 / 1-9).
 * 두 경로가 같은 타입을 돌려주므로 화면 코드는 어느 쪽인지 몰라도 된다.
 *
 * <p><b>기본값은 실서버다.</b> 예전에는 "false 라고 적지 않으면 목업" 이었다. 그런데
 * `frontend/.env` 는 커밋하지 않으므로 <b>새로 클론한 사람에게는 그 파일이 없다</b> —
 * 서버를 멀쩡히 띄워 두고도 화면은 가짜 데이터를 보고, 진짜 사원번호로 로그인하면
 * "사원번호 또는 비밀번호가 올바르지 않습니다" 가 뜬다. 서버 로그에는 아무것도 남지 않아
 * 원인을 찾기 어렵다 (9/11 실제로 겪었다). 목업은 일부러 켤 때만 쓴다.
 */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * 실서버로 전환했지만 아직 백엔드에 없는 경로. 여기 걸린 경로만 404 일 때 목업으로 떨어진다.
 * 404 를 무조건 목업으로 돌리면 `/drafts/9999` 같은 정상적인 "없음" 까지 가짜 데이터로 덮여
 * 버리므로, 목록을 명시해 두고 엔드포인트가 생길 때마다 지운다.
 *
 * 2026-09-10 기준 미구현: 없음에 가깝다. /me/github 는 실서버에 붙어 목록에서 뺐다 —
 * 목업으로 떨어지면 화면만 "연결됨" 이 되고 서버에는 토큰이 없어, 전체 등록이
 * "GitHub 을 연결해 주세요" 로 막힌다.
 */
const MOCK_FALLBACK_PATHS = [
  /^\/auth\/login/,
  /^\/me\/password/,
]

function fallsBackToMock(path: string): boolean {
  return MOCK_FALLBACK_PATHS.some((p) => p.test(path))
}

const TOKEN_KEY = 'worklog.token'
const MUST_CHANGE_KEY = 'worklog.mustChangePassword'

export const auth = {
  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  },
  save(token: string) {
    localStorage.setItem(TOKEN_KEY, token)
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(MUST_CHANGE_KEY)
  },
  /**
   * 사원번호 로그인 뒤 호출한다. 비밀번호를 아직 안 바꾼 계정은 표시를 남겨,
   * RequireAuth 가 다른 화면으로 못 가게 막는다 (TODO_0910 §1-1).
   */
  signIn(token: string, mustChangePassword: boolean) {
    auth.save(token)
    if (mustChangePassword) localStorage.setItem(MUST_CHANGE_KEY, '1')
    else localStorage.removeItem(MUST_CHANGE_KEY)
  },
  get mustChangePassword(): boolean {
    return localStorage.getItem(MUST_CHANGE_KEY) === '1'
  },
  clearMustChangePassword() {
    localStorage.removeItem(MUST_CHANGE_KEY)
  },

  /**
   * 로그아웃 — 토큰을 버리고 로그인 화면으로 보낸다.
   *
   * JWT 는 서버에 세션이 없으므로 클라이언트에서 버리는 것이 곧 로그아웃이다.
   * 전체 페이지 이동이라 react-query 캐시에 남은 남의 데이터도 같이 사라진다.
   * 한 기기에서 사람이 바뀌는 상황(요구사항 3)이 이 경로다.
   */
  logout() {
    auth.clear()
    window.location.assign('/login')
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

  if (res.status === 404 && fallsBackToMock(path.split('?')[0])) {
    console.warn(`[worklog] ${path} 는 아직 서버에 없어 목업으로 대신합니다.`)
    return (await handleMock(method, path, body)) as T
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
