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
 * <p>그래서 그 대화를 마크다운으로 그려 읽기 전용 문서로 띄우고, <b>누른 질문 자리로 커서를
 * 보낸다</b>. 파일을 만들지 않는다 — 가상 문서라 저장할 것도, 지울 것도 없다.
 */
export const AI_SCHEME = 'worklog-ai'

/** 문서마다 "질문 시각 → 몇 번째 줄". 그릴 때 만들어 두고 커서를 보낼 때 쓴다. */
const lineIndex = new Map<string, Map<string, number>>()

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

    const lines: string[] = []
    const index = new Map<string, number>()
    lines.push(`# ${read.title ?? '제목 없는 대화'}`, '')
    // 어디서 온 글인지 적어 둔다. 이 문서는 저장되지 않으므로 원본을 찾을 길을 남긴다.
    lines.push(`> ${file}`, '')

    for (const turn of read.turns) {
      index.set(turn.at, lines.length)
      lines.push(`## ${time(turn.at)} 질문`, '', turn.prompt, '')
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

    lineIndex.set(uri.toString(), index)
    return lines.join('\n')
  }
}

/**
 * 그 대화를 열고 {@code at} 에 물어본 질문으로 커서를 보낸다.
 *
 * <p>시각을 열쇠로 쓴다 — 사이드바가 들고 있는 질문은 200자에서 잘려 있어 본문과 글자가
 * 다르다. 시각은 자르지 않는다.
 */
export async function openAiTurn(args: { cwd?: string; id?: string; title?: string; at?: string }): Promise<void> {
  if (!args?.cwd || !args.id) return
  const uri = uriFor(args.cwd, args.id, args.title ?? '대화')

  const doc = await vscode.workspace.openTextDocument(uri)
  await vscode.languages.setTextDocumentLanguage(doc, 'markdown')
  const editor = await vscode.window.showTextDocument(doc, { preview: false })

  const line = args.at ? lineIndex.get(uri.toString())?.get(args.at) : undefined
  if (line === undefined) return
  const at = new vscode.Position(line, 0)
  editor.selection = new vscode.Selection(at, at)
  // 질문을 맨 위에 붙여 둔다 — 가운데에 두면 앞 대화가 같이 보여 어디가 그 질문인지 흐려진다.
  editor.revealRange(new vscode.Range(at, at), vscode.TextEditorRevealType.AtTop)
}
