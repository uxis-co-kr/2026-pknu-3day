import { open, readdir, readFile, stat } from 'node:fs/promises'
import * as os from 'node:os'
import * as path from 'node:path'
import { log } from './log'
import type { AiSessionSummary, AiTurn } from './types'

/**
 * 이 폴더에서 오간 AI 대화(Claude Code)를 읽어 요약 재료를 만든다.
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
 * 그날 한 일을 모으는 도구라면 이것도 재료다.
 *
 * <p>Claude Code 는 세션을 {@code ~/.claude/projects/<경로를 -로 바꾼 이름>/<세션id>.jsonl}
 * 에 한 줄 한 JSON 으로 남긴다. 여기서는 **질문과 그 답변**을 뽑는다.
 */
const ROOT = path.join(os.homedir(), '.claude', 'projects')

/**
 * 한 세션에서 **담아 보낼** 질문·답변 쌍의 수. 너무 많으면 일지 프롬프트가 넘친다.
 *
 * <p>개수({@code promptCount})는 자르지 않고 전부 센다 — 자른 개수를 보고하면 60번 물어본
 * 세션이 12개로 보인다 (BACKLOG2_client C-1 ①).
 */
const MAX_TURNS = 12
/** 질문 한 줄의 길이 상한. */
const MAX_PROMPT_LEN = 200
/** 답변 한 줄의 길이 상한. 답변은 질문보다 훨씬 길다 — 중앙값이 1000자를 넘는다. */
const MAX_ANSWER_LEN = 300
/** 첫 질문으로 제목을 만들 때의 길이. */
const MAX_TITLE_LEN = 40
/** 세션 파일이 커도 끝부분만 읽는다 — 오늘 대화는 뒤에 있다. */
const TAIL_BYTES = 2 * 1024 * 1024
/** 대화가 시작된 시각을 찾을 만큼만 앞에서 읽는다. 첫 줄 하나면 되지만 길 수도 있다. */
const HEAD_BYTES = 64 * 1024

/**
 * 폴더 안의 대화 파일 하나.
 *
 * <p>파일 하나가 대화 하나는 아니다 — 이어 쓰거나 갈라 쓰면 Claude Code 는 <b>지금까지의
 * 대화를 통째로 복사한 새 파일</b>을 만들고 세션 id 도 새로 붙인다. 옛 파일은 그대로 남는다.
 * 그래서 파일만 세면 Claude Code 사이드바에 하나로 보이는 대화가 여기서는 둘, 셋으로 늘어난다.
 */
interface SessionFile {
  path: string
  id: string
  /** 이 대화가 시작된 시각. 복사본끼리는 이 값이 밀리초까지 같다 — 묶는 열쇠다. */
  origin: string | undefined
  mtimeMs: number
}

