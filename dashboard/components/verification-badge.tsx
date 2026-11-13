"use client"

import * as React from "react"
import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { STRINGS } from "@/lib/strings"
import type { VerificationLevel } from "@/lib/types/plot"
import {
  FileQuestion,
  FileCheck,
  UserCheck,
  ShieldCheck,
} from "lucide-react"

interface VerificationBadgeProps {
  level: VerificationLevel
  showTooltip?: boolean
  className?: string
  size?: "sm" | "default"
}

export function VerificationBadge({
  level,
  showTooltip = true,
  className = "",
  size = "default",
}: VerificationBadgeProps) {
  const config = React.useMemo(() => {
    switch (level) {
      case "SELF_DECLARED":
        return {
          label: STRINGS.verificationLevels.SELF_DECLARED.label,
          description: STRINGS.verificationLevels.SELF_DECLARED.description,
          icon: <FileQuestion className="size-3 shrink-0" />,
          variant: "outline" as const,
          badgeClass:
            "border-zinc-400 bg-zinc-100/60 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800/40 dark:text-zinc-300 font-mono tracking-tight",
        }
      case "DOCUMENT_SUPPORTED":
        return {
          label: STRINGS.verificationLevels.DOCUMENT_SUPPORTED.label,
          description: STRINGS.verificationLevels.DOCUMENT_SUPPORTED.description,
          icon: <FileCheck className="size-3 shrink-0 text-sky-600 dark:text-sky-400" />,
          variant: "outline" as const,
          badgeClass:
            "border-sky-300 bg-sky-50 text-sky-800 dark:border-sky-800 dark:bg-sky-950/40 dark:text-sky-300 font-mono tracking-tight",
        }
      case "FIELD_VERIFIED":
        return {
          label: STRINGS.verificationLevels.FIELD_VERIFIED.label,
          description: STRINGS.verificationLevels.FIELD_VERIFIED.description,
          icon: <UserCheck className="size-3 shrink-0 text-emerald-600 dark:text-emerald-400" />,
          variant: "outline" as const,
          badgeClass:
            "border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 font-mono tracking-tight font-medium",
        }
      case "NLA_VERIFIED":
        return {
          label: STRINGS.verificationLevels.NLA_VERIFIED.label,
          description: STRINGS.verificationLevels.NLA_VERIFIED.description,
          icon: <ShieldCheck className="size-3 shrink-0 text-indigo-600 dark:text-indigo-400" />,
          variant: "outline" as const,
          badgeClass:
            "border-indigo-300 bg-indigo-50 text-indigo-800 dark:border-indigo-800 dark:bg-indigo-950/40 dark:text-indigo-300 font-mono tracking-tight font-semibold",
        }
    }
  }, [level])

  const badgeElement = (
    <Badge
      variant={config.variant}
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-md ${
        size === "sm" ? "px-1.5 py-0.5 text-[10px]" : "px-2 py-0.5 text-xs"
      } ${config.badgeClass} ${className}`}
    >
      {config.icon}
      <span>{config.label}</span>
    </Badge>
  )

  if (!showTooltip) {
    return badgeElement
  }

  return (
    <Tooltip>
      <TooltipTrigger render={badgeElement} />
      <TooltipContent side="top" className="max-w-xs text-xs leading-relaxed p-2.5">
        <div className="font-semibold">{config.label}</div>
        <p className="mt-1 text-muted-foreground">{config.description}</p>
        <p className="mt-2 text-[11px] text-amber-600 dark:text-amber-400 border-t pt-1.5">
          {STRINGS.verificationLevels.presenceNotice}
        </p>
      </TooltipContent>
    </Tooltip>
  )
}
