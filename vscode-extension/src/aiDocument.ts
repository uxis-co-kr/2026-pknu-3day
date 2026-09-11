import * as vscode from 'vscode'
import { readFullConversation, sessionFilePath } from './aiSessions'
import { log } from './log'

/**
 * AI 대화를 <b>읽을 수 있는 문서</b>로 연다 (BACKLOG2 §2-3).
 *
 * <p>사이드바에서 질문을 누르면 답변이 자식 노드로 붙었다. 트리 한 줄에 답변 전문을 넣는
 * 것이라 줄바꿈도 코드 블록도 사라지고 뒷부분은 잘렸다. 원본인 {@code .jsonl} 을 그대로
 * 열어 주는 것도 답이 아니다 — 한 줄에 JSON 이 통째로 들어 있어 사람이 읽을 수 없다.
 *
 * <p>그래서 그 대화를 마크다운으로 그려 <b>미리보기로</b> 띄운다. 답변에는 목록·코드 블록이
 * 들어 있어, 원문 그대로 보면 `\`\`\`ts` 같은 기호가 글자로 남는다. 파일을 만들지 않는다 —
 * 가상 문서라 저장할 것도, 지울 것도 없다.
 *
 * <p>미리보기에는 커서를 둘 수 없다. 대신 <b>누른 질문에 표를 달아</b> 그린다 — 어디를 보러
 * 왔는지 눈으로 찾을 수 있게.
 */
export const AI_SCHEME = 'worklog-ai'

/** 문서마다 "지금 보러 온 질문의 시각". 그 질문에만 표를 단다. */
const focused = new Map<string, string>()

/** 같은 대화를 다른 질문으로 다시 열면 표만 옮겨 다시 그린다 — 탭이 늘지 않는다. */
const changed = new vscode.EventEmitter<vscode.Uri>()

function uriFor(cwd: string, id: string, title: string): vscode.Uri {
  // 제목은 탭에 보이는 이름이다. 경로로 쓸 수 없는 글자만 걷어낸다.
  const name = title.replace(/[\\/:*?"<>|#]/g, ' ').trim().slice(0, 60) || '대화'
  return vscode.Uri.from({
    scheme: AI_SCHEME,
    path: `/${name}.md`,
    query: new URLSearchParams({ cwd, id }).toString(),
  })
}

function time(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 읽기 전용 가상 문서. 열 때마다 기록 파일에서 다시 그린다. */
export class AiConversationProvider implements vscode.TextDocumentContentProvider {
  async provideTextDocumentContent(uri: vscode.Uri): Promise<string> {
    const params = new URLSearchParams(uri.query)
    const cwd = params.get('cwd') ?? ''
    const id = params.get('id') ?? ''
    const file = sessionFilePath(cwd, id)

    let read: Awaited<ReturnType<typeof readFullConversation>>
    try {
      read = await readFullConversation(file)
    } catch (e) {
      log(`대화 기록을 읽지 못했습니다 (${file}): ${e instanceof Error ? e.message : String(e)}`)
      return `# 대화를 열 수 없습니다\n\n기록 파일을 읽지 못했습니다.\n\n\`${file}\`\n`
    }

    const here = focused.get(uri.toString())
    const lines: string[] = []
    lines.push(`# ${read.title ?? '제목 없는 대화'}`, '')
    // 어디서 온 글인지 적어 둔다. 이 문서는 저장되지 않으므로 원본을 찾을 길을 남긴다.
    lines.push(`> ${file}`, '')

    for (const turn of read.turns) {
      // 미리보기에는 커서를 둘 수 없다. 누르고 온 질문에 표를 달아 눈으로 찾게 한다.
      const mark = turn.at === here ? ' ⬅︎ 여기' : ''
      lines.push(`## ${time(turn.at)} 질문${mark}`, '', turn.prompt, '')
      if (turn.answer) {
        lines.push('**답변**', '', turn.answer, '')
      } else {
        lines.push('_아직 답이 오지 않았습니다._', '')
      }
      lines.push('---', '')
    }
    if (read.turns.length === 0) {
      lines.push('_이 기록에서 질문을 찾지 못했습니다._', '')
    }

    return lines.join('\n')
  }
}

/**
 * 그 대화를 <b>렌더된 마크다운</b>으로 열고, 누른 질문에 표를 달아 둔다.
 *
 * <p>시각을 열쇠로 쓴다 — 사이드바가 들고 있는 질문은 200자에서 잘려 있어 본문과 글자가
 * 다르다. 시각은 자르지 않는다.
 *
 * <p>미리보기를 열지 못하는 환경(마크다운 확장이 꺼져 있는 경우 등)에서는 원문 문서라도
 * 띄운다 — 아무 일도 일어나지 않는 것보다 낫다.
 */
export async function openAiTurn(args: { cwd?: string; id?: string; title?: string; at?: string }): Promise<void> {
  if (!args?.cwd || !args.id) return
  const uri = uriFor(args.cwd, args.id, args.title ?? '대화')

  if (args.at) {
    focused.set(uri.toString(), args.at)
    // 이미 열려 있는 미리보기라면 표만 옮겨 다시 그린다.
    changed.fire(uri)
  }

  try {
    await vscode.commands.executeCommand('markdown.showPreview', uri)
  } catch (e) {
    log(`미리보기를 열지 못해 원문으로 엽니다: ${e instanceof Error ? e.message : String(e)}`)
    const doc = await vscode.workspace.openTextDocument(uri)
    await vscode.window.showTextDocument(doc, { preview: false })
  }
}
