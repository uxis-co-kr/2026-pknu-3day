import { log } from './log'
import type { SessionPayload } from './types'

export interface UploaderConfig {
  serverUrl: string
  apiKey: string
}

export type SendResult = { ok: true; sent: number } | { ok: false; reason: string }

const TIMEOUT_MS = 10_000

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
