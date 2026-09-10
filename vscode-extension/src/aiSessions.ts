import { readdir, readFile, stat } from 'node:fs/promises'
import * as os from 'node:os'
import * as path from 'node:path'
import { log } from './log'
import type { AiSessionSummary } from './types'

/**
 * 이 폴더에서 오간 AI 대화(Claude Code)를 읽어 요약 재료를 만든다.
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
 * 그날 한 일을 모으는 도구라면 이것도 재료다.
 *
 * <p>Claude Code 는 세션을 {@code ~/.claude/projects/<경로를 -로 바꾼 이름>/<세션id>.jsonl}
 * 에 한 줄 한 JSON 으로 남긴다. 여기서는 **사용자가 친 말만** 뽑는다. 답변까지 담으면
 * 프롬프트가 감당할 수 없고, 무엇을 시켰는지가 그날의 의도에 더 가깝다.
 */
const ROOT = path.join(os.homedir(), '.claude', 'projects')

/** 한 세션에서 가져올 프롬프트 수. 너무 많으면 일지 프롬프트가 넘친다. */
const MAX_PROMPTS = 12
/** 프롬프트 한 줄의 길이 상한. */
const MAX_LEN = 200
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

  const prompts: string[] = []
  let firstAt: string | undefined
  let lastAt: string | undefined

  for (const line of text.split('\n')) {
    if (!line.startsWith('{')) continue // 잘린 첫 줄은 버린다.
    let entry: SessionEntry
    try {
      entry = JSON.parse(line) as SessionEntry
    } catch {
      continue
    }
    const at = entry.timestamp
    if (!at || kstDate(at) !== workDate) continue
    if (entry.type !== 'user' || entry.isMeta || entry.isSidechain) continue

    const said = plainText(entry.message?.content)
    if (!said) continue

    firstAt ??= at
    lastAt = at
    if (prompts.length < MAX_PROMPTS) {
      prompts.push(said.length > MAX_LEN ? said.slice(0, MAX_LEN) + '…' : said)
    }
  }

  if (!lastAt || prompts.length === 0) return undefined
  return {
    id: path.basename(file, '.jsonl'),
    firstAt: firstAt ?? lastAt,
    lastAt,
    promptCount: prompts.length,
    prompts,
  }
}

interface SessionEntry {
  type?: string
  isMeta?: boolean
  isSidechain?: boolean
  timestamp?: string
  message?: { role?: string; content?: unknown }
}

/**
 * 사람이 친 말만 남긴다.
 *
 * <p>도구 결과나 시스템이 끼워 넣은 안내는 `<local-command-…>` 같은 태그로 시작한다.
 * 그런 줄은 그날의 의도가 아니므로 버린다.
 */
function plainText(content: unknown): string | undefined {
  let text: string | undefined
  if (typeof content === 'string') {
    text = content
  } else if (Array.isArray(content)) {
    text = content
      .filter((b): b is { type: string; text: string } =>
        typeof b === 'object' && b !== null && (b as { type?: string }).type === 'text')
      .map((b) => b.text)
      .join(' ')
  }
  const trimmed = text?.trim()
  if (!trimmed || trimmed.startsWith('<')) return undefined
  return trimmed.replace(/\s+/g, ' ')
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
