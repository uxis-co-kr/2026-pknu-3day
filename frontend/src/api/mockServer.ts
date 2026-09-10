import activitiesJson from '@/mocks/activities.json'
import apiKeyIssuedJson from '@/mocks/api-key-issued.json'
import apiKeysJson from '@/mocks/api-keys.json'
import draft7 from '@/mocks/draft-7.json'
import draft8 from '@/mocks/draft-8.json'
import draft9 from '@/mocks/draft-9.json'
import draftsJson from '@/mocks/drafts.json'
import meJson from '@/mocks/me.json'
import reposJson from '@/mocks/repos.json'
import llmJson from '@/mocks/settings-llm.json'
import notifyJson from '@/mocks/settings-notify.json'
import statsDailyJson from '@/mocks/stats-daily.json'
import statsPeopleJson from '@/mocks/stats-people.json'
import sessionsJson from '@/mocks/vscode-sessions.json'
import type {
  Activity, ApiKey, DailyStats, Draft, DraftSummary, GithubLink, IssuedApiKey, LlmSettings,
  LoginRequest, LoginResponse, Me, NotifySettings, Page, PeopleStats, Repo, VscodeSession,
} from '@/types/api'

/**
 * VITE_USE_MOCK=true 일 때 apiClient 가 부르는 가짜 서버.
 * 실서버와 같은 경로·같은 응답 모양으로만 답한다. 저장처럼 화면이 되돌려 받아야 하는
 * 변경은 메모리에 남겨서, 목업 상태에서도 편집 흐름을 그대로 눌러볼 수 있게 한다.
 */

const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v)) as T

const db = {
  me: clone(meJson) as Me,
  activities: clone(activitiesJson.items) as Activity[],
  sessions: clone(sessionsJson) as VscodeSession[],
  drafts: clone(draftsJson) as DraftSummary[],
  details: clone([draft7, draft8, draft9]) as Draft[],
  repos: clone(reposJson) as Repo[],
  apiKeys: clone(apiKeysJson) as ApiKey[],
  notify: clone(notifyJson) as NotifySettings,
  llm: clone(llmJson) as LlmSettings,
  statsDaily: clone(statsDailyJson) as DailyStats,
  statsPeople: clone(statsPeopleJson) as PeopleStats,
  /**
   * 사원번호 로그인 목업 (9/10 회의). 서버가 붙기 전까지 화면 흐름을 눌러볼 수 있게 한다.
   * 비밀번호를 바꾸지 않은 계정은 아이디와 비밀번호가 같다.
   */
  accounts: [
    { loginId: '0004', password: '0004', name: '배태일', role: 'MEMBER', mustChangePassword: true },
    { loginId: 'admin', password: 'admin', name: '관리자', role: 'ADMIN', mustChangePassword: true },
  ] as { loginId: string; password: string; name: string; role: 'MEMBER' | 'ADMIN'; mustChangePassword: boolean }[],
  github: { linked: false, login: null, avatarUrl: null, linkedAt: null } as GithubLink,
}

let nextId = 100
const now = () => new Date().toISOString()

export class MockHttpError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const notFound = (what: string) => new MockHttpError(404, 'NOT_FOUND', `${what}을(를) 찾을 수 없습니다.`)

/** 초안 본문을 근거에서 조립한다. 실서버의 DraftGenerator 자리를 흉내만 낸다. */
function composeDraft(userId: number, date: string): string {
  const user = db.activities.find((a) => a.user?.id === userId)?.user
  const acts = db.activities.filter((a) => a.user?.id === userId && a.occurredAt.startsWith(date))
  const sess = db.sessions.filter((s) => s.userId === userId && s.workDate === date)
  const done = acts.map((a) => {
    const repo = `[${a.repo.fullName}]`
    if (a.type === 'COMMIT') return `- ${repo} ${a.summary ?? a.title} (commit ${a.sha})`
    const verb = a.type === 'PR_OPENED' ? '오픈' : '머지'
    return `- ${repo} PR #${a.externalId} ${verb}: ${a.title}`
  })
  const wip = sess.map((s) => `- [${s.repo?.fullName ?? s.remoteUrl}] ${s.branch} — ${s.summary ?? '작업 중'} (미커밋)`)
  const todos = sess.flatMap((s) => s.todos.map((t) => `- ${t.text}`))
  return [
    `# ${date} 업무 일지 — ${user?.name ?? ''}`, '',
    '## 완료한 작업', ...(done.length ? done : ['- 없음']), '',
    '## 진행 중 / 미커밋', ...(wip.length ? wip : ['- 없음']), '',
    '## 계획 / TODO', ...(todos.length ? todos : ['- 없음']), '',
    '## 메모', '(직접 작성)', '',
  ].join('\n')
}

