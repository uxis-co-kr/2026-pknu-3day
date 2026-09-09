/** 화면 표기는 전부 KST 기준이다 (PRD 12. 확정된 가정). */
const KST_OFFSET_MIN = 9 * 60
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const

function toKst(d: Date): Date {
  return new Date(d.getTime() + (KST_OFFSET_MIN + d.getTimezoneOffset()) * 60_000)
}

/** YYYY-MM-DD (KST) */
export function todayKst(): string {
  return toIsoDate(toKst(new Date()))
}

export function toIsoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  return toIsoDate(new Date(y, m - 1, d + days))
}

/** "2026-09-09 (수) · 오늘" — 오늘이 아니면 뒤 꼬리표가 빠진다. */
export function formatDateLabel(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const wd = WEEKDAYS[new Date(y, m - 1, d).getDay()]
  return `${isoDate} (${wd})${isoDate === todayKst() ? ' · 오늘' : ''}`
}

/** "10:12" */
export function formatTime(iso: string): string {
  const d = toKst(new Date(iso))
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

/**
 * 목업 모드의 기준 시각. 아트보드가 2026-09-09 18:00 을 "지금" 으로 놓고 그려졌기 때문에,
 * 실제 시계를 쓰면 "6시간 전" 같은 값이 볼 때마다 달라진다.
 */
const MOCK_NOW = new Date('2026-09-09T18:00:00+09:00').getTime()

function nowMs(): number {
  return import.meta.env.VITE_USE_MOCK !== 'false' ? MOCK_NOW : Date.now()
}

/** "8분 전" · "6시간 전" · "3일 전" */
export function formatRelative(iso: string | null | undefined): string {
  if (!iso) return '없음'
  const diffMin = Math.floor((nowMs() - new Date(iso).getTime()) / 60_000)
  if (diffMin < 1) return '방금'
  if (diffMin < 60) return `${diffMin}분 전`
  const h = Math.floor(diffMin / 60)
  if (h < 24) return `${h}시간 전`
  return `${Math.floor(h / 24)}일 전`
}

export function startOfMonth(isoDate: string): string {
  return `${isoDate.slice(0, 7)}-01`
}

export function endOfMonth(isoDate: string): string {
  const [y, m] = isoDate.split('-').map(Number)
  return toIsoDate(new Date(y, m, 0))
}

/** "2026년 9월" */
export function monthLabel(isoDate: string): string {
  const [y, m] = isoDate.split('-').map(Number)
  return `${y}년 ${m}월`
}

/** 그 달의 1일이 무슨 요일인지 (0=일). 달력 첫 줄의 빈 칸 수와 같다. */
export function firstWeekdayOfMonth(isoDate: string): number {
  const [y, m] = isoDate.split('-').map(Number)
  return new Date(y, m - 1, 1).getDay()
}
