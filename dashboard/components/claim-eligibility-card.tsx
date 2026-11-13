"use client"

import type { Plot } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import {
  ShieldCheck,
  ShieldX,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Lock,
  Sparkles,
} from "lucide-react"

interface ClaimEligibilityCardProps {
  plot: Plot
}

export function ClaimEligibilityCard({ plot }: ClaimEligibilityCardProps) {
  // Non-negotiable rule:
  // Eligible ONLY IF level is FIELD_VERIFIED or higher AND status is ACTIVE.
  const isLevelEligible =
    plot.verificationLevel === "FIELD_VERIFIED" || plot.verificationLevel === "NLA_VERIFIED"
  const isStatusEligible = plot.status === "ACTIVE"
  const isEligible = isLevelEligible && isStatusEligible

  // Formulate specific ineligibility reason
  let reason = ""
  if (!isLevelEligible && !isStatusEligible) {
    reason = `Ineligible: Verification level is ${plot.verificationLevel} (must be FIELD_VERIFIED or higher) and status is ${plot.status} (must be ACTIVE).`
  } else if (!isLevelEligible) {
    reason = `Ineligible: Plot verification level is ${plot.verificationLevel}. Under platform rules, payouts require FIELD_VERIFIED or higher (human on-site inspection with geotagged photo).`
  } else if (!isStatusEligible) {
    if (plot.status === "DISPUTED") {
      reason = "Ineligible: Boundary overlaps with another claim. Disputed status pauses all payouts and claims."
    } else if (plot.status === "FLAGGED") {
      reason = "Ineligible: Plot has open verification flags requiring verifier resolution before claims can be processed."
    } else {
      reason = `Ineligible: Plot status is ${plot.status}. Only ACTIVE plots may process claims.`
    }
  }

  return (
    <Card className={isEligible ? "border-emerald-300 dark:border-emerald-800 bg-emerald-50/20 dark:bg-emerald-950/10" : "border-zinc-200 dark:border-zinc-800"}>
      <CardHeader className="pb-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              {isEligible ? (
                <ShieldCheck className="size-4 text-emerald-600 dark:text-emerald-400" />
              ) : (
                <ShieldX className="size-4 text-rose-600 dark:text-rose-400" />
              )}
              Insurance Claim & Payout Eligibility
            </CardTitle>
            <CardDescription className="text-xs">
              Automated policy rules governing parametric insurance triggers and payouts
            </CardDescription>
          </div>

          <Badge
            variant="outline"
            className={`font-semibold text-xs gap-1.5 px-2.5 py-1 ${
              isEligible
                ? "border-emerald-400 bg-emerald-100 text-emerald-800 dark:border-emerald-700 dark:bg-emerald-950 dark:text-emerald-300"
                : "border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300"
            }`}
          >
            {isEligible ? (
              <>
                <CheckCircle2 className="size-3.5 text-emerald-600" />
                CLAIM ELIGIBLE
              </>
            ) : (
              <>
                <XCircle className="size-3.5 text-rose-600" />
                INELIGIBLE FOR PAYOUTS
              </>
            )}
          </Badge>
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-4 pt-0">
        {/* Eligibility Criteria Checklist */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
          {/* Level Criterion */}
          <div className="flex items-center justify-between p-2.5 rounded-md border bg-card">
            <div className="flex items-center gap-2">
              {isLevelEligible ? (
                <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              ) : (
                <XCircle className="size-4 text-rose-500 shrink-0" />
              )}
              <div className="flex flex-col">
                <span className="font-medium">Level ≥ FIELD_VERIFIED</span>
                <span className="text-[11px] text-muted-foreground">Current: {plot.verificationLevel}</span>
              </div>
            </div>
            <Badge variant="outline" className={`text-[10px] ${isLevelEligible ? "text-emerald-700 border-emerald-300" : "text-rose-600 border-rose-300"}`}>
              {isLevelEligible ? "Met" : "Unmet"}
            </Badge>
          </div>

          {/* Status Criterion */}
          <div className="flex items-center justify-between p-2.5 rounded-md border bg-card">
            <div className="flex items-center gap-2">
              {isStatusEligible ? (
                <CheckCircle2 className="size-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              ) : (
                <XCircle className="size-4 text-rose-500 shrink-0" />
              )}
              <div className="flex flex-col">
                <span className="font-medium">Status = ACTIVE</span>
                <span className="text-[11px] text-muted-foreground">Current: {plot.status}</span>
              </div>
            </div>
            <Badge variant="outline" className={`text-[10px] ${isStatusEligible ? "text-emerald-700 border-emerald-300" : "text-rose-600 border-rose-300"}`}>
              {isStatusEligible ? "Met" : "Unmet"}
            </Badge>
          </div>
        </div>

        {/* Reason Message when Ineligible */}
        {!isEligible && (
          <div className="flex items-start gap-2.5 rounded-md border border-rose-200 bg-rose-50/60 dark:border-rose-900 dark:bg-rose-950/20 p-3 text-xs text-rose-900 dark:text-rose-200">
            <AlertTriangle className="size-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
            <div className="leading-relaxed">
              <span className="font-semibold">Action Locked: </span>
              {reason}
            </div>
          </div>
        )}

        {/* Action Buttons (Disabled when Ineligible) */}
        <div className="flex flex-wrap items-center gap-3 pt-1 border-t">
          {isEligible ? (
            <>
              <Button size="sm" className="text-xs gap-1.5 bg-primary text-primary-foreground">
                <Sparkles className="size-3.5" />
                Review Parametric Claim
              </Button>
              <Button size="sm" variant="outline" className="text-xs gap-1.5 border-emerald-300 text-emerald-800 dark:text-emerald-300">
                Authorize Payout
              </Button>
            </>
          ) : (
            <>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <span className="inline-block">
                      <Button size="sm" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                        <Lock className="size-3.5" />
                        Review Parametric Claim
                      </Button>
                    </span>
                  }
                />
                <TooltipContent side="top" className="max-w-xs text-xs p-2.5">
                  <p className="font-semibold text-rose-600 dark:text-rose-400">Action Disabled</p>
                  <p className="mt-1 text-muted-foreground">{reason}</p>
                </TooltipContent>
              </Tooltip>

              <Tooltip>
                <TooltipTrigger
                  render={
                    <span className="inline-block">
                      <Button size="sm" variant="outline" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                        <Lock className="size-3.5" />
                        Authorize Payout
                      </Button>
                    </span>
                  }
                />
                <TooltipContent side="top" className="max-w-xs text-xs p-2.5">
                  <p className="font-semibold text-rose-600 dark:text-rose-400">Action Disabled</p>
                  <p className="mt-1 text-muted-foreground">{reason}</p>
                </TooltipContent>
              </Tooltip>
            </>
          )}

          <div className="text-[11px] text-muted-foreground ml-auto font-mono">
            Policy Code: RINDA-2026A-MZ
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
