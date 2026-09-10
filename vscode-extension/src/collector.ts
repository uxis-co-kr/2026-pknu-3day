import { readFile, stat } from 'node:fs/promises'
import * as path from 'node:path'
import * as vscode from 'vscode'
import * as git from './git'
import { log } from './log'
import type { EditTimelineEntry, SessionPayload, TodoItem, UncommittedFile } from './types'

/** PRD F6-1 — diff 는 파일당 200줄까지만 보낸다. */
const DIFF_LINE_LIMIT = 200
/** 추적되지 않는 새 파일을 읽어들일 상한. 이보다 크면 줄 수만 세고 본문은 싣지 않는다. */
const MAX_READ_BYTES = 1024 * 1024

const TODO_PATTERN = /\b(TODO|FIXME)\s*:\s*(.+?)\s*$/

interface Save {
  firstSavedAt: string
  lastSavedAt: string
  saveCount: number
}

/**
 * 재시작해도 남아야 하는 값. 저장 이벤트와 계획 메모는 git 에서 다시 뽑을 수 없어,
 * 메모리에만 두면 VS Code 를 끄는 순간 사라진다. 서버는 editTimeline 을 통째로
 * 덮어쓰므로 그 상태로 전송하면 그날 쌓인 기록까지 지워진다.
 */
interface PersistedState {
  /** 어느 날짜의 기록인지. 날이 바뀌면 통째로 버린다. */
  date: string
  saves: Record<string, Save>
  plans: string[]
}

const STATE_KEY = 'worklog.session.v1'

/** {@link vscode.Memento} 와 같은 모양. 테스트·미리보기에서는 주지 않는다. */
export interface StateStore {
  get<T>(key: string): T | undefined
  update(key: string, value: unknown): Thenable<void>
}

/**
 * 워크스페이스에서 커밋되지 않은 작업을 모은다 (PRD F6 수집 항목 1~5).
 *
 * 워크스페이스 폴더마다 저장소가 다를 수 있으므로 payload 를 폴더 단위로 만든다.
 * 서버는 (user, remoteUrl, branch, workDate) 로 UPSERT 하므로 각각 별개 세션이 된다.
 */
export class Collector {
  /** 파일 저장 이벤트. 키는 절대 경로 — 폴더별로 나눠 담으려면 상대 경로로는 부족하다. */
  private readonly saves = new Map<string, Save>()

  /** 하루에 여러 번 적을 수 있다. 적은 순서대로 쌓인다. */
  private plans: string[] = []

  /** 마지막으로 되살리거나 비운 날짜. 자정을 넘기면 초기화하는 기준이다. */
  private stateDate = todayKst()

  private readonly store: StateStore | undefined

  constructor(store?: StateStore) {
    this.store = store
    this.restore()
  }

  /** 마지막 수집에서 센 미커밋 파일 수. 상태바가 읽는다. */
  private lastCount = 0

  /**
   * payload 가 어느 폴더에서 나왔는지. 사이드바가 파일을 열려면 절대 경로가 필요한데,
   * 그 경로는 서버로 보내는 값이 아니라 payload 에 담을 수 없다.
   */
  private readonly folders = new WeakMap<SessionPayload, string>()

  /** 이 payload 를 만든 워크스페이스 폴더의 절대 경로. */
  folderOf(payload: SessionPayload): string | undefined {
    return this.folders.get(payload)
  }

  recordSave(fsPath: string, at: Date = new Date()): void {
    this.rolloverIfNeeded()
    const iso = at.toISOString()
    const prev = this.saves.get(fsPath)
    if (prev) {
      prev.lastSavedAt = iso
      prev.saveCount += 1
    } else {
      this.saves.set(fsPath, { firstSavedAt: iso, lastSavedAt: iso, saveCount: 1 })
    }
    this.persist()
  }

  /** 계획 메모를 하나 더한다. 같은 문구를 두 번 적으면 무시한다. */
  addPlanNote(note: string): void {
    this.rolloverIfNeeded()
    const trimmed = note.trim()
    if (!trimmed || this.plans.includes(trimmed)) return
    this.plans.push(trimmed)
    this.persist()
  }

  removePlanNote(note: string): void {
    const i = this.plans.indexOf(note)
    if (i < 0) return
    this.plans.splice(i, 1)
    this.persist()
  }

  get planNotes(): string[] {
    return [...this.plans]
  }

  get uncommittedCount(): number {
    return this.lastCount
  }

  /** 워크스페이스의 git 폴더마다 하나씩. 저장소가 없으면 빈 배열. */
  async collect(collectDiff: boolean): Promise<SessionPayload[]> {
    this.rolloverIfNeeded()
    const folders = vscode.workspace.workspaceFolders ?? []
    const payloads: SessionPayload[] = []
    let total = 0

    for (const folder of folders) {
      const cwd = folder.uri.fsPath
      if (!(await git.isRepo(cwd))) continue

      const [branch, remoteUrl, lastCommitAt, changed] = await Promise.all([
        git.currentBranch(cwd),
        git.remoteUrl(cwd),
        git.lastCommitAt(cwd),
        git.changedFiles(cwd),
      ])
      if (!branch || !remoteUrl) {
        log(`${folder.name}: 브랜치나 origin 을 읽지 못해 건너뜁니다`)
        continue
      }

      const uncommittedFiles = await this.buildFiles(cwd, changed, collectDiff)
      const todos = await this.scanTodos(cwd, changed)
      total += uncommittedFiles.length

      const payload: SessionPayload = {
        remoteUrl,
        branch,
        workDate: todayKst(),
        uncommittedFiles,
        todos,
        // 서버 계약은 문자열 한 칸이다 (PRD 7). 여러 줄로 담아 보낸다.
        planNote: this.plans.length > 0 ? this.plans.join('\n') : undefined,
        editTimeline: this.timelineFor(cwd),
        lastCommitAt,
      }
      this.folders.set(payload, cwd)
      payloads.push(payload)
    }

    this.lastCount = total
    return payloads
  }

