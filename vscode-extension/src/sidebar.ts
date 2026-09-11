import * as path from 'node:path'
import * as vscode from 'vscode'
import { todayKst } from './collector'
import type { Collector } from './collector'
import type { UnpushedCommit } from './git'
import { Uploader } from './uploader'
import type { RemoteSession } from './uploader'
import type { AiSessionSummary, AiTurn, IdleAiSession, SessionPayload, TodoItem, UncommittedFile } from './types'

/**
 * 사이드바 뷰 — 지금 무엇이 서버로 갈지 보여 준다 (PRD X3, BACKLOG 1-14).
 *
 * <p>상태바는 "미커밋 N파일" 한 줄뿐이라, 무엇이 수집됐는지 확인하려면 전송한 뒤
 * DB 나 대시보드를 봐야 했다. 이 뷰는 전송 전에 목록 그대로 보여 준다.
 *
 * <p>수집은 화면을 열거나 새로고침할 때만 한다. diff 본문은 화면에 쓰지 않으므로
 * 받아 오지 않는다 — 저장소가 크면 diff 수집이 가장 무겁다.
 */
export class WorkLogTreeProvider implements vscode.TreeDataProvider<Node> {
  private readonly changed = new vscode.EventEmitter<Node | undefined>()
  readonly onDidChangeTreeData = this.changed.event

  private payloads: SessionPayload[] = []
  private status: Status = { kind: 'idle' }
  private loading = false
  /** 서버에 저장된 내역. 펼칠 때만 불러온다 — 열지도 않을 것을 1분마다 받아 올 이유가 없다. */
  private history: History = { kind: 'idle' }

  constructor(private readonly collector: Collector) {}

  /** 전송 결과를 상태 줄에 반영한다. */
  setStatus(status: Status): void {
    this.status = status
    this.changed.fire(undefined)
  }

  async refresh(): Promise<void> {
    if (this.loading) return
    this.loading = true
    try {
      this.payloads = await this.collector.collect(false)
    } finally {
      this.loading = false
      this.changed.fire(undefined)
    }
  }

  /**
   * 서버에 저장된 내 최근 내역을 불러온다 (사이드바 하단).
   *
   * <p>지금까지 VS Code 안에서는 <b>보낸 것이 실제로 서버에 들어갔는지</b> 확인할 길이
   * 없었다. 대시보드를 열어야 알 수 있었다. 펼칠 때만 부른다.
   */
  async loadHistory(config: { serverUrl: string; apiKey: string }): Promise<void> {
    if (this.history.kind === 'loading') return
    this.history = { kind: 'loading' }
    this.changed.fire(undefined)

    const to = todayKst()
    const result = await new Uploader(config).fetchRecent(daysBefore(to, HISTORY_DAYS - 1), to)
    this.history = result.ok
      ? { kind: 'loaded', sessions: result.sessions }
      : { kind: 'failed', reason: result.reason }
    this.changed.fire(undefined)
  }

  getTreeItem(node: Node): vscode.TreeItem {
    return node.item
  }

  getChildren(node?: Node): Node[] {
    if (!node) return this.roots()
    return node.children ?? []
  }

