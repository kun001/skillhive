import { useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { knowledgeApi } from '@/api/client'
import type { KnowledgeDocument, KnowledgePreviewSheet } from '@/api/knowledge-types'
import { buttonVariants } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import { knowledgeKeys } from './use-knowledge-queries'

function SheetGrid({ sheet }: { sheet: KnowledgePreviewSheet }) {
  const columns = sheet.rows.reduce((count, row) => Math.max(count, row.length), 0)
  return (
    <div className="max-h-[70vh] overflow-auto rounded-lg border">
      <table className="w-full border-separate border-spacing-0 text-sm">
        <caption className="sr-only">{sheet.name}</caption>
        <thead className="sticky top-0 z-10 bg-muted">
          <tr><th className="min-w-12 border-b border-r p-2" aria-label="#" />
            {Array.from({ length: columns }, (_, index) => <th key={index} scope="col" className="min-w-28 border-b border-r px-3 py-2 font-medium">{String.fromCharCode(65 + index)}</th>)}
          </tr>
        </thead>
        <tbody>
          {sheet.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              <th scope="row" className="sticky left-0 border-b border-r bg-muted px-3 py-2 font-normal text-muted-foreground">{rowIndex + 1}</th>
              {row.map((cell, columnIndex) => {
                const merge = sheet.merges.find((range) => range.row === rowIndex && range.column === columnIndex)
                const covered = sheet.merges.some((range) => rowIndex >= range.row && rowIndex < range.row + range.rowSpan
                  && columnIndex >= range.column && columnIndex < range.column + range.columnSpan
                  && (rowIndex !== range.row || columnIndex !== range.column))
                if (covered) return null
                return <td key={columnIndex} rowSpan={merge?.rowSpan} colSpan={merge?.columnSpan}
                  className={cn('max-w-96 whitespace-pre-wrap break-words border-b border-r px-3 py-2 align-top',
                    cell.bold && 'font-semibold', cell.align === 'center' ? 'text-center' : cell.align === 'right' ? 'text-right' : 'text-left')}>
                  {cell.text}
                </td>
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function KnowledgeOfficeFilePreview({ document }: { document: KnowledgeDocument }) {
  const { t } = useTranslation()
  const [sheetIndex, setSheetIndex] = useState(0)
  const [imageFailed, setImageFailed] = useState(false)
  const preview = useQuery({
    queryKey: [...knowledgeKeys.document(document.id), 'office-preview', document.currentVersion],
    queryFn: () => knowledgeApi.officePreview(document.id, document.currentVersion),
    refetchInterval: (query) => query.state.data?.status === 'PROCESSING' ? 2000 : false,
    staleTime: Infinity,
    retry: false,
  })
  const data = preview.data
  const download = <a href={knowledgeApi.contentUrl(document.id, { version: document.currentVersion })} download
    className={buttonVariants({ variant: 'outline', size: 'sm' })}><Download className="mr-2 h-4 w-4" aria-hidden />{t('knowledge.preview.downloadOriginal')}</a>

  if (preview.isPending || data?.status === 'PROCESSING') {
    return <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" aria-hidden />{t('knowledge.preview.generating')}</div>
      {download}
    </div>
  }
  if (preview.isError || !data || data.status !== 'READY' || imageFailed) {
    return <div className="flex min-h-[24rem] flex-col items-center justify-center gap-4 px-6 text-center">
      <p className="text-sm text-muted-foreground">{t('knowledge.preview.officeFailed')}</p>
      {download}
      {data?.status !== 'FAILED' && <button className="text-sm underline" onClick={() => { setImageFailed(false); void preview.refetch() }}>{t('knowledge.preview.retry')}</button>}
    </div>
  }
  const sheet = data.sheets[sheetIndex]
  return <div className="space-y-4 p-4 sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">{document.previewKind === 'OFFICE'
        ? t('knowledge.preview.officeLimit', { count: data.pageLimit })
        : t('knowledge.preview.sheetLimit', { sheets: data.sheetLimit, rows: data.rowLimit, columns: data.columnLimit })}</p>
      {download}
    </div>
    {document.previewKind === 'OFFICE' ? (
      <div className="space-y-5 rounded-lg bg-muted/40 p-3 sm:p-5">
        {Array.from({ length: data.pageCount }, (_, index) => <figure key={index} className="space-y-2">
          <img src={knowledgeApi.previewPageUrl(document.id, document.currentVersion, index + 1)}
            alt={t('knowledge.preview.page', { count: index + 1 })} loading={index === 0 ? 'eager' : 'lazy'}
            onError={() => setImageFailed(true)} className="mx-auto h-auto max-w-full rounded border bg-white shadow-sm" />
          <figcaption className="text-center text-xs text-muted-foreground">{t('knowledge.preview.page', { count: index + 1 })}</figcaption>
        </figure>)}
      </div>
    ) : <>
      <div className="flex flex-wrap gap-2" aria-label={t('knowledge.preview.sheets')}>
        {data.sheets.map((item, index) => <button key={index} aria-pressed={sheetIndex === index}
          className={buttonVariants({ variant: sheetIndex === index ? 'default' : 'outline', size: 'sm' })}
          onClick={() => setSheetIndex(index)}>{item.name}</button>)}
      </div>
      {sheet ? <SheetGrid sheet={sheet} /> : <p className="text-sm text-muted-foreground">{t('knowledge.preview.emptySheet')}</p>}
      {sheet?.truncated && <p className="text-xs text-muted-foreground">{t('knowledge.preview.sheetTruncated')}</p>}
      <p className="text-xs text-muted-foreground">{t('knowledge.preview.formulaNote')}</p>
    </>}
  </div>
}
