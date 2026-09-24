import { withBasePath } from '@/shared/lib/base-path'
import { cn } from '@/shared/lib/utils'

interface BrandMarkProps {
  className?: string
  imageClassName?: string
  alt?: string
}

/**
 * Reuse the SkillHive mark wherever the app needs a compact brand symbol.
 */
export function BrandMark({ className, imageClassName, alt = 'SkillHive' }: BrandMarkProps) {
  return (
    <span className={cn('inline-flex items-center justify-center overflow-hidden rounded-xl', className)}>
      <img
        src={withBasePath('/skillhive-mark.svg')}
        alt={alt}
        className={cn('h-full w-full object-contain', imageClassName)}
      />
    </span>
  )
}
