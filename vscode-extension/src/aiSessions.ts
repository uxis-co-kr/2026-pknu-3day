import { log } from './log'
import { claudeSource } from './ai/claude'
import { codexSource } from './ai/codex'
import { geminiSource } from './ai/gemini'
import { type AiSource, type FullTurn, type SessionRef, kstDate } from './ai/source'
import { setUserDir, vscodeChatSource } from './ai/vscodeChat'
import type { AiSessionSummary, AiTurn } from './types'

/**
 * 이 폴더에서 오간 AI 대화를 읽어 요약 재료를 만든다.
 *
 * <p>커밋에도 미커밋 변경에도 남지 않는 작업이 있다 — 무엇을 어떻게 할지 묻고 정한 과정이다.
 * 그날 한 일을 모으는 도구라면 이것도 재료다.
 *
 * <p>도구마다 기록을 두는 곳도 모양도 다르다. 그것은 {@link AiSource} 뒤에 있고, 여기에는
 * <b>도구가 무엇이든 같은 규칙</b>만 남는다 — 몇 개를 담을지, 몇 자에서 자를지, 어느 것을
 * 보낼지.
 */

/** 읽을 곳들. 순서는 사이드바에 나오는 차례와 상관없다 — 마지막으로 말한 대화가 앞에 온다. */
const SOURCES: AiSource[] = [claudeSource, vscodeChatSource, codexSource, geminiSource]

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

/**
 * 확장이 켜질 때 한 번 부른다.
 *
 * <p>내장 채팅 기록이 어디 있는지는 <b>지금 돌고 있는 편집기</b>에게 물어야 안다 —
 * VS Code·Cursor·Windsurf 가 저마다 다른 곳에 둔다.
 *
 * @param globalStoragePath `context.globalStorageUri.fsPath`
 */
export function initAiSources(globalStoragePath: string | undefined): void {
  setUserDir(globalStoragePath)
}

/**
 * 세션 id 앞에 붙는 이름표. 서버는 id 하나로 대화를 합치므로 도구가 다르면 달라야 한다
 * — 그러지 않으면 Gemini 의 대화가 Codex 의 대화를 덮는다.
 *
 * <p>Claude 만 접두사가 없다. 이미 그 id 로 서버에 쌓인 대화가 있어, 붙이는 순간 같은
 * 대화가 남남이 되고 서버가 적어 둔 요약을 잃는다.
 */
function tag(source: AiSource, id: string): string {
  return source.key ? `${source.key}:${id}` : id
}

/** 접두사를 보고 어디서 온 대화인지 가린다. 접두사가 없으면 Claude 다 (옛 id). */
function sourceOf(id: string): { source: AiSource; rawId: string } {
  const at = id.indexOf(':')
  if (at > 0) {
    const found = SOURCES.find((s) => s.key === id.slice(0, at))
    if (found) return { source: found, rawId: id.slice(at + 1) }
  }
  return { source: claudeSource, rawId: id }
}

/**
 * 그 대화가 어느 도구의 것인지.
 *
 * <p>{@code key} 가 빈 문자열이면 Claude Code 다 — 접두사를 쓰지 않는 유일한 도구이고,
 * 사이드바는 그때 도구 이름을 적지 않는다. 대부분 한 도구만 쓰므로 줄마다 같은 이름이
 * 붙으면 읽을 것만 는다.
 */
export function sourceOfId(id: string): { key: string; label: string } {
  const { source } = sourceOf(id)
  return { key: source.key, label: source.label }
}

/** 그 대화가 어느 도구의 것인지, 이름만. */
export function sourceLabelOf(id: string): string {
  return sourceOf(id).source.label
}

/**
 * 보낼 대화 — <b>오늘 질문을 올린 것</b>과 <b>지금 열려 있는 빈 곳이 아닌 것</b>.
 *
 * <p>둘을 합치는 이유는 서로 메우기 때문이다. 오늘 물었으면 닫았더라도 오늘 한 일이니
 * 남아야 하고(닫는 순간 그날 기록이 서버에서 지워지면 안 된다), 열어 둔 채 아직 묻지
 * 않았거나 어제 묻던 대화는 지금 붙들고 있는 일이니 함께 보이는 편이 맞다.
 *
 * @param cwd 워크스페이스 폴더의 절대 경로
 * @param workDate YYYY-MM-DD (KST)
 */