  private roots(): Node[] {
    const nodes: Node[] = [leaf(statusLabel(this.status), statusIcon(this.status))]

    if (this.payloads.length === 0) {
      nodes.push(leaf('수집된 저장소 없음', 'info', '워크스페이스가 git 저장소가 아니거나 origin 이 없습니다'))
      return nodes
    }

    for (const p of this.payloads) {
      const cwd = this.collector.folderOf(p)
      const name = repoName(p.remoteUrl)

      const notes = this.collector.planNotesOf(cwd)
      const children: Node[] = [
        group(
          `계획 ${notes.length}건`,
          'note',
          notes.map((n) => planNode(n, cwd)),
          notes.length > 0,
        ),
      ]
      children.push(group(`미커밋 ${p.uncommittedFiles.length}개`, 'diff', p.uncommittedFiles.map((f) => fileNode(f, cwd))))
      children.push(unpushedGroup(this.collector.unpushedOf(p)))
      children.push(aiGroup(p.aiSessions, this.collector.idleAiOf(p)))
      children.push(group(`TODO ${p.todos.length}개`, 'checklist', p.todos.map((t) => todoNode(t, cwd))))
      children.push(
        group(
          `저장 이벤트 ${p.editTimeline.length}개`,
          'history',
          p.editTimeline.map((e) => leaf(e.path, 'file', `${e.saveCount}회 · ${time(e.lastSavedAt)}`, `${e.saveCount}회`)),
        ),
      )

      nodes.push(group(`${name} · ${p.branch}`, 'repo', children, true))
    }

    nodes.push(historyNode(this.history, this.payloads.map((p) => p.remoteUrl)))
    return nodes
  }
}

/** 서버 내역을 며칠치 보여 줄지. 확인용이라 길게 볼 이유가 없다. */
const HISTORY_DAYS = 7

type History =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'loaded'; sessions: RemoteSession[] }
  | { kind: 'failed'; reason: string }

/**
 * "서버에 저장된 내역" — 위쪽 "오늘 보낼 내용" 과 달리 **서버가 들고 있는 것**이다.
 *
 * <p>둘을 섞지 않는다. 위는 아직 보내지 않았을 수 있는 지금 상태고, 여기는 서버가 받은
 * 결과다. 보낸 것이 들어갔는지 확인하는 자리다.
 */
function historyNode(history: History, openRemotes: string[]): Node {
  const node: Node = group('서버에 저장된 내역', 'cloud', historyChildren(history, openRemotes))
  node.kind = 'history'
  // 다시 그려도 펼친 상태가 유지되도록 id 를 고정한다.
  node.item.id = 'worklog.history'
  node.item.tooltip = `펼치면 서버에 저장된 최근 ${HISTORY_DAYS}일치 내 기록을 불러옵니다`
  if (history.kind === 'loaded') {
    node.item.description = `최근 ${HISTORY_DAYS}일 · ${history.sessions.length}건`
  } else if (history.kind === 'failed') {
    node.item.description = history.reason
  }
  return node
}

function historyChildren(history: History, openRemotes: string[]): Node[] {
  if (history.kind === 'idle') return [leaf('펼치면 불러옵니다', 'ellipsis')]
  if (history.kind === 'loading') return [leaf('불러오는 중…', 'sync')]
  if (history.kind === 'failed') {
    return [leaf(history.reason, 'warning', '서버 주소와 API Key 를 확인하세요')]
  }

  // 지금 열어 둔 저장소의 것만 본다. 다른 저장소 기록까지 섞이면 확인하려던 것이 묻힌다.
  const names = new Set(openRemotes.map(repoName))
  const mine = history.sessions.filter((s) => names.has(repoName(s.remoteUrl)))
  if (mine.length === 0) {
    return [leaf('이 저장소로 보낸 기록이 없습니다', 'info')]
  }

  const byDate = new Map<string, RemoteSession[]>()
  for (const s of mine) byDate.set(s.workDate, [...(byDate.get(s.workDate) ?? []), s])

  return [...byDate.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([workDate, sessions]) => {
      const files = sessions.reduce((n, x) => n + x.uncommittedFiles.length, 0)
      const day = group(
        workDate,
        'calendar',
        sessions.map((s) => remoteSessionNode(s)),
        workDate === todayKst(),
      )
      day.item.description = `저장소 ${sessions.length} · 미커밋 ${files}파일`
      return day
    })
}

