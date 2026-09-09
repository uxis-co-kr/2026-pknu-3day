export default function DiffStat({ additions, deletions }: { additions: number | null; deletions: number | null }) {
  // 서버 스키마가 NOT NULL DEFAULT 0 이라 PR 활동은 null 이 아니라 0 으로 온다.
  // 변경량이 없는 행에는 아무것도 그리지 않는다 (아트보드 2 의 PR 행에 +0 −0 이 없다).
  if (!additions && !deletions) return null
  return (
    <span className="shrink-0 whitespace-nowrap text-[12px] tabular-nums">
      <span className="text-status-confirmed">+{additions ?? 0}</span>{' '}
      <span className="text-status-failed">−{deletions ?? 0}</span>
    </span>
  )
}
