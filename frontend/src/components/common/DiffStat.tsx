export default function DiffStat({ additions, deletions }: { additions: number | null; deletions: number | null }) {
  if (additions === null && deletions === null) return null
  return (
    <span className="shrink-0 whitespace-nowrap text-[12px] tabular-nums">
      <span className="text-status-confirmed">+{additions ?? 0}</span>{' '}
      <span className="text-status-failed">−{deletions ?? 0}</span>
    </span>
  )
}
