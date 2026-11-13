"use client"

import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { VerificationBadge } from "@/components/verification-badge"
import type { OverviewStats } from "@/lib/types/plot"
import { MOCK_OVERVIEW_STATS } from "@/lib/api/mock-data"
import {
  Layers,
  AlertTriangle,
  Scale,
  Clock,
  ShieldCheck,
} from "lucide-react"

export function SectionCards({ stats = MOCK_OVERVIEW_STATS }: { stats?: OverviewStats }) {
  return (
    <div className="flex flex-col gap-4 px-4 lg:px-6">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4 *:data-[slot=card]:bg-linear-to-t *:data-[slot=card]:from-primary/5 *:data-[slot=card]:to-card *:data-[slot=card]:shadow-xs dark:*:data-[slot=card]:bg-card">
        {/* 1. Total Registered Plots */}
        <Card className="@container/card">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider">
              <Layers className="size-3.5 text-primary" />
              Total Registered Plots
            </CardDescription>
            <CardTitle className="text-2xl font-semibold tabular-nums @[250px]/card:text-3xl">
              {stats.totalPlots}
            </CardTitle>
            <CardAction>
              <Badge variant="outline" className="text-xs font-normal">
                Season 2026A
              </Badge>
            </CardAction>
          </CardHeader>
          <CardFooter className="flex-col items-start gap-1 text-xs">
            <div className="text-muted-foreground">
              Scope: Maize crops in Rwanda
            </div>
          </CardFooter>
        </Card>

        {/* 2. Flagged Plots */}
        <Card className="@container/card">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-amber-700 dark:text-amber-400">
              <AlertTriangle className="size-3.5" />
              Flagged Plots
            </CardDescription>
            <CardTitle className="text-2xl font-semibold tabular-nums text-amber-700 dark:text-amber-400 @[250px]/card:text-3xl">
              {stats.flaggedPlotsCount}
            </CardTitle>
            <CardAction>
              <Button
                variant="ghost"
                size="sm"
                nativeButton={false}
                render={<Link href="/dashboard/review-queue" />}
                className="h-auto p-0 hover:bg-transparent"
              >
                <Badge
                  variant="outline"
                  className="border-amber-300 text-amber-700 dark:border-amber-700 dark:text-amber-400 text-xs font-normal cursor-pointer hover:bg-amber-50"
                >
                  Needs Review →
                </Badge>
              </Button>
            </CardAction>
          </CardHeader>
          <CardFooter className="flex-col items-start gap-1 text-xs">
            <div className="text-muted-foreground">
              Area mismatch, boundary, or satellite review
            </div>
          </CardFooter>
        </Card>

        {/* 3. Disputed Plots */}
        <Card className="@container/card">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-rose-700 dark:text-rose-400">
              <Scale className="size-3.5" />
              Disputed Plots
            </CardDescription>
            <CardTitle className="text-2xl font-semibold tabular-nums text-rose-700 dark:text-rose-400 @[250px]/card:text-3xl">
              {stats.disputedPlotsCount}
            </CardTitle>
            <CardAction>
              <Button
                variant="ghost"
                size="sm"
                nativeButton={false}
                render={<Link href="/dashboard/review-queue" />}
                className="h-auto p-0 hover:bg-transparent"
              >
                <Badge
                  variant="outline"
                  className="border-rose-300 text-rose-700 dark:border-rose-700 dark:text-rose-400 text-xs font-normal cursor-pointer hover:bg-rose-50"
                >
                  Payouts Paused →
                </Badge>
              </Button>
            </CardAction>
          </CardHeader>
          <CardFooter className="flex-col items-start gap-1 text-xs">
            <div className="text-muted-foreground">
              Overlapping boundary claims
            </div>
          </CardFooter>
        </Card>

        {/* 4. Awaiting Field Verification */}
        <Card className="@container/card">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-sky-700 dark:text-sky-400">
              <Clock className="size-3.5" />
              Awaiting Field Visit
            </CardDescription>
            <CardTitle className="text-2xl font-semibold tabular-nums text-sky-700 dark:text-sky-400 @[250px]/card:text-3xl">
              {stats.awaitingFieldVerificationCount}
            </CardTitle>
            <CardAction>
              <Badge variant="outline" className="text-xs font-normal">
                Queue Active
              </Badge>
            </CardAction>
          </CardHeader>
          <CardFooter className="flex-col items-start gap-1 text-xs">
            <div className="text-muted-foreground">
              Pending agent visit with geotagged photo
            </div>
          </CardFooter>
        </Card>
      </div>

      {/* Breakdown by Verification Level */}
      <Card className="border bg-card">
        <CardHeader className="py-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <CardTitle className="text-sm font-semibold flex items-center gap-1.5">
                <ShieldCheck className="size-4 text-primary" />
                Plots by Verification Level
              </CardTitle>
              <CardDescription className="text-xs">
                Minimum level for insurance claims and payouts is FIELD_VERIFIED
              </CardDescription>
            </div>
            <div className="text-xs text-muted-foreground">
              Total: <span className="font-semibold text-foreground">{stats.totalPlots}</span> plots
            </div>
          </div>
        </CardHeader>
        <CardFooter className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-0 pb-3">
          <div className="flex items-center justify-between p-2 rounded-md border bg-muted/40">
            <VerificationBadge level="SELF_DECLARED" size="sm" />
            <span className="font-semibold tabular-nums text-sm">
              {stats.byVerificationLevel.SELF_DECLARED}
            </span>
          </div>
          <div className="flex items-center justify-between p-2 rounded-md border bg-muted/40">
            <VerificationBadge level="DOCUMENT_SUPPORTED" size="sm" />
            <span className="font-semibold tabular-nums text-sm">
              {stats.byVerificationLevel.DOCUMENT_SUPPORTED}
            </span>
          </div>
          <div className="flex items-center justify-between p-2 rounded-md border border-emerald-300 dark:border-emerald-800 bg-emerald-50/40 dark:bg-emerald-950/20">
            <VerificationBadge level="FIELD_VERIFIED" size="sm" />
            <span className="font-semibold tabular-nums text-sm text-emerald-700 dark:text-emerald-400">
              {stats.byVerificationLevel.FIELD_VERIFIED}
            </span>
          </div>
          <div className="flex items-center justify-between p-2 rounded-md border border-indigo-300 dark:border-indigo-800 bg-indigo-50/40 dark:bg-indigo-950/20">
            <VerificationBadge level="NLA_VERIFIED" size="sm" />
            <span className="font-semibold tabular-nums text-sm text-indigo-700 dark:text-indigo-400">
              {stats.byVerificationLevel.NLA_VERIFIED}
            </span>
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}
