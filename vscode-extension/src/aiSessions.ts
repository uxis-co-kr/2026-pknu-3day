import { readdir, readFile, stat } from 'node:fs/promises'
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

/** `/Users/me/work/app` → `-Users-me-work-app` */
function encodeCwd(cwd: string): string {
  return cwd.replace(/[/\\]/g, '-')
}

/**
 * @param cwd 워크스페이스 폴더의 절대 경로
 * @param workDate YYYY-MM-DD (KST). 그날 오간 것만 모은다.
 */
export async function collectAiSessions(cwd: string, workDate: string): Promise<AiSessionSummary[]> {
  const dir = path.join(ROOT, encodeCwd(cwd))
  let files: string[]
  try {
    files = (await readdir(dir)).filter((f) => f.endsWith('.jsonl'))
  } catch {
    return [] // Claude Code 를 쓰지 않는 폴더이거나 기록이 없다.
  }

  const out: AiSessionSummary[] = []
  for (const file of files) {
    try {
      const summary = await readSession(path.join(dir, file), workDate)
      if (summary) out.push(summary)
    } catch (e) {
      log(`AI 세션 ${file} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
    }
  }
  // 마지막으로 말한 세션이 앞에 오게.
  out.sort((a, b) => b.lastAt.localeCompare(a.lastAt))
  return out
}

async function readSession(file: string, workDate: string): Promise<AiSessionSummary | undefined> {
  const info = await stat(file)
  // 그날 손대지 않은 세션은 열지 않는다.
  if (kstDate(info.mtime.toISOString()) < workDate) return undefined

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
    if (!at || kstDate(at) !== workDate) continue
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
