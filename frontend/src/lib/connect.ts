import { API_BASE } from '@/api/apiClient'

/**
 * 방금 발급한 키를 VS Code 확장에 <b>그대로 건네주는</b> 길.
 *
 * <p>확장이 전송을 하려면 백엔드 주소와 개인 키가 둘 다 있어야 한다. 예전에는 둘을 사람이
 * 날랐다 — 여기서 키를 복사하고, 편집기로 건너가 명령 팔레트를 열고, 주소를 적고, 키를
 * 붙여 넣었다. 주소를 잘못 적으면 "서버에 연결할 수 없음" 이 떠서 애먼 키를 의심하게 되고,
 * 키는 발급 직후 한 번만 보이는 값이라 그 사이에 잃어버리면 처음부터 다시였다.
 *
 * <p>두 값을 모두 아는 쪽은 이 화면이다. 옮기는 일도 이 화면이 한다.
 */

/**
 * 확장이 불러야 할 백엔드 주소.
 *
 * <p>대시보드 주소가 아니다. 대시보드는 Vite dev server(5173)가 띄우고 `/api` 요청만 같은 PC 의
 * 8080 으로 넘기므로, 브라우저 주소창의 포트를 그대로 주면 확장은 아무 데도 닿지 못한다.
 *
 * <p>순서대로 본다.
 * <ol>
 *   <li>{@code VITE_BACKEND_PUBLIC_URL} — 배포가 이 짐작과 다를 때 못 박는 자리
 *   <li>{@code VITE_API_BASE_URL} 이 절대 주소면 그것 (프록시 없이 8080 을 직접 부르는 설정)
 *   <li>그 밖에는 지금 보고 있는 호스트의 8080 — 프록시가 같은 PC 의 8080 을 보고 있다
 * </ol>
 */
export function backendBaseUrl(): string {
  const pinned = import.meta.env.VITE_BACKEND_PUBLIC_URL as string | undefined
  if (pinned?.trim()) return trimApi(pinned.trim())
  if (/^https?:\/\//i.test(API_BASE)) return trimApi(API_BASE)
  const port = (import.meta.env.VITE_BACKEND_PORT as string | undefined)?.trim() || '8080'
  return `${window.location.protocol}//${window.location.hostname}:${port}`
}

/** 끝 슬래시와 `/api` 를 떼어 낸다. `/api` 는 확장이 스스로 붙인다. */
function trimApi(url: string): string {
  return url.replace(/\/+$/, '').replace(/\/api$/i, '').replace(/\/+$/, '')
}

/**
 * 붙여 넣기 한 번으로 끝나는 연결 코드.
 *
 * <p>확장의 `src/connect.ts` 가 같은 형식을 읽는다. 둘 중 하나를 바꾸면 다른 쪽도 바꿔야 한다.
 */
export function connectCode(apiKey: string): string {
  const body = JSON.stringify({ s: backendBaseUrl(), k: apiKey })
  // 키도 주소도 ASCII 라 btoa 로 충분하다. base64url 로 바꿔 링크에도 그대로 실린다.
  return 'wlc1_' + btoa(body).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/**
 * 편집기를 직접 여는 링크.
 *
 * <p>VS Code 는 {@code vscode://}, Cursor 는 {@code cursor://} 로 자기 확장을 깨운다. 스킴만
 * 다르고 뒷길은 같다.
 */
export function editorConnectLink(apiKey: string, scheme: 'vscode' | 'cursor' = 'vscode'): string {
  const params = new URLSearchParams({ server: backendBaseUrl(), key: apiKey })
  return `${scheme}://withly.worklog-drafter/connect?${params.toString()}`
}
