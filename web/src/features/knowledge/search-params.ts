import type { KnowledgeDocumentSort } from '@/api/knowledge-types'
import { KNOWLEDGE_FILE_CATEGORIES } from './file-types'

export type KnowledgeTimeRange = '7' | '30' | '90'
export type KnowledgeView = 'list' | 'grid'

/** URL state of the knowledge base workspace; defaults are omitted to keep links short. */
export interface KnowledgeBaseSearch {
  folder?: number
  q?: string
  type?: string
  owner?: 'me'
  time?: KnowledgeTimeRange
  sort?: KnowledgeDocumentSort
  page?: number
  view?: KnowledgeView
}

/** URL state of the knowledge index: keyword and team space filter. */
export interface KnowledgeIndexSearch {
  q?: string
  namespace?: string
}

export function validateKnowledgeIndexSearch(search: Record<string, unknown>): KnowledgeIndexSearch {
  const q = typeof search.q === 'string' ? search.q.trim().slice(0, 100) : ''
  const namespace = typeof search.namespace === 'string' ? search.namespace.trim().replace(/^@/, '') : ''
  return { q: q || undefined, namespace: namespace || undefined }
}

const SORTS: KnowledgeDocumentSort[] = ['updated', 'title', 'created']
const TIME_RANGES: KnowledgeTimeRange[] = ['7', '30', '90']

function positiveInteger(value: unknown): number | undefined {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number(value) : NaN
  return Number.isInteger(number) && number > 0 ? number : undefined
}

export function validateKnowledgeBaseSearch(search: Record<string, unknown>): KnowledgeBaseSearch {
  const q = typeof search.q === 'string' ? search.q.trim().slice(0, 100) : ''
  const type = typeof search.type === 'string' && KNOWLEDGE_FILE_CATEGORIES.some((category) => category.id === search.type)
    ? search.type
    : undefined
  const time = TIME_RANGES.find((range) => String(search.time) === range)
  const sort = SORTS.find((candidate) => search.sort === candidate)
  return {
    folder: positiveInteger(search.folder),
    q: q || undefined,
    type,
    owner: search.owner === 'me' ? 'me' : undefined,
    time,
    sort: sort && sort !== 'updated' ? sort : undefined,
    page: positiveInteger(search.page),
    view: search.view === 'grid' ? 'grid' : undefined,
  }
}

/** Start of the selected time window, rounded to the day so the query key stays stable. */
export function updatedFromForRange(range: KnowledgeTimeRange | undefined, now: Date = new Date()): string | undefined {
  if (!range) {
    return undefined
  }
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - Number(range))
  return start.toISOString()
}
