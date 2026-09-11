import { readFile, readdir, stat } from 'node:fs/promises'
import * as path from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../log'
import {
  type AiSource, type FullTurn, type RawSession, type SessionRef,
  isoOf, jsonLines, kstDate, plain,
} from './source'

/**
 * VS Code 내장 채팅 — Copilot Chat 처럼 편집기의 채팅 API 를 쓰는 확장 <b>전부</b>.
 *
 * <p>대화를 확장이 아니라 <b>편집기가</b> 저장한다. 그래서 확장마다 파고들 필요가 없다:
 * {@code <User>/workspaceStorage/<해시>/chatSessions/<세션id>.json|jsonl} 한 곳만 읽으면
 * 그 API 를 쓰는 확장의 대화가 모두 들어온다.
 *
 * <p>폴더를 찾는 방법도 Claude Code 보다 낫다 — 같은 해시 폴더의 {@code workspace.json} 에
 * 열었던 폴더의 <b>절대경로가 그대로</b> 적혀 있다. 이름 규칙을 추측하다 밑줄에서 빗나갈
 * 일이 없다.
 *
 * <p>기록 형식이 두 가지다. 옛것은 통짜 JSON, 새것은 <b>고친 자리만 덧붙이는 기록</b>
 * ({@code .jsonl}) 이다 — 되감아야 내용이 보인다. 이 PC 에 둘이 섞여 있었다.
 */

/**
 * 편집기의 {@code User} 폴더. 확장이 켜질 때 {@link setUserDir} 로 받는다.
 *
 * <p>경로를 직접 짜 넣지 않는다 — 편집기마다(VS Code·Cursor·Windsurf·VSCodium) 다르고 OS
 * 마다 다르다. 확장의 globalStorage 가 {@code <User>/globalStorage/<확장id>} 이므로 거기서
 * 거슬러 올라가면 <b>지금 돌고 있는 편집기</b>의 것이 정확히 나온다.
 */
let userDir: string | undefined

/** @param globalStorage `context.globalStorageUri.fsPath` */
export function setUserDir(globalStorage: string | undefined): void {
  userDir = globalStorage ? path.dirname(path.dirname(globalStorage)) : undefined
  log(`내장 채팅 기록 폴더: ${userDir ? path.join(userDir, 'workspaceStorage') : '(없음)'}`)
}

/** 기록 한 통. 편집기가 적는 모양 중 우리가 보는 것만. */
interface ChatSession {
  customTitle?: string
  requests?: ChatRequest[]
}

interface ChatRequest {
  message?: { text?: string }
  /** epoch 밀리초 */
  timestamp?: number
  /** 답변 조각들. 글이 든 조각에는 {@code kind} 가 없다. */
  response?: { kind?: string; value?: unknown }[]
}

/** 덧붙이는 기록의 한 줄. {@code k} 는 고칠 자리, {@code v} 는 넣을 값. */
interface Patch {
  kind?: number
  k?: (string | number)[]
  v?: unknown
}

/**
 * 덧붙이는 기록을 되감아 통짜로 만든다.
 *
 * <p>{@code kind:0} 은 처음 모습, {@code 1} 은 그 자리에 넣기, {@code 2} 는 그 자리에
 * 이어 붙이기다. 되감지 않으면 첫 줄만 보이는데 거기 {@code requests} 는 늘 비어 있다 —
 * 질문은 모두 뒤에 덧붙는다.
 */
export function replay(text: string): ChatSession {
  let doc: Record<string, unknown> | undefined
  for (const line of jsonLines(text)) {
    const patch = line as Patch
    if (patch.kind === 0) {
      doc = (patch.v as Record<string, unknown>) ?? {}
      continue
    }
    if (!doc || !Array.isArray(patch.k) || patch.k.length === 0) continue
    let cur: Record<string, unknown> | unknown[] | undefined = doc
    for (const seg of patch.k.slice(0, -1)) {
      cur = (cur as Record<string, unknown>)?.[seg as string] as Record<string, unknown> | undefined
      if (cur === null || typeof cur !== 'object') break
    }
    if (cur === null || typeof cur !== 'object') continue
    const last = patch.k[patch.k.length - 1] as string
    const box = cur as Record<string, unknown>
    if (patch.kind === 2 && Array.isArray(box[last]) && Array.isArray(patch.v)) {
      ;(box[last] as unknown[]).push(...patch.v)
    } else {
      box[last] = patch.v
    }
  }
  return (doc as ChatSession) ?? {}
}

