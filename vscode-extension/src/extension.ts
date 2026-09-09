import * as vscode from 'vscode'
import { Collector } from './collector'
import { Uploader } from './uploader'

let collector: Collector
let statusBar: vscode.StatusBarItem
let timer: NodeJS.Timeout | undefined

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
    statusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground')
  } else {
    statusBar.text = `$(git-commit) 미커밋 ${collector.uncommittedCount}파일`
    statusBar.backgroundColor = undefined
  }
  statusBar.show()
}

async function send(): Promise<void> {
  const { serverUrl, apiKey } = readConfig()
  const payload = await collector.collect()
  if (!payload) {
    renderStatusBar('수집 미구현 (1-6)')
    return
  }
  const result = await new Uploader({ serverUrl, apiKey }).send(payload)
  renderStatusBar(result.ok ? undefined : result.reason)
}

export function activate(context: vscode.ExtensionContext): void {
  collector = new Collector()

  statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100)
  statusBar.command = 'worklog.sendNow'
  statusBar.tooltip = 'WorkLog Drafter — 클릭하면 지금 전송'
  context.subscriptions.push(statusBar)
  renderStatusBar()

  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument((doc) => {
      collector.recordSave(vscode.workspace.asRelativePath(doc.uri))
      renderStatusBar()
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.recordPlan', async () => {
      const note = await vscode.window.showInputBox({
        title: 'WorkLog: 오늘 계획 기록',
        prompt: '오늘 무엇을 할 계획인지 한두 줄로 적으세요. 초안의 "계획 / TODO" 에 들어갑니다.',
        placeHolder: '예) 오후에 출석 중복 검증 로직 마무리',
      })
      if (note) {
        collector.setPlanNote(note)
        vscode.window.showInformationMessage('WorkLog: 오늘 계획을 기록했습니다.')
      }
    }),
  )

  context.subscriptions.push(vscode.commands.registerCommand('worklog.sendNow', send))

  const { intervalMinutes } = readConfig()
  timer = setInterval(() => void send(), intervalMinutes * 60 * 1000)
}

export function deactivate(): void {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
  // VS Code 종료 시 마지막 전송 (PRD F6). 1-7 에서 동기 전송으로 마무리한다.
}
