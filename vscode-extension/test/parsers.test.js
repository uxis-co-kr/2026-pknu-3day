require('./stub-vscode')
const test = require('node:test')
const assert = require('node:assert/strict')

const { parseClaude } = require('../out/ai/claude')
const { parseVscodeChat, replay } = require('../out/ai/vscodeChat')
const { parseCodex, cwdOf } = require('../out/ai/codex')
const { parseGemini } = require('../out/ai/gemini')

/** 2026-09-11 10:00 KST. 업무 일자는 KST 라 UTC 로 적으면 전날이 되기 쉽다. */
const AT = '2026-09-11T01:00:00.000Z'
const DAY = '2026-09-11'
const lines = (...rows) => rows.map((r) => JSON.stringify(r)).join('\n') + '\n'

test('Claude — 질문과 답, 그리고 제목', () => {
  const text = lines(
    { type: 'ai-title', aiTitle: '첫 제목' },
    { type: 'user', timestamp: AT, message: { content: [{ type: 'text', text: '이걸 고쳐라' }] } },
    { type: 'assistant', timestamp: AT, message: { content: [{ type: 'text', text: '고쳤습니다' }] } },
    { type: 'ai-title', aiTitle: '고친 제목' },
  )
  const out = parseClaude(text, DAY)
  assert.equal(out.title, '고친 제목', '제목은 마지막에 적힌 것을 쓴다')
  assert.equal(out.turns.length, 1)
  assert.equal(out.turns[0].prompt, '이걸 고쳐라')
  assert.equal(out.turns[0].answer, '고쳤습니다')
})

test('Claude — 편집기가 끼워 넣은 덩어리가 앞에 붙어도 질문이 살아남는다', () => {
  // 이어 붙인 뒤에 보면 첫 글자가 `<` 라서 질문이 통째로 사라졌던 자리다.
  const text = lines({
    type: 'user',
    timestamp: AT,
    message: { content: [{ type: 'text', text: '<ide_opened_file>a.ts</ide_opened_file>' }, { type: 'text', text: '진짜 질문' }] },
  })
  assert.equal(parseClaude(text, DAY).turns[0].prompt, '진짜 질문')
})

test('Claude — 곁가지·메타·다른 날짜는 세지 않는다', () => {
  const text = lines(
    { type: 'user', timestamp: AT, isSidechain: true, message: { content: [{ type: 'text', text: '곁가지' }] } },
    { type: 'user', timestamp: AT, isMeta: true, message: { content: [{ type: 'text', text: '메타' }] } },
    { type: 'user', timestamp: '2026-09-09T01:00:00.000Z', message: { content: [{ type: 'text', text: '그저께' }] } },
    { type: 'user', timestamp: AT, message: { content: [{ type: 'text', text: '오늘 것' }] } },
  )
  const out = parseClaude(text, DAY)
  assert.deepEqual(out.turns.map((t) => t.prompt), ['오늘 것'])
})

test('내장 채팅 — 통짜 JSON', () => {
  const doc = {
    customTitle: '버튼 색 바꾸기',
    requests: [{
      message: { text: '추가 버튼 색을 바꿔라' },
      timestamp: Date.parse(AT),
      response: [
        { kind: 'thinking', value: '음…' },
        { value: '바꾸겠습니다' },
        { kind: 'toolInvocationSerialized', value: null },
      ],
    }],
  }
  const out = parseVscodeChat(JSON.stringify(doc), DAY)
  assert.equal(out.title, '버튼 색 바꾸기')
  assert.equal(out.turns.length, 1)
  assert.equal(out.turns[0].prompt, '추가 버튼 색을 바꿔라')
  assert.equal(out.turns[0].answer, '바꾸겠습니다', 'kind 가 붙은 조각(생각·도구)은 답이 아니다')
  assert.equal(out.turns[0].at, AT)
})

test('내장 채팅 — 답변 조각의 값이 문자열이든 {value} 든 읽는다', () => {
  // 한쪽만 받으면 답변이 통째로 빈칸이 되던 자리다.
  const one = { requests: [{ message: { text: 'q' }, timestamp: Date.parse(AT), response: [{ value: '문자열 답' }] }] }
  const two = { requests: [{ message: { text: 'q' }, timestamp: Date.parse(AT), response: [{ value: { value: '감싼 답' } }] }] }
  assert.equal(parseVscodeChat(JSON.stringify(one), DAY).turns[0].answer, '문자열 답')
  assert.equal(parseVscodeChat(JSON.stringify(two), DAY).turns[0].answer, '감싼 답')
})

