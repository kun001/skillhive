import { cn } from '@/shared/lib/utils'
import { fileIconFor } from './file-types'

interface KnowledgeFileIconProps {
  extension: string
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const SIZES = {
  sm: { box: 'h-8 w-8 rounded-lg', icon: 'h-4 w-4' },
  md: { box: 'h-10 w-10 rounded-xl', icon: 'h-5 w-5' },
  lg: { box: 'h-16 w-16 rounded-2xl', icon: 'h-8 w-8' },
}

export function KnowledgeFileIcon({ extension, size = 'sm', className }: KnowledgeFileIconProps) {
  const { Icon, tone } = fileIconFor(extension)
  return (
    <span className={cn('inline-flex shrink-0 items-center justify-center', SIZES[size].box, tone, className)} aria-hidden>
      <Icon className={SIZES[size].icon} />
    </span>
  )
}
