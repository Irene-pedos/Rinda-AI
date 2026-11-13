"use client"

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { VerificationBadge } from "@/components/verification-badge"
import { STRINGS } from "@/lib/strings"
import { Info, AlertTriangle } from "lucide-react"

export function VerificationInfoPanel() {
  return (
    <Card className="border-primary/20 bg-primary/5">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Info className="size-4 text-primary" />
          <CardTitle className="text-sm font-semibold">
            Rinda AI Verification Hierarchy & Fraud Safeguards
          </CardTitle>
        </div>
        <CardDescription className="text-xs">
          How plots are verified and what each level unlocks on the platform
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 pt-0 text-xs">
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
          <div className="rounded-md border bg-card p-2.5">
            <div className="mb-1">
              <VerificationBadge level="SELF_DECLARED" showTooltip={false} size="sm" />
            </div>
            <p className="text-[11px] text-muted-foreground leading-snug">
              {STRINGS.verificationLevels.SELF_DECLARED.description}
            </p>
          </div>

          <div className="rounded-md border bg-card p-2.5">
            <div className="mb-1">
              <VerificationBadge level="DOCUMENT_SUPPORTED" showTooltip={false} size="sm" />
            </div>
            <p className="text-[11px] text-muted-foreground leading-snug">
              {STRINGS.verificationLevels.DOCUMENT_SUPPORTED.description}
            </p>
          </div>

          <div className="rounded-md border border-emerald-300 dark:border-emerald-800 bg-emerald-50/40 dark:bg-emerald-950/20 p-2.5">
            <div className="mb-1">
              <VerificationBadge level="FIELD_VERIFIED" showTooltip={false} size="sm" />
            </div>
            <p className="text-[11px] text-muted-foreground leading-snug">
              {STRINGS.verificationLevels.FIELD_VERIFIED.description}
            </p>
          </div>

          <div className="rounded-md border border-indigo-300 dark:border-indigo-800 bg-indigo-50/40 dark:bg-indigo-950/20 p-2.5">
            <div className="mb-1">
              <VerificationBadge level="NLA_VERIFIED" showTooltip={false} size="sm" />
            </div>
            <p className="text-[11px] text-muted-foreground leading-snug">
              {STRINGS.verificationLevels.NLA_VERIFIED.description}
            </p>
          </div>
        </div>

        <div className="flex items-start gap-2 rounded-md bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800 p-2.5 text-amber-900 dark:text-amber-200">
          <AlertTriangle className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
          <div className="text-[11px] leading-relaxed">
            <span className="font-semibold">Critical Rule: </span>
            {STRINGS.verificationLevels.presenceNotice}{" "}
            <span className="text-muted-foreground dark:text-amber-300/80">
              A neighbor with a similar parcel size in the same administrative cell could pass automatic checks;
              physical confirmation with a geotagged photo prevents fraudulent enrollment.
            </span>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
