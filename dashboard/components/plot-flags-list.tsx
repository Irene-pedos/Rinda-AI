"use client"

import type { PlotFlag } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { STRINGS } from "@/lib/strings"
import { AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react"

interface PlotFlagsListProps {
  flags: PlotFlag[]
}

export function PlotFlagsList({ flags }: PlotFlagsListProps) {
  if (flags.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <ShieldAlert className="size-4 text-emerald-600 dark:text-emerald-400" />
            Validation & Fraud Flags
          </CardTitle>
          <CardDescription className="text-xs">
            Automated boundary, area, and administrative rule checks
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-0">
          <div className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50/40 dark:border-emerald-800 dark:bg-emerald-950/20 p-3 text-xs text-emerald-800 dark:text-emerald-300">
            <CheckCircle2 className="size-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            <span>
              All automated checks passed. No area mismatch, boundary overlap, or mock location flags recorded.
            </span>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-amber-200 dark:border-amber-900/60">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-amber-800 dark:text-amber-400">
            <AlertTriangle className="size-4 text-amber-600 dark:text-amber-400" />
            Active Review Flags ({flags.length})
          </CardTitle>
          <Badge variant="outline" className="border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300 text-[11px]">
            Action Required
          </Badge>
        </div>
        <CardDescription className="text-xs">
          Anomalies detected during plot registration or satellite verification
        </CardDescription>
      </CardHeader>

      <CardContent className="flex flex-col gap-3 pt-0">
        {flags.map((flag) => {
          const isResolved = Boolean(flag.resolvedAt)

          return (
            <div
              key={flag.id}
              className="flex flex-col gap-1.5 rounded-md border p-3 bg-card shadow-xs"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Badge
                    variant="outline"
                    className="border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300 font-mono text-[10px]"
                  >
                    {flag.type}
                  </Badge>
                  <span className="text-xs font-semibold">
                    {STRINGS.flags[flag.type] || flag.type}
                  </span>
                </div>
                <Badge
                  variant={isResolved ? "secondary" : "outline"}
                  className={`text-[10px] ${
                    isResolved ? "text-muted-foreground" : "border-amber-400 text-amber-700 dark:text-amber-400 font-semibold"
                  }`}
                >
                  {isResolved ? "Resolved" : "Open"}
                </Badge>
              </div>

              <p className="text-xs text-muted-foreground leading-relaxed pl-1">
                {flag.details}
              </p>

              <div className="flex items-center justify-between text-[10px] text-muted-foreground border-t pt-1.5 mt-1 font-mono">
                <span>Created: {new Date(flag.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}</span>
                {flag.resolvedAt ? (
                  <span>
                    Resolved by {flag.resolvedBy || "Verifier"} on{" "}
                    {new Date(flag.resolvedAt).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
                  </span>
                ) : (
                  <span className="text-amber-600 dark:text-amber-400 font-medium">Pending Review</span>
                )}
              </div>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
