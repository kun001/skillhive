import { init } from 'pptx-preview'

// The library frees shared resources globally. Serialize parsing and only let
// the current viewer dispose them, so a stale render cannot destroy a new deck.
let queue = Promise.resolve()
let current: ReturnType<typeof init> | undefined

export async function renderPptx(host: HTMLElement, prepared: { data: ArrayBuffer; width: number; height: number }, isActive: () => boolean) {
  const previous = queue
  let release: () => void = () => undefined
  queue = new Promise<void>((resolve) => { release = resolve })
  await previous
  let viewer: ReturnType<typeof init> | undefined
  const dispose = () => {
    if (viewer && current === viewer) { viewer.destroy(); current = undefined }
  }
  try {
    if (!isActive()) return undefined
    viewer = init(host, { width: prepared.width, height: prepared.height, mode: 'list' })
    current = viewer
    const result = await viewer.preview(prepared.data)
    if (!isActive()) { dispose(); return undefined }
    return { result, dispose }
  } catch (error) {
    dispose()
    throw error
  } finally { release() }
}
