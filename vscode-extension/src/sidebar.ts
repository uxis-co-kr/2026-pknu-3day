import * as path from 'node:path'
import * as vscode from 'vscode'
import type { Collector } from './collector'
import type { UnpushedCommit } from './git'
import type { SessionPayload, TodoItem, UncommittedFile } from './types'

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

      const notes = this.collector.planNotes
      const children: Node[] = [
        group(
          `계획 ${notes.length}건`,
          'note',
          notes.map((n) => planNode(n)),
          notes.length > 0,
        ),
      ]
      children.push(group(`미커밋 ${p.uncommittedFiles.length}개`, 'diff', p.uncommittedFiles.map((f) => fileNode(f, cwd))))
      children.push(unpushedGroup(this.collector.unpushedOf(p)))
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
    return nodes
  }
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

/** 계획 한 줄. 우클릭으로 지울 수 있게 contextValue 와 note 를 달아 둔다. */
function planNode(note: string): Node & { note: string } {
  const node = leaf(note, 'circle-small-filled') as Node & { note: string }
  node.item.contextValue = 'worklog.plan'
  node.note = note
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
