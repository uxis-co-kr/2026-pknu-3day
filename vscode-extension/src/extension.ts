import { createHash } from 'node:crypto'
import * as path from 'node:path'
import * as vscode from 'vscode'
import { Collector, todayKst } from './collector'
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

/**
 * 설정에 아무것도 없을 때 쓰는 주소. 서버를 띄운 PC 가 따로 있으면 이 값으로는 닿지 못한다
 * — 자기 컴퓨터를 부른다 (BACKLOG2 §2-1).
 */
const DEFAULT_SERVER_URL = 'http://localhost:8080'

function readConfig() {
  const cfg = vscode.workspace.getConfiguration('worklog')
  return {
    serverUrl: cfg.get<string>('serverUrl', DEFAULT_SERVER_URL),
    apiKey: cfg.get<string>('apiKey', ''),
    intervalMinutes: cfg.get<number>('intervalMinutes', 10),
    collectDiff: cfg.get<boolean>('collectDiff', true),
  }
}

/** 상태바에는 "미커밋 N파일" 또는 오류만 표시한다 (PRD F6). */
function renderStatusBar(error?: string) {
  if (error) {
    const keyProblem = error === NO_API_KEY || error === WRONG_API_KEY
    const urlProblem = CONNECTION_FAILURES.includes(error)
    statusBar.text = `$(warning) WorkLog: ${error}`
    // 키나 주소가 문제면 다시 전송해 봐야 같은 곳에서 막힌다. 고칠 자리로 바로 보낸다.
    statusBar.command = keyProblem
      ? 'worklog.setApiKey'
      : urlProblem
        ? 'worklog.setServerUrl'
        : 'worklog.sendNow'
    statusBar.tooltip = keyProblem
      ? `${error} — 클릭하면 키를 입력합니다`
      : urlProblem
        ? `${error} — 클릭하면 서버 주소를 설정합니다`
        : `${error} — 클릭하면 다시 전송합니다`
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
 * 서버에 **닿지도 못한** 사유. 대개 주소가 틀린 것이다 — 기본값이 `localhost` 라
 * 서버를 띄운 PC 가 따로 있으면 여기서 막힌다.
 */
const CONNECTION_FAILURES = ['서버에 연결할 수 없음', '서버 응답 없음']

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
 * 사람이 적은 서버 주소를 쓸 수 있는 형태로 다듬는다.
 *
 * <p>`192.168.0.224:8080` 처럼 스킴 없이 적거나, 주소창에서 복사해 끝 슬래시나 `/api` 를
 * 달고 오는 일이 잦다. `/api` 는 {@link Uploader} 가 붙이므로 그대로 두면 `/api/api` 가 된다.
 *
 * @return 다듬은 주소. 주소로 볼 수 없으면 undefined
 */
function normalizeServerUrl(raw: string): string | undefined {
  const trimmed = raw.trim()
  if (!trimmed) return undefined
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`
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

/**
 * 백엔드 주소를 그 자리에서 받아 저장한다.
 *
 * <p>서버를 한 대만 띄우고 여럿이 붙는 것이 이 도구의 쓰임인데, 기본값은 `localhost` 다.
 * 남의 PC 에서 열면 자기 백엔드를 부르다 실패한다. 설정 화면을 뒤지게 하지 않고
 * 키와 마찬가지로 명령으로 받는다 (BACKLOG2 §2-1).
 *
 * @param step `1/2` 처럼 몇 단계 중 몇 번째인지. 키 입력과 이어서 물을 때만 준다
 * @return 저장한 주소. 취소했으면 undefined
 */
async function askServerUrl(step?: string): Promise<string | undefined> {
  const current = readConfig().serverUrl
  const answer = await vscode.window.showInputBox({
    title: step ? `WorkLog: 서버 주소 (${step})` : 'WorkLog: 서버 주소 설정',
    prompt: '대시보드를 여는 주소가 아니라 백엔드 주소입니다. 예) http://192.168.0.224:8080',
    value: current,
    ignoreFocusOut: true, // 서버를 띄운 사람에게 주소를 물어보는 동안 닫히면 안 된다
    validateInput: (v) =>
      normalizeServerUrl(v) ? undefined : '주소:포트 형태로 적어 주세요. 예) http://192.168.0.224:8080',
  })
  if (answer === undefined) return undefined
  const url = normalizeServerUrl(answer)
  if (!url) return undefined
  // 워크스페이스마다 다른 서버를 볼 이유가 없다. 키와 같이 사용자 설정에 둔다.
  await vscode.workspace
    .getConfiguration('worklog')
    .update('serverUrl', url, vscode.ConfigurationTarget.Global)
  log(`서버 주소를 ${url} 로 저장했습니다.`)
  return url
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
async function askApiKey(step?: string): Promise<boolean> {
  const current = readConfig().apiKey
  const key = await vscode.window.showInputBox({
    title: step ? `WorkLog: API Key (${step})` : 'WorkLog: API Key 입력',
    prompt: '대시보드 설정 > API 연동에서 발급한 키를 붙여 넣으세요.',
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

/**
 * 서버 주소와 API Key 를 차례로 받는다.
 *
 * <p>주소를 먼저 묻는다 — 키가 맞아도 주소가 자기 PC 를 가리키면 아무 데도 닿지 않고,
 * 그때 뜨는 "서버에 연결할 수 없음" 은 키를 의심하게 만든다. 키를 넣는 자리가 곧
 * 이 확장을 처음 쓰는 자리이므로 여기서 둘 다 받는다.
 */
async function askServerAndKey(): Promise<void> {
  const url = await askServerUrl('1/2')
  if (url === undefined) return // 주소를 취소했으면 키도 묻지 않는다
  await askApiKey('2/2')
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
      if (CONNECTION_FAILURES.includes(result.reason)) {
        await promptForServerUrl(result.reason)
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
 * 서버에 닿지 못했을 때 주소부터 묻는다.
 *
 * <p>"전송 실패" 한 줄만 띄우면 무엇을 고쳐야 하는지 알 수 없다. 지금 부르고 있는 주소를
 * 그대로 보여 주면 `localhost` 를 부르고 있다는 것이 바로 보인다.
 */
async function promptForServerUrl(failure: string): Promise<void> {
  const setUrl = '서버 주소 설정'
  const picked = await vscode.window.showWarningMessage(
    `WorkLog: ${readConfig().serverUrl} 에 닿지 못했습니다 (${failure}). 서버를 띄운 PC 의 주소가 맞는지 확인해 주세요.`,
    setUrl,
  )
  if (picked !== setUrl) return
  if ((await askServerUrl()) === undefined) return

  const result = await send('주소 확인')
  if (result.kind === 'sent') {
    void vscode.window.showInformationMessage(
      `WorkLog: 연결됐습니다. 오늘 작업 ${result.files}파일을 보냈습니다.`)
  } else if (result.kind === 'failed') {
    // 여기서 또 물으면 끝이 없다. 사유만 알리고 멈춘다.
    void vscode.window.showWarningMessage(`WorkLog: 아직 닿지 못했습니다 — ${result.reason}`)
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
 * 계획을 적는 임시 문서. 파일 경로 → 어느 폴더의 계획인지.
 *
 * <p>빠른 길일 뿐이고 믿을 것은 못 된다 — 확장 호스트가 다시 뜨면 비는데 탭은 복원된다.
 * {@link planFolderOf} 를 보라.
 */
const planDocs = new Map<string, string>()

/** 계획 문서를 두는 곳(확장의 globalStorage). activate 에서 채운다. */
let plansDir: vscode.Uri | undefined

/**
 * 계획 문서의 파일 이름.
 *
 * <p>예전에는 폴더 이름을 앞에 세웠다. 그런데 이 저장소의 폴더 이름이 `0909` 라서
 * 탭과 문서 첫 줄에 `0909 계획` 이 떠 <b>날짜로 읽혔다</b> — 실제 업무 일자는 9/11 인데.
 * 폴더가 하나면 이름을 넣지 않는다.
 *
 * <p>워크스페이스에 이름이 같은 폴더가 둘 이상이면 경로 해시를 덧붙여 나눈다 — 같은
 * 파일을 두 폴더가 나눠 쓰면 한쪽 계획이 다른 쪽을 덮는다.
 */
function planFileName(folder: string): string {
  const folders = vscode.workspace.workspaceFolders ?? []
  if (folders.length <= 1) return 'WorkLog 계획.md'
  const base = path.basename(folder)
  const safe = base.replace(/[\\/:*?"<>|]/g, '_') || 'workspace'
  const twins = folders.filter((f) => path.basename(f.uri.fsPath) === base)
  if (twins.length <= 1) return `WorkLog 계획 (${safe}).md`
  return `WorkLog 계획 (${safe}-${createHash('sha1').update(folder).digest('hex').slice(0, 6)}).md`
}

/** 머리말에 적어 두는 표시. 탭이 복원된 뒤에도 어느 폴더의 계획인지 알기 위해서다. */
const FOLDER_MARK = /^\s*폴더:\s*(\S.*?)\s*$/

function folderFromDocument(text: string): string | undefined {
  for (const line of text.split('\n').slice(0, 12)) {
    const m = FOLDER_MARK.exec(line)
    if (m) return m[1]
  }
  return undefined
}

/** 스킴을 보지 않고 경로로만 가린다 — globalStorage 의 스킴은 환경마다 다를 수 있다. */
function isInside(dir: vscode.Uri, file: vscode.Uri): boolean {
  return file.path.startsWith(dir.path.replace(/\/+$/, '') + '/')
}

/**
 * 저장된 문서가 계획 문서면 어느 폴더의 것인지 돌려준다.
 *
 * <p>열 때 기억해 둔 지도({@link planDocs})만 믿으면 안 된다. 확장 호스트가 다시 뜨면
 * (창 새로 고침·VS Code 재시작·확장 재설치) 지도는 비는데 <b>탭은 그대로 복원된다.</b>
 * 그 상태로 저장하면 아무 일도 일어나지 않고, 사용자에게는 저장이 먹지 않는 것으로 보인다.
 * 그래서 <b>경로</b>로 먼저 가리고 폴더는 문서 머리말에서 되찾는다.
 */
function planFolderOf(doc: vscode.TextDocument): string | undefined {
  const known = planDocs.get(doc.uri.fsPath)
  if (known) return known
  if (!plansDir || !isInside(plansDir, doc.uri)) return undefined

  const marked = folderFromDocument(doc.getText())
  if (marked) return marked
  // 머리말을 지웠더라도 폴더가 하나뿐이면 물을 것이 없다.
  const folders = vscode.workspace.workspaceFolders ?? []
  if (folders.length === 1) return folders[0].uri.fsPath
  log(`계획 문서를 저장했지만 어느 폴더의 것인지 알 수 없습니다: ${doc.uri.fsPath}`)
  return undefined
}

/**
 * 계획 문서에서 계획 줄만 뽑는다.
 *
 * <p>안내 주석과 날짜 제목은 우리가 넣은 것이라 계획이 아니다. 목록 표식(`- `, `1. `,
 * 체크박스)은 markdown 으로 적기 편하라고 둔 것이므로 떼어 낸다. 한 줄이 계획 하나다 —
 * 사이드바가 줄 단위로 보여 주고 우클릭으로 한 줄씩 지울 수 있어야 한다.
 */
export function parsePlanDocument(text: string): string[] {
  const notes: string[] = []
  let inComment = false
  for (const raw of text.split('\n')) {
    let line = raw.trim()
    if (inComment) {
      const close = line.indexOf('-->')
      if (close < 0) continue
      line = line.slice(close + 3).trim()
      inComment = false
    }
    line = line.replace(/<!--[\s\S]*?-->/g, ' ').trim()
    const open = line.indexOf('<!--')
    if (open >= 0) {
      inComment = true
      line = line.slice(0, open).trim()
    }
    if (!line || line.startsWith('#')) continue
    const note = line
      // 표식만 있고 내용이 없는 줄(빈 `- `)은 아래에서 걸러지도록 통째로 비운다.
      .replace(/^([-*+]|\d+[.)])(\s+|$)/, '')
      .replace(/^\[[ xX]\]\s*/, '')
      .trim()
    if (note) notes.push(note)
  }
  return notes
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

/** "2026-09-11 (금)". 업무 일자는 KST 다 (PRD 12). */
function dateLabel(): string {
  const iso = todayKst()
  const [y, m, d] = iso.split('-').map(Number)
  return `${iso} (${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`
}

/**
 * 문서 맨 위에 넣는 안내.
 *
 * <p><b>업무 일자를 적는다.</b> 예전에는 폴더 이름(`0909`)이 첫 줄에 있어 날짜로 읽혔다.
 * <p><b>폴더 경로도 적는다.</b> 탭이 복원된 뒤 저장해도 어느 폴더의 계획인지 알기 위해서다.
 */
function planHeader(folder: string): string {
  return [
    `<!-- WorkLog 계획 · ${dateLabel()}`,
    `     폴더: ${folder}`,
    '     오늘 할 일을 한 줄에 하나씩 적고 저장(Cmd+S)하세요.',
    '     사이드바의 "계획" 과 오늘 업무 일지의 "계획 / TODO" 에 반영됩니다.',
    '     빈 줄·이 주석·# 로 시작하는 줄은 빼고 보냅니다.',
    '     지운 줄은 계획에서도 지워집니다. -->',
  ].join('\n')
}

/**
 * 예전 이름(`<폴더> 계획.md`)으로 만든 파일이 남아 있으면 적어 둔 계획을 옮기고 지운다.
 *
 * <p>그대로 두면 이름이 비슷한 파일이 둘 생기고, 사용자가 옛 탭에 계속 적게 된다.
 */
async function migrateLegacyPlanFile(dir: vscode.Uri, current: vscode.Uri, folder: string): Promise<void> {
  const safe = path.basename(folder).replace(/[\\/:*?"<>|]/g, '_') || 'workspace'
  const legacy = vscode.Uri.joinPath(dir, `${safe} 계획.md`)
  if (legacy.fsPath === current.fsPath) return

  let text: string
  try {
    text = new TextDecoder().decode(await vscode.workspace.fs.readFile(legacy))
  } catch {
    return // 없으면 그만이다
  }
  const notes = parsePlanDocument(text)
  if (notes.length > 0 && collector.planNotesOf(folder).length === 0) {
    collector.setPlanNotes(notes, folder)
    log(`예전 계획 파일에서 ${notes.length}건을 옮겼습니다`)
  }
  planDocs.delete(legacy.fsPath)
  await Promise.resolve(vscode.workspace.fs.delete(legacy)).catch(() => undefined)
}

/**
 * 계획을 임시 문서 탭에서 적는다 (BACKLOG2_client C-2).
 *
 * <p>예전에는 창 맨 위의 한 줄 입력(`showInputBox`)이었다. 긴 계획을 쓸 수 없고, 여러 건을
 * 적으려면 명령을 그만큼 다시 불러야 했다. 여기서는 **진짜 파일**을 열어 준다 — 이름 없는
 * 문서로 열면 `Cmd+S` 가 "다른 이름으로 저장" 창을 띄워, 저장할 때 받는다는 약속이 깨진다.
 * 파일은 확장의 globalStorage 에 두므로 사용자의 저장소에는 남지 않는다.
 */
async function openPlanDocument(context: vscode.ExtensionContext): Promise<void> {
  const folder = await pickFolder()
  if (!folder) return

  await vscode.workspace.fs.createDirectory(context.globalStorageUri)
  const file = vscode.Uri.joinPath(context.globalStorageUri, planFileName(folder))
  await migrateLegacyPlanFile(context.globalStorageUri, file, folder)
  planDocs.set(file.fsPath, folder)

  // 이미 열어 두고 고치는 중이면 덮어쓰지 않는다. 적던 내용이 사라지면 안 된다.
  const open = vscode.workspace.textDocuments.find((d) => d.uri.fsPath === file.fsPath)
  if (open?.isDirty) {
    await vscode.window.showTextDocument(open, { preview: false })
    return
  }

  const notes = collector.planNotesOf(folder)
  const body = notes.length > 0 ? notes.map((n) => `- ${n}`).join('\n') : '- '
  const text = `${planHeader(folder)}\n\n${body}`
  await vscode.workspace.fs.writeFile(file, new TextEncoder().encode(text))

  const doc = await vscode.workspace.openTextDocument(file)
  const editor = await vscode.window.showTextDocument(doc, { preview: false })
  // 마지막 줄 끝에 커서를 둔다 — 열자마자 이어 적을 수 있게.
  const end = doc.lineAt(doc.lineCount - 1).range.end
  editor.selection = new vscode.Selection(end, end)
  editor.revealRange(new vscode.Range(end, end))
}

/** 계획 문서를 저장했을 때. 문서가 곧 그 폴더의 계획 전부다. */
function applyPlanDocument(doc: vscode.TextDocument, folder: string): void {
  const notes = parsePlanDocument(doc.getText())
  collector.setPlanNotes(notes, folder)
  log(`계획 ${notes.length}건 (${folder})`)
  void tree.refresh()
  // 저장할 때마다 알림 창을 띄우면 성가시다. 상태바에 잠깐 보여 주는 정도로 둔다.
  // 다만 0건은 "안 먹었다" 와 구별되지 않으므로 그렇게 읽히지 않게 적는다.
  vscode.window.setStatusBarMessage(
    notes.length > 0
      ? `$(check) WorkLog: 계획 ${notes.length}건을 저장했습니다.`
      : '$(info) WorkLog: 계획으로 읽을 줄이 없어 오늘 계획을 비웠습니다.',
    4000,
  )
}

/**
 * 사이드바만 주기적으로 다시 그린다 (보내지는 않는다).
 *
 * <p>AI 대화는 workspace 밖(`~/.claude/projects`)에 쌓여 파일 감시가 닿지 않는다. 전송은
 * 몇 분에 한 번인데 대화는 그 사이에도 는다. 내 화면이라도 맞게 두려고 짧게 다시 읽는다
 * (BACKLOG2_client C-1 ②).
 */
const TREE_REFRESH_MS = 60_000
let treeTimer: NodeJS.Timeout | undefined

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
  // 저장 이벤트가 계획 문서인지 가릴 때 쓴다. 탭이 복원된 뒤에도 알아볼 수 있어야 한다.
  plansDir = context.globalStorageUri
  // 스킴을 남겨 둔다 — 계획 저장이 안 먹을 때 여기부터 본다 (한 번 여기서 막혔다).
  log(`계획 문서 폴더: ${plansDir.scheme}:${plansDir.path}`)
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
    // "서버에 저장된 내역" 은 펼칠 때만 부른다 — 열지도 않을 것을 1분마다 받아 올 이유가 없다.
    view.onDidExpandElement((e) => {
      if (e.element.kind === 'history') void tree.loadHistory(readConfig())
    }),
  )
  void tree.refresh()

  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument((doc) => {
      // 스킴 검사보다 먼저 본다. globalStorage 가 file 스킴이 아닌 환경에서는 계획 문서가
      // 여기서 통째로 걸러져, 저장해도 아무 일이 일어나지 않았다.
      const planFolder = planFolderOf(doc)
      if (planFolder) {
        // 계획 문서는 사용자의 작업 파일이 아니다. 저장 이벤트로 세지 않는다.
        applyPlanDocument(doc, planFolder)
        return
      }
      if (doc.uri.scheme !== 'file') return
      collector.recordSave(doc.uri.fsPath)
      scheduleTreeRefresh()
    }),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.refresh', () => tree.refresh()),
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('worklog.setApiKey', () => askServerAndKey()),
    vscode.commands.registerCommand('worklog.setServerUrl', () => askServerUrl()),
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
    vscode.commands.registerCommand('worklog.recordPlan', () => openPlanDocument(context)),
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
  // 창이 보이지 않을 때까지 돌 이유는 없지만, 1분에 한 번 git 을 부르는 정도는 가볍다.
  treeTimer = setInterval(() => void tree.refresh(), TREE_REFRESH_MS)
  context.subscriptions.push({ dispose: () => treeTimer && clearInterval(treeTimer) })

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
