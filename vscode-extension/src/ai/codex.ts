import { readFile, readdir, stat } from 'node:fs/promises'
import * as path from 'node:path'
import { log } from '../log'
import {
  type AiSource, type FullTurn, type RawSession, type SessionRef,
  inHome, jsonLines, kstDate, readHead, said,
} from './source'

/**
 * Codex — CLI 와 VS Code 확장(`openai.chatgpt`)이 <b>같은 곳</b>에 적는다.
 * {@code ~/.codex/sessions/YYYY/MM/DD/rollout-<시각>-<id>.jsonl}.
 *
 * <p>확장이라고 따로 팔 것이 없다는 뜻이다 — 확장으로 한 대화에도 {@code "source":"vscode"}
 * 가 적힐 뿐 파일은 한 자리다.
 *
 * <p>폴더는 첫 줄 {@code session_meta} 의 {@code cwd} 에 <b>절대경로 그대로</b> 있다.
 */

const ROOT = inHome('.codex', 'sessions')
/** 대화 제목이 모여 있는 곳. 있으면 쓰고 없으면 첫 질문으로 만든다. */
const INDEX = inHome('.codex', 'session_index.jsonl')
/** 첫 줄(session_meta)을 찾을 만큼만 읽는다. 그 줄에 지시문이 통째로 들어 있어 넉넉히 잡는다. */
const HEAD_BYTES = 256 * 1024

interface Entry {
  timestamp?: string
  type?: string
  payload?: {
    type?: string
    role?: string
    cwd?: string
    content?: { type?: string; text?: string }[]
  }
}

/** 첫 줄에 적힌 작업 폴더. 이 파일이 어느 저장소의 대화인지는 이것으로만 알 수 있다. */
export function cwdOf(text: string): string | undefined {
  for (const line of jsonLines(text)) {
    const entry = line as Entry
    if (entry.type === 'session_meta') return entry.payload?.cwd
  }
  return undefined
}

export function parseCodex(text: string, workDate?: string): RawSession {
  const turns: FullTurn[] = []
  let current: FullTurn | undefined

  for (const line of jsonLines(text)) {
    const entry = line as Entry
    if (entry.type !== 'response_item' || entry.payload?.type !== 'message') continue
    const at = entry.timestamp
    if (!at || (workDate && kstDate(at) !== workDate)) continue

    // developer·system 은 도구가 끼워 넣은 지시다. 사람이 친 말이 아니다.
    const role = entry.payload.role
    if (role !== 'user' && role !== 'assistant') continue

    const text_ = said((entry.payload.content ?? []).map((c) => c.text))
    if (!text_) continue

    if (role === 'user') {
      current = { at, prompt: text_ }
      turns.push(current)
    } else if (current) {
      current.answer = text_
      current.answerAt = at
    }
  }
  return { turns }
}

/** 대화 id → 제목. 파일이 없으면 빈 지도. */
async function titles(): Promise<Map<string, string>> {
  const map = new Map<string, string>()
  try {
    for (const line of jsonLines(await readFile(INDEX, 'utf8'))) {
      const row = line as { id?: string; thread_name?: string }
      if (row.id && row.thread_name) map.set(row.id, row.thread_name)
    }
  } catch {
    // 제목은 없어도 된다 — 첫 질문으로 만든다.
  }
  return map
}

/**
 * 날짜 폴더를 훑는다. {@code since} 를 주면 <b>그 하루 앞부터</b> 본다.
 *
 * <p>폴더 이름은 그 PC 의 날짜이고 우리가 세는 업무 일자는 KST 다. 시간대가 다른 PC 에서는
 * 하루가 어긋나므로 앞뒤로 하루를 더 본다. 그래도 폴더 셋이라 훑는 값이 싸다.
 */
async function dayDirs(since?: string): Promise<string[]> {
  const out: string[] = []
  const wanted = since ? new Set(neighbours(since)) : undefined
  for (const y of await listDir(ROOT)) {
    for (const m of await listDir(path.join(ROOT, y))) {
      for (const d of await listDir(path.join(ROOT, y, m))) {
        if (wanted && !wanted.has(`${y}-${m}-${d}`)) continue
        out.push(path.join(ROOT, y, m, d))
      }
    }
  }
  return out
}

/** 그날과 앞뒤 하루. */
function neighbours(day: string): string[] {
  const base = new Date(`${day}T00:00:00Z`).getTime()
  return [-1, 0, 1].map((n) => new Date(base + n * 86_400_000).toISOString().slice(0, 10))
}

async function listDir(dir: string): Promise<string[]> {
  try {
    return await readdir(dir)
  } catch {
    return []
  }
}

export const codexSource: AiSource = {
  key: 'codex',
  label: 'Codex',

  async list(cwd, since) {
    const named = await titles()
    const refs: SessionRef[] = []
    for (const dir of await dayDirs(since)) {
      for (const name of await listDir(dir)) {
        if (!name.startsWith('rollout-') || !name.endsWith('.jsonl')) continue
        const file = path.join(dir, name)
        try {
          const info = await stat(file)
          // 다른 폴더에서 한 대화는 이 저장소의 일이 아니다. 폴더는 첫 줄에 있으므로
          // 앞부분만 읽는다 — 기록 하나가 몇 MB 라 통째로 읽으면 훑기만 해도 무겁다.
          if (cwdOf(await readHead(file, HEAD_BYTES)) !== cwd) continue
          // 파일 이름은 `rollout-<시각>-<id>` 다. 뒤의 36자가 id.
          const id = name.slice('rollout-'.length, -'.jsonl'.length).slice(-36)
          refs.push({ id, file, mtimeMs: info.mtimeMs, title: named.get(id) })
        } catch (e) {
          log(`Codex 기록 ${name} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
        }
      }
    }
    return refs
  },

  async read(ref, workDate) {
    const raw = parseCodex(await readFile(ref.file, 'utf8'), workDate)
    return { ...raw, title: ref.title }
  },
}
