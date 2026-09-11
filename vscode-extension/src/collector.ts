import { readFile, stat } from 'node:fs/promises'
import * as path from 'node:path'
import * as vscode from 'vscode'
import { collectAiSessions } from './aiSessions'
import * as git from './git'
import { log } from './log'
import type { SessionPayload, TodoItem, UncommittedFile, UnsavedFile } from './types'

/** PRD F6-1 — diff 는 파일당 200줄까지만 보낸다. */
const DIFF_LINE_LIMIT = 200
/** 추적되지 않는 새 파일을 읽어들일 상한. 이보다 크면 줄 수만 세고 본문은 싣지 않는다. */
const MAX_READ_BYTES = 1024 * 1024

/**
 * 셀 표식은 `TODO:` 하나다. `FIXME:` 는 세지 않는다 — 둘을 같이 세면 무엇이 몇 건인지
 * 흐려진다. 한 가지만 놓고 "오늘 남겨 둔 할 일" 로 읽는 편이 낫다.
 */
const TODO_PATTERN = /\bTODO\s*:\s*(.+?)\s*$/

/**
 * 표식 앞에 이것만 있어야 한다 — 블록 주석의 시작.
 *
 * <p>문서 파일에서도 `<!-- TODO: … -->` 는 주석이라 여기에 둔다.
 */
const BLOCK_LEAD = /(?:\/\*+|<!--)[ \t]*$/

/**
 * 표식 앞에 이것만 있어야 한다 — 한 줄 주석의 시작. `//!`, `///` 같은 문서 주석 표식도 받는다.
 *
 * <p>문서 파일에서는 인정하지 않는다. `#` 는 제목이고 `--` 는 밑줄이다.
 */
