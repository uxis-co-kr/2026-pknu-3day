/**
 * 파서를 편집기 없이 돌리기 위한 최소한의 `vscode`.
 *
 * <p>`ai/*` 는 vscode API 를 쓰지 않지만 진단 한 줄을 남기려고 `log` 를 부르고, 그 log 가
 * 편집기 채널을 쓴다. 테스트에서는 그 채널이 없을 뿐이므로 빈 껍데기로 충분하다.
 */
const Module = require('node:module')

const stub = {
  window: { createOutputChannel: () => ({ appendLine() {}, dispose() {} }) },
}

const load = Module._load
Module._load = function (request, ...rest) {
  return request === 'vscode' ? stub : load.call(this, request, ...rest)
}