function remoteSessionNode(s: RemoteSession): Node {
  const ai = s.aiSessions ?? []
  const plans = (s.planNote ?? '').split('\n').map((p) => p.trim()).filter(Boolean)
  const children: Node[] = [
    leaf(`미커밋 ${s.uncommittedFiles.length}개`, 'diff'),
    leaf(`TODO ${s.todos.length}개`, 'checklist'),
    leaf(`저장 이벤트 ${s.editTimeline.length}개`, 'history'),
    group(`계획 ${plans.length}건`, 'note', plans.map((p) => leaf(p, 'circle-small-filled'))),
    group(
      `AI 대화 ${ai.length}세션`,
      'comment-discussion',
      ai.map((a) => leaf(a.title ?? a.id, 'comment', undefined, `${a.promptCount ?? 0}개`)),
    ),
  ]
  const node = group(s.branch, 'git-branch', children)
  node.item.description = `보고 ${time(s.reportedAt)}`
  node.item.tooltip = `${s.repo?.fullName ?? s.remoteUrl} · ${s.branch}\n보고 ${s.reportedAt}`
  return node
}

/** `iso` 에서 `days` 일 전의 YYYY-MM-DD. */
function daysBefore(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  const t = new Date(y, m - 1, d - days)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())}`
}

export type Status =
  | { kind: 'idle' }
  | { kind: 'sent'; at: Date; files: number }
  | { kind: 'failed'; at: Date; reason: string }

function statusLabel(s: Status): string {
  if (s.kind === 'idle') return '아직 전송하지 않음'
  if (s.kind === 'sent') return `전송 완료 ${time(s.at.toISOString())} · 미커밋 ${s.files}파일`
  return `전송 실패 ${time(s.at.toISOString())} · ${s.reason}`
}

function statusIcon(s: Status): string {
  if (s.kind === 'sent') return 'check'
  if (s.kind === 'failed') return 'warning'
  return 'circle-outline'
}

interface Node {
  item: vscode.TreeItem
  children?: Node[]
  /** 펼칠 때 무엇을 해야 하는지 가리는 표시. 지금은 서버 내역 하나뿐이다. */
  kind?: 'history'
}

function leaf(label: string, icon: string, tooltip?: string, description?: string): Node {
  const item = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.None)
  item.iconPath = new vscode.ThemeIcon(icon)
  if (tooltip) item.tooltip = tooltip
  if (description) item.description = description
  return { item }
}

function group(label: string, icon: string, children: Node[], expanded = false): Node {
  const state = children.length === 0
    ? vscode.TreeItemCollapsibleState.None
    : expanded
      ? vscode.TreeItemCollapsibleState.Expanded
      : vscode.TreeItemCollapsibleState.Collapsed
  const item = new vscode.TreeItem(label, state)
  item.iconPath = new vscode.ThemeIcon(icon)
  return { item, children }
}

/**
 * 아직 푸시하지 않은 커밋.
 *
 * <p>이 구간은 GitHub 수집기가 보지 못한다 — 원격에 없으니 API 로 안 나온다.
 * 서버로도 보내지 않는다 (PRD 7 요청 본문에 자리가 없다). 여기서만 보인다.
 */
function unpushedGroup(commits: UnpushedCommit[] | undefined): Node {
  if (commits === undefined) {
    return leaf('미푸시 — 셀 수 없음', 'cloud', '업스트림이 없습니다. 한 번도 푸시하지 않은 브랜치입니다')
  }
  return group(
    `미푸시 ${commits.length}개`,
    'cloud-upload',
    commits.map((c) => leaf(c.subject, 'git-commit', `${c.sha} · ${time(c.at)}`, c.sha)),
  )
}

/** 계획 한 줄. 우클릭으로 지울 수 있게 contextValue·note·folder 를 달아 둔다. */
function planNode(note: string, folder: string | undefined): Node & { note: string; folder?: string } {
  const node = leaf(note, 'circle-small-filled') as Node & { note: string; folder?: string }
  node.item.contextValue = 'worklog.plan'
  node.note = note
  node.folder = folder
  return node
}

/**
 * 이 폴더에서 오간 AI 대화.
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이다 — 무엇을 어떻게 할지 묻고 정한 과정.
 */
function aiGroup(sessions: AiSessionSummary[], idle: IdleAiSession[]): Node {
  return group(
    `AI 대화 ${sessions.length}세션`,
    'comment-discussion',
    [...sessions.map((s) => sessionNode(s)), ...idle.map((s) => idleNode(s))],
  )
}

/**
 * 오늘 질문이 없어 **보내지 않는** 대화.
 *
 * <p>Claude Code 사이드바에는 있는데 여기에 없으면 빠진 것처럼 보인다. 그렇다고 그냥
 * 끼워 넣으면 보내지도 않을 것을 "오늘 보낼 내용" 에 올리는 셈이라, 보내지 않는다고 적는다.
 */
function idleNode(s: IdleAiSession): Node {
  const node = leaf(
    s.title,
    'circle-slash',
    `${s.title}\n\n오늘 질문이 없어 보내지 않습니다 (마지막 ${day(s.lastAt)})`,
    `${day(s.lastAt)} · 보내지 않음`,
  )
  return node
}

/** "9/10" */
function day(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : `${d.getMonth() + 1}/${d.getDate()}`
}

/** 세션 하나. 시각만으로는 무슨 대화였는지 알 수 없어 제목을 앞에 세운다. */
function sessionNode(s: AiSessionSummary): Node {
  const node = group(s.title, 'comment', s.turns.map((t) => turnNode(t)))
  // 담은 것은 12개까지지만 실제로 물어본 횟수를 보여 준다.
  node.item.description = `${time(s.firstAt)}–${time(s.lastAt)} · ${s.promptCount}개`
  node.item.tooltip = s.turns.length < s.promptCount
    ? `${s.title}\n\n${s.promptCount}개 중 최근 ${s.turns.length}개만 보냅니다`
    : s.title
  return node
}

/** 질문 하나. 답변이 있으면 펼쳐 볼 수 있게 자식으로 단다. */
function turnNode(turn: AiTurn): Node {
  const node = turn.answer
    ? group(turn.prompt, 'quote', [leaf(turn.answer, 'comment-discussion', turn.answer)])
    : leaf(turn.prompt, 'quote')
  node.item.description = time(turn.at)
  node.item.tooltip = turn.answer ? `${turn.prompt}\n\n${turn.answer}` : turn.prompt
  return node
}

/** 파일 노드는 누르면 열린다. 새 파일은 diff 대신 본문이 가므로 표시를 나눈다. */
function fileNode(f: UncommittedFile, cwd: string | undefined): Node {
  const node = leaf(path.basename(f.path), 'file', f.path, `+${f.additions} −${f.deletions}`)
  node.item.resourceUri = cwd ? vscode.Uri.file(path.join(cwd, f.path)) : undefined
  if (node.item.resourceUri) {
    node.item.command = { command: 'vscode.open', title: '열기', arguments: [node.item.resourceUri] }
  }
  return node
}

function todoNode(t: TodoItem, cwd: string | undefined): Node {
  const node = leaf(t.text, 'checklist', `${t.path}:${t.line}`, `${path.basename(t.path)}:${t.line}`)
  if (cwd) {
    const uri = vscode.Uri.file(path.join(cwd, t.path))
    node.item.command = {
      command: 'vscode.open',
      title: '열기',
      // TODO 는 1부터 세지만 VS Code 의 selection 은 0부터다.
      arguments: [uri, { selection: new vscode.Range(t.line - 1, 0, t.line - 1, 0) }],
    }
  }
  return node
}

/** origin URL 에서 owner/repo 만 뽑는다. 못 뽑으면 URL 그대로. */
function repoName(remoteUrl: string): string {
  const m = /([^/:]+\/[^/]+?)(?:\.git)?$/.exec(remoteUrl)
  return m ? m[1] : remoteUrl
}

function time(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toTimeString().slice(0, 8)
}
