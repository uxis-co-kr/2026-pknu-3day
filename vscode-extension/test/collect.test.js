require('./stub-vscode')
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

/**
 * 네 도구의 기록을 한꺼번에 모으는 길 전체를 돌려 본다.
 *
 * <p>가짜 홈 폴더를 만들어 `HOME` 을 거기로 돌린다 — 도구들이 기록을 홈 아래 둔다는 것이
 * 이 기능의 전제라, 그 전제까지 같이 시험하는 편이 낫다. 모듈이 켜질 때 경로를 잡으므로
 * 반드시 `require` 보다 <b>먼저</b> 바꾼다.
 */
const HOME = fs.mkdtempSync(path.join(os.tmpdir(), 'worklog-home-'))
process.env.HOME = HOME

const { collectAiSessions, initAiSources, sourceOfId } = require('../out/aiSessions')

/** 기록에 적힌 폴더. 밑줄이 든 이름으로 둔다 — Claude 는 이름을 바꿔 저장한다. */
const CWD = '/tmp/worklog-test/repo_v2'
const DAY = '2026-09-11'
/** 10:00 KST. 시각을 UTC 로 적으면 전날로 세기 쉽다. */
const at = (hhmm) => `2026-09-11T${hhmm}:00.000Z`
const write = (file, text) => {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, text)
}
const lines = (...rows) => rows.map((r) => JSON.stringify(r)).join('\n') + '\n'

// ── Claude Code: 질문 15개짜리 대화 하나 + 빈 대화 하나 + 지난주에 닫은 대화 하나
const claudeDir = path.join(HOME, '.claude', 'projects', '-tmp-worklog-test-repo-v2')
write(path.join(claudeDir, 'aaaa-1111.jsonl'), lines(
  { type: 'ai-title', aiTitle: 'Claude 로 한 일' },
  ...Array.from({ length: 15 }, (_, i) => [
    { type: 'user', timestamp: at(`01:${String(i).padStart(2, '0')}`), message: { content: [{ type: 'text', text: `${i}번 질문 ` + 'ㄱ'.repeat(250) }] } },
    { type: 'assistant', timestamp: at(`01:${String(i).padStart(2, '0')}`), message: { content: [{ type: 'text', text: '답 ' + 'ㄴ'.repeat(400) }] } },
  ]).flat(),
))
write(path.join(claudeDir, 'bbbb-2222.jsonl'), lines(
  { type: 'user', timestamp: at('02:00'), isSidechain: true, message: { content: [{ type: 'text', text: '곁가지뿐' }] } },
))
const stale = path.join(claudeDir, 'cccc-3333.jsonl')
write(stale, lines({ type: 'user', timestamp: at('03:00'), message: { content: [{ type: 'text', text: '오늘 것처럼 보이지만' }] } }))
fs.utimesSync(stale, new Date('2026-09-01'), new Date('2026-09-01'))

// ── VS Code 내장 채팅: 이 폴더를 열었던 창의 기록
const USER = path.join(HOME, 'Library', 'Application Support', 'Code', 'User')
const ws = path.join(USER, 'workspaceStorage', 'deadbeef')
write(path.join(ws, 'workspace.json'), JSON.stringify({ folder: `file://${CWD}` }))
write(path.join(ws, 'chatSessions', 'chat-1.json'), JSON.stringify({
  customTitle: '내장 채팅으로 한 일',
  requests: [
    { message: { text: '버튼 색을 바꿔라' }, timestamp: Date.parse(at('04:00')), response: [{ value: '바꿨습니다' }] },
    { message: { text: '테스트도 고쳐라' }, timestamp: Date.parse(at('04:30')), response: [{ kind: 'thinking', value: '음' }] },
  ],
}))
// 다른 폴더를 열었던 창의 기록은 섞이면 안 된다.
const other = path.join(USER, 'workspaceStorage', 'cafe')
write(path.join(other, 'workspace.json'), JSON.stringify({ folder: 'file:///tmp/남의저장소' }))
write(path.join(other, 'chatSessions', 'chat-9.json'), JSON.stringify({
  customTitle: '남의 대화', requests: [{ message: { text: '남의 질문' }, timestamp: Date.parse(at('05:00')), response: [] }],
}))

