import * as vscode from 'vscode'

/** 확장은 조용히 돌아야 하므로 진단은 전부 이 채널로만 남긴다 (보기: 출력 > WorkLog). */
let channel: vscode.OutputChannel | undefined

export function initLog(context: vscode.ExtensionContext): void {
  channel = vscode.window.createOutputChannel('WorkLog')
  context.subscriptions.push(channel)
}

export function log(message: string): void {
  const stamp = new Date().toISOString().slice(11, 19)
  channel?.appendLine(`[${stamp}] ${message}`)
}
