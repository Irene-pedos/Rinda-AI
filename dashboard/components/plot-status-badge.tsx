"use client"

import * as React from "react"
import { Badge } from "@/components/ui/badge"
import { STRINGS } from "@/lib/strings"
import type { PlotStatus } from "@/lib/types/plot"
import { CheckCircle2, AlertTriangle, Scale, XCircle } from "lucide-react"

interface PlotStatusBadgeProps {
  status: PlotStatus
  size?: "sm" | "default"
  className?: string
}

export function PlotStatusBadge({
  status,
  size = "default",
  className = "",
}: PlotStatusBadgeProps) {
  const config = React.useMemo(() => {
    switch (status) {
      case "ACTIVE":
        return {
          label: STRINGS.statuses.ACTIVE.label,
          icon: <CheckCircle2 className="size-3 shrink-0 text-emerald-600 dark:text-emerald-400" />,
          badgeClass:
            "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-300",
        }
      case "FLAGGED":
        return {
          label: STRINGS.statuses.FLAGGED.label,
          icon: <AlertTriangle className="size-3 shrink-0 text-amber-600 dark:text-amber-400" />,
          badgeClass:
            "border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300 font-medium",
        }
      case "DISPUTED":
        return {
          label: STRINGS.statuses.DISPUTED.label,
          icon: <Scale className="size-3 shrink-0 text-rose-600 dark:text-rose-400" />,
          badgeClass:
            "border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-300 font-medium",
        }
      case "REJECTED":
        return {
          label: STRINGS.statuses.REJECTED.label,
          icon: <XCircle className="size-3 shrink-0 text-zinc-500" />,
          badgeClass:
            "border-zinc-300 bg-zinc-100 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-400",
        }
    }
  }, [status])

  return (
    <Badge
      variant="outline"
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md ${
        size === "sm" ? "px-1.5 py-0.5 text-[10px]" : "px-2 py-0.5 text-xs"
      } ${config.badgeClass} ${className}`}
    >
      {config.icon}
      <span>{config.label}</span>
    </Badge>
  )
}
