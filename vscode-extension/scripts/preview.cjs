/**
 * 수집기를 VS Code 없이 돌려 본다 — 무엇이 서버로 갈지 보내기 전에 확인하는 용도.
 *
 *   node scripts/preview.cjs [저장소경로] [--no-diff] [--plan "메모"] [--full]
 *
 * Collector 가 쓰는 vscode API 는 workspace.workspaceFolders 뿐이라, 그것만 흉내 내면
 * 컴파일된 out/collector.js 를 그대로 부를 수 있다. 실제 전송은 하지 않는다 (읽기 전용).
 */
const Module = require('node:module')
const path = require('node:path')
const fs = require('node:fs')

const args = process.argv.slice(2)
const flags = new Set(args.filter((a) => a.startsWith('--')))
const positional = args.filter((a) => !a.startsWith('--') && !a.startsWith('메모'))
const planIndex = args.indexOf('--plan')
const planNote = planIndex >= 0 ? args[planIndex + 1] : undefined

const target = path.resolve(positional[0] ?? process.cwd())
if (!fs.existsSync(path.join(target, '.git'))) {
  console.error(`git 저장소가 아닙니다: ${target}`)
  console.error('사용법: node scripts/preview.cjs [저장소경로] [--no-diff] [--plan "메모"] [--full]')
  process.exit(1)
}

// --- vscode 모듈 흉내 ---------------------------------------------------------
const stub = {
  workspace: {
    workspaceFolders: [{ name: path.basename(target), uri: { fsPath: target } }],
  },
}
const resolveFilename = Module._resolveFilename
Module._resolveFilename = function (request, ...rest) {
  if (request === 'vscode') return 'vscode'
  return resolveFilename.call(this, request, ...rest)
}
require.cache.vscode = { id: 'vscode', filename: 'vscode', loaded: true, exports: stub }

// --- 수집 --------------------------------------------------------------------
const outDir = path.join(__dirname, '..', 'out')
if (!fs.existsSync(path.join(outDir, 'collector.js'))) {
  console.error('out/collector.js 가 없습니다. 먼저 `npm run compile` 을 실행하세요.')
  process.exit(1)
}
const { Collector } = require(path.join(outDir, 'collector.js'))

async function main() {
  const collector = new Collector()
  if (planNote) collector.addPlanNote(planNote)

  const payloads = await collector.collect(!flags.has('--no-diff'))

  if (payloads.length === 0) {
    console.log(`${target}: 보낼 것이 없습니다 (브랜치나 origin 을 못 읽었거나 저장소가 아님).`)
    return
  }

  for (const p of payloads) {
    console.log('─'.repeat(72))
    console.log(`remoteUrl   ${p.remoteUrl}`)
    console.log(`branch      ${p.branch}`)
    console.log(`workDate    ${p.workDate}   (KST)`)
    console.log(`lastCommit  ${p.lastCommitAt ?? '(없음)'}`)
    console.log(`planNote    ${p.planNote ?? '(없음)'}`)

    console.log(`\n미커밋 ${p.uncommittedFiles.length}개`)
    for (const f of p.uncommittedFiles) {
      const diff = f.diff ? `${f.diff.split('\n').length}줄` : '(없음)'
      // 서버로 가는 payload 에는 untracked 플래그가 없다. 새 파일은 diff 머리글로 알아본다.
      const isNew = f.diff?.startsWith('@@ 새 파일 @@') ? '  [새 파일]' : ''
      console.log(`  +${f.additions} −${f.deletions}  ${f.path}${isNew}  diff ${diff}`)
    }

    const unpushed = collector.unpushedOf(p)
    if (unpushed === undefined) {
      console.log('\n미푸시 — 셀 수 없음 (업스트림 없음)')
    } else {
      console.log(`\n미푸시 ${unpushed.length}개  (서버로는 보내지 않는다 — 화면 표시용)`)
      for (const c of unpushed.slice(0, 10)) console.log(`  ${c.sha}  ${c.subject}`)
      if (unpushed.length > 10) console.log(`  … 외 ${unpushed.length - 10}개`)
    }

    console.log(`\nTODO ${p.todos.length}개`)
    for (const t of p.todos.slice(0, 20)) console.log(`  ${t.path}:${t.line}  ${t.text}`)
    if (p.todos.length > 20) console.log(`  … 외 ${p.todos.length - 20}개`)

    // editTimeline 은 VS Code 의 저장 이벤트로만 쌓이므로 여기서는 항상 비어 있다.
    console.log(`\nedit timeline ${p.editTimeline.length}개  (VS Code 밖에서는 항상 0 — 저장 이벤트가 없다)`)

    if (flags.has('--full')) {
      console.log('\n--- 서버로 갈 JSON ---')
      console.log(JSON.stringify(p, null, 2))
    }
  }
  console.log('─'.repeat(72))
  console.log('전송은 하지 않았습니다. 실제로 보내려면 VS Code 에서 `WorkLog: 지금 전송`.')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