const LINE_LEAD = /(?:\/\/+|#+|--+|;+|%+)!*[ \t]*$/

/**
 * 블록 주석 안에서 표식 앞에 허용되는 것 — 줄머리 장식(`*`)과 공백뿐.
 *
 * <p>자바독의 ` * TODO: …` 는 받고, ` * 설명인데 TODO: …` 는 받지 않는다.
 */
const BLOCK_BODY_LEAD = /^[ \t]*\**[ \t]*$/

/** 여러 줄 주석. 언어마다 다르지만 이 둘이면 코드에서 만나는 것 대부분을 덮는다. */
const BLOCK_OPEN = /\/\*|<!--/
const BLOCK_CLOSE = /\*\/|-->/

/**
 * 주석 표식이 문서 문법과 겹치는 파일. `#` 는 제목이고 `--` 는 밑줄이라, 여기서는
 * 한 줄 주석을 인정하지 않는다 — `<!-- … -->` 만 주석으로 본다.
 */
const PROSE_EXTENSIONS = new Set(['.md', '.markdown', '.mdx', '.txt', '.rst', '.adoc'])

/**
 * 재시작해도 남아야 하는 값. 계획 문서는 git 에서 다시 뽑을 수 없어, 메모리에만 두면
 * VS Code 를 끄는 순간 사라진다.
 */
interface PersistedState {
  /** 어느 날짜의 기록인지. 날이 바뀌면 통째로 버린다. */
  date: string
  /** 폴더 절대 경로 → 그 폴더의 계획 문서. 폴더마다 하는 일이 다르다. */
  plans: Record<string, string>
}

const STATE_KEY = 'worklog.session.v1'

/** payload 에 담지 않는, 화면 전용 값. */
interface LocalInfo {
  cwd: string
  unpushed?: git.UnpushedCommit[]
}

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
  /**
   * 고치기 시작한 뒤 아직 저장하지 않은 파일 → 처음 고친 시각. 키는 절대 경로다.
   *
   * <p>무엇이 저장되지 않았는지는 {@link vscode.workspace.textDocuments} 가 늘 정확히
   * 알고 있다. 여기 담는 것은 <b>언제부터</b> 그랬는지뿐이라, 재시작하면 비워도 된다 —
   * 그때는 시각 없이 목록만 보낸다.
   */
  private readonly dirtySince = new Map<string, string>()

  /**
   * 폴더별 오늘 계획. <b>문서 한 통이 그 폴더의 오늘 계획 하나다.</b>
   *
   * <p>예전에는 한 줄을 계획 하나로 세어 여러 건으로 쌓았다. 그러다 보니 제목·들여쓰기
   * 같은 markdown 구조가 줄 단위로 흩어졌다 — 계획은 원래 문서로 쓰는 것이라, 하루에
   * 한 통이면 족하다.
   *
   * <p>폴더별로 나눠 두는 것은 그대로다. 하나로 합치면 회사 일 계획이 개인 프로젝트
   * 세션에 실려 간다.
   */
  private plans = new Map<string, string>()

  /** 마지막으로 되살리거나 비운 날짜. 자정을 넘기면 초기화하는 기준이다. */
  private stateDate = todayKst()

  private readonly store: StateStore | undefined

  constructor(store?: StateStore) {
    this.store = store
    this.restore()
  }

  /** 마지막 수집에서 센 미커밋 파일 수. 상태바가 읽는다. */
  private lastCount = 0

  /** 마지막 수집의 미푸시 커밋 수. 업스트림이 하나도 없으면 undefined. */
  private lastUnpushed: number | undefined

  /**
   * 서버로 보내지 않지만 화면에는 필요한 값. 폴더 절대 경로는 사이드바가 파일을 열 때
   * 쓰고, 미푸시 커밋은 PRD 7 의 요청 본문에 자리가 없어 payload 에 담을 수 없다.
   */
  private readonly locals = new WeakMap<SessionPayload, LocalInfo>()

  /** 이 payload 를 만든 워크스페이스 폴더의 절대 경로. */
  folderOf(payload: SessionPayload): string | undefined {
    return this.locals.get(payload)?.cwd
  }

  /** 아직 푸시하지 않은 커밋. 업스트림이 없으면 undefined (0 과 다르다). */
  unpushedOf(payload: SessionPayload): git.UnpushedCommit[] | undefined {
    return this.locals.get(payload)?.unpushed
  }

  /** 마지막 수집에서 센 미푸시 커밋 수. 셀 수 없으면 undefined. */
  get unpushedCount(): number | undefined {
    return this.lastUnpushed
  }

  /** 파일을 고치기 시작했다. 이미 세고 있으면 처음 시각을 그대로 둔다. */
  markDirty(fsPath: string, at: Date = new Date()): void {
    if (!this.dirtySince.has(fsPath)) this.dirtySince.set(fsPath, at.toISOString())
  }

  /** 저장했거나 되돌렸다. 더 이상 미저장이 아니다. */
  markSaved(fsPath: string): void {
    this.dirtySince.delete(fsPath)
  }

  /**
   * 그 폴더의 오늘 계획 문서를 통째로 바꾼다.
   *
   * <p>계획 문서를 저장할 때 부른다 — 문서가 곧 그 폴더의 오늘 계획 전부이므로, 문서에서
   * 지운 것은 계획에서도 지워져야 한다. 빈 문서는 "오늘 계획을 비웠다" 는 뜻이다.
   */
  setPlan(markdown: string, folder?: string): void {
    this.rolloverIfNeeded()
    const key = folder ?? this.soleFolder()
    if (!key) return
    const text = markdown.trim()
    if (!text) this.plans.delete(key)
    else this.plans.set(key, text)
    this.persist()
  }

  /** 그 폴더의 오늘 계획 문서. 없으면 빈 문자열. 폴더를 주지 않으면 폴더가 하나일 때만 답한다. */
  planOf(folder?: string): string {
    const key = folder ?? this.soleFolder()
    return (key && this.plans.get(key)) || ''
  }

  /** 워크스페이스에 git 폴더가 하나뿐이면 그 경로. 여럿이면 undefined. */
  private soleFolder(): string | undefined {
    const folders = vscode.workspace.workspaceFolders ?? []
    return folders.length === 1 ? folders[0].uri.fsPath : undefined
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
    // 업스트림이 있는 저장소가 하나도 없으면 undefined 로 남는다.
    let unpushedTotal: number | undefined

    for (const folder of folders) {
      const cwd = folder.uri.fsPath
      if (!(await git.isRepo(cwd))) continue

      const [branch, remoteUrl, lastCommitAt, changed, unpushed] = await Promise.all([
        git.currentBranch(cwd),
        git.remoteUrl(cwd),
        git.lastCommitAt(cwd),
        git.changedFiles(cwd),
        git.unpushedCommits(cwd),
      ])
      if (!branch || !remoteUrl) {
        log(`${folder.name}: 브랜치나 origin 을 읽지 못해 건너뜁니다`)
        continue
      }

      const uncommittedFiles = await this.buildFiles(cwd, changed, collectDiff)
      const todos = await this.scanTodos(cwd, changed)
      const workDate = todayKst()
      const aiSessions = await collectAiSessions(cwd, workDate)
      total += uncommittedFiles.length

      const plan = this.plans.get(cwd) ?? ''

      const payload: SessionPayload = {
        remoteUrl,
        branch,
        workDate,
        uncommittedFiles,
        todos,
        // 서버 계약은 문자열 한 칸이다 (PRD 7). 계획 문서를 그대로 담아 보낸다.
        planNote: plan || undefined,
        unsavedFiles: this.unsavedIn(cwd),
        lastCommitAt,
        aiSessions,
        // 업스트림이 없으면 담지 않는다. 빈 배열로 보내면 "미푸시 없음" 이 돼 버린다.
        unpushedCommits: unpushed,
      }
      this.locals.set(payload, { cwd, unpushed })
      if (unpushed) unpushedTotal = (unpushedTotal ?? 0) + unpushed.length
      payloads.push(payload)
    }

    this.lastCount = total
    this.lastUnpushed = unpushedTotal
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
   * 변경된 파일 안의 TODO (PRD F6-3).
   *
   * <p>파일마다 {@link findTodos} 로 주석 안의 것만 고른다.
   */
  private async scanTodos(cwd: string, changed: git.ChangedFile[]): Promise<TodoItem[]> {
    const todos: TodoItem[] = []
    for (const file of changed) {
      const lines = await readLines(path.join(cwd, file.path))
      if (!lines) continue
      todos.push(...findTodos(file.path, lines))
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
    log(`업무 일자가 ${this.stateDate} → ${today} 로 바뀌어 계획을 비웁니다`)
    this.stateDate = today
    this.plans.clear()
    this.persist()
  }

  private persist(): void {
    void this.store?.update(STATE_KEY, {
      date: this.stateDate,
      plans: Object.fromEntries(this.plans),
    } satisfies PersistedState)
  }

  private restore(): void {
    const saved = this.store?.get<PersistedState>(STATE_KEY)
    if (!saved) return
    if (saved.date !== this.stateDate) {
      log(`저장된 기록이 ${saved.date} 것이라 쓰지 않습니다 (오늘은 ${this.stateDate})`)
      return
    }
    for (const [folder, saved0] of Object.entries(saved.plans ?? {})) {
      // 계획을 줄 단위로 세던 판은 배열로 저장했다. 그날 적어 둔 것을 잃지 않게 이어 붙인다.
      const text = Array.isArray(saved0) ? saved0.join('\n') : saved0
      if (typeof text === 'string' && text.trim()) this.plans.set(folder, text)
    }
    log(`계획 ${this.plans.size}개 폴더를 되살렸습니다`)
  }

  /**
   * 이 폴더 안에서 <b>고쳐 놓고 저장하지 않은</b> 파일 (PRD F6-5 를 대신한다).
   *
   * <p>편집기가 들고 있는 문서를 그대로 본다 — 저장하지 않은 내용은 디스크에 없으니
   * git 도, 우리가 따로 센 기록도 알 수 없다. 여기가 유일한 출처다.
   *
   * <p>이름 없는 문서(Untitled)는 뺀다. 어느 폴더의 일인지 정할 수 없다.
   */
  private unsavedIn(cwd: string): UnsavedFile[] {
    const prefix = cwd.endsWith(path.sep) ? cwd : cwd + path.sep
    const files: UnsavedFile[] = []
    for (const doc of vscode.workspace.textDocuments) {
      if (!doc.isDirty || doc.isUntitled || doc.uri.scheme !== 'file') continue
      const fsPath = doc.uri.fsPath
      if (!fsPath.startsWith(prefix)) continue
      files.push({
        path: fsPath.slice(prefix.length).split(path.sep).join('/'),
        dirtySince: this.dirtySince.get(fsPath),
      })
    }
    return files
  }
}

/**
 * 파일 한 개에서 <b>`TODO:` 로 시작하는 주석</b>만 고른다 (PRD F6-3).
 *
 *
 * <p>두 가지를 거른다. 하나는 주석이 아닌 것 — 문자열 리터럴이나 문서 본문의 `TODO:` 는
 * 코드에 남겨 둔 할 일이 아니다. 다른 하나는 <b>주석 중간에 지나가듯 적힌 것</b> —
 * `// 지금은 이렇게 두지만 TODO: 나중에 고치기` 같은 설명문까지 세면, 사이드바와 업무
 * 일지의 TODO 수가 실제로 남겨 둔 할 일보다 부풀었다. 표식이 주석 <b>맨 앞</b>에 와야 한다.
 *
 * <p>text 에는 표식과 콜론을 뺀 내용만 담는다. 화면이 "TODO · {text}" 로 그리기 때문에
 * 표식을 그대로 두면 "TODO · TODO: ..." 가 된다.
 */
export function findTodos(filePath: string, lines: string[]): TodoItem[] {
  const prose = PROSE_EXTENSIONS.has(path.extname(filePath).toLowerCase())
  const todos: TodoItem[] = []
  let inBlock = false
  lines.forEach((line, i) => {
    const m = TODO_PATTERN.exec(line)
    if (m && startsComment(line, m.index, inBlock, prose)) {
      todos.push({ path: filePath, line: i + 1, text: stripCloser(m[1]) })
    }
    inBlock = blockAfter(line, inBlock)
  })
  return todos
}

/**
 * 그 줄의 `at` 자리에서 주석이 <b>시작되는지</b>. `inBlock` 은 줄이 시작될 때의 블록 상태다.
 *
 * <p>주석 안이기만 하면 되는 것이 아니라, 표식 앞에 주석 표식과 공백 말고는 아무것도
 * 없어야 한다. 앞에 말이 붙어 있으면 남겨 둔 할 일이 아니라 설명문이다.
 */
function startsComment(line: string, at: number, inBlock: boolean, prose: boolean): boolean {
  const before = line.slice(0, at)
  // 블록 주석 안에서는 줄머리 장식만 허용한다. `*/` 나 `-->` 는 여기에 걸려 저절로 빠진다
  // — 블록이 이미 닫혔다면 그 뒤는 주석이 아니다.
  if (inBlock) return BLOCK_BODY_LEAD.test(before)
  return BLOCK_LEAD.test(before) || (!prose && LINE_LEAD.test(before))
}

/** 이 줄을 지나고 나서도 블록 주석 안인지. 여닫이를 줄 끝까지 따라간다. */
function blockAfter(line: string, inBlock: boolean): boolean {
  let rest = line
  let block = inBlock
  for (;;) {
    if (block) {
      const close = rest.search(BLOCK_CLOSE)
      if (close < 0) return true
      rest = rest.slice(close + (rest.startsWith('-->', close) ? 3 : 2))
      block = false
    } else {
      const open = rest.search(BLOCK_OPEN)
      if (open < 0) return false
      rest = rest.slice(open + (rest.startsWith('<!--', open) ? 4 : 2))
      block = true
    }
  }
}

/** `<!-- TODO: 고치기 -->` 처럼 내용 뒤에 남는 주석 닫음표는 내용이 아니다. */
function stripCloser(text: string): string {
  return text.replace(/\s*(?:\*\/|-->)\s*$/, '')
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
