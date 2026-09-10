import * as vscode from 'vscode'
import { Collector } from './collector'
import { initLog, log } from './log'
import { WorkLogTreeProvider } from './sidebar'
import { Uploader } from './uploader'

let collector: Collector
let tree: WorkLogTreeProvider
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

/** API Key 가 없을 때 Uploader 가 돌려주는 사유. 첫 실행 안내를 띄우는 조건이다. */
const NO_API_KEY = 'API Key 미설정'

async function send(reason: string): Promise<string | undefined> {
  if (sending) {
    log(`${reason}: 이미 전송 중이라 건너뜁니다`)
    return undefined
  }
  sending = true
  try {
    const { serverUrl, apiKey, collectDiff } = readConfig()
    const payloads = await collector.collect(collectDiff)
    log(`${reason}: 저장소 ${payloads.length}곳, 미커밋 ${collector.uncommittedCount}파일`)

    if (payloads.length === 0) {
      renderStatusBar()
      return undefined
    }
    const result = await new Uploader({ serverUrl, apiKey }).send(payloads)
    renderStatusBar(result.ok ? undefined : result.reason)
    tree.setStatus(
      result.ok
        ? { kind: 'sent', at: new Date(), files: collector.uncommittedCount }
        : { kind: 'failed', at: new Date(), reason: result.reason },
    )
    void tree.refresh()
    return result.ok ? undefined : result.reason
  } catch (e) {
    // 여기까지 온 예외는 버그다. 확장을 죽이지는 않는다.
    log(`전송 중 예기치 못한 오류: ${e instanceof Error ? e.stack ?? e.message : String(e)}`)
    renderStatusBar('전송 실패')
    return '전송 실패'
  } finally {
    sending = false
  }
}

/**
 * 설정 화면을 열어 준다. 설치 직후에는 API Key 가 비어 있어 전송이 무조건 실패하는데,
 * 상태바 경고만으로는 무엇을 해야 하는지 알 수 없다 (BACKLOG F-6 사용자 테스트).
 */
async function promptForApiKey(): Promise<void> {
  const open = '설정 열기'
  const picked = await vscode.window.showWarningMessage(
    'WorkLog: API Key 가 설정되지 않아 전송하지 못했습니다. 대시보드 설정 화면에서 발급한 키(wl_ 로 시작)를 넣어 주세요.',
    open,
  )
  if (picked === open) {
    await vscode.commands.executeCommand('workbench.action.openSettings', 'worklog.apiKey')
  }
}

/**
 * 저장할 때마다 git 을 부르면 연속 저장에서 낭비가 크다. 1초 안의 저장은 한 번으로 묶는다.
 */
let treeRefreshTimer: NodeJS.Timeout | undefined
function scheduleTreeRefresh() {
  if (treeRefreshTimer) clearTimeout(treeRefreshTimer)
  treeRefreshTimer = setTimeout(() => void tree.refresh(), 1000)
}

function restartTimer() {
  if (timer) clearInterval(timer)
  const minutes = Math.max(5, readConfig().intervalMinutes)
  timer = setInterval(() => void send('주기 전송'), minutes * 60 * 1000)
  log(`자동 전송 주기: ${minutes}분`)
}

export function activate(context: vscode.ExtensionContext): void {
  initLog(context)
  // globalState 에 저장 기록·계획을 남긴다. 재시작해도 그날 것은 이어 쌓인다.
  collector = new Collector(context.globalState)

  statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100)
  statusBar.command = 'worklog.sendNow'
  context.subscriptions.push(statusBar)
  renderStatusBar()

  tree = new WorkLogTreeProvider(collector)
  context.subscriptions.push(vscode.window.registerTreeDataProvider('worklog.session', tree))
  void tree.refresh()

  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument((doc) => {
      if (doc.uri.scheme !== 'file') return
      collector.recordSave(doc.uri.fsPath)
      scheduleTreeRefresh()
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.refresh', () => tree.refresh()),
  )

  // 사이드바에서 계획 한 줄을 지운다. 잘못 적은 메모가 그날 내내 남지 않게.
  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.removePlan', (node?: { note?: string }) => {
      if (!node?.note) return
      collector.removePlanNote(node.note)
      log(`계획 삭제: ${node.note}`)
      void tree.refresh()
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.recordPlan', async () => {
      const count = collector.planNotes.length
      const note = await vscode.window.showInputBox({
        title: 'WorkLog: 계획 추가',
        prompt: count === 0
          ? '오늘 무엇을 할 계획인지 한 줄로 적으세요. 초안의 "계획 / TODO" 에 들어갑니다.'
          : `이미 ${count}건 적었습니다. 덧붙일 계획을 적으세요.`,
        placeHolder: '예) 오후에 출석 중복 검증 로직 마무리',
      })
      if (note !== undefined && note.trim()) {
        collector.addPlanNote(note)
        log(`계획 추가: ${note.trim()} (총 ${collector.planNotes.length}건)`)
        void tree.refresh()
      }
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.sendNow', async () => {
      const failure = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Window, title: 'WorkLog 전송 중…' },
        () => send('지금 전송'),
      )
      // 사용자가 직접 누른 경우에만 안내한다. 주기·시작 전송까지 알리면 성가시다.
      if (failure === NO_API_KEY) await promptForApiKey()
    }),
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
  if (treeRefreshTimer) {
    clearTimeout(treeRefreshTimer)
    treeRefreshTimer = undefined
  }
  await send('종료 시 전송')
}
