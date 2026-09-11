/**
 * 대시보드가 건네주는 **연결** 을 해석한다 — 서버 주소와 개인 키를 한 덩어리로.
 *
 * <p>예전에는 사람이 둘을 따로 날랐다. 대시보드에서 키를 복사하고, 편집기로 건너와
 * 명령 팔레트를 열고, 주소를 적고, 키를 붙여 넣었다. 키는 발급 직후 한 번만 보이는 값이라
 * 그 사이에 잃어버리면 처음부터 다시였다. 두 값을 <b>모두 알고 있는 쪽</b>은 대시보드이므로,
 * 옮기는 일도 대시보드가 하게 한다 — 링크 한 번이나 붙여넣기 한 번으로.
 *
 * <p>vscode API 를 쓰지 않는다. 편집기 없이 시험할 수 있어야 이 해석이 맞는지 확인된다.
 */

/** 서버 주소와 개인 키. 이 둘이 있어야 전송이 된다. */
export interface Connection {
  serverUrl: string
  apiKey: string
}

/**
 * 연결 코드 앞머리. 뒤에 오는 것은 `{"s":주소,"k":키}` 를 base64url 로 적은 값이다.
 *
 * <p>숫자 `1` 은 형식의 판이다. 담는 값이 늘어 옛 코드를 못 읽게 되는 날, 새 앞머리를
 * 쓰면 옛 확장이 "모르는 코드" 라고 말할 수 있다 — 조용히 틀리게 읽는 것보다 낫다.
 */
export const CONNECT_CODE_PREFIX = 'wlc1_'

/** 대시보드 링크가 들어오는 자리. `vscode://withly.worklog-drafter/connect` 의 뒷길이다. */
export const CONNECT_URI_PATH = '/connect'

/**
 * 사람이 적은 서버 주소를 쓸 수 있는 형태로 다듬는다.
 *
 * <p>`192.168.0.224:8080` 처럼 스킴 없이 적거나, 주소창에서 복사해 끝 슬래시나 `/api` 를
 * 달고 오는 일이 잦다. `/api` 는 Uploader 가 붙이므로 그대로 두면 `/api/api` 가 된다.
 *
 * @return 다듬은 주소. 주소로 볼 수 없으면 undefined
 */
export function normalizeServerUrl(raw: string): string | undefined {
  const trimmed = raw.trim()
  if (!trimmed) return undefined
  // 스킴을 적었다면 http(s) 여야 한다. 아닌 것을 스킴 없는 주소로 보고 `http://` 를 덧붙이면
  // `ftp://호스트` 가 `http://ftp//호스트` 로 둔갑해, 호스트가 `ftp` 인 멀쩡한 주소가 된다.
  const scheme = /^([a-z][a-z0-9+.-]*):\/\//i.exec(trimmed)
  if (scheme && !/^https?$/i.test(scheme[1])) return undefined
  const withScheme = scheme ? trimmed : `http://${trimmed}`
  let url: URL
  try {
    url = new URL(withScheme)
  } catch {
    return undefined
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return undefined
  if (!url.hostname) return undefined
  const tail = url.pathname.replace(/\/+$/, '').replace(/\/api$/i, '')
  return `${url.origin}${tail}`
}

/** 연결을 코드 한 줄로 적는다. 시험과 진단용이다 — 실제로 만드는 쪽은 대시보드다. */
export function encodeConnection(connection: Connection): string {
  const body = JSON.stringify({ s: connection.serverUrl, k: connection.apiKey })
  return CONNECT_CODE_PREFIX + Buffer.from(body, 'utf8').toString('base64url')
}

/**
 * 붙여 넣은 것이 무엇이든 연결로 읽어 본다.
 *
 * <p>사람은 손에 잡히는 것을 그대로 붙여 넣는다. 연결 코드일 때도 있고, 링크를 통째로
 * 복사해 올 때도 있고, 링크에서 `?` 뒷부분만 들고 올 때도 있다. 어느 쪽이든 받아 준다 —
 * "형식이 틀렸다" 고 돌려보내는 것은 여기서 할 일이 아니다.
 *
 * <p>받아들이는 모양
 * <ul>
 *   <li>{@code wlc1_...} — 연결 코드
 *   <li>{@code vscode://withly.worklog-drafter/connect?server=...&key=...} — 링크
 *   <li>{@code server=...&key=...} — 링크의 쿼리만
 *   <li>위 어느 쪽이든 {@code code=wlc1_...} 로 실려 온 것
 * </ul>
 *
 * @return 읽어 낸 연결. 둘 중 하나라도 성하지 않으면 undefined
 */
export function decodeConnection(raw: string): Connection | undefined {
  const text = raw.trim()
  if (!text) return undefined
  if (text.startsWith(CONNECT_CODE_PREFIX)) return fromCode(text)

  let query = text.startsWith('?') ? text.slice(1) : text
  // 링크 판단은 **맨 앞**으로만 한다. 아무 데나 `://` 가 있는지로 가리면, VS Code 가
  // 이미 풀어서 건네는 쿼리(`server=http://192.168.0.224:8080&key=…`)가 링크로 잘못 잡혀
  // 물음표를 찾다 실패한다 — 딥링크로 들어온 연결이 통째로 버려지던 자리다.
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) {
    const mark = text.indexOf('?')
    if (mark < 0) return undefined
    query = text.slice(mark + 1)
  }
  const params = new URLSearchParams(query)
  const code = params.get('code')
  if (code) return fromCode(code)
  return build(params.get('server'), params.get('key'))
}

function fromCode(code: string): Connection | undefined {
  const body = code.slice(CONNECT_CODE_PREFIX.length)
  if (!body) return undefined
  let parsed: unknown
  try {
    parsed = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'))
  } catch {
    // 코드를 자르거나 다른 것을 붙여 넣은 것이다. 여기서 터뜨릴 일은 아니다.
    return undefined
  }
  if (typeof parsed !== 'object' || parsed === null) return undefined
  const { s, k } = parsed as { s?: unknown; k?: unknown }
  return build(typeof s === 'string' ? s : null, typeof k === 'string' ? k : null)
}

function build(server: string | null, key: string | null): Connection | undefined {
  if (!server || !key) return undefined
  const serverUrl = normalizeServerUrl(server)
  if (!serverUrl) return undefined
  const apiKey = key.trim()
  if (!apiKey) return undefined
  return { serverUrl, apiKey }
}
