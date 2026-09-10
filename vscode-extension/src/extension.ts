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
/** 키 확인 때문에 부른 전송인지. 알림을 두 번 띄우지 않기 위한 표시다. */
let verifying = false

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
    const keyProblem = error === NO_API_KEY || error === WRONG_API_KEY
    statusBar.text = `$(warning) WorkLog: ${error}`
    // 키가 문제면 다시 전송해 봐야 같은 곳에서 막힌다. 바로 키 입력으로 보낸다.
    statusBar.command = keyProblem ? 'worklog.setApiKey' : 'worklog.sendNow'
    statusBar.tooltip = keyProblem ? `${error} — 클릭하면 키를 입력합니다` : `${error} — 클릭하면 다시 전송합니다`
    statusBar.backgroundColor = new vscode.ThemeColor('statusBarItem.warningBackground')
  } else {
    // 미푸시는 셀 수 있을 때만 붙인다. 업스트림이 없는 브랜치에서 0 으로 보이면 거짓말이다.
    const unpushed = collector.unpushedCount
    const tail = unpushed ? ` · 미푸시 ${unpushed}` : ''
    statusBar.text = `$(git-commit) 미커밋 ${collector.uncommittedCount}파일${tail}`
    statusBar.command = 'worklog.sendNow'
    statusBar.tooltip = 'WorkLog Drafter — 클릭하면 지금 전송'
    statusBar.backgroundColor = undefined
  }
  statusBar.show()
}

/** API Key 가 없을 때 Uploader 가 돌려주는 사유. 첫 실행 안내를 띄우는 조건이다. */
const NO_API_KEY = 'API Key 미설정'
/** 서버가 401/403 을 돌려줬을 때 Uploader 가 주는 사유. */
const WRONG_API_KEY = 'API Key 오류'

/**
 * 전송 결과.
 *
 * <p>예전에는 셋을 모두 `undefined` 로 돌려줬다 — 보냈을 때, 보낼 것이 없을 때, 이미
 * 보내는 중일 때. 부르는 쪽에서는 전부 성공으로 보여서, 서버에 닿지도 않았는데
 * "전송했습니다" 나 "키를 확인했습니다" 라고 말했다.
 */
type SendResult =
  | { kind: 'sent'; files: number }
  /** 워크스페이스가 git 저장소가 아니거나 origin 이 없다. 서버에 닿지 않았다. */
  | { kind: 'nothing' }
  /** 다른 전송이 도는 중이라 건너뛰었다. */
  | { kind: 'skipped' }
  | { kind: 'failed'; reason: string }

async function send(reason: string): Promise<SendResult> {
  if (sending) {
    log(`${reason}: 이미 전송 중이라 건너뜁니다`)
    return { kind: 'skipped' }
  }
  sending = true
  try {
    const { serverUrl, apiKey, collectDiff } = readConfig()
    const payloads = await collector.collect(collectDiff)
    log(`${reason}: 저장소 ${payloads.length}곳, 미커밋 ${collector.uncommittedCount}파일`)

    if (payloads.length === 0) {
      renderStatusBar()
      return { kind: 'nothing' }
    }
    const result = await new Uploader({ serverUrl, apiKey }).send(payloads)
    renderStatusBar(result.ok ? undefined : result.reason)
    tree.setStatus(
      result.ok
        ? { kind: 'sent', at: new Date(), files: collector.uncommittedCount }
        : { kind: 'failed', at: new Date(), reason: result.reason },
    )
    void tree.refresh()
    return result.ok
      ? { kind: 'sent', files: collector.uncommittedCount }
      : { kind: 'failed', reason: result.reason }
  } catch (e) {
    // 여기까지 온 예외는 버그다. 확장을 죽이지는 않는다.
    log(`전송 중 예기치 못한 오류: ${e instanceof Error ? e.stack ?? e.message : String(e)}`)
    renderStatusBar('전송 실패')
    return { kind: 'failed', reason: '전송 실패' }
  } finally {
    sending = false
  }
}

