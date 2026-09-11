require('./stub-vscode')
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')

const { parseVscodeChat } = require('../out/ai/vscodeChat')
const { parseCodex, cwdOf } = require('../out/ai/codex')
const { parseGemini } = require('../out/ai/gemini')
const { parseClaude } = require('../out/ai/claude')

/**
 * 이 PC 에 실제로 쌓인 기록으로 파서를 돌려 본다.
 *
 * <p>꾸며 낸 자료만으로는 모르는 것이 있다 — 답변 조각의 값이 문자열일 때도 {value} 일
 * 때도 있다는 것은 진짜 기록에서만 드러났다. 그 도구를 안 쓰는 PC 에서는 건너뛴다.
 */
const all = (dir, depth = 4) => {
  const out = []
  const walk = (at, left) => {
    let names
    try {
      names = fs.readdirSync(at, { withFileTypes: true })
    } catch {
      return
    }
    for (const e of names) {
      const p = path.join(at, e.name)
      if (e.isDirectory()) { if (left > 0) walk(p, left - 1) } else out.push(p)
    }
  }
  walk(dir, depth)
  return out
}
const biggest = (files) => files.sort((a, b) => fs.statSync(b).size - fs.statSync(a).size)[0]

test('내장 채팅 — 실제 기록에서 질문과 답이 나온다', (t) => {
  const files = all(path.join(os.homedir(), 'Library', 'Application Support', 'Code', 'User', 'workspaceStorage'), 2)
    .filter((f) => f.includes(`${path.sep}chatSessions${path.sep}`))
  if (files.length === 0) return t.skip('이 PC 에는 내장 채팅 기록이 없다')

  const withTurns = files
    .map((f) => ({ f, out: parseVscodeChat(fs.readFileSync(f, 'utf8')) }))
    .filter((r) => r.out.turns.length > 0)
  if (withTurns.length === 0) return t.skip('기록은 있지만 오간 말이 없다')

  const answered = withTurns.filter((r) => r.out.turns.some((x) => x.answer))
  assert.ok(answered.length > 0, '답변이 하나도 안 읽히면 조각의 모양이 또 바뀐 것이다')
  for (const { out } of withTurns) {
    for (const turn of out.turns) {
      assert.ok(turn.prompt.trim().length > 0)
      assert.ok(!Number.isNaN(Date.parse(turn.at)), `시각이 ISO 여야 한다: ${turn.at}`)
    }
  }
  t.diagnostic(`세션 ${withTurns.length}개 · 질문 ${withTurns.reduce((n, r) => n + r.out.turns.length, 0)}개`)
})

test('Codex — 실제 기록에서 작업 폴더를 읽는다', (t) => {
  const files = all(path.join(os.homedir(), '.codex', 'sessions'), 4).filter((f) => f.endsWith('.jsonl'))
  if (files.length === 0) return t.skip('이 PC 에는 Codex 기록이 없다')

  const file = biggest(files)
  const text = fs.readFileSync(file, 'utf8')
  const cwd = cwdOf(text)
  assert.ok(cwd && path.isAbsolute(cwd), `첫 줄에 작업 폴더가 있어야 한다: ${cwd}`)
  const out = parseCodex(text)
  assert.ok(out.turns.length > 0, '사람이 친 말이 하나도 없으면 걸러 내는 규칙이 과한 것이다')
  assert.ok(out.turns.every((x) => !x.prompt.startsWith('<')), '지시문이 질문으로 새면 안 된다')
  t.diagnostic(`${path.basename(file)} · 질문 ${out.turns.length}개 · ${cwd}`)
})

test('Gemini — 실제 기록을 읽다 깨지지 않는다', (t) => {
  const files = all(path.join(os.homedir(), '.gemini', 'tmp'), 3).filter((f) => f.includes(`${path.sep}chats${path.sep}`))
  if (files.length === 0) return t.skip('이 PC 에는 Gemini 기록이 없다')

  let turns = 0
  for (const f of files) turns += parseGemini(fs.readFileSync(f, 'utf8')).turns.length
  // 이 PC 의 기록은 확장이 붙을 때 만드는 인사뿐이라 0이 맞다. 깨지지 않는 것만 본다.
  t.diagnostic(`기록 ${files.length}개 · 사람이 친 질문 ${turns}개`)
})

test('Claude — 실제 기록에서 질문과 답이 나온다', (t) => {
  const files = all(path.join(os.homedir(), '.claude', 'projects'), 2).filter((f) => f.endsWith('.jsonl'))
  if (files.length === 0) return t.skip('이 PC 에는 Claude Code 기록이 없다')

  const out = parseClaude(fs.readFileSync(biggest(files), 'utf8'))
  assert.ok(out.turns.length > 0)
  assert.ok(out.turns.some((x) => x.answer), '답이 하나도 없으면 기록 모양이 바뀐 것이다')
  t.diagnostic(`질문 ${out.turns.length}개 · 제목: ${out.title ?? '(없음)'}`)
})
