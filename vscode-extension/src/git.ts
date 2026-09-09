import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const run = promisify(execFile)

/** git 이 없거나 워크스페이스가 저장소가 아니면 undefined. 확장은 이 경우 조용히 넘어간다. */
async function git(cwd: string, args: string[]): Promise<string | undefined> {
  try {
    const { stdout } = await run('git', args, { cwd, maxBuffer: 16 * 1024 * 1024 })
    return stdout
  } catch {
    return undefined
  }
}

export async function isRepo(cwd: string): Promise<boolean> {
  return (await git(cwd, ['rev-parse', '--is-inside-work-tree']))?.trim() === 'true'
}

export async function currentBranch(cwd: string): Promise<string | undefined> {
  const out = (await git(cwd, ['rev-parse', '--abbrev-ref', 'HEAD']))?.trim()
  // 커밋이 하나도 없는 저장소에서도 브랜치 이름은 나온다. 'HEAD' 는 detached 상태.
  return out && out !== 'HEAD' ? out : out
}

export async function remoteUrl(cwd: string): Promise<string | undefined> {
  return (await git(cwd, ['config', '--get', 'remote.origin.url']))?.trim() || undefined
}

/** 마지막 커밋 시각 (ISO-8601). 커밋이 없으면 undefined. */
export async function lastCommitAt(cwd: string): Promise<string | undefined> {
  return (await git(cwd, ['log', '-1', '--format=%cI']))?.trim() || undefined
}

export interface ChangedFile {
  path: string
  additions: number
  deletions: number
  /** 아직 git 이 추적하지 않는 새 파일 */
  untracked: boolean
}

/**
 * 커밋되지 않은 변경 목록 (PRD F6-1).
 *
 * `git diff --numstat HEAD` 는 스테이지에 올린 것과 올리지 않은 것을 함께 본다.
 * 추적되지 않는 새 파일은 diff 에 안 잡히므로 `status --porcelain` 으로 따로 모은다.
 */
export async function changedFiles(cwd: string): Promise<ChangedFile[]> {
  const files: ChangedFile[] = []

  const numstat = await git(cwd, ['diff', '--numstat', 'HEAD'])
  for (const line of (numstat ?? '').split('\n')) {
    if (!line.trim()) continue
    const [add, del, ...rest] = line.split('\t')
    const path = rest.join('\t')
    if (!path) continue
    // 바이너리는 '-' 로 나온다.
    files.push({
      path,
      additions: Number(add) || 0,
      deletions: Number(del) || 0,
      untracked: false,
    })
  }

  const status = await git(cwd, ['status', '--porcelain', '--untracked-files=all'])
  for (const line of (status ?? '').split('\n')) {
    if (!line.startsWith('?? ')) continue
    files.push({ path: line.slice(3).trim(), additions: 0, deletions: 0, untracked: true })
  }

  return files
}

/** 파일 하나의 diff. 스테이지 포함 (HEAD 와 비교). */
export async function fileDiff(cwd: string, path: string): Promise<string | undefined> {
  return await git(cwd, ['diff', 'HEAD', '--', path])
}
