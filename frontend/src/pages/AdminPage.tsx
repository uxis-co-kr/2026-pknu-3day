import { Card } from '@/components/ui/card'

/**
 * 관리자 콘솔 — 뼈대만 있다. 내용은 **담당자 2(조웅식)** 가 채운다 (TODO_0910 §1-3).
 *
 * <p>로그인 화면에서 여기로 들어오는 길이 필요해 자리를 먼저 만들었다.
 * 각 항목이 기다리는 API 도 함께 적어 둔다.
 */
const SECTIONS = [
  { title: '팀원 전체 내역', wait: 'GET /stats/people (2-14)', hint: '기존 /people 화면을 그대로 쓸 수 있습니다' },
  { title: 'LLM 모델 선택', wait: 'GET/PUT /settings/llm (2-14)', hint: '설정 화면의 LLM 카드를 옮겨 오면 됩니다' },
  { title: 'Mattermost 웹훅 연결', wait: 'webhook URL', hint: '코드·실패 경로는 이미 동작합니다 (F-3)' },
  { title: '직원 목록 · GitHub 활성화 상태', wait: '와플 사원 목록 중계 + users 연결', hint: '미가입 기여자 문제와 같은 뿌리입니다' },
]

export default function AdminPage() {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-[18px] font-semibold">관리자 콘솔</h1>
        <p className="mt-1 text-[13px] text-muted-foreground">
          담당자 2 가 구현합니다. 아래는 9/10 회의에서 정한 항목과 각각이 기다리는 것입니다.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {SECTIONS.map((s) => (
          <Card key={s.title} className="rounded-lg p-5 shadow-none">
            <h2 className="text-sm font-semibold">{s.title}</h2>
            <p className="mt-1.5 text-[12px] text-muted-foreground">{s.hint}</p>
            <p className="mt-3 inline-block rounded bg-muted px-2 py-1 font-mono text-[11px] text-muted-foreground">
              대기: {s.wait}
            </p>
          </Card>
        ))}
      </div>
    </div>
  )
}