function syncSummary(d: Draft) {
  const meta = db.drafts.find((x) => x.id === d.id)
  if (meta) Object.assign(meta, { status: d.status, updatedAt: d.updatedAt, confirmedAt: d.confirmedAt })
}

type Handler = (params: Record<string, string>, query: URLSearchParams, body: unknown) => unknown

const routes: [string, string, Handler][] = [
  ['GET', '/me', () => db.me],

  ['POST', '/auth/login', (_p, _q, body) => {
    const { loginId, password } = body as LoginRequest
    const found = db.accounts.find((a) => a.loginId === loginId && a.password === password)
    if (!found) throw new MockHttpError(401, 'INVALID_CREDENTIALS', '사원번호 또는 비밀번호가 올바르지 않습니다.')
    db.me = { ...db.me, name: found.name, loginId: found.loginId, role: found.role, mustChangePassword: found.mustChangePassword }
    return {
      token: `mock-${found.role.toLowerCase()}-token`,
      mustChangePassword: found.mustChangePassword,
      role: found.role,
    } satisfies LoginResponse
  }],

  ['POST', '/me/password', (_p, _q, body) => {
    const { currentPassword, newPassword } = body as { currentPassword: string; newPassword: string }
    const acc = db.accounts.find((a) => a.loginId === db.me.loginId)
    if (!acc || acc.password !== currentPassword) {
      throw new MockHttpError(400, 'WRONG_PASSWORD', '현재 비밀번호가 올바르지 않습니다.')
    }
    if (newPassword.length < 4) throw new MockHttpError(400, 'WEAK_PASSWORD', '비밀번호는 4자 이상이어야 합니다.')
    if (newPassword === acc.loginId) {
      throw new MockHttpError(400, 'WEAK_PASSWORD', '사원번호와 같은 비밀번호는 쓸 수 없습니다.')
    }
    acc.password = newPassword
    acc.mustChangePassword = false
    db.me = { ...db.me, mustChangePassword: false }
    return null
  }],

  ['GET', '/me/github', () => db.github],

  ['POST', '/me/github', () => {
    // 실서버에서는 GitHub OAuth 로 넘어갔다 돌아온다. 목업은 바로 붙은 것으로 친다.
    db.github = { linked: true, login: 'Ae-Ti', avatarUrl: null, linkedAt: new Date().toISOString() }
    db.me = { ...db.me, login: 'Ae-Ti' }
    return db.github
  }],

  ['DELETE', '/me/github', () => {
    db.github = { linked: false, login: null, avatarUrl: null, linkedAt: null }
    db.me = { ...db.me, login: '' }
    return null
  }],

  ['GET', '/activities', (_p, q) => {
    const date = q.get('date')
    const userId = q.get('userId')
    const repoId = q.get('repoId')
    const type = q.get('type')
    const items = db.activities.filter((a) =>
      (!date || a.occurredAt.startsWith(date)) &&
      (!userId || a.user?.id === Number(userId)) &&
      (!repoId || a.repo.id === Number(repoId)) &&
      (!type || a.type === type))
    return { items, page: 0, size: 50, total: items.length } satisfies Page<Activity>
  }],

  ['GET', '/stats/daily', () => db.statsDaily],
  // 기간 탭이 실제로 반응하도록 요청한 구간만 잘라서 합계를 다시 낸다.
  ['GET', '/stats/people', (_p, q) => {
    const from = q.get('from') ?? db.statsPeople.from
    const to = q.get('to') ?? db.statsPeople.to
    const userId = q.get('userId')
    const items = db.statsPeople.items
      .filter((i) => !userId || i.user.id === Number(userId))
      .map((i) => {
        const series = i.series.filter((s) => s.date >= from && s.date <= to)
        return {
          ...i,
          series,
          totals: {
            commits: series.reduce((n, s) => n + s.commits, 0),
            prs: series.reduce((n, s) => n + s.prs, 0),
            merges: series.reduce((n, s) => n + s.merges, 0),
          },
        }
      })
    return { ...db.statsPeople, from, to, items }
  }],

  ['GET', '/vscode/sessions', (_p, q) => {
    const date = q.get('date')
    const userId = q.get('userId')
    return db.sessions.filter((s) =>
      (!date || s.workDate === date) && (!userId || s.userId === Number(userId)))
  }],

  ['GET', '/drafts', (_p, q) => {
    const date = q.get('date')
    // 업무 일지 목록은 기간으로 부른다. 서버는 최근 날짜가 먼저 오게 정렬해 준다.
    const from = q.get('from')
    const to = q.get('to')
    const userId = q.get('userId')
    const status = q.get('status')
    return db.drafts.filter((d) =>
      (!date || d.workDate === date) &&
      (!from || d.workDate >= from) &&
      (!to || d.workDate <= to) &&
      (!userId || d.userId === Number(userId)) &&
      (!status || d.status === status))
      .sort((x, y) => y.workDate.localeCompare(x.workDate))
  }],

  ['GET', '/drafts/:id', (p) => {
    const d = db.details.find((x) => x.id === Number(p.id))
    if (!d) throw notFound('초안')
    return d
  }],

  ['PATCH', '/drafts/:id', (p, _q, body) => {
    const d = db.details.find((x) => x.id === Number(p.id))
    if (!d) throw notFound('초안')
    // 완료(확정) 버튼을 없앴다 (9/10 결정). 잠글 상태가 없으므로 언제든 고칠 수 있다.
    d.userEdited = true
    d.contentMd = (body as { contentMd: string }).contentMd
    d.updatedAt = now()
    syncSummary(d)
    return d
  }],

  ['POST', '/drafts/:id/confirm', (p) => {
    const d = db.details.find((x) => x.id === Number(p.id))
    if (!d) throw notFound('초안')
    d.status = 'CONFIRMED'
    d.confirmedAt = now()
    d.updatedAt = d.confirmedAt
    syncSummary(d)
    return d
  }],

  ['POST', '/drafts/:id/notify', (p) => {
    const d = db.details.find((x) => x.id === Number(p.id))
    if (!d) throw notFound('초안')
    // 전송 조건은 "사람이 한 번이라도 저장했는가" 다 (9/10 결정). 자동 생성 그대로를
    // 채널에 흘리지 않기 위한 문턱이다.
    if (!d.userEdited) {
      throw new MockHttpError(409, 'DRAFT_NOT_EDITED', '한 번 저장한 뒤에 보낼 수 있습니다.')
    }
    return { sent: true }
  }],

  // 확정본이 있는 날짜에 다시 부르면 version 을 올린 새 DRAFT 를 만든다 (E2E 4).
  ['POST', '/drafts/generate', (_p, _q, body) => {
    const { date, userId } = body as { date: string; userId?: number }
    const uid = userId ?? db.me.id
    const has = db.activities.some((a) => a.user?.id === uid && a.occurredAt.startsWith(date))
    if (!has) return null
    const version = Math.max(0, ...db.drafts.filter((d) => d.userId === uid && d.workDate === date).map((d) => d.version)) + 1
    const detail: Draft = {
      // 사용자가 버튼을 눌러 만드는 경로라 자동 생성이 아니다.
      id: ++nextId, userId: uid, workDate: date, version, status: 'DRAFT', autoGenerated: false, userEdited: false,
      contentMd: composeDraft(uid, date),
      sourceActivities: db.activities.filter((a) => a.user?.id === uid && a.occurredAt.startsWith(date)),
      sourceSessions: db.sessions.filter((s) => s.userId === uid && s.workDate === date),
      createdAt: now(), updatedAt: now(), confirmedAt: null,
    }
    db.details.push(detail)
    const { contentMd: _c, sourceActivities: _a, sourceSessions: _s, ...meta } = detail
    db.drafts.push(meta)
    // 실서버의 생성 응답은 상세와 모양이 다르다 — 근거가 id 배열이고 타임스탬프가 없다.
    // 목업이 상세를 돌려주면 이 차이가 가려져 실서버에서만 터진다.
    return {
      id: detail.id,
      userId: detail.userId,
      workDate: detail.workDate,
      version: detail.version,
      status: detail.status,
      contentMd: detail.contentMd,
      sourceActivityIds: detail.sourceActivities.map((a) => a.id),
      sourceSessionIds: detail.sourceSessions.map((x) => x.id),
    }
  }],

  // 내가 등록한 리포만 (9/10 결정).
  ['GET', '/repos', () => db.repos.filter((r) => r.registeredBy?.id === db.me.id)],

  ['POST', '/repos/import', () => {
    const added = db.repos.filter((r) => r.registeredBy?.id !== db.me.id)
    added.forEach((r) => { r.registeredBy = { id: db.me.id, login: db.me.login } })
    return { count: added.length, repos: added.map((r) => r.fullName) }
  }],

  ['POST', '/repos/sync-all', () => {
    const mine = db.repos.filter((r) => r.registeredBy?.id === db.me.id)
    mine.forEach((r) => { r.lastSyncedAt = now(); r.syncStatus = 'OK' })
    return { count: mine.length, repos: mine.map((r) => r.fullName) }
  }],

  ['POST', '/repos', (_p, _q, body) => {
    // 서버와 같은 규칙으로 주소에서 owner/repo 를 뽑는다 (RepoService.parseRepoRef).
    const fullName = (body as { fullName: string }).fullName
      ?.trim()
      .replace(/^git@github\.com:/, 'https://github.com/')
      .replace(/^(https?:\/\/)?(www\.)?github\.com\//, '')
      .replace(/\.git$/, '')
      .split('/').slice(0, 2).join('/')
    if (!fullName || !/^[\w.-]+\/[\w.-]+$/.test(fullName)) {
      throw new MockHttpError(400, 'INVALID_FULL_NAME', 'GitHub 주소를 붙여 넣어 주세요. 예) https://github.com/owner/repo')
    }
    if (db.repos.some((r) => r.fullName === fullName)) {
      throw new MockHttpError(409, 'REPO_ALREADY_REGISTERED', '이미 등록된 리포입니다.')
    }
    const repo: Repo = {
      id: ++nextId, fullName, defaultBranch: 'main', lastSyncedAt: null,
      registeredBy: { id: db.me.id, login: db.me.login },
      todayActivityCount: 0, syncStatus: 'OK',
    }
    db.repos.push(repo)
    return repo
  }],

  ['DELETE', '/repos/:id', (p) => {
    const i = db.repos.findIndex((r) => r.id === Number(p.id))
    if (i < 0) throw notFound('리포')
    db.repos.splice(i, 1)
    return null
  }],

  ['POST', '/repos/:id/sync', (p) => {
    const r = db.repos.find((x) => x.id === Number(p.id))
    if (!r) throw notFound('리포')
    r.syncStatus = 'SYNCING'
    setTimeout(() => { r.syncStatus = 'OK'; r.lastSyncedAt = now() }, 1200)
    return null
  }],

  ['GET', '/me/api-keys', () => db.apiKeys],

  ['POST', '/me/api-keys', (_p, _q, body) => {
    const { label } = body as { label: string }
    const issued: IssuedApiKey = {
      ...(clone(apiKeyIssuedJson) as IssuedApiKey),
      id: ++nextId, label, createdAt: now(),
    }
    const { key: _k, ...meta } = issued
    db.apiKeys.push({ ...meta, lastUsedAt: null })
    return issued
  }],

  ['DELETE', '/me/api-keys/:id', (p) => {
    const i = db.apiKeys.findIndex((k) => k.id === Number(p.id))
    if (i < 0) throw notFound('API Key')
    db.apiKeys.splice(i, 1)
    return null
  }],

  ['GET', '/settings/notify', () => db.notify],
  ['PUT', '/settings/notify', (_p, _q, body) => Object.assign(db.notify, body)],
  ['GET', '/settings/llm', () => db.llm],
  ['PUT', '/settings/llm', (_p, _q, body) => Object.assign(db.llm, body)],
]

function match(pattern: string, path: string): Record<string, string> | null {
  const pp = pattern.split('/')
  const ap = path.split('/')
  if (pp.length !== ap.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < pp.length; i += 1) {
    if (pp[i].startsWith(':')) params[pp[i].slice(1)] = ap[i]
    else if (pp[i] !== ap[i]) return null
  }
  return params
}

export async function handleMock(method: string, url: string, body: unknown): Promise<unknown> {
  const [path, qs = ''] = url.split('?')
  const query = new URLSearchParams(qs)
  await new Promise((r) => setTimeout(r, 150))
  for (const [m, pattern, handler] of routes) {
    if (m !== method) continue
    const params = match(pattern, path)
    if (params) return handler(params, query, body)
  }
  throw new MockHttpError(404, 'NOT_FOUND', `목업에 없는 경로입니다: ${method} ${path}`)
}
