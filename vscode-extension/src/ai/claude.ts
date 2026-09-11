import { readdir, readFile, stat } from 'node:fs/promises'
import * as path from 'node:path'
import { log } from '../log'
import {
  type AiSource, type FullTurn, type RawSession, type SessionRef,
  inHome, jsonLines, kstDate, readHead, said,
} from './source'

/**
 * Claude Code — {@code ~/.claude/projects/<경로를 -로 바꾼 이름>/<세션id>.jsonl}.
 *
 * <p>한 줄에 한 JSON 이고, 사람이 친 말은 {@code type:'user'}, 답은 {@code 'assistant'} 다.
 */

const ROOT = inHome('.claude', 'projects')
/** 켜져 있는 세션이 저마다 남기는 곳. 끝나면 파일도 사라진다. */
const SESSIONS_DIR = inHome('.claude', 'sessions')
/** 세션 파일이 커도 끝부분만 읽는다 — 오늘 대화는 뒤에 있다. */
const TAIL_BYTES = 2 * 1024 * 1024
/** 대화가 시작된 시각을 찾을 만큼만 앞에서 읽는다. 첫 줄 하나면 되지만 길 수도 있다. */
const HEAD_BYTES = 64 * 1024

interface SessionEntry {
  type?: string
  isMeta?: boolean
  isSidechain?: boolean
  timestamp?: string
  /** {@code type: 'ai-title'} 줄에만 있다. */
  aiTitle?: string
  message?: { role?: string; content?: unknown }
}

/**
 * `/Users/me/work/app_v2` → `-Users-me-work-app-v2`
 *
 * <p>Claude Code 는 폴더 이름에서 <b>영숫자와 `-` 가 아닌 글자를 모두 `-` 로</b> 바꾼다.
 * 슬래시만 바꾸면 밑줄·점·공백이 든 경로에서 엉뚱한 폴더를 찾는다 — `git_collector` 의
 * 기록은 `-…-git-collector` 에 있는데 `-…-git_collector` 를 뒤지고는 조용히 빈 목록을
 * 돌려줬다. 그 저장소의 AI 대화가 통째로 빠져 있었고, 밑줄이 없는 저장소에서는 멀쩡해서
 * 눈에 띄지 않았다.
 */
export function encodeCwd(cwd: string): string {
  return cwd.replace(/[^A-Za-z0-9-]/g, '-')
}

/** 그 폴더의 그 대화가 담긴 기록 파일. */
export function sessionFilePath(cwd: string, id: string): string {
  return path.join(ROOT, encodeCwd(cwd), `${id}.jsonl`)
}

/** 파일 앞부분에서 첫 시각을 뽑는다. 대화가 시작된 때이고, 복사본끼리 같다. */
async function originOf(file: string): Promise<string | undefined> {
  for (const entry of jsonLines(await readHead(file, HEAD_BYTES))) {
    const at = (entry as SessionEntry).timestamp
    if (at) return at
  }
  return undefined
}

/**
 * 같은 대화에서 갈라져 나온 파일은 <b>마지막에 손댄 것 하나만</b> 남긴다.
 *
 * <p>파일 하나가 대화 하나는 아니다 — 이어 쓰거나 갈라 쓰면 Claude Code 는 지금까지의 대화를
 * 통째로 복사한 새 파일을 만들고 세션 id 도 새로 붙인다. 새 파일이 옛 파일을 안고 있으므로
 * 마지막 것만 봐도 잃는 것이 없다. 시작점을 읽지 못한 파일은 묶지 않는다 — 남남일 수도 있는
 * 것을 합치는 쪽이 더 나쁘다.
 */
function newestPerOrigin(files: (SessionRef & { origin?: string })[]): SessionRef[] {
  const newest = new Map<string, SessionRef & { origin?: string }>()
  const loose: SessionRef[] = []
  for (const file of files) {
    if (!file.origin) {
      loose.push(file)
      continue
    }
    const prev = newest.get(file.origin)
    if (!prev || prev.mtimeMs < file.mtimeMs) newest.set(file.origin, file)
  }
  const kept = [...loose, ...newest.values()]
  if (kept.length < files.length) {
    log(`AI 대화 ${files.length}개 파일 중 ${files.length - kept.length}개는 같은 대화의 옛 사본이라 뺍니다`)
  }
  return kept
}

/** 켜져 있는 세션이 적어 두는 파일. 필요한 것만 적는다. */
interface RunningSession {
  pid?: number
  sessionId?: string
  cwd?: string
}

