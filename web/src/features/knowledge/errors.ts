import i18n from '@/i18n/config'

/** Server messages are already localized via Accept-Language, so surface them as-is. */
export function knowledgeErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return i18n.t('apiError.unknown')
}
