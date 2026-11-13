"use client"

import { isMockApiEnabled } from "@/lib/api/client"
import { STRINGS } from "@/lib/strings"
import { AlertCircle, ShieldAlert } from "lucide-react"

export function DemoDataBanner() {
  const isMock = isMockApiEnabled()

  if (!isMock) return null

  return (
    <div className="bg-amber-500/10 border-b border-amber-500/20 text-amber-900 dark:text-amber-200 px-4 py-2 text-xs flex items-center justify-between gap-3">
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center gap-1 rounded bg-amber-500/20 px-1.5 py-0.5 font-mono text-[10px] font-semibold text-amber-800 dark:text-amber-300">
          <AlertCircle className="size-3" />
          {STRINGS.demo.mockBadge}
        </span>
        <span className="font-medium">{STRINGS.demo.bannerTitle}:</span>
        <span className="hidden sm:inline text-muted-foreground dark:text-amber-300/80">
          {STRINGS.demo.bannerDescription}
        </span>
      </div>
      <div className="shrink-0 text-[11px] font-mono text-muted-foreground dark:text-amber-400/80">
        NEXT_PUBLIC_USE_MOCK_API=true
      </div>
    </div>
  )
}

export function AuthGuardBlockingScreen() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center p-6 text-center">
      <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-rose-100 text-rose-600 dark:bg-rose-950 dark:text-rose-400">
        <ShieldAlert className="size-6" />
      </div>
      <h2 className="mt-4 text-xl font-semibold tracking-tight">
        {STRINGS.authGuard.title}
      </h2>
      <p className="mt-2 max-w-md text-sm text-muted-foreground leading-relaxed">
        {STRINGS.authGuard.message}
      </p>
      <div className="mt-6 rounded-md bg-muted p-3 font-mono text-xs text-left text-muted-foreground">
        <div># To test in development:</div>
        <div>NEXT_PUBLIC_USE_MOCK_API=true</div>
        <div className="mt-2"># Or configure authentication token:</div>
        <div>localStorage.setItem(&apos;rinda_auth_token&apos;, &apos;&lt;token&gt;&apos;)</div>
      </div>
    </div>
  )
}