// ── Codex: 날짜 폴더 아래 rollout 하나. 제목은 색인에 있다.
write(path.join(HOME, '.codex', 'session_index.jsonl'), lines(
  { id: '019fea63-9652-7a32-8c03-81cb1b593395', thread_name: 'Codex 로 한 일', updated_at: at('06:00') },
))
write(path.join(HOME, '.codex', 'sessions', '2026', '09', '11', 'rollout-2026-09-11T15-00-00-019fea63-9652-7a32-8c03-81cb1b593395.jsonl'), lines(
  { timestamp: at('06:00'), type: 'session_meta', payload: { cwd: CWD, source: 'vscode' } },
  { timestamp: at('06:00'), type: 'response_item', payload: { type: 'message', role: 'user', content: [{ type: 'input_text', text: '배포 스크립트를 고쳐라' }] } },
  { timestamp: at('06:10'), type: 'response_item', payload: { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: '고쳤습니다' }] } },
))
// 다른 폴더에서 한 Codex 대화.
write(path.join(HOME, '.codex', 'sessions', '2026', '09', '11', 'rollout-2026-09-11T16-00-00-ffffffff-0000-0000-0000-000000000000.jsonl'), lines(
  { timestamp: at('07:00'), type: 'session_meta', payload: { cwd: '/tmp/남의저장소' } },
  { timestamp: at('07:00'), type: 'response_item', payload: { type: 'message', role: 'user', content: [{ type: 'input_text', text: '남의 질문' }] } },
))

// ── Gemini: 폴더 이름이 아니라 .project_root 로 가린다. 제목이 없어 첫 질문에서 만든다.
const gem = path.join(HOME, '.gemini', 'tmp', 'repo_v2')
write(path.join(gem, '.project_root'), CWD)
write(path.join(gem, 'chats', 'session-2026-09-11T08-00-a2a-serv.jsonl'), lines(
  { sessionId: 'a2a-server', startTime: at('08:00'), kind: 'main' },
  { $set: { messages: [
    { timestamp: at('08:00'), type: 'user', content: [{ text: '<session_context>…' }] },
    { timestamp: at('08:00'), type: 'user', content: [{ text: '제목이 없는 대화라 이 질문이 제목이 된다 ' + '가'.repeat(80) }] },
    { timestamp: at('08:05'), type: 'gemini', content: [{ text: '알겠습니다' }] },
  ] } },
))
// 같은 이름의 남의 폴더 — 이름만 보면 헷갈리는 자리다.
const gemOther = path.join(HOME, '.gemini', 'tmp', 'repo_v2-1')
write(path.join(gemOther, '.project_root'), '/tmp/남의저장소')
write(path.join(gemOther, 'chats', 'session-2026-09-11T09-00-a2a-serv.jsonl'), lines(
  { sessionId: 'a2a-server', startTime: at('09:00'), kind: 'main' },
  { $set: { messages: [{ timestamp: at('09:00'), type: 'user', content: [{ text: '남의 질문' }] }] } },
))

initAiSources(path.join(USER, 'globalStorage', 'withly.worklog-drafter'))

test('네 도구의 대화를 한 목록으로 모은다', async () => {
  const got = await collectAiSessions(CWD, DAY)
  assert.equal(got.length, 4, '도구마다 하나씩 — 남의 폴더 대화 셋은 들어오지 않는다')
  assert.deepEqual(got.map((s) => sourceOfId(s.id).key).sort(), ['', 'codex', 'gemini', 'vscode'])
  assert.ok(!got.some((s) => s.turns.some((t) => t.prompt.includes('남의'))), '남의 저장소 질문이 섞이면 안 된다')
})

test('마지막으로 말한 대화가 앞에 온다', async () => {
  const got = await collectAiSessions(CWD, DAY)
  const order = [...got].sort((a, b) => b.lastAt.localeCompare(a.lastAt))
  assert.deepEqual(got.map((s) => s.id), order.map((s) => s.id))
  assert.equal(sourceOfId(got[0].id).key, 'gemini', '가장 늦게 오간 것이 Gemini 다')
})