/**
 * 키를 그 자리에서 받아 저장한다.
 *
 * <p>예전에는 설정 화면만 열어 줬다. 그러면 대시보드에서 복사한 키를 들고 설정 검색창에
 * `worklog` 를 치고 칸을 찾아 붙여 넣어야 한다. 키는 발급 직후 한 번만 보이는 값이라
 * 그 사이에 잃어버리기 쉽다. 입력창을 바로 띄운다.
 *
 * @return 저장했으면 true
 */
async function askApiKey(reason?: string): Promise<boolean> {
  const current = readConfig().apiKey
  const key = await vscode.window.showInputBox({
    title: 'WorkLog: API Key 입력',
    prompt: reason ?? '대시보드 설정 > API 연동에서 발급한 키를 붙여 넣으세요.',
    placeHolder: 'wl_...',
    value: current,
    password: true,
    ignoreFocusOut: true, // 대시보드로 창을 옮겨 복사해 오는 동안 닫히면 안 된다
    validateInput: (v) => (v.trim().length === 0 ? '키를 붙여 넣어 주세요.' : undefined),
  })
  if (key === undefined) {
    return false
  }
  // 워크스페이스마다 다른 키를 쓸 이유가 없다. 사용자 설정에 둔다.
  await vscode.workspace
    .getConfiguration('worklog')
    .update('apiKey', key.trim(), vscode.ConfigurationTarget.Global)
  log('API Key 를 새로 저장했습니다.')
  // 저장만 하고 두면 상태바에 옛 오류가 그대로 남는다. 키를 고쳤는데도 실패한 것처럼
  // 보이므로 바로 한 번 보내 결과를 갱신한다. 사용자에게는 "확인" 으로 알린다 —
  // 키만 넣었는데 "전송했습니다" 가 뜨면 무엇이 나갔는지 몰라 놀란다.
  verifying = true
  try {
    const result = await send('키 확인')
    if (result.kind === 'sent') {
      void vscode.window.showInformationMessage(
        `WorkLog: 키를 저장하고 확인했습니다. 오늘 작업 ${result.files}파일을 보냈습니다.`)
    } else if (result.kind === 'nothing') {
      // 서버에 닿지도 않았다. 여기서 "확인했습니다" 라고 하면 거짓말이 된다.
      void vscode.window.showInformationMessage(
        'WorkLog: 키를 저장했습니다. 보낼 작업이 없어 아직 확인하지는 못했습니다 —'
          + ' git 저장소를 열고 다시 전송해 보세요.')
    } else if (result.kind === 'failed') {
      void vscode.window.showWarningMessage(
        `WorkLog: 키를 저장했지만 확인에 실패했습니다 — ${result.reason}`)
    }
  } finally {
    verifying = false
  }
  return true
}

/** 사용자가 직접 누른 전송의 결과를 알린다. 무엇이 일어났는지 그대로 말한다. */
async function tellResult(result: SendResult): Promise<void> {
  switch (result.kind) {
    case 'sent':
      void vscode.window.showInformationMessage(`WorkLog: 전송했습니다 (미커밋 ${result.files}파일).`)
      return
    case 'nothing':
      void vscode.window.showInformationMessage(
        'WorkLog: 보낼 것이 없습니다. 열린 폴더가 git 저장소가 아니거나 origin 이 없습니다.')
      return
    case 'skipped':
      return // 다른 전송이 도는 중이다. 굳이 알릴 일이 아니다.
    case 'failed':
      if (result.reason === NO_API_KEY || result.reason === WRONG_API_KEY) {
        await promptForApiKey(result.reason)
        return
      }
      void vscode.window.showWarningMessage(`WorkLog: 전송하지 못했습니다 — ${result.reason}`)
  }
}

/**
 * 전송이 키 때문에 막혔을 때 알린다.
 *
 * <p>키가 비어 있는 것과 서버가 거절한 것(발급을 다시 받았거나 계정이 지워졌을 때)을
 * 나눠 말한다. 둘 다 할 일은 같지만, 왜 막혔는지 모르면 같은 키를 다시 넣어 본다.
 */
