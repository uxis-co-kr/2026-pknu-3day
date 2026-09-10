import { useState } from 'react'
import { Check, Copy } from 'lucide-react'
import { Button } from '@/components/ui/button'
import GithubLinkCard from '@/components/settings/GithubLinkCard'
import PasswordPage from '@/pages/PasswordPage'
import RepoSection from '@/components/settings/RepoSection'
import { Card } from '@/components/ui/card'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useApiKeys, useIssueApiKey, useRevokeApiKey } from '@/api/hooks'
import { formatRelative } from '@/lib/date'
import type { IssuedApiKey } from '@/types/api'

function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <Card className="rounded-lg p-6 shadow-none">
      <h2 className="text-sm font-semibold">{title}</h2>
      {description && <p className="mt-1 text-[12px] text-muted-foreground">{description}</p>}
      <div className="mt-4">{children}</div>
    </Card>
  )
}

/**
 * 설정 — 9/10 회의 기준. API 연동 · 깃허브 연동 · 리포지터리.
 *
 * <p>Mattermost 와 LLM 모델은 관리자 콘솔로 옮겨간다 (담당자 2). 옮겨갈 때까지 여기 둔다.
 */
export default function SettingsPage() {
  const keys = useApiKeys()
  const issue = useIssueApiKey()
  const revoke = useRevokeApiKey()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [label, setLabel] = useState('')
  const [issued, setIssued] = useState<IssuedApiKey | null>(null)
  const [copied, setCopied] = useState(false)


  function openDialog() {
    setLabel('')
    setIssued(null)
    setCopied(false)
    setDialogOpen(true)
  }

  return (
    <div className="space-y-4">
      <Section title="깃허브 연동">
        <GithubLinkCard />
      </Section>

      <Section title="API 연동 (API Key)" description="VS Code 확장과 외부 연동에서 씁니다. 평문 키는 발급 직후 한 번만 보여 줍니다.">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="h-9 text-[12px]">라벨</TableHead>
              <TableHead className="h-9 w-[160px] text-[12px]">생성일</TableHead>
              <TableHead className="h-9 w-[160px] text-[12px]">마지막 사용</TableHead>
              <TableHead className="h-9 w-[90px] text-right text-[12px]">폐기</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {(keys.data ?? []).map((k) => (
              <TableRow key={k.id}>
                <TableCell className="text-table font-medium">{k.label}</TableCell>
                <TableCell className="text-table text-muted-foreground">{k.createdAt.slice(0, 10)}</TableCell>
                <TableCell className="text-table text-muted-foreground">{formatRelative(k.lastUsedAt)}</TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost" size="sm"
                    className="h-8 text-[12px] text-muted-foreground hover:text-status-failed"
                    onClick={() => revoke.mutate(k.id)}
                  >
                    폐기
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {(keys.data ?? []).length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="py-8 text-center text-[13px] text-muted-foreground">
                  발급된 키가 없습니다.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <Button size="sm" className="mt-4 h-[34px]" onClick={openDialog}>새 키 발급</Button>
      </Section>

      <Section title="리포지터리" description="여기 등록한 리포의 커밋·PR 만 수집합니다.">
        <RepoSection />
      </Section>

      <Section title="비밀번호">
        <PasswordPage />
      </Section>

      {/*
        * Mattermost 웹훅과 LLM 모델은 **관리자만** 바꾼다 (9/10 결정). 관리자 콘솔에 있고,
        * 서버도 /settings/notify · /settings/llm 을 ADMIN 으로 잠근다.
        */}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-[440px]">
          {issued ? (
            <>
              <DialogHeader>
                <DialogTitle className="text-base">API Key가 발급되었습니다</DialogTitle>
                <DialogDescription className="text-[13px] text-status-failed">
                  이 키는 지금 한 번만 보여 줍니다. 다시 볼 수 없습니다.
                </DialogDescription>
              </DialogHeader>
              <div className="flex items-center gap-2">
                <code className="flex-1 truncate rounded-md border bg-muted px-3 py-2 text-[12px]">{issued.key}</code>
                <Button
                  variant="outline" size="icon" className="size-9" aria-label="복사"
                  onClick={() => {
                    void navigator.clipboard.writeText(issued.key)
                    setCopied(true)
                  }}
                >
                  {copied ? <Check className="text-status-confirmed" /> : <Copy />}
                </Button>
              </div>
              <DialogFooter>
                <Button size="sm" className="h-[34px]" onClick={() => setDialogOpen(false)}>닫기</Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <DialogHeader>
                <DialogTitle className="text-base">새 키 발급</DialogTitle>
                <DialogDescription className="text-[13px]">
                  어디에서 쓸 키인지 알아볼 수 있게 라벨을 적어 주세요.
                </DialogDescription>
              </DialogHeader>
              <Input
                value={label} onChange={(e) => setLabel(e.target.value)}
                placeholder="예) MacBook VS Code" className="h-[34px] text-[13px]"
              />
              <DialogFooter>
                <Button variant="outline" size="sm" className="h-[34px]" onClick={() => setDialogOpen(false)}>
                  취소
                </Button>
                <Button
                  size="sm" className="h-[34px]"
                  disabled={!label.trim() || issue.isPending}
                  onClick={async () => setIssued(await issue.mutateAsync(label.trim()))}
                >
                  발급
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
