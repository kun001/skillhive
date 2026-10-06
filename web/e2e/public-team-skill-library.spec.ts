import { expect, test } from '@playwright/test'
import { registerSession } from './helpers/session'
import { csrfHeaders } from './helpers/csrf'

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem('i18nextLng', 'zh'))
})

test('guests can browse and download public skills and switch libraries', async ({ page }) => {
  await page.goto('/skills')
  const publicLibrary = page.getByRole('button', { name: '公共技能库', exact: true })
  const teamLibrary = page.getByRole('button', { name: '团队技能库', exact: true })
  await expect(publicLibrary).toHaveAttribute('aria-pressed', 'true')
  const response = await page.request.get('/api/web/skills?library=public&size=2')
  expect(response.ok()).toBeTruthy()
  const data = (await response.json()).data
  expect(data.total).toBeGreaterThan(0)
  expect(data.items.every((skill: { namespace: string }) => skill.namespace === 'global')).toBeTruthy()
  await page.screenshot({ path: '../.dev/public-team-public.png' })
  const skill = data.items[0]
  const detail = await page.request.get(`/api/web/skills/global/${skill.slug}`)
  expect(detail.ok()).toBeTruthy()
  expect((await detail.json()).data.canDownload).toBe(true)
  const download = await page.request.get(`/api/web/skills/global/${skill.slug}/download`)
  expect(download.ok()).toBeTruthy()
  expect((await download.body()).length).toBeGreaterThan(0)
  const teams = await page.request.get('/api/web/skills?library=team')
  expect((await teams.json()).data.total).toBe(0)
  await page.goto(`/space/global/${skill.slug}`)
  await expect(page).toHaveURL(new RegExp(`/space/global/${skill.slug}`))
  await expect(page.getByRole('button', { name: '下载', exact: true })).toBeEnabled()
  await page.goto('/skills?q=agent&page=2')
  await teamLibrary.focus()
  await page.keyboard.press('Enter')
  await expect(teamLibrary).toHaveAttribute('aria-pressed', 'true')
  await expect(page).toHaveURL(/library=team/)
  await expect(page).toHaveURL(/page=0/)
  await expect(page.getByText('登录后查看团队技能')).toBeVisible()
  await publicLibrary.click()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(publicLibrary).toBeVisible()
  await expect(teamLibrary).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.dev/public-team-mobile.png' })
})

test('ordinary members cannot see Global management or team skills outside their memberships', async ({ page }, testInfo) => {
  await registerSession(page, testInfo, { allowMockSession: false })
  const namespaces = await page.request.get('/api/v1/me/namespaces')
  expect(namespaces.ok()).toBeTruthy()
  const myNamespaces = (await namespaces.json()).data as Array<{ type: string; slug: string }>
  expect(myNamespaces.some((namespace) => namespace.type === 'GLOBAL')).toBe(false)
  const global = await page.request.get('/api/v1/namespaces/global')
  expect(global.status()).toBe(403)
  const publish = await page.request.post('/api/web/skills/global/publish', {
    headers: await csrfHeaders(page),
    multipart: { visibility: 'PUBLIC', file: { name: 'test.zip', mimeType: 'application/zip', buffer: Buffer.from('not a package') } },
  })
  expect(publish.status()).toBe(403)
  const teams = await page.request.get('/api/web/skills?library=team')
  expect(teams.ok()).toBeTruthy()
  expect((await teams.json()).data.items.every((skill: { namespace: string }) =>
    myNamespaces.some((namespace) => namespace.slug === skill.namespace))).toBeTruthy()
  await page.goto('/dashboard/namespaces')
  await expect(page.getByTestId('namespace-card-global')).toHaveCount(0)
  await page.goto('/skills?library=public')
  await expect(page.getByRole('button', { name: '公共技能库', exact: true })).toHaveAttribute('aria-pressed', 'true')
})