test('내장 채팅 — 덧붙이는 기록(.jsonl)을 되감는다', () => {
  // 첫 줄의 requests 는 늘 비어 있다. 되감지 않으면 대화가 통째로 0건이 된다.
  const text = lines(
    { kind: 0, v: { customTitle: null, requests: [], inputState: { inputText: '' } } },
    { kind: 1, k: ['customTitle'], v: '되감은 제목' },
    { kind: 2, k: ['requests'], v: [{ message: { text: '첫 질문' }, timestamp: Date.parse(AT), response: [{ value: '첫 답' }] }] },
    { kind: 1, k: ['inputState', 'inputText'], v: '적다 만 말' },
    { kind: 2, k: ['requests'], v: [{ message: { text: '둘째 질문' }, timestamp: Date.parse(AT), response: [] }] },
    { kind: 1, k: ['requests', 1, 'response'], v: [{ value: '둘째 답' }] },
  )
  const out = parseVscodeChat(text, DAY)
  assert.equal(out.title, '되감은 제목')
  assert.deepEqual(out.turns.map((t) => t.prompt), ['첫 질문', '둘째 질문'])
  assert.deepEqual(out.turns.map((t) => t.answer), ['첫 답', '둘째 답'])
})

test('내장 채팅 — 줄이 하나뿐인 덧붙임 기록도 되감기로 읽는다', () => {
  // 통째로 파싱되지만 `{kind, v}` 라 대화가 아니다. 빈 대화로 읽혀야 한다.
  const text = lines({ kind: 0, v: { requests: [] } })
  assert.deepEqual(parseVscodeChat(text, DAY).turns, [])
  assert.deepEqual(replay(text).requests, [])
})

test('내장 채팅 — 코드를 붙여 넣고 물어본 질문이 사라지지 않는다', () => {
  // 이 PC 의 실제 기록에서 사라져 있던 질문이다. 사람이 친 말을 편집기가 따로 적어 두므로
  // `<` 로 시작한다고 도구가 끼워 넣은 문맥이라 볼 수 없다.
  const doc = {
    requests: [{
      message: { text: '<!-- Google tag -->\n<script async src="x"></script>\n이거 왜 안 붙지?' },
      timestamp: Date.parse(AT),
      response: [{ value: '<div> 를 여기 넣으세요' }],
    }],
  }
  const out = parseVscodeChat(JSON.stringify(doc), DAY)
  assert.equal(out.turns.length, 1)
  assert.match(out.turns[0].prompt, /이거 왜 안 붙지\?$/)
  assert.equal(out.turns[0].answer, '<div> 를 여기 넣으세요', '답도 마찬가지다')
})

test('내장 채팅 — 다른 날의 질문은 빼고 센다', () => {
  const doc = {
    requests: [
      { message: { text: '어제' }, timestamp: Date.parse('2026-09-10T01:00:00.000Z'), response: [] },
      { message: { text: '오늘' }, timestamp: Date.parse(AT), response: [] },
    ],
  }
  assert.deepEqual(parseVscodeChat(JSON.stringify(doc), DAY).turns.map((t) => t.prompt), ['오늘'])
})

test('Codex — 첫 줄에서 작업 폴더를 읽는다', () => {
  const text = lines({ timestamp: AT, type: 'session_meta', payload: { cwd: '/Users/me/work/app_v2', source: 'vscode' } })
  assert.equal(cwdOf(text), '/Users/me/work/app_v2')
  assert.equal(cwdOf(lines({ type: 'event_msg', payload: {} })), undefined)
})

test('Codex — 사람이 친 말과 답만 남긴다', () => {
  const text = lines(
    { timestamp: AT, type: 'session_meta', payload: { cwd: '/x' } },
    { timestamp: AT, type: 'response_item', payload: { type: 'message', role: 'developer', content: [{ type: 'input_text', text: '<app-context>…' }] } },
    { timestamp: AT, type: 'response_item', payload: { type: 'message', role: 'user', content: [{ type: 'input_text', text: '<recommended_plugins>…' }] } },
    { timestamp: AT, type: 'response_item', payload: { type: 'message', role: 'user', content: [{ type: 'input_text', text: 'gps 코드를 점검해라' }] } },
    { timestamp: AT, type: 'response_item', payload: { type: 'reasoning', content: [{ text: '생각 중' }] } },
    { timestamp: AT, type: 'response_item', payload: { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: '점검했습니다' }] } },
  )
  const out = parseCodex(text, DAY)
  assert.equal(out.turns.length, 1, '지시문·도구 줄은 질문이 아니다')
  assert.equal(out.turns[0].prompt, 'gps 코드를 점검해라')
  assert.equal(out.turns[0].answer, '점검했습니다')
})

test('Gemini — 마지막에 다시 적힌 것만 본다', () => {
  const head = { sessionId: 'a2a-server', startTime: AT, kind: 'main' }
  const text = lines(
    head,
    { $set: { messages: [{ timestamp: AT, type: 'user', content: [{ text: '<session_context>…' }] }] } },
    head,
    { $set: { messages: [
      { timestamp: AT, type: 'user', content: [{ text: '<session_context>…' }] },
      { timestamp: AT, type: 'user', content: [{ text: '이 함수를 고쳐라' }] },
      { timestamp: AT, type: 'info', content: [{ text: '토큰을 아꼈습니다' }] },
      { timestamp: AT, type: 'gemini', content: [{ text: '고쳤습니다' }] },
    ] } },
  )
  const out = parseGemini(text, DAY)
  assert.equal(out.turns.length, 1)
  assert.equal(out.turns[0].prompt, '이 함수를 고쳐라')
  assert.equal(out.turns[0].answer, '고쳤습니다', 'info 줄은 답이 아니다')
})