/** 통짜든 덧붙임이든 한 모양으로 읽는다. */
export function parseVscodeChat(text: string, workDate?: string): RawSession {
  // 통짜면 통째로 파싱된다. 되감기 기록은 JSON 여러 개가 이어 붙은 것이라 파싱이 깨지고,
  // 줄이 하나뿐이면 깨지지는 않지만 `{kind, v}` 라 한눈에 알아볼 수 있다.
  let doc: ChatSession
  try {
    const whole = JSON.parse(text) as Record<string, unknown>
    doc = 'kind' in whole && 'v' in whole ? replay(text) : (whole as ChatSession)
  } catch {
    doc = replay(text)
  }

  const turns: FullTurn[] = []
  for (const req of doc.requests ?? []) {
    const at = isoOf(req.timestamp)
    if (!at || (workDate && kstDate(at) !== workDate)) continue
    // 여기서는 태그를 걷어내지 않는다 — 편집기가 사람이 친 말만 따로 적어 두므로 걷어낼
    // 것이 없고, 걷어내면 코드를 붙여 넣고 물어본 질문이 사라진다 (source.ts 의 plain 참고).
    const prompt = plain([req.message?.text])
    if (!prompt) continue
    const answer = plain((req.response ?? []).map(answerText))
    turns.push({ at, prompt, answer, answerAt: at })
  }
  return { title: doc.customTitle?.trim() || undefined, turns }
}

/**
 * 답변 조각 하나에서 글만.
 *
 * <p>{@code kind} 가 붙은 조각은 생각·도구 호출·파일 편집이라 답이 아니다. 글 조각에는
 * {@code kind} 가 없는데, 값이 <b>문자열일 때도 {value} 일 때도</b> 있다. 한쪽만 받으면
 * 답변이 통째로 빈칸이 된다.
 */
function answerText(block: { kind?: string; value?: unknown }): string | undefined {
  if (block.kind !== undefined) return undefined
  const v = block.value
  if (typeof v === 'string') return v
  if (v && typeof v === 'object' && typeof (v as { value?: unknown }).value === 'string') {
    return (v as { value: string }).value
  }
  return undefined
}

/** 이 폴더를 열었던 해시 폴더들. 편집기를 여러 번 열면 여럿일 수 있다. */
async function storageDirs(cwd: string): Promise<string[]> {
  if (!userDir) return []
  const root = path.join(userDir, 'workspaceStorage')
  let names: string[]
  try {
    names = await readdir(root)
  } catch {
    return [] // 내장 채팅을 쓰지 않는 편집기다.
  }

  const found: string[] = []
  for (const name of names) {
    const dir = path.join(root, name)
    try {
      const meta = JSON.parse(await readFile(path.join(dir, 'workspace.json'), 'utf8')) as { folder?: string }
      // 여러 폴더를 묶은 창(.code-workspace)은 건너뛴다 — 어느 폴더의 대화인지 기록에 없다.
      if (!meta.folder) continue
      if (path.resolve(fileURLToPath(meta.folder)) === path.resolve(cwd)) found.push(dir)
    } catch {
      continue // 창을 닫는 중이거나 우리가 볼 것이 없는 폴더다.
    }
  }
  return found
}

export const vscodeChatSource: AiSource = {
  key: 'vscode',
  label: '내장 채팅',

  async list(cwd) {
    const refs: SessionRef[] = []
    for (const dir of await storageDirs(cwd)) {
      const chats = path.join(dir, 'chatSessions')
      let names: string[]
      try {
        names = (await readdir(chats)).filter((f) => f.endsWith('.json') || f.endsWith('.jsonl'))
      } catch {
        continue // 이 창에서는 채팅을 연 적이 없다.
      }
      for (const name of names) {
        const file = path.join(chats, name)
        try {
          const info = await stat(file)
          refs.push({ id: name.replace(/\.jsonl?$/, ''), file, mtimeMs: info.mtimeMs })
        } catch (e) {
          log(`내장 채팅 ${name} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
        }
      }
    }
    return refs
  },

  async read(ref, workDate) {
    return parseVscodeChat(await readFile(ref.file, 'utf8'), workDate)
  },
}
