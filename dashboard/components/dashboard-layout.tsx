"use client"

import * as React from "react"
import { AppSidebar } from "@/components/app-sidebar"
import { SiteHeader } from "@/components/site-header"
import { DemoDataBanner, AuthGuardBlockingScreen } from "@/components/demo-data-banner"
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar"
import { isMockApiEnabled, isAuthConfigured } from "@/lib/api/client"

interface DashboardLayoutProps {
  children: React.ReactNode
  title?: string
}

function emptySubscribe() {
  return () => {}
}

export function DashboardLayout({ children, title = "Overview" }: DashboardLayoutProps) {
  const isMounted = React.useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false
  )

  // Safety rule: when NEXT_PUBLIC_USE_MOCK_API=false, refuse to start without auth
  const isMock = isMockApiEnabled()
  const hasAuth = isMounted ? isAuthConfigured() : true
  const isBlockedBySafetyGuard = !isMock && !hasAuth

  return (
    <SidebarProvider
      style={
        {
          "--sidebar-width": "calc(var(--spacing) * 72)",
          "--header-height": "calc(var(--spacing) * 12)",
        } as React.CSSProperties
      }
    >
      <AppSidebar variant="inset" />
      <SidebarInset>
        <SiteHeader title={title} />
        <DemoDataBanner />
        <div className="flex flex-1 flex-col">
          {isBlockedBySafetyGuard ? (
            <AuthGuardBlockingScreen />
          ) : (
            children
          )}
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}
