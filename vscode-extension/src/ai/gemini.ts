import { readFile, readdir, stat } from 'node:fs/promises'
import * as path from 'node:path'
import { log } from '../log'
import { type AiSource, type FullTurn, type RawSession, type SessionRef, inHome, jsonLines, kstDate, said } from './source'

/**
 * Gemini — CLI 와 IDE 확장(Gemini Code Assist 의 에이전트)이 <b>같은 곳</b>에 적는다.
 * {@code ~/.gemini/tmp/<폴더이름>/chats/session-<시각>-<이름>.jsonl}.
 *
 * <p>폴더 이름은 저장소 이름을 줄여 붙인 것이라 믿을 수 없다 — 같은 이름의 저장소가 둘이면
 * 어느 쪽인지 알 수 없다. 대신 그 폴더에 {@code .project_root} 가 있고 <b>절대경로</b>가
 * 한 줄로 들어 있다. 그것으로 가린다.
 *
 * <p>기록은 고칠 때마다 <b>전체를 다시 적는다</b>({@code {"$set":{"messages":[…]}}}).
 * 그래서 마지막 {@code $set} 하나만 보면 된다 — 앞의 것은 모두 옛 모습이다.
 */

const ROOT = inHome('.gemini', 'tmp')

interface Message {
  timestamp?: string
  /** 'user' 는 사람, 'gemini'(또는 'model'·'assistant') 는 답이다. */
  type?: string
  content?: { text?: string }[] | string
}

export function parseGemini(text: string, workDate?: string): RawSession {
  let messages: Message[] = []
  for (const line of jsonLines(text)) {
    const set = (line as { $set?: { messages?: Message[] } }).$set
    if (set?.messages) messages = set.messages
  }

  const turns: FullTurn[] = []
  let current: FullTurn | undefined
  for (const m of messages) {
    const at = m.timestamp
    if (!at || (workDate && kstDate(at) !== workDate)) continue
    const body = said(Array.isArray(m.content) ? m.content.map((c) => c.text) : [m.content])
    if (!body) continue

    if (m.type === 'user') {
      current = { at, prompt: body }
      turns.push(current)
      continue
    }
    // 'info'·'error' 같은 안내 줄은 답이 아니다.
    if ((m.type === 'gemini' || m.type === 'model' || m.type === 'assistant') && current) {
      current.answer = body
      current.answerAt = at
    }
  }
  return { turns }
}

/** 이 폴더의 대화가 담긴 자리. 못 찾으면 undefined. */
async function chatsDir(cwd: string): Promise<string | undefined> {
  let names: string[]
  try {
    names = await readdir(ROOT)
  } catch {
    return undefined // Gemini 를 쓰지 않는다.
  }
  for (const name of names) {
    try {
      const root = (await readFile(path.join(ROOT, name, '.project_root'), 'utf8')).trim()
      if (path.resolve(root) === path.resolve(cwd)) return path.join(ROOT, name, 'chats')
    } catch {
      continue
    }
  }
  return undefined
}

export const geminiSource: AiSource = {
  key: 'gemini',
  label: 'Gemini',

  async list(cwd) {
    const dir = await chatsDir(cwd)
    if (!dir) return []
    let names: string[]
    try {
      names = (await readdir(dir)).filter((f) => f.endsWith('.jsonl'))
    } catch {
      return []
    }

    const refs: SessionRef[] = []
    for (const name of names) {
      const file = path.join(dir, name)
      try {
        const info = await stat(file)
        // 파일 안의 sessionId 는 'a2a-server' 처럼 여럿이 같은 값을 쓴다. 파일 이름을 id 로 삼는다.
        refs.push({ id: path.basename(name, '.jsonl'), file, mtimeMs: info.mtimeMs })
      } catch (e) {
        log(`Gemini 기록 ${name} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
      }
    }
    return refs
  },

  async read(ref, workDate) {
    return parseGemini(await readFile(ref.file, 'utf8'), workDate)
  },
}
