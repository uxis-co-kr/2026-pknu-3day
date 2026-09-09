import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/** 초안 본문은 LLM 이 만든 단순한 Markdown 이다. 미리보기 탭에서만 쓴다. */
export default function MarkdownPreview({ source }: { source: string }) {
  return (
    <Markdown
      remarkPlugins={[remarkGfm]}
      components={{
        h1: ({ children }) => <h1 className="mb-4 text-[17px] font-semibold">{children}</h1>,
        h2: ({ children }) => <h2 className="mb-2 mt-6 text-[14px] font-semibold">{children}</h2>,
        h3: ({ children }) => <h3 className="mb-2 mt-4 text-[13px] font-semibold">{children}</h3>,
        ul: ({ children }) => <ul className="mb-2 list-disc space-y-1 pl-5">{children}</ul>,
        ol: ({ children }) => <ol className="mb-2 list-decimal space-y-1 pl-5">{children}</ol>,
        li: ({ children }) => <li className="text-[13px] leading-6">{children}</li>,
        p: ({ children }) => <p className="mb-2 text-[13px] leading-6 text-muted-foreground">{children}</p>,
        a: ({ children, href }) => (
          <a href={href} target="_blank" rel="noreferrer" className="text-primary underline underline-offset-2">
            {children}
          </a>
        ),
        code: ({ children }) => <code className="rounded bg-muted px-1 py-0.5 text-[12px]">{children}</code>,
      }}
    >
      {source}
    </Markdown>
  )
}