async function promptForApiKey(failure: string): Promise<void> {
  const wrong = failure === WRONG_API_KEY
  const enter = '키 입력'
  const picked = await vscode.window.showWarningMessage(
    wrong
      ? 'WorkLog: 서버가 이 API Key 를 받지 않았습니다. 대시보드에서 새로 발급해 주세요.'
      : 'WorkLog: API Key 가 설정되지 않아 전송하지 못했습니다.',
    enter,
  )
  if (picked === enter) {
    await askApiKey()
  }
}

/**
 * 계획을 어느 폴더에 적을지 고른다.
 *
 * <p>폴더가 하나면 묻지 않는다. 여럿일 때 묻지 않으면 계획 하나가 모든 폴더에 붙어,
 * 회사 일 계획이 개인 프로젝트 세션에 실려 간다.
 */
async function pickFolder(): Promise<string | undefined> {
  const folders = vscode.workspace.workspaceFolders ?? []
  if (folders.length === 0) {
    void vscode.window.showWarningMessage('WorkLog: 열린 폴더가 없습니다.')
    return undefined
  }
  if (folders.length === 1) return folders[0].uri.fsPath

  const picked = await vscode.window.showQuickPick(
    folders.map((f) => ({ label: f.name, description: f.uri.fsPath })),
    { title: '어느 폴더의 계획입니까?' },
  )
  return picked?.description
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
  const view = vscode.window.createTreeView('worklog.session', { treeDataProvider: tree })
  context.subscriptions.push(
    view,
    // 사이드바를 열 때 낡은 숫자를 그대로 보여 주지 않는다.
    view.onDidChangeVisibility((e) => {
      if (e.visible) void tree.refresh()
    }),
  )
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

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.setApiKey', () => askApiKey()),
  )

  // 커밋·푸시는 대개 터미널이나 소스 제어 패널에서 한다. 파일 저장만 보고 있으면 그때
  // 미커밋·미푸시 숫자가 낡은 채로 남아 실제와 달라 보인다.
  const gitWatcher = vscode.workspace.createFileSystemWatcher('**/.git/{HEAD,index,refs/**}')
  context.subscriptions.push(
    gitWatcher,
    gitWatcher.onDidChange(scheduleTreeRefresh),
    gitWatcher.onDidCreate(scheduleTreeRefresh),
    gitWatcher.onDidDelete(scheduleTreeRefresh),
  )

  // 창을 비웠다 돌아오면 그 사이 밖에서 무슨 일이 있었을 수 있다.
  context.subscriptions.push(
    vscode.window.onDidChangeWindowState((state) => {
      if (state.focused) scheduleTreeRefresh()
    }),
  )

  // 사이드바에서 계획 한 줄을 지운다. 잘못 적은 메모가 그날 내내 남지 않게.
  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.removePlan', (node?: { note?: string; folder?: string }) => {
      if (!node?.note) return
      collector.removePlanNote(node.note, node.folder)
      log(`계획 삭제: ${node.note}`)
      void tree.refresh()
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.recordPlan', async () => {
      const folder = await pickFolder()
      if (!folder) return

      const count = collector.planNotesOf(folder).length
      const note = await vscode.window.showInputBox({
        title: 'WorkLog: 계획 추가',
        prompt: count === 0
          ? '오늘 무엇을 할 계획인지 한 줄로 적으세요. 업무 일지의 "계획 / TODO" 에 들어갑니다.'
          : `이미 ${count}건 적었습니다. 덧붙일 계획을 적으세요.`,
        placeHolder: '예) 오후에 출석 중복 검증 로직 마무리',
      })
      if (note !== undefined && note.trim()) {
        collector.addPlanNote(note, folder)
        log(`계획 추가 (${folder}): ${note.trim()}`)
        void tree.refresh()
      }
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.sendNow', async () => {
      const result = await vscode.window.withProgress(
        { location: vscode.ProgressLocation.Window, title: 'WorkLog 전송 중…' },
        () => send('지금 전송'),
      )
      if (verifying) {
        return // 키 확인이 부른 전송이다. 알림은 그쪽에서 한다.
      }
      // 사용자가 직접 누른 경우에만 안내한다. 주기·시작 전송까지 알리면 성가시다.
      await tellResult(result)
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
