import { log } from './log'
import type { SessionPayload } from './types'

export interface UploaderConfig {
  serverUrl: string
  apiKey: string
}

export type SendResult = { ok: true; sent: number } | { ok: false; reason: string }

/**
 * 서버에 저장된 세션 한 건 (GET /vscode/sessions 응답).
 *
 * <p>사이드바 하단의 "서버에 저장된 내역" 이 쓴다 — <b>보낸 것이 실제로 들어갔는지</b>를
 * VS Code 안에서 확인할 길이 이것뿐이다. 화면에 쓰는 것만 적는다.
 */
export interface RemoteSession {
  id: number
  repo: { id: number; fullName: string } | null
  remoteUrl: string
  branch: string
  /** YYYY-MM-DD (KST) */
  workDate: string
  uncommittedFiles: { path: string }[]
  todos: unknown[]
  planNote?: string | null
  unsavedFiles?: unknown[]
  aiSessions?: { id: string; title?: string; promptCount?: number }[]
  reportedAt: string
}

export type FetchResult =
  | { ok: true; sessions: RemoteSession[] }
  | { ok: false; reason: string }

const TIMEOUT_MS = 10_000

/**
 * 등록 리포 목록은 자주 바뀌지 않는다. 전송 주기(기본 10분)보다 짧게 두어, 리포를 새로
 * 등록한 뒤 다음 전송에는 반영되게 한다.
 */
const REPOS_TTL_MS = 5 * 60 * 1000

/** 마지막으로 읽어 둔 등록 리포(owner/repo). 전송과 사이드바가 함께 쓴다. */
let repoCache: { at: number; names: Set<string> } | undefined

/**
 * 서버에 <b>등록된</b> 리포 이름. 캐시가 살아 있으면 서버를 부르지 않는다.
 *
 * <p>확장은 워크스페이스가 git 저장소이기만 하면 수집한다. 등록하지 않은 리포 — 개인
 * 프로젝트나 남의 코드를 열어 둔 창 — 까지 보내면 그 코드가 회사 서버에 쌓인다.
 *
 * <p>`/api/repos` 가 아니라 `/api/repos/known` 을 부른다. 앞의 것은 <b>내가 등록한 것만</b>
 * 준다 — 리포는 한 사람만 등록할 수 있으므로(팀에서 두 번째 사람은 등록할 길이 없다),
 * 그것으로 가리면 남이 등록한 리포에서 일하는 팀원의 기록이 통째로 버려진다 (BACKLOG2 §2-4).
 *
 * @return 등록 리포 이름. 서버에 닿지 못하면 undefined (모른다 — 0개와 다르다)
 */
export async function registeredRepos(config: UploaderConfig): Promise<Set<string> | undefined> {
  if (repoCache && Date.now() - repoCache.at < REPOS_TTL_MS) return repoCache.names
  if (!config.apiKey) return undefined

  const base = config.serverUrl.replace(/\/+$/, '')
  const url = `${base}/api/repos/known`
  try {
    const res = await fetch(url, {
      headers: { 'X-Api-Key': config.apiKey },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (!res.ok) {
      log(`등록 리포 목록을 읽지 못했습니다 (${res.status}). 이번에는 거르지 않고 보냅니다`)
      return undefined
    }
    // 이름 배열이다. 예전 서버는 객체 배열({fullName})을 주므로 둘 다 받는다.
    const body = (await res.json()) as (string | { fullName?: string })[]
    const names = new Set(
      (Array.isArray(body) ? body : [])
        .map((r) => (typeof r === 'string' ? r : r.fullName ?? '').toLowerCase())
        .filter(Boolean),
    )
    repoCache = { at: Date.now(), names }
    log(`등록된 리포 ${names.size}곳을 읽었습니다`)
    return names
  } catch (e) {
    log(`등록 리포 목록을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
    return undefined
  }
}

/** 마지막으로 읽어 둔 등록 리포. 서버를 부르지 않는다 — 사이드바가 그릴 때 쓴다. */
export function cachedRegisteredRepos(): Set<string> | undefined {
  return repoCache?.names
}

/**
 * 수집한 세션을 백엔드로 보낸다 (PRD 7. POST /vscode/sessions, 헤더 X-Api-Key).
 *
 * <p>어떤 실패도 밖으로 던지지 않는다. 확장이 예외로 죽으면 안 되고, 사용자에게는
 * 상태바 한 줄로만 알린다 (PRD F6 수용 기준).
 */
export class Uploader {
  private readonly config: UploaderConfig

  constructor(config: UploaderConfig) {
    this.config = config
  }

  async send(payloads: SessionPayload[]): Promise<SendResult> {
    if (payloads.length === 0) {
      return { ok: true, sent: 0 }
    }
    if (!this.config.apiKey) {
      return { ok: false, reason: 'API Key 미설정' }
    }

    let sent = 0
    for (const payload of payloads) {
      const result = await this.postOne(payload)
      if (!result.ok) return result
      sent += 1
    }
    return { ok: true, sent }
  }

  /**
   * 서버에 저장된 내 최근 세션 (GET /vscode/sessions?from&to&mine=true).
   *
   * <p>{@code mine=true} 가 없으면 팀 전원의 기록이 내려온다 — 확장은 제 userId 를 모른다.
   */
  async fetchRecent(from: string, to: string): Promise<FetchResult> {
    if (!this.config.apiKey) {
      return { ok: false, reason: 'API Key 미설정' }
    }
    const base = this.config.serverUrl.replace(/\/+$/, '')
    const url = `${base}/api/vscode/sessions?from=${from}&to=${to}&mine=true`
    try {
      const res = await fetch(url, {
        headers: { 'X-Api-Key': this.config.apiKey },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      })
      if (res.status === 401 || res.status === 403) return { ok: false, reason: 'API Key 오류' }
      if (!res.ok) return { ok: false, reason: `서버 오류 ${res.status}` }
      const body = (await res.json()) as RemoteSession[]
      log(`서버 내역 ${Array.isArray(body) ? body.length : 0}건을 읽었습니다 (${from}~${to})`)
      return { ok: true, sessions: Array.isArray(body) ? body : [] }
    } catch (e) {
      const reason = e instanceof Error && e.name === 'TimeoutError' ? '서버 응답 없음' : '서버에 연결할 수 없음'
      log(`서버 내역을 읽지 못했습니다: ${reason} (${url})`)
      return { ok: false, reason }
    }
  }

  private async postOne(payload: SessionPayload): Promise<SendResult> {
    const base = this.config.serverUrl.replace(/\/+$/, '')
    const url = `${base}/api/vscode/sessions`
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Api-Key': this.config.apiKey },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      })

      if (res.status === 401 || res.status === 403) {
        log(`전송 거부됨 (${res.status}) — API Key 를 확인하세요`)
        return { ok: false, reason: 'API Key 오류' }
      }
      if (!res.ok) {
        const body = await res.text().catch(() => '')
        log(`전송 실패 ${res.status}: ${body.slice(0, 300)}`)
        return { ok: false, reason: `서버 오류 ${res.status}` }
      }

      const body = (await res.json().catch(() => ({}))) as { id?: number }
      log(`전송 완료 ${payload.branch} (session id=${body.id ?? '?'}, 미커밋 ${payload.uncommittedFiles.length}파일)`)
      return { ok: true, sent: 1 }
    } catch (e) {
      const reason = e instanceof Error && e.name === 'TimeoutError' ? '서버 응답 없음' : '서버에 연결할 수 없음'
      log(`전송 실패: ${reason} (${url}) — ${e instanceof Error ? e.message : String(e)}`)
      return { ok: false, reason }
    }
  }
}