test('세션 id 에 도구 이름표가 붙는다 — Claude 만 빼고', async () => {
  const got = await collectAiSessions(CWD, DAY)
  const id = (key) => got.find((s) => sourceOfId(s.id).key === key).id
  assert.equal(id(''), 'aaaa-1111', 'Claude 는 서버에 쌓인 옛 id 와 이어야 한다')
  assert.equal(id('vscode'), 'vscode:chat-1')
  assert.equal(id('codex'), 'codex:019fea63-9652-7a32-8c03-81cb1b593395')
  assert.equal(id('gemini'), 'gemini:session-2026-09-11T08-00-a2a-serv')
})

test('물어본 횟수는 전부 세고, 담는 것은 최근 12개까지', async () => {
  const got = await collectAiSessions(CWD, DAY)
  const claude = got.find((s) => sourceOfId(s.id).key === '')
  assert.equal(claude.promptCount, 15, '자른 개수를 보고하면 15번 물은 대화가 12개로 보인다')
  assert.equal(claude.turns.length, 12)
  assert.match(claude.turns[0].prompt, /^3번 질문/, '뒤에서 12개 — 오후에 한 일이 빠지면 안 된다')
  assert.ok(claude.turns[0].prompt.length <= 201, '질문은 200자에서 자른다')
  assert.ok(claude.turns[0].answer.length <= 301, '답변은 300자에서 자른다')
})

test('제목 — 기록에 있으면 그것, 없으면 자르기 전의 첫 질문', async () => {
  const got = await collectAiSessions(CWD, DAY)
  const of = (key) => got.find((s) => sourceOfId(s.id).key === key)
  assert.equal(of('').title, 'Claude 로 한 일')
  assert.equal(of('vscode').title, '내장 채팅으로 한 일')
  assert.equal(of('codex').title, 'Codex 로 한 일', '색인에 적힌 이름을 쓴다')
  assert.ok(of('gemini').title.startsWith('제목이 없는 대화라'))
  assert.ok(of('gemini').title.length <= 41, '제목은 40자에서 자른다')
})

test('질문이 없는 대화와 그날 손대지 않은 기록은 보내지 않는다', async () => {
  const got = await collectAiSessions(CWD, DAY)
  assert.ok(!got.some((s) => s.id === 'bbbb-2222'), '곁가지뿐인 대화는 빈 것이다')
  assert.ok(!got.some((s) => s.id === 'cccc-3333'), '지난주에 닫은 기록은 열지 않는다')
})

test('열어 둔 대화는 오늘 묻지 않았어도 싣는다', async () => {
  // 어제 묻던 대화를 지금 붙들고 있으면 그것도 오늘 하는 일이다.
  const held = path.join(claudeDir, 'dddd-4444.jsonl')
  write(held, lines(
    { type: 'user', timestamp: '2026-09-10T01:00:00.000Z', message: { content: [{ type: 'text', text: '어제 묻던 것' }] } },
  ))
  write(path.join(HOME, '.claude', 'sessions', '1.json'),
    JSON.stringify({ pid: process.pid, sessionId: 'dddd-4444', cwd: CWD }))

  const got = await collectAiSessions(CWD, DAY)
  const open = got.find((s) => s.id === 'dddd-4444')
  assert.ok(open, '열려 있는 대화는 날짜와 상관없이 싣는다')
  assert.equal(open.turns[0].prompt, '어제 묻던 것')

  fs.rmSync(path.join(HOME, '.claude', 'sessions', '1.json'))
  fs.rmSync(held)
})

test('도구를 가리는 이름표는 되돌려 읽을 수 있다', () => {
  assert.deepEqual(sourceOfId('vscode:x'), { key: 'vscode', label: '내장 채팅' })
  assert.deepEqual(sourceOfId('codex:x'), { key: 'codex', label: 'Codex' })
  assert.deepEqual(sourceOfId('gemini:x'), { key: 'gemini', label: 'Gemini' })
  // 접두사가 없으면 Claude 다 — 서버에 쌓인 옛 id 가 그 모양이다.
  assert.deepEqual(sourceOfId('9c1f0e42-1111-2222'), { key: '', label: 'Claude Code' })
  // 모르는 접두사를 붙여 보내는 도구는 없다. 그래도 Claude 로 읽어 파일을 못 찾을 뿐이어야 한다.
  assert.equal(sourceOfId('unknown:x').key, '')
})

test.after(() => fs.rmSync(HOME, { recursive: true, force: true }))
