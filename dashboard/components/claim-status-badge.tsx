"use client"

import type { ClaimStatus } from "@/lib/types/claim"
import { Badge } from "@/components/ui/badge"
import {
  Clock,
  CheckCircle2,
  XCircle,
  Banknote,
  FileSearch,
  AlertCircle,
  HelpCircle,
} from "lucide-react"

interface ClaimStatusBadgeProps {
  status: ClaimStatus
  className?: string
}

export function ClaimStatusBadge({ status, className }: ClaimStatusBadgeProps) {
  switch (status) {
    case "UNDER_REVIEW":
      return (
        <Badge
          variant="outline"
          className={`border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300 gap-1 text-xs font-semibold ${className}`}
        >
          <Clock className="size-3 text-amber-600 dark:text-amber-400" />
          Under Review
        </Badge>
      )
    case "FIELD_VISIT_REQUESTED":
      return (
        <Badge
          variant="outline"
          className={`border-sky-300 bg-sky-50 text-sky-800 dark:border-sky-800 dark:bg-sky-950/40 dark:text-sky-300 gap-1 text-xs font-semibold ${className}`}
        >
          <FileSearch className="size-3 text-sky-600 dark:text-sky-400" />
          Field Visit Requested
        </Badge>
      )
    case "APPROVED":
      return (
        <Badge
          variant="outline"
          className={`border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 gap-1 text-xs font-semibold ${className}`}
        >
          <CheckCircle2 className="size-3 text-emerald-600 dark:text-emerald-400" />
          Approved (Unpaid)
        </Badge>
      )
    case "PAID":
      return (
        <Badge
          variant="outline"
          className={`border-emerald-500 bg-emerald-600 text-white dark:border-emerald-500 dark:bg-emerald-700 gap-1 text-xs font-bold ${className}`}
        >
          <Banknote className="size-3" />
          Simulated Paid
        </Badge>
      )
    case "DECLINED":
      return (
        <Badge
          variant="outline"
          className={`border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300 gap-1 text-xs font-semibold ${className}`}
        >
          <XCircle className="size-3 text-rose-600 dark:text-rose-400" />
          Declined
        </Badge>
      )
    case "REVIEW_REQUESTED":
      return (
        <Badge
          variant="outline"
          className={`border-purple-300 bg-purple-50 text-purple-800 dark:border-purple-800 dark:bg-purple-950/40 dark:text-purple-300 gap-1 text-xs font-semibold ${className}`}
        >
          <AlertCircle className="size-3 text-purple-600 dark:text-purple-400" />
          Farmer Review Requested
        </Badge>
      )
    case "FINAL":
      return (
        <Badge
          variant="outline"
          className={`border-zinc-300 bg-zinc-100 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 gap-1 text-xs font-semibold ${className}`}
        >
          <CheckCircle2 className="size-3" />
          Final Decision
        </Badge>
      )
    default:
      return (
        <Badge variant="outline" className={`gap-1 text-xs ${className}`}>
          <HelpCircle className="size-3" />
          {status}
        </Badge>
      )
  }
}