/** 폴더의 대화 파일을 모두 훑어 시작점까지 읽어 둔다. */
async function listSessionFiles(cwd: string): Promise<SessionFile[]> {
  const dir = path.join(ROOT, encodeCwd(cwd))
  let names: string[]
  try {
    names = (await readdir(dir)).filter((f) => f.endsWith('.jsonl'))
  } catch {
    return [] // Claude Code 를 쓰지 않는 폴더이거나 기록이 없다.
  }

  const files: SessionFile[] = []
  for (const name of names) {
    const file = path.join(dir, name)
    try {
      const info = await stat(file)
      files.push({ path: file, id: path.basename(name, '.jsonl'), origin: await originOf(file), mtimeMs: info.mtimeMs })
    } catch (e) {
      log(`AI 세션 ${name} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
    }
  }
  return files
}

/** 파일 앞부분에서 첫 시각을 뽑는다. 대화가 시작된 때이고, 복사본끼리 같다. */
async function originOf(file: string): Promise<string | undefined> {
  const handle = await open(file, 'r')
  try {
    const buffer = Buffer.alloc(HEAD_BYTES)
    const { bytesRead } = await handle.read(buffer, 0, HEAD_BYTES, 0)
    for (const line of buffer.subarray(0, bytesRead).toString('utf8').split('\n')) {
      if (!line.startsWith('{')) continue
      let entry: SessionEntry
      try {
        entry = JSON.parse(line) as SessionEntry
      } catch {
        continue // 마지막 조각이 잘렸다. 앞 줄에서 못 찾았으면 그냥 포기한다.
      }
      if (entry.timestamp) return entry.timestamp
    }
  } finally {
    await handle.close()
  }
  return undefined
}

/**
 * 같은 대화에서 갈라져 나온 파일은 <b>마지막에 손댄 것 하나만</b> 남긴다.
 *
 * <p>새 파일은 옛 파일의 내용을 통째로 안고 있으므로, 마지막 것만 봐도 잃는 것이 없다.
 * 시작점을 읽지 못한 파일은 묶지 않는다 — 남남일 수도 있는 것을 합치는 쪽이 더 나쁘다.
 */
function newestPerOrigin(files: SessionFile[]): SessionFile[] {
  const newest = new Map<string, SessionFile>()
  const loose: SessionFile[] = []
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

/** `/Users/me/work/app` → `-Users-me-work-app` */
function encodeCwd(cwd: string): string {
  return cwd.replace(/[/\\]/g, '-')
}

/**
 * 보낼 대화 — <b>오늘 질문을 올린 것</b>과 <b>지금 열려 있는 빈 곳이 아닌 것</b>.
 *
 * <p>둘을 합치는 이유는 서로 메우기 때문이다. 오늘 물었으면 닫았더라도 오늘 한 일이니
 * 남아야 하고(닫는 순간 그날 기록이 서버에서 지워지면 안 된다), 열어 둔 채 아직 묻지
 * 않았거나 어제 묻던 대화는 지금 붙들고 있는 일이니 함께 보이는 편이 맞다.
 *
 * <p>예전에는 뒤쪽을 "보내지 않음" 으로 따로 붙였다. 이제 같은 목록에 들어간다.
 *
 * @param cwd 워크스페이스 폴더의 절대 경로
 * @param workDate YYYY-MM-DD (KST)
 */
export async function collectAiSessions(cwd: string, workDate: string): Promise<AiSessionSummary[]> {
  const open = await openSessionIds(cwd)
  const out: AiSessionSummary[] = []
  for (const file of newestPerOrigin(await listSessionFiles(cwd))) {
    try {
      const today = await readSession(file.path, workDate)
      if (today) {
        out.push(today)
        continue
      }
      // 오늘 질문이 없어도 열려 있으면 싣는다. 날짜를 걸지 않고 읽어 마지막 오간 것을 담는다
      // — 질문이 하나도 없는 대화는 readSession 이 undefined 를 준다(빈 곳은 뺀다).
      if (!open.has(file.id)) continue
      const whole = await readSession(file.path)
      if (whole) out.push(whole)
    } catch (e) {
      log(`AI 세션 ${file.id} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
    }
  }
  // 마지막으로 말한 세션이 앞에 오게.
  out.sort((a, b) => b.lastAt.localeCompare(a.lastAt))
  return out
}

/** 켜져 있는 Claude Code 세션이 저마다 남기는 곳. 끝나면 파일도 사라진다. */
const SESSIONS_DIR = path.join(os.homedir(), '.claude', 'sessions')

/** {@link SESSIONS_DIR} 안의 파일. 필요한 것만 적는다. */
interface RunningSession {
  pid?: number
  sessionId?: string
  cwd?: string
}

/**
 * 이 폴더에서 <b>지금 열려 있는</b> 대화의 id.
 *
 * <p>기록 파일(`~/.claude/projects/…/*.jsonl`)은 대화를 닫아도 그대로 남는다. 그것만 보고는
 * 지금 열린 대화와 지난 대화를 가릴 수 없어, 며칠 전에 닫은 대화가 사이드바에 계속 붙어
 * 있었다. Claude Code 는 켜져 있는 세션마다 `~/.claude/sessions/<n>.json` 을 두고 거기에
 * 대화 id 와 작업 폴더를 적어 둔다 — 그것을 본다.
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

/**
 * 세션 파일 하나를 요약한다.
 *
 * @param workDate 주면 그날 오간 것만 센다. 주지 않으면 (열려 있는 대화를 실을 때)
 *                 날짜를 가리지 않고 마지막까지 오간 것을 담는다.
 */
async function readSession(file: string, workDate?: string): Promise<AiSessionSummary | undefined> {
  const info = await stat(file)
  // 그날 손대지 않은 세션은 열지 않는다.
  if (workDate && kstDate(info.mtime.toISOString()) < workDate) return undefined

  const buffer = await readFile(file)
  const text = buffer.subarray(Math.max(0, buffer.length - TAIL_BYTES)).toString('utf8')

  const turns: AiTurn[] = []
  /** 실제로 물어본 횟수. turns 는 잘리지만 이 값은 전부 센다. */
  let total = 0
  let firstAt: string | undefined
  let lastAt: string | undefined
  /** 그날 처음 물어본 말. 제목을 만들 때 쓴다 — turns[0] 은 잘린 뒤의 첫 줄이라 다르다. */
  let firstPrompt: string | undefined
  /** Claude Code 가 남긴 세션 제목. 대화 중에 여러 번 고쳐 적히므로 마지막 것을 쓴다. */
  let recordedTitle: string | undefined
  /** 지금 답변을 받고 있는 질문. */
  let current: AiTurn | undefined

  for (const line of text.split('\n')) {
    if (!line.startsWith('{')) continue // 잘린 첫 줄은 버린다.
    let entry: SessionEntry
    try {
      entry = JSON.parse(line) as SessionEntry
    } catch {
      continue
    }

    // 제목 줄에는 시각이 없다. 아래 날짜 검사에 걸리지 않게 먼저 본다.
    if (entry.type === 'ai-title') {
      const title = entry.aiTitle?.trim()
      if (title) recordedTitle = title
      continue
    }

    const at = entry.timestamp
    if (!at || (workDate && kstDate(at) !== workDate)) continue
    if (entry.isMeta || entry.isSidechain) continue

    if (entry.type === 'user') {
      const prompt = said(entry.message?.content)
      if (!prompt) continue
      firstAt ??= at
      firstPrompt ??= prompt
      lastAt = at
      total += 1
      current = { at, prompt: clip(prompt, MAX_PROMPT_LEN) }
      // 최근 것을 남긴다 — 앞쪽 12개만 두면 오후에 한 일이 통째로 빠진다.
      turns.push(current)
      if (turns.length > MAX_TURNS) turns.shift()
      continue
    }

    if (entry.type === 'assistant' && current) {
      const answer = said(entry.message?.content)
      // 마지막 것만 남긴다. 도구를 부르는 사이에 흘리는 "…하겠습니다" 는 답이 아니다.
      if (answer) current.answer = clip(answer, MAX_ANSWER_LEN)
      lastAt = at
    }
  }

  if (!lastAt || total === 0) return undefined
  return {
    id: path.basename(file, '.jsonl'),
    // 기록에 제목이 있으면 그것이 가장 낫다. 없는 세션도 있어 첫 질문으로 만든다.
    title: recordedTitle ?? clip(firstPrompt ?? '제목 없는 대화', MAX_TITLE_LEN),
    firstAt: firstAt ?? lastAt,
    lastAt,
    // 담은 개수가 아니라 **실제로 물어본 횟수**다 (BACKLOG2_client C-1).
    promptCount: total,
    turns,
  }
}

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
 * 사람이 친 말, 또는 모델이 답한 말만 남긴다.
 *
 * <p>도구 결과나 편집기가 끼워 넣은 안내는 `<ide_opened_file>`·`<system-reminder>` 같은
 * 태그로 시작한다. 그런 덩어리는 그날의 의도가 아니므로 버린다.
 *
 * <p><b>덩어리마다 판단한다.</b> 이어 붙인 뒤에 보면 안 된다 — VS Code 안에서 Claude Code 를
 * 쓰면 사람이 친 말 **앞에** `<ide_opened_file>` 안내가 한 덩어리 붙는다. 붙여 놓고 보면
 * 첫 글자가 `<` 라서 그 질문이, 나아가 그 대화가 통째로 사라졌다.
 */
function said(content: unknown): string | undefined {
  const blocks = Array.isArray(content)
    ? content
        .filter((b): b is { type: string; text?: unknown } =>
          typeof b === 'object' && b !== null && (b as { type?: string }).type === 'text')
        .map((b) => String(b.text ?? ''))
    : typeof content === 'string'
      ? [content]
      : []
  const kept = blocks.map((b) => b.trim()).filter((b) => b && !b.startsWith('<'))
  const joined = kept.join(' ').replace(/\s+/g, ' ').trim()
  return joined || undefined
}

function clip(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '…' : text
}

/** ISO 문자열을 KST 기준 YYYY-MM-DD 로. 업무 일자는 KST 다 (PRD 12). */
function kstDate(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(iso))
}
