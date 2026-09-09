import * as vscode from 'vscode'
import { Collector } from './collector'
import { initLog, log } from './log'
import { Uploader } from './uploader'

let collector: Collector
let statusBar: vscode.StatusBarItem
let timer: NodeJS.Timeout | undefined
/** 전송이 겹치지 않게 한다 — 주기 타이머와 "지금 전송" 이 동시에 들어올 수 있다. */
let sending = false

function readConfig() {
  const cfg = vscode.workspace.getConfiguration('worklog')
  return {
    serverUrl: cfg.get<string>('serverUrl', 'http://localhost:8080'),
    apiKey: cfg.get<string>('apiKey', ''),
    intervalMinutes: cfg.get<number>('intervalMinutes', 30),
    collectDiff: cfg.get<boolean>('collectDiff', true),
  }
}

/** 상태바에는 "미커밋 N파일" 또는 오류만 표시한다 (PRD F6). */
function renderStatusBar(error?: string) {
  if (error) {
    statusBar.text = `$(warning) WorkLog: ${error}`
    statusBar.tooltip = `${error} — 클릭하면 다시 전송합니다`
    statusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground')
  } else {
    statusBar.text = `$(git-commit) 미커밋 ${collector.uncommittedCount}파일`
    statusBar.tooltip = 'WorkLog Drafter — 클릭하면 지금 전송'
    statusBar.backgroundColor = undefined
  }
  statusBar.show()
}

async function send(reason: string): Promise<void> {
  if (sending) {
    log(`${reason}: 이미 전송 중이라 건너뜁니다`)
    return
  }
  sending = true
  try {
    const { serverUrl, apiKey, collectDiff } = readConfig()
    const payloads = await collector.collect(collectDiff)
    log(`${reason}: 저장소 ${payloads.length}곳, 미커밋 ${collector.uncommittedCount}파일`)

    if (payloads.length === 0) {
      renderStatusBar()
      return
    }
    const result = await new Uploader({ serverUrl, apiKey }).send(payloads)
    renderStatusBar(result.ok ? undefined : result.reason)
  } catch (e) {
    // 여기까지 온 예외는 버그다. 확장을 죽이지는 않는다.
    log(`전송 중 예기치 못한 오류: ${e instanceof Error ? e.stack ?? e.message : String(e)}`)
    renderStatusBar('전송 실패')
  } finally {
    sending = false
  }
}

function restartTimer() {
  if (timer) clearInterval(timer)
  const minutes = Math.max(5, readConfig().intervalMinutes)
  timer = setInterval(() => void send('주기 전송'), minutes * 60 * 1000)
  log(`자동 전송 주기: ${minutes}분`)
}

export function activate(context: vscode.ExtensionContext): void {
  initLog(context)
  collector = new Collector()

  statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100)
  statusBar.command = 'worklog.sendNow'
  context.subscriptions.push(statusBar)
  renderStatusBar()

  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument((doc) => {
      if (doc.uri.scheme !== 'file') return
      collector.recordSave(doc.uri.fsPath)
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.recordPlan', async () => {
      const note = await vscode.window.showInputBox({
        title: 'WorkLog: 오늘 계획 기록',
        prompt: '오늘 무엇을 할 계획인지 한두 줄로 적으세요. 초안의 "계획 / TODO" 에 들어갑니다.',
        placeHolder: '예) 오후에 출석 중복 검증 로직 마무리',
        value: collector.todayPlanNote,
      })
      if (note !== undefined && note.trim()) {
        collector.setPlanNote(note.trim())
        log(`계획 메모: ${note.trim()}`)
        vscode.window.showInformationMessage('WorkLog: 오늘 계획을 기록했습니다.')
      }
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.sendNow', () =>
      vscode.window.withProgress(
        { location: vscode.ProgressLocation.Window, title: 'WorkLog 전송 중…' },
        () => send('지금 전송'),
      ),
    ),
  )

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('worklog.intervalMinutes')) restartTimer()
    }),
  )

  restartTimer()
  // 켤 때 한 번 훑어 상태바를 채운다. 서버가 없어도 상태바 오류만 남는다.
  void send('시작 시 전송')
}

/**
 * VS Code 종료 시 마지막 전송 (PRD F6). 반환한 Promise 를 VS Code 가 잠시 기다려 주지만
 * 보장되는 시간이 아니므로, 실패하더라도 다음 실행의 주기 전송이 같은 세션을 UPSERT 한다.
 */
export async function deactivate(): Promise<void> {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
  await send('종료 시 전송')
}
