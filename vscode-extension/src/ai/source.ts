import { open } from 'node:fs/promises'
import * as os from 'node:os'
import * as path from 'node:path'

/**
 * AI 대화 기록을 읽어 오는 곳 하나 — Claude Code, VS Code 내장 채팅, Codex, Gemini.
 *
 * <p>예전에는 Claude Code 한 곳만 읽었다. 그 코드가 `aiSessions.ts` 에 통째로 들어 있었고
 * 경로도 형식도 거기 박혀 있었다. 그래서 Copilot Chat 이나 Codex 를 쓰는 사람이 이 확장을
 * 깔면 <b>AI 대화만 조용히 0건</b>이 되었다 — 사이드바는 "0세션" 이라고만 적으니 대화를
 * 안 한 것인지 못 읽은 것인지 알 길이 없었다.
 *
 * <p>도구마다 다른 것은 <b>기록이 어디 있고 어떤 모양인가</b> 둘뿐이다. 그 둘만 이 인터페이스
 * 뒤로 숨기면, 몇 개를 담을지·몇 자에서 자를지 같은 규칙은 {@code aiSessions.ts} 한 곳에
 * 남아 도구가 늘어도 갈라지지 않는다.
 */
export interface AiSource {
  /**
   * 세션 id 앞에 붙는 이름표. 서버는 id 하나로 대화를 합치므로 도구가 다르면 달라야 한다.
   *
   * <p>Claude 만 빈 문자열이다 — 이미 서버에 그 id 로 쌓인 대화가 있고, 접두사를 붙이면
   * 같은 대화가 남남이 되어 서버가 적어 둔 요약을 잃는다.
   */
  readonly key: string
  /** 사이드바에 보여 줄 이름. */
  readonly label: string
  /**
   * 이 폴더에서 오간 대화의 기록 파일들. 그 도구를 쓰지 않으면 빈 배열.
   *
   * @param since YYYY-MM-DD. 그날부터만 봐도 된다는 귀띔이다 — 기록을 날짜 폴더에 나눠
   *              담는 도구(Codex)가 옛 파일을 안 여는 데 쓴다. 지켜야 하는 약속은 아니다.
   */
  list(cwd: string, since?: string): Promise<SessionRef[]>
  /**
   * 기록 파일 하나를 <b>자르지 않고</b> 읽는다. 자르는 것은 부르는 쪽 일이다.
   *
   * @param workDate 주면 그날 오간 것만. 비우면 파일에 담긴 전부
   */
  read(ref: SessionRef, workDate?: string): Promise<RawSession | undefined>
}

/** 기록 파일 하나를 가리키는 표. */
export interface SessionRef {
  /** 그 도구 안에서의 id. 접두사는 붙지 않은 날것이다. */
  id: string
  file: string
  mtimeMs: number
  /** 지금 열려 있는 대화인지. 아는 도구만 채운다 (Claude Code). */
  live?: boolean
  /** 목록을 만들 때 이미 제목을 아는 도구(Codex)가 채운다. */
  title?: string
}

/** 자르지 않은 질문·답변 하나. */
export interface FullTurn {
  /** ISO-8601. 물어본 시각 */
  at: string
  prompt: string
  answer?: string
  /** 답이 온 시각. 대화가 언제까지 이어졌는지는 질문 시각만으로는 알 수 없다. */
  answerAt?: string
}

/** 기록 파일 하나에서 읽어 낸 것. */
export interface RawSession {
  /** 도구가 적어 둔 제목. 없으면 부르는 쪽이 첫 질문으로 만든다. */
  title?: string
  turns: FullTurn[]
}

/**
 * 사람이 친 말, 또는 모델이 답한 말만 남긴다.
 *
 * <p>도구 결과나 편집기가 끼워 넣은 안내는 `<ide_opened_file>`·`<session_context>` 같은
 * 태그로 시작한다. 그런 덩어리는 그날의 의도가 아니므로 버린다. 네 도구가 모두 이 모양으로
 * 문맥을 끼워 넣는다 — Gemini 는 `<session_context>`, Codex 는 `<app-context>` 다.
 *
 * <p><b>덩어리마다 판단한다.</b> 이어 붙인 뒤에 보면 안 된다 — VS Code 안에서 Claude Code 를
 * 쓰면 사람이 친 말 <b>앞에</b> 안내가 한 덩어리 붙는다. 붙여 놓고 보면 첫 글자가 `<` 라서
 * 그 질문이, 나아가 그 대화가 통째로 사라졌다.
 */
export function said(parts: (string | undefined)[]): string | undefined {
  return plain(parts.filter((b) => !(b ?? '').trim().startsWith('<')))
}

/**
 * 태그를 걷어내지 <b>않고</b> 이어 붙인다.
 *
 * <p>{@link said} 의 규칙은 <b>사람이 친 말과 도구가 끼워 넣은 문맥이 한 자리에 섞여 있는</b>
 * 기록에만 맞는다. VS Code 내장 채팅은 사람이 친 말을 따로 적어 두므로 그 규칙을 쓰면 안
 * 된다 — `<!-- … -->` 나 `<script>` 를 붙여 넣고 물어본 질문이 통째로 사라진다.
 * 실제로 이 PC 의 기록에서 그렇게 사라진 질문이 있었다.
 */
export function plain(parts: (string | undefined)[]): string | undefined {
  const joined = parts.map((b) => (b ?? '').trim()).filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
  return joined || undefined
}

/** ISO 문자열을 KST 기준 YYYY-MM-DD 로. 업무 일자는 KST 다 (PRD 12). */
export function kstDate(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(iso))
}

/** epoch 밀리초를 ISO 로. VS Code 내장 채팅은 시각을 숫자로 적는다. */
export function isoOf(ms: number | undefined): string | undefined {
  if (typeof ms !== 'number' || !Number.isFinite(ms)) return undefined
  const d = new Date(ms)
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString()
}

/**
 * 파일 앞부분만 읽는다.
 *
 * <p>어느 폴더의 대화인지는 첫 줄에 있는데, 기록 파일은 몇 MB 씩 된다. 폴더를 가리려고
 * 통째로 읽으면 훑기만 해도 수십 MB 를 읽는다.
 */
export async function readHead(file: string, bytes: number): Promise<string> {
  const handle = await open(file, 'r')
  try {
    const buffer = Buffer.alloc(bytes)
    const { bytesRead } = await handle.read(buffer, 0, bytes, 0)
    return buffer.subarray(0, bytesRead).toString('utf8')
  } finally {
    await handle.close()
  }
}

/** 홈 폴더 아래 경로. 도구들이 기록을 여기 둔다. */
export function inHome(...parts: string[]): string {
  return path.join(os.homedir(), ...parts)
}

/** 한 줄 한 JSON 인 파일을 훑는다. 쓰는 중이라 잘린 줄은 건너뛴다. */
export function* jsonLines(text: string): Generator<Record<string, unknown>> {
  for (const line of text.split('\n')) {
    if (!line.startsWith('{')) continue
    try {
      yield JSON.parse(line) as Record<string, unknown>
    } catch {
      continue
    }
  }
}
