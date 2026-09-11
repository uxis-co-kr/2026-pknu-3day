require('./stub-vscode')
const test = require('node:test')
const assert = require('node:assert/strict')

const { decodeConnection, encodeConnection, normalizeServerUrl } = require('../out/connect')

const SERVER = 'http://192.168.0.224:8080'
const KEY = 'wl_abcdefghijklmnopqrstuvwxyz'

test('연결 코드를 적었다 다시 읽는다', () => {
  const code = encodeConnection({ serverUrl: SERVER, apiKey: KEY })
  assert.ok(code.startsWith('wlc1_'))
  assert.deepEqual(decodeConnection(code), { serverUrl: SERVER, apiKey: KEY })
})

test('대시보드 링크를 통째로 붙여 넣어도 읽는다', () => {
  const link = `vscode://withly.worklog-drafter/connect?server=${encodeURIComponent(SERVER)}&key=${KEY}`
  assert.deepEqual(decodeConnection(link), { serverUrl: SERVER, apiKey: KEY })
})

test('링크의 쿼리만 와도 읽는다 — URI 핸들러가 그것만 준다', () => {
  assert.deepEqual(
    decodeConnection(`server=${encodeURIComponent(SERVER)}&key=${KEY}`),
    { serverUrl: SERVER, apiKey: KEY },
  )
})

test('VS Code 가 풀어서 건네는 쿼리도 읽는다 — 주소에 :// 가 그대로 들어 있다', () => {
  assert.deepEqual(decodeConnection(`server=${SERVER}&key=${KEY}`), { serverUrl: SERVER, apiKey: KEY })
})

test('코드를 링크에 실어 보내도 읽는다', () => {
  const code = encodeConnection({ serverUrl: SERVER, apiKey: KEY })
  assert.deepEqual(
    decodeConnection(`vscode://withly.worklog-drafter/connect?code=${code}`),
    { serverUrl: SERVER, apiKey: KEY },
  )
})

test('주소는 다듬어 들어간다 — 끝 슬래시와 /api 는 Uploader 가 붙인다', () => {
  const c = decodeConnection(`server=${encodeURIComponent('192.168.0.224:8080/api/')}&key=${KEY}`)
  assert.equal(c.serverUrl, SERVER)
})

test('반쪽짜리나 망가진 것은 받지 않는다', () => {
  assert.equal(decodeConnection(''), undefined)
  assert.equal(decodeConnection('   '), undefined)
  assert.equal(decodeConnection(`server=${encodeURIComponent(SERVER)}`), undefined, '키가 없다')
  assert.equal(decodeConnection(`key=${KEY}`), undefined, '주소가 없다')
  assert.equal(decodeConnection('wlc1_'), undefined, '알맹이가 없다')
  assert.equal(decodeConnection('wlc1_@@@@'), undefined, '코드가 깨졌다')
  assert.equal(
    decodeConnection(`server=${encodeURIComponent('ftp://192.168.0.224')}&key=${KEY}`),
    undefined,
    'http 가 아니다',
  )
  // 코드를 자르면 JSON 이 깨진다. 조용히 절반만 읽으면 엉뚱한 서버로 보낸다.
  const code = encodeConnection({ serverUrl: SERVER, apiKey: KEY })
  assert.equal(decodeConnection(code.slice(0, code.length - 8)), undefined)
})

test('주소가 아닌 것은 주소로 보지 않는다', () => {
  assert.equal(normalizeServerUrl(''), undefined)
  assert.equal(normalizeServerUrl('http://'), undefined)
  assert.equal(normalizeServerUrl('localhost:8080'), 'http://localhost:8080')
})
