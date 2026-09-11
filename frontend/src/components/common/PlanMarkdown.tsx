import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * 계획 칸의 Markdown.
 *
 * <p>확장에서 계획은 <b>markdown 문서 한 통</b>으로 적는다 — 하루에 하나다. 그래서 여기서도
 * 적은 모양(제목·목록·들여쓰기·체크박스)대로 보여 줘야 무엇을 하려 했는지 읽힌다.
 * 줄글로 늘어놓으면 `## 오전` 같은 표식이 그대로 노출돼 오히려 읽기 나빠진다.
 *
 * <p>초안 본문을 그리는 {@link MarkdownPreview} 와 나눠 둔다. 저쪽은 문서 한 장을 읽는
 * 자리라 13px 에 여백이 넉넉하고, 여기는 세션 상세의 한 칸이라 12px 에 촘촘해야 한다.
 */
export default function PlanMarkdown({ source }: { source: string }) {
  return (
    <div className="min-w-0 break-words text-[12px] leading-5">
      <Markdown
        remarkPlugins={[remarkGfm]}
        components={{
          // 계획 문서 안의 제목은 문단을 나누는 표식이다. 본문보다 조금 도드라지기만 하면 된다.
          h1: ({ children }) => <p className="mt-2 font-semibold first:mt-0">{children}</p>,
          h2: ({ children }) => <p className="mt-2 font-semibold first:mt-0">{children}</p>,
          h3: ({ children }) => <p className="mt-2 font-medium first:mt-0">{children}</p>,
          ul: ({ children }) => <ul className="list-disc pl-4 marker:text-muted-foreground/50">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal pl-4 marker:text-muted-foreground/50">{children}</ol>,
          // 체크박스 목록(`- [ ]`)은 표식이 따로 붙으므로 불릿을 지운다.
          li: ({ children, className }) => (
            <li className={className?.includes('task-list-item') ? 'list-none' : undefined}>{children}</li>
          ),
          p: ({ children }) => <p className="mt-1 first:mt-0">{children}</p>,
          // 체크박스는 읽기 전용이다. props 를 그대로 펴면 react-markdown 의 node 까지
          // DOM 으로 새어 나가 경고가 뜨므로, 필요한 것만 받아 새로 그린다.
          input: ({ checked }) => (
            <input type="checkbox" checked={checked === true} disabled readOnly className="mr-1.5 align-[-1px]" />
          ),
          blockquote: ({ children }) => (
            <blockquote className="border-l-2 pl-2 text-muted-foreground">{children}</blockquote>
          ),
          code: ({ children }) => <code className="rounded bg-muted px-1 py-px text-[11px]">{children}</code>,
          a: ({ children, href }) => (
            <a href={href} target="_blank" rel="noreferrer" className="text-primary underline underline-offset-2">
              {children}
            </a>
          ),
          hr: () => <hr className="my-2" />,
        }}
      >
        {source}
      </Markdown>
    </div>
  )
}
