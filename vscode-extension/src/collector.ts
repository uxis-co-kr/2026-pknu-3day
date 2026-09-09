import type { SessionPayload } from './types'

/**
 * 워크스페이스에서 미커밋 작업을 모은다 (PRD F6 수집 항목 1~5).
 *
 * 1-6 에서 구현: git status --porcelain + git diff(스테이지 포함, 파일당 200줄),
 * 브랜치·원격 URL, 변경 파일 안의 TODO:/FIXME: 스캔, 파일 저장 타임라인.
 */
export class Collector {
  /** 파일 저장 이벤트 누적. extension.ts 의 onDidSaveTextDocument 가 넣는다. */
  private readonly saves = new Map<string, { firstSavedAt: string; lastSavedAt: string; saveCount: number }>()

  private planNote: string | undefined

  recordSave(path: string, at: Date = new Date()): void {
    const iso = at.toISOString()
    const prev = this.saves.get(path)
    if (prev) {
      prev.lastSavedAt = iso
      prev.saveCount += 1
    } else {
      this.saves.set(path, { firstSavedAt: iso, lastSavedAt: iso, saveCount: 1 })
    }
  }

  setPlanNote(note: string): void {
    this.planNote = note
  }

  /** 명령 팔레트로 적어 둔 오늘 계획. collect() 가 planNote 필드에 그대로 싣는다. */
  get todayPlanNote(): string | undefined {
    return this.planNote
  }

  /** 상태바 "미커밋 N파일" 표시에 쓴다. 1-6 전까지는 0. */
  get uncommittedCount(): number {
    return 0
  }

  async collect(): Promise<SessionPayload | undefined> {
    // 1-6 에서 구현.
    return undefined
  }
}