  private async buildFiles(
    cwd: string,
    changed: git.ChangedFile[],
    collectDiff: boolean,
  ): Promise<UncommittedFile[]> {
    const files: UncommittedFile[] = []
    for (const file of changed) {
      if (file.untracked) {
        const lines = await readLines(path.join(cwd, file.path))
        files.push({
          path: file.path,
          additions: lines?.length ?? 0,
          deletions: 0,
          // 새 파일은 diff 가 없으므로 본문을 diff 모양으로 만들어 보낸다.
          diff:
            collectDiff && lines
              ? ['@@ 새 파일 @@', ...lines.slice(0, DIFF_LINE_LIMIT).map((l) => `+${l}`)].join('\n')
              : undefined,
        })
        continue
      }
      files.push({
        path: file.path,
        additions: file.additions,
        deletions: file.deletions,
        diff: collectDiff ? truncate(await git.fileDiff(cwd, file.path)) : undefined,
      })
    }
    return files
  }

  /**
   * 변경된 파일 안의 TODO/FIXME (PRD F6-3).
   *
   * text 에는 표식과 콜론을 뺀 내용만 담는다. 화면이 "TODO · {text}" 로 그리기 때문에
   * 표식을 그대로 두면 "TODO · TODO: ..." 가 된다.
   */
  private async scanTodos(cwd: string, changed: git.ChangedFile[]): Promise<TodoItem[]> {
    const todos: TodoItem[] = []
    for (const file of changed) {
      const lines = await readLines(path.join(cwd, file.path))
      if (!lines) continue
      lines.forEach((line, i) => {
        const m = TODO_PATTERN.exec(line)
        if (m) todos.push({ path: file.path, line: i + 1, text: m[2] })
      })
    }
    return todos
  }

  /**
   * 날이 바뀌면 어제 기록을 버린다. 자정을 넘겨 켜 둔 VS Code 가 어제 저장 이벤트를
   * 오늘 세션으로 보내면 안 된다.
   */
  private rolloverIfNeeded(): void {
    const today = todayKst()
    if (today === this.stateDate) return
    log(`업무 일자가 ${this.stateDate} → ${today} 로 바뀌어 저장 기록과 계획을 비웁니다`)
    this.stateDate = today
    this.saves.clear()
    this.plans = []
    this.persist()
  }

  private persist(): void {
    void this.store?.update(STATE_KEY, {
      date: this.stateDate,
      saves: Object.fromEntries(this.saves),
      plans: this.plans,
    } satisfies PersistedState)
  }

  private restore(): void {
    const saved = this.store?.get<PersistedState>(STATE_KEY)
    if (!saved) return
    if (saved.date !== this.stateDate) {
      log(`저장된 기록이 ${saved.date} 것이라 쓰지 않습니다 (오늘은 ${this.stateDate})`)
      return
    }
    for (const [fsPath, save] of Object.entries(saved.saves ?? {})) this.saves.set(fsPath, save)
    this.plans = [...(saved.plans ?? [])]
    log(`저장 기록 ${this.saves.size}건, 계획 ${this.plans.length}건을 되살렸습니다`)
  }

  /** 이 폴더 안에서 저장된 파일만, git 기준 상대 경로로 바꿔 담는다 (PRD F6-5). */
  private timelineFor(cwd: string): EditTimelineEntry[] {
    const prefix = cwd.endsWith(path.sep) ? cwd : cwd + path.sep
    const entries: EditTimelineEntry[] = []
    for (const [fsPath, save] of this.saves) {
      if (!fsPath.startsWith(prefix)) continue
      entries.push({ path: fsPath.slice(prefix.length).split(path.sep).join('/'), ...save })
    }
    return entries
  }
}

function truncate(diff: string | undefined): string | undefined {
  if (!diff) return undefined
  const lines = diff.split('\n')
  if (lines.length <= DIFF_LINE_LIMIT) return diff
  return [...lines.slice(0, DIFF_LINE_LIMIT), `... (${lines.length - DIFF_LINE_LIMIT}줄 생략)`].join('\n')
}

/** 텍스트 파일이면 줄 배열, 바이너리·과대 파일·삭제된 파일이면 undefined. */
async function readLines(fsPath: string): Promise<string[] | undefined> {
  try {
    const info = await stat(fsPath)
    if (!info.isFile() || info.size > MAX_READ_BYTES) return undefined
    const buffer = await readFile(fsPath)
    if (buffer.subarray(0, 8192).includes(0)) return undefined // NUL 이 있으면 바이너리로 본다
    const lines = buffer.toString('utf8').split('\n')
    // 마지막 개행 뒤의 빈 조각은 줄이 아니다. 그대로 두면 새 파일의 +N 이 git 보다 1 크고,
    // diff 본문 끝에 빈 '+' 줄이 붙는다.
    if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop()
    return lines
  } catch {
    return undefined
  }
}

/** YYYY-MM-DD (KST). 업무 일자는 KST 기준이다 (PRD 12). */
export function todayKst(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
