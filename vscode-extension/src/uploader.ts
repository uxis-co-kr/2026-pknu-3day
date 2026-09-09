import type { SessionPayload } from './types'

export interface UploaderConfig {
  serverUrl: string
  apiKey: string
}

/**
 * 수집한 세션을 백엔드로 보낸다 (PRD F6 전송).
 *
 * 1-7 에서 구현: POST {serverUrl}/api/vscode/sessions, 헤더 X-Api-Key.
 * 실패해도 예외를 밖으로 던지지 않고 상태바 오류 표시로만 알린다 (F6 수용 기준).
 */
export class Uploader {
  constructor(private readonly config: UploaderConfig) {}

  async send(_payload: SessionPayload): Promise<{ ok: true } | { ok: false; reason: string }> {
    // 1-7 에서 구현.
    if (!this.config.apiKey) {
      return { ok: false, reason: 'API Key 가 설정되지 않았습니다 (worklog.apiKey)' }
    }
    return { ok: false, reason: '전송 미구현 (1-7)' }
  }
}
