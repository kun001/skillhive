import { Suspense, useEffect, useRef, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Menu, X } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { BrandWordmark } from '@/shared/components/brand-wordmark'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { ThemeToggle } from '@/shared/components/theme-toggle'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { dismissOpenOverlays } from '@/shared/lib/dismiss-open-overlays'
import { syncDocumentLanguage } from '@/shared/lib/document-language'
import { DashboardSidebar, SIDEBAR_GROUPS } from '@/pages/dashboard'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { withBasePath } from '@/shared/lib/base-path'
import { getAppHeaderClassName } from './layout-header-style'
import { getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { t, i18n } = useTranslation()
  const { pathname, resolvedPathname } = useRouterState({
    select: (s) => ({
      pathname: s.location.pathname,
      resolvedPathname: s.resolvedLocation?.pathname,
    }),
  })
  const { user, isLoading } = useAuth()
  const [isHeaderElevated, setIsHeaderElevated] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const previousPathnameRef = useRef(pathname)
  const contentLayoutPathname = resolveAppMainContentPathname(pathname, resolvedPathname)
  const mainContentLayout = getAppMainContentLayout(contentLayoutPathname)
  const isDashboardSubRoute = pathname !== '/dashboard' && pathname.startsWith('/dashboard')
  const showSidebar = (isDashboardSubRoute && pathname !== '/dashboard/publish') || pathname.startsWith('/settings/')
  const governanceVisible = canViewGovernanceCenter(user?.platformRoles)
  const filteredDashboardGroups = SIDEBAR_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => (
        (!item.admin || governanceVisible)
        && (!item.passwordCapability || user?.canChangePassword === true)
      )),
    }))
    .filter((group) => group.items.length > 0)

  useEffect(() => {
    syncDocumentLanguage(i18n.resolvedLanguage ?? i18n.language)
  }, [i18n.language, i18n.resolvedLanguage])

  useEffect(() => {
    const updateHeaderElevation = () => {
      setIsHeaderElevated(window.scrollY > 0)
    }

    updateHeaderElevation()
    window.addEventListener('scroll', updateHeaderElevation, { passive: true })

    return () => {
      window.removeEventListener('scroll', updateHeaderElevation)
    }
  }, [])

  // Pathname-only: search debounce on /search must not dismiss overlays mid-typing.
  useEffect(() => {
    if (previousPathnameRef.current === pathname) {
      return
    }
    previousPathnameRef.current = pathname
    dismissOpenOverlays()
  }, [pathname])

  const landingLinks = [
    { label: t('nav.landing'), hash: 'hero' },
  ]

  return (
    <div className="min-h-screen flex flex-col relative" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      {/* Header */}
      <header className={getAppHeaderClassName(isHeaderElevated)} style={{ borderColor: 'hsl(var(--border))' }}>
        <Link to="/" className="flex min-h-11 flex-shrink-0 items-center" aria-label={t('hiveLanding.homeAria')}>
          <BrandWordmark />
        </Link>

        {/* Desktop nav — lg+ only */}
        <nav className="hidden lg:flex items-center gap-5 text-[15px] font-normal" style={{ color: 'hsl(var(--text-secondary))' }}>
          {landingLinks.map((item) => <a key={item.hash} href={withBasePath(`/#${item.hash}`)} className="px-2 py-2 text-sm hover:text-foreground transition-colors">{item.label}</a>)}
          <Link to="/skills" search={{ q: '', sort: 'all', page: 0, view: 'list' }} className="px-2 py-2 text-sm hover:text-foreground transition-colors" aria-current={pathname === '/skills' ? 'page' : undefined}>{t('hiveLanding.skillLibraryLabel')}</Link>
          <Link to="/knowledge" className="px-2 py-2 text-sm hover:text-foreground transition-colors">{t('hiveLanding.knowledgeLabel')}</Link>
          {user && <Link to="/dashboard" className="px-2 py-2 text-sm hover:text-foreground transition-colors">{t('nav.dashboard')}</Link>}
        </nav>

        <div className="flex items-center gap-1 sm:gap-2 flex-shrink-0" style={{ color: 'hsl(var(--text-secondary))' }}>
          <Link to="/changelog" className="hidden min-h-11 items-center rounded-lg px-3 text-sm transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring lg:inline-flex" aria-current={pathname === '/changelog' ? 'page' : undefined}>{t('changelog.title')}</Link>
          {/* Hamburger — visible below lg */}
          <button
            type="button"
            className="lg:hidden inline-flex h-11 w-11 items-center justify-center rounded-lg hover:bg-accent transition-colors"
            onClick={() => setMobileMenuOpen((prev) => !prev)}
            aria-expanded={mobileMenuOpen}
            aria-label={t(mobileMenuOpen ? 'layout.closeNavigation' : 'layout.openNavigation')}
          >
            {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <ThemeToggle />
          <div className="hidden sm:block"><LanguageSwitcher /></div>
          {user && <NotificationBell />}
          {isLoading ? null : user ? (
            <UserMenu user={user} />
          ) : (
            <Link
              to="/login"
              className="hidden rounded-full border border-border px-4 py-2 text-sm font-semibold text-foreground hover:bg-secondary transition-colors sm:inline-flex"
            >
              {t('nav.login')}
            </Link>
          )}
        </div>
      </header>

      {/* Mobile nav dropdown */}
      {mobileMenuOpen ? (
        <div className="lg:hidden sticky top-[72px] z-40 border-b border-border bg-background/95 backdrop-blur-xl">
          <nav className="flex flex-col px-4 py-3 gap-1">
            {landingLinks.map((item) => <a key={item.hash} href={withBasePath(`/#${item.hash}`)} className="rounded-lg px-4 py-2.5 text-sm text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setMobileMenuOpen(false)}>{item.label}</a>)}
            <Link to="/skills" search={{ q: '', sort: 'all', page: 0, view: 'list' }} className="rounded-lg px-4 py-2.5 text-sm text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setMobileMenuOpen(false)} aria-current={pathname === '/skills' ? 'page' : undefined}>{t('hiveLanding.skillLibraryLabel')}</Link>
            <Link to="/knowledge" className="rounded-lg px-4 py-2.5 text-sm text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setMobileMenuOpen(false)}>{t('hiveLanding.knowledgeLabel')}</Link>
            <Link to="/changelog" className="rounded-lg px-4 py-2.5 text-sm text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setMobileMenuOpen(false)} aria-current={pathname === '/changelog' ? 'page' : undefined}>{t('changelog.title')}</Link>
            {user && <Link to="/dashboard" className="rounded-lg px-4 py-2.5 text-sm text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setMobileMenuOpen(false)}>{t('nav.dashboard')}</Link>}
            <div className="px-4 py-2 sm:hidden"><LanguageSwitcher /></div>
            {!user && !isLoading && <Link to="/login" className="rounded-lg px-4 py-2.5 text-sm font-semibold text-foreground sm:hidden" onClick={() => setMobileMenuOpen(false)}>{t('nav.login')}</Link>}
          </nav>
        </div>
      ) : null}

      {/* Main content */}
      <main className={mainContentLayout.mainClassName}>
        <Suspense
          fallback={
            <div className="space-y-3 animate-fade-up">
              <div className="h-8 w-36 animate-shimmer rounded-md" />
              <div className="h-4 w-56 animate-shimmer rounded-md" />
              <div className="h-48 animate-shimmer rounded-lg" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
          {showSidebar ? (
            <div className="flex flex-col lg:flex-row gap-6">
              <DashboardSidebar groups={filteredDashboardGroups} user={user} t={t} pathname={pathname} />
              <div className="flex-1 min-w-0">
                <Outlet />
              </div>
            </div>
          ) : (
            <Outlet />
          )}
        </div>
        </Suspense>
      </main>

      {/* Footer */}
      <footer className="relative z-10 mt-auto border-t bg-secondary/70" style={{ borderColor: 'hsl(var(--border))' }}>
        <div className="mx-auto flex w-full max-w-[1200px] flex-col gap-5 px-5 py-6 sm:px-8">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <Link to="/" aria-label={t('hiveLanding.homeAria')}><BrandWordmark /></Link>
            <p className="text-sm text-muted-foreground">{t('hiveLanding.footerDescription')}</p>
          </div>
          <nav aria-label={t('footer.resources')} className="flex flex-wrap gap-x-5 gap-y-2 border-t border-border pt-4 text-xs text-muted-foreground">
            <Link to="/skills" search={{ q: '', sort: 'all', page: 0, view: 'list' }} className="hover:text-foreground">{t('hiveLanding.skillLibraryLabel')}</Link>
            <Link to="/knowledge" className="hover:text-foreground">{t('hiveLanding.knowledgeLabel')}</Link>
            <Link to="/changelog" className="hover:text-foreground">{t('changelog.title')}</Link>
            {/* 暂时隐藏文档入口
            <a href="https://iflytek.github.io/skillhub/" target="_blank" rel="noreferrer" className="hover:text-foreground">{t('footer.docs')}</a>
            */}
            <Link to="/privacy" className="hover:text-foreground">{t('footer.privacy')}</Link>
            <Link to="/terms" className="hover:text-foreground">{t('footer.terms')}</Link>
          </nav>
        </div>
      </footer>
    </div>
  )
}