export async function collectAiSessions(cwd: string, workDate: string): Promise<AiSessionSummary[]> {
  const out: AiSessionSummary[] = []
  for (const source of SOURCES) {
    let refs: SessionRef[]
    try {
      refs = await source.list(cwd, workDate)
    } catch (e) {
      log(`${source.label} 기록을 훑지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
      continue
    }
    // 기록이 아예 없는 것은 그 도구를 안 쓴다는 뜻이라 정상이다. 다만 조용히 0건이면
    // "대화를 안 했나 보다" 로 읽혀 버그가 숨으므로 한 줄 남긴다.
    if (refs.length === 0) {
      log(`${source.label}: 이 폴더의 대화 기록 없음`)
      continue
    }

    for (const ref of refs) {
      try {
        const summary = await summarize(source, ref, workDate)
        if (summary) out.push(summary)
      } catch (e) {
        log(`${source.label} 세션 ${ref.id} 을 읽지 못했습니다: ${e instanceof Error ? e.message : String(e)}`)
      }
    }
  }
  // 마지막으로 말한 세션이 앞에 오게.
  out.sort((a, b) => b.lastAt.localeCompare(a.lastAt))
  return out
}

/** 기록 하나를 그날치 요약으로. 보낼 것이 없으면 undefined. */
async function summarize(source: AiSource, ref: SessionRef, workDate: string): Promise<AiSessionSummary | undefined> {
  // 그날 손대지 않은 기록은 열지 않는다. 열려 있는 대화는 날짜와 상관없이 본다.
  if (!ref.live && kstDate(new Date(ref.mtimeMs).toISOString()) < workDate) return undefined

  const today = await source.read(ref, workDate)
  if (today && today.turns.length > 0) return fold(source, ref, today.title, today.turns)

  // 오늘 질문이 없어도 열려 있으면 싣는다. 날짜를 걸지 않고 읽어 마지막 오간 것을 담는다
  // — 질문이 하나도 없는 대화는 여기서도 걸러진다(빈 곳은 뺀다).
  if (!ref.live) return undefined
  const whole = await source.read(ref)
  if (!whole || whole.turns.length === 0) return undefined
  return fold(source, ref, whole.title, whole.turns)
}

/** 자르지 않은 대화를 보낼 크기로 접는다. */
function fold(source: AiSource, ref: SessionRef, title: string | undefined, turns: FullTurn[]): AiSessionSummary {
  // 최근 것을 남긴다 — 앞쪽 12개만 두면 오후에 한 일이 통째로 빠진다.
  const kept: AiTurn[] = turns.slice(-MAX_TURNS).map((t) => ({
    at: t.at,
    prompt: clip(t.prompt, MAX_PROMPT_LEN),
    ...(t.answer ? { answer: clip(t.answer, MAX_ANSWER_LEN) } : {}),
  }))
  const last = turns[turns.length - 1]
  return {
    id: tag(source, ref.id),
    // 기록에 제목이 있으면 그것이 가장 낫다. 없는 도구도 있어 첫 질문으로 만든다.
    // turns[0] 이 아니라 자르기 전의 첫 질문이다 — 자른 뒤의 첫 줄은 그날의 시작이 아니다.
    title: title?.trim() || clip(turns[0].prompt, MAX_TITLE_LEN),
    firstAt: turns[0].at,
    lastAt: last.answerAt ?? last.at,
    // 담은 개수가 아니라 **실제로 물어본 횟수**다 (BACKLOG2_client C-1).
    promptCount: turns.length,
    turns: kept,
  }
}

function clip(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '…' : text
}

export type { FullTurn } from './ai/source'

/**
 * 대화 하나를 <b>자르지 않고</b> 읽는다 — 사람이 읽을 문서를 만들기 위해서다.
 *
 * <p>{@link collectAiSessions} 가 만드는 요약본은 질문 200자·답변 300자로 자르고 최근
 * 12개만 담는다. 서버로 보낼 것이라 그렇다. 여기서는 그 반대가 필요하다.
 *
 * <p>기록 파일의 자리는 도구마다 다르고, 내장 채팅은 id 만으로 알 수도 없다(창마다 다른
 * 해시 폴더에 들어 있다). 그래서 목록을 다시 훑어 찾는다.
 */
export async function readConversation(
  cwd: string,
  id: string,
): Promise<{ title: string | undefined; turns: FullTurn[]; file: string } | undefined> {
  const { source, rawId } = sourceOf(id)
  const ref = (await source.list(cwd)).find((r) => r.id === rawId)
  if (!ref) {
    log(`${source.label} 세션 ${rawId} 의 기록 파일을 찾지 못했습니다`)
    return undefined
  }
  const read = await source.read(ref)
  if (!read) return undefined
  return { title: read.title ?? ref.title, turns: read.turns, file: ref.file }
}