/**
 * 이 폴더에서 <b>지금 열려 있는</b> 대화의 id.
 *
 * <p>기록 파일은 대화를 닫아도 그대로 남는다. 그것만 보고는 지금 열린 대화와 지난 대화를
 * 가릴 수 없어, 며칠 전에 닫은 대화가 사이드바에 계속 붙어 있었다.
 */
async function openSessionIds(cwd: string): Promise<Set<string>> {
  const ids = new Set<string>()
  let names: string[]
  try {
    names = (await readdir(SESSIONS_DIR)).filter((f) => f.endsWith('.json'))
  } catch {
    return ids // Claude Code 가 켜져 있지 않다.
  }

  for (const name of names) {
    try {
      const info = JSON.parse(await readFile(path.join(SESSIONS_DIR, name), 'utf8')) as RunningSession
      // 다른 폴더에서 켠 대화는 이 저장소의 일이 아니다.
      if (!info.sessionId || info.cwd !== cwd) continue
      // 갑자기 꺼지면 파일이 남을 수 있다. 프로세스가 살아 있는지 확인한다.
      if (info.pid !== undefined && !isAlive(info.pid)) continue
      ids.add(info.sessionId)
    } catch {
      continue // 쓰는 중이라 반쯤 적힌 파일일 수 있다.
    }
  }
  return ids
}

/** 그 프로세스가 살아 있는지. 신호 0 은 보내지 않고 존재만 확인한다. */
function isAlive(pid: number): boolean {
  try {
    process.kill(pid, 0)
    return true
  } catch (e) {
    // 남의 프로세스면 EPERM 이 온다 — 없는 것이 아니라 못 건드리는 것이다.
    return (e as NodeJS.ErrnoException).code === 'EPERM'
  }
}

/** 파일 하나를 질문·답변으로 푼다. 테스트가 파일 없이 부를 수 있게 본문만 받는다. */
export function parseClaude(text: string, workDate?: string): RawSession {
  const turns: FullTurn[] = []
  let title: string | undefined
  let current: FullTurn | undefined

  for (const line of jsonLines(text)) {
    const entry = line as SessionEntry
    // 제목 줄에는 시각이 없다. 아래 날짜 검사에 걸리지 않게 먼저 본다.
    if (entry.type === 'ai-title') {
      const recorded = entry.aiTitle?.trim()
      if (recorded) title = recorded // 대화 중에 여러 번 고쳐 적히므로 마지막 것을 쓴다.
      continue
    }

    const at = entry.timestamp
    if (!at || (workDate && kstDate(at) !== workDate)) continue
    if (entry.isMeta || entry.isSidechain) continue

    if (entry.type === 'user') {
      const prompt = said(textBlocks(entry.message?.content))
      if (!prompt) continue
      current = { at, prompt }
      turns.push(current)
      continue
    }
    if (entry.type === 'assistant' && current) {
      // 마지막 것만 남긴다. 도구를 부르는 사이에 흘리는 "…하겠습니다" 는 답이 아니다.
      const answer = said(textBlocks(entry.message?.content))
      if (answer) current.answer = answer
      current.answerAt = at
    }
  }
  return { title, turns }
}

/** 말 덩어리를 문자열 배열로. 문자열 하나로 오기도 한다. */
function textBlocks(content: unknown): string[] {
  if (typeof content === 'string') return [content]
  if (!Array.isArray(content)) return []
  return content
    .filter((b): b is { type: string; text?: unknown } =>
      typeof b === 'object' && b !== null && (b as { type?: string }).type === 'text')
    .map((b) => String(b.text ?? ''))
}

export const claudeSource: AiSource = {
  // 접두사가 없다 — 서버에 이미 이 id 로 쌓인 대화와 이어야 한다 (source.ts 참고).
  key: '',
  label: 'Claude Code',

  async list(cwd) {
    const dir = path.join(ROOT, encodeCwd(cwd))
    let names: string[]
    try {
      names = (await readdir(dir)).filter((f) => f.endsWith('.jsonl'))
    } catch {
      return [] // 이 폴더에서 Claude Code 를 쓰지 않았다.
    }

    const live = await openSessionIds(cwd)
    const files: (SessionRef & { origin?: string })[] = []
    for (const name of names) {
      const file = path.join(dir, name)
      const id = path.basename(name, '.jsonl')
      try {
        const info = await stat(file)
        files.push({ id, file, mtimeMs: info.mtimeMs, live: live.has(id), origin: await originOf(file) })
      } catch (e) {
        log(`AI 세션 ${name} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
      }
    }
    return newestPerOrigin(files)
  },

  async read(ref, workDate) {
    const buffer = await readFile(ref.file)
    const text = buffer.subarray(Math.max(0, buffer.length - TAIL_BYTES)).toString('utf8')
    return parseClaude(text, workDate)
  },
}
