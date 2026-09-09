import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { cn } from '@/lib/utils'

/** Figma 는 아바타를 사진이 아니라 이름 첫 글자 원형으로 그렸다. 사진이 있으면 사진을 쓴다. */
export default function UserAvatar({
  name, login, avatarUrl, className,
}: {
  name?: string | null
  login?: string
  avatarUrl?: string | null
  className?: string
}) {
  const initial = (name ?? login ?? '?').trim().charAt(0)
  return (
    <Avatar className={cn('size-[30px]', className)}>
      {avatarUrl ? <AvatarImage src={avatarUrl} alt={name ?? login ?? ''} /> : null}
      <AvatarFallback className="bg-primary/10 text-[12px] font-medium text-primary">
        {initial}
      </AvatarFallback>
    </Avatar>
  )
}
