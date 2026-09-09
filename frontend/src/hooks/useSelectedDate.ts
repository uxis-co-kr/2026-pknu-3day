import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import { addDays, todayKst } from '@/lib/date'

/**
 * 상단 바 날짜 선택기가 홈·인원 화면을 지배한다 (디자인 브리프 3.2).
 * 주소에 담아 두면 새로고침·링크 공유에도 같은 날짜가 열린다.
 */
export function useSelectedDate() {
  const [params, setParams] = useSearchParams()
  const date = params.get('date') ?? todayKst()

  const setDate = useCallback(
    (next: string) => {
      const p = new URLSearchParams(params)
      if (next === todayKst()) p.delete('date')
      else p.set('date', next)
      setParams(p, { replace: true })
    },
    [params, setParams],
  )

  return {
    date,
    setDate,
    prev: () => setDate(addDays(date, -1)),
    next: () => setDate(addDays(date, 1)),
  }
}
