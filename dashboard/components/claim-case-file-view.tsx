"use client"

import * as React from "react"
import Link from "next/link"
import type { ClaimCaseFile, ClaimDecisionType } from "@/lib/types/claim"
import { apiClient } from "@/lib/api/client"
import { VerificationBadge } from "@/components/verification-badge"
import { PlotStatusBadge } from "@/components/plot-status-badge"
import { ClaimStatusBadge } from "@/components/claim-status-badge"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import {
  Sparkles,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  FileSearch,
  Banknote,
  Lock,
  ArrowLeft,
  CloudRain,
  Satellite,
  User,
  ExternalLink,
  History,
  AlertTriangle,
} from "lucide-react"

interface ClaimCaseFileViewProps {
  initialCaseFile: ClaimCaseFile
}

export function ClaimCaseFileView({ initialCaseFile }: ClaimCaseFileViewProps) {
  const [caseFile, setCaseFile] = React.useState<ClaimCaseFile>(initialCaseFile)
  const [decisionReason, setDecisionReason] = React.useState("")
  const [isSubmitting, setIsSubmitting] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null)

  const { claim, farmer, plot, cover, payoutAccount } = caseFile

  const isLevelEligible =
    plot.verificationLevel === "FIELD_VERIFIED" || plot.verificationLevel === "NLA_VERIFIED"
  const isStatusEligible = plot.status === "ACTIVE"
  const isEligible = isLevelEligible && isStatusEligible

  const handleDecision = async (decision: ClaimDecisionType) => {
    if (!decisionReason.trim()) {
      setError("Please provide a decision rationale explaining this outcome.")
      return
    }

    try {
      setIsSubmitting(true)
      setError(null)
      await apiClient.decideClaim(claim.id, decision, decisionReason.trim())
      setSuccessMessage(`Claim #${claim.id} has been marked as ${decision}.`)
      // Refresh case file
      const updated = await apiClient.getClaimCaseFile(claim.id)
      if (updated) setCaseFile(updated)
      setDecisionReason("")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record decision")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleFieldVisitRequest = async () => {
    try {
      setIsSubmitting(true)
      setError(null)
      await apiClient.requestFieldVisit(claim.id)
      setSuccessMessage("Field visit dispatched to cooperative agronomist.")
      const updated = await apiClient.getClaimCaseFile(claim.id)
      if (updated) setCaseFile(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to request visit")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handlePayout = async () => {
    try {
      setIsSubmitting(true)
      setError(null)
      await apiClient.payoutClaim(claim.id)
      setSuccessMessage("Simulated mobile-money payout dispatched successfully.")
      const updated = await apiClient.getClaimCaseFile(claim.id)
      if (updated) setCaseFile(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to process payout")
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-5">
      {/* Back Link */}
      <Button
        variant="ghost"
        size="sm"
        nativeButton={false}
        render={<Link href="/dashboard/claims" />}
        className="gap-1.5 text-xs -ml-2 text-muted-foreground w-fit"
      >
        <ArrowLeft className="size-4" />
        Back to Claims Queue
      </Button>

      {/* Case File Header Card */}
      <div className="rounded-lg border bg-card p-4 sm:p-5 shadow-xs flex flex-col gap-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b pb-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-xl sm:text-2xl font-bold font-mono text-primary">
              #CLM-000{claim.id}
            </h1>
            <ClaimStatusBadge status={claim.status} />
            <VerificationBadge level={plot.verificationLevel} />
            <PlotStatusBadge status={plot.status} />
            <Badge variant="outline" className="font-mono text-xs">
              {cover?.seasonCode || "2026A"}
            </Badge>
          </div>

          <div className="flex items-center gap-2 text-xs">
            <span className="text-muted-foreground">Trigger Date:</span>
            <span className="font-mono font-semibold">{claim.triggerDate}</span>
          </div>
        </div>

        {/* Farmer & Plot Quick Stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-muted-foreground flex items-center gap-1">
              <User className="size-3" /> Farmer
            </span>
            <span className="font-semibold">{farmer.fullName}</span>
            <span className="font-mono text-[11px] text-muted-foreground">{farmer.phone}</span>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-muted-foreground">Plot UPI</span>
            <strong className="font-mono text-primary">{plot.upi}</strong>
            <span className="text-[11px] text-muted-foreground">
              Area: {plot.walkedAreaM2.toLocaleString()} m² ({plot.areaHa} ha)
            </span>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-muted-foreground">Cooperative</span>
            <span className="font-semibold">{farmer.cooperativeName}</span>
            <span className="text-[11px] text-muted-foreground">Southern Province</span>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-muted-foreground">Payout Target</span>
            <span className="font-mono font-semibold text-emerald-700 dark:text-emerald-400">
              {claim.amount != null ? `${claim.amount.toLocaleString()} RWF` : "Rate unconfirmed"}
            </span>
            {payoutAccount && (
              <span className="font-mono text-[10px] text-muted-foreground">
                {payoutAccount.provider}: {payoutAccount.msisdnMasked}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Success Banner */}
      {successMessage && (
        <div className="flex items-center gap-2 rounded-md border border-emerald-300 bg-emerald-50 p-3 text-xs text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300">
          <CheckCircle2 className="size-4 shrink-0 text-emerald-600" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div className="flex items-center gap-2 rounded-md border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
          <XCircle className="size-4 shrink-0 text-rose-600" />
          <span>{error}</span>
        </div>
      )}

      {/* Grid: Evidence Snapshot + Coverage & Payout Account */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* Frozen Parametric Evidence */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Sparkles className="size-4 text-primary" />
              Parametric Trigger Evidence Snapshot
            </CardTitle>
            <CardDescription className="text-xs">
              Automated weather & satellite index readings captured at the moment of trigger
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 pt-0 text-xs">
            {claim.evidenceSnapshot ? (
              <div className="grid grid-cols-2 gap-2.5">
                <div className="flex flex-col p-2.5 rounded-md border bg-muted/10 gap-1">
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <CloudRain className="size-3 text-sky-600" />
                    Consecutive Dry Spell
                  </span>
                  <span className="text-lg font-bold font-mono text-foreground">
                    {claim.evidenceSnapshot.drySpellDays} days
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Threshold: &gt; 21 consecutive days
                  </span>
                </div>

                <div className="flex flex-col p-2.5 rounded-md border bg-muted/10 gap-1">
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <CloudRain className="size-3 text-sky-600" />
                    Rainfall Deficit (CHIRPS)
                  </span>
                  <span className="text-lg font-bold font-mono text-amber-700 dark:text-amber-400">
                    -{claim.evidenceSnapshot.rainfallDeficitPct}%
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Compared to 10-year season baseline
                  </span>
                </div>

                <div className="flex flex-col p-2.5 rounded-md border bg-muted/10 gap-1">
                  <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                    <Satellite className="size-3 text-primary" />
                    NDVI Vegetation Index Drop
                  </span>
                  <span className="text-lg font-bold font-mono text-rose-700 dark:text-rose-400">
                    -{claim.evidenceSnapshot.satelliteNdviDropPct}%
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Sentinel-2 MSI Level-2A 10m
                  </span>
                </div>

                <div className="flex flex-col p-2.5 rounded-md border bg-muted/10 gap-1">
                  <span className="text-[11px] text-muted-foreground">Trigger Rule Code</span>
                  <span className="text-xs font-bold font-mono text-foreground break-all">
                    {claim.evidenceSnapshot.triggerIndex || "DROUGHT_V2"}
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Deterministic policy formula
                  </span>
                </div>
              </div>
            ) : (
              <p className="text-muted-foreground italic text-xs">
                No frozen evidence record attached.
              </p>
            )}

            <div className="rounded-md border bg-muted/10 p-2.5 text-[11px] text-muted-foreground">
              <strong className="text-foreground">Policy Rule:</strong> Satellite output is a review flag, not proof of crop. Never auto-reject or auto-approve based on satellite data alone.
            </div>
          </CardContent>
        </Card>

        {/* Insurance Coverage & Payout Target */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Banknote className="size-4 text-emerald-600" />
              Policy Cover & Payout Account
            </CardTitle>
            <CardDescription className="text-xs">
              Insured sum, calculated compensation, and farmer mobile-money verification
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 pt-0 text-xs">
            {cover ? (
              <div className="grid grid-cols-2 gap-2.5">
                <div className="flex flex-col p-2.5 rounded-md border">
                  <span className="text-[10px] text-muted-foreground">Sum Insured</span>
                  <span className="font-mono font-bold text-sm">
                    {cover.sumInsured.toLocaleString()} RWF
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Premium: {cover.premium.toLocaleString()} RWF
                  </span>
                </div>

                <div className="flex flex-col p-2.5 rounded-md border">
                  <span className="text-[10px] text-muted-foreground">Payout Percentage</span>
                  <span className="font-mono font-bold text-sm text-emerald-700 dark:text-emerald-400">
                    {claim.payoutRate != null ? `${(claim.payoutRate * 100).toFixed(0)}%` : "Pending rate"}
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    Based on drought severity scale
                  </span>
                </div>
              </div>
            ) : null}

            {/* Payout Account Status */}
            <div className="flex items-center justify-between p-3 rounded-md border bg-muted/10">
              <div className="flex items-center gap-2">
                <Banknote className="size-4 text-emerald-600 shrink-0" />
                <div className="flex flex-col">
                  <span className="font-semibold text-foreground">
                    {payoutAccount?.provider || "MTN_MOMO"} ({payoutAccount?.msisdnMasked || "No account"})
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    {payoutAccount?.verified ? "Account Verified (OTP Confirmed)" : "Unverified Account"}
                  </span>
                </div>
              </div>
              <Badge
                variant="outline"
                className={`text-[10px] ${
                  payoutAccount?.verified
                    ? "border-emerald-300 text-emerald-800 bg-emerald-50 dark:bg-emerald-950/40"
                    : "border-rose-300 text-rose-800"
                }`}
              >
                {payoutAccount?.verified ? "VERIFIED" : "UNVERIFIED"}
              </Badge>
            </div>

            {/* Associated Plot Link */}
            <div className="flex items-center justify-between pt-2 border-t">
              <span className="text-muted-foreground text-[11px]">
                Plot: <strong className="font-mono text-foreground">{plot.upi}</strong> ({plot.name})
              </span>
              <Button
                size="sm"
                variant="outline"
                nativeButton={false}
                render={<Link href={`/dashboard/plots/${plot.id}`} />}
                className="h-7 text-[11px] gap-1"
              >
                <ExternalLink className="size-3" />
                Inspect Plot Detail
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Decision Actions Panel (Enforcing Level >= FIELD_VERIFIED & Status === ACTIVE) */}
      <Card className={isEligible ? "border-emerald-300 dark:border-emerald-800" : "border-zinc-200 dark:border-zinc-800"}>
        <CardHeader className="pb-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
            <div>
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                {isEligible ? (
                  <ShieldCheck className="size-4 text-emerald-600" />
                ) : (
                  <Lock className="size-4 text-rose-600" />
                )}
                Officer Decision & Payout Authorization
              </CardTitle>
              <CardDescription className="text-xs">
                Under platform rules, financial payouts and approval actions require level FIELD_VERIFIED or higher
              </CardDescription>
            </div>

            <Badge
              variant="outline"
              className={`text-xs font-semibold ${
                isEligible
                  ? "border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40"
                  : "border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-800 dark:bg-rose-950/40"
              }`}
            >
              {isEligible ? "ELIGIBLE FOR ACTIONS" : "ACTIONS LOCKED"}
            </Badge>
          </div>
        </CardHeader>

        <CardContent className="flex flex-col gap-4 pt-0 text-xs">
          {!isEligible && (
            <div className="flex items-start gap-2.5 rounded-md border border-rose-200 bg-rose-50/70 p-3 text-xs text-rose-900 dark:border-rose-900 dark:bg-rose-950/20 dark:text-rose-200">
              <AlertTriangle className="size-4 shrink-0 text-rose-600 mt-0.5" />
              <div>
                <strong className="font-semibold">Action Locked: </strong>
                This plot is currently at verification level{" "}
                <code className="font-mono font-semibold">{plot.verificationLevel}</code> (status:{" "}
                <code className="font-mono">{plot.status}</code>). Insurance payouts are strictly prohibited
                until a human field check is recorded by a cooperative agent.
              </div>
            </div>
          )}

          {/* Rationale Input */}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="decision-reason" className="text-xs font-semibold">
              Officer Decision Rationale <span className="text-rose-500">*</span>
            </Label>
            <Textarea
              id="decision-reason"
              value={decisionReason}
              onChange={(e) => setDecisionReason(e.target.value)}
              placeholder="Record explanation for approving, declining, or requesting an on-site field visit (mandatory for audit trail)."
              rows={3}
              className="text-xs resize-none"
              disabled={isSubmitting || !isEligible}
            />
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center gap-2 pt-2 border-t">
            {isEligible ? (
              <>
                <Button
                  size="sm"
                  onClick={() => handleDecision("APPROVED")}
                  disabled={isSubmitting || !decisionReason.trim() || claim.status === "APPROVED" || claim.status === "PAID"}
                  className="text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
                >
                  <CheckCircle2 className="size-3.5" />
                  Approve Claim
                </Button>

                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => handleDecision("DECLINED")}
                  disabled={isSubmitting || !decisionReason.trim() || claim.status === "PAID"}
                  className="text-xs gap-1.5 border-rose-300 text-rose-800 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300"
                >
                  <XCircle className="size-3.5 text-rose-600" />
                  Decline Claim
                </Button>

                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleFieldVisitRequest}
                  disabled={isSubmitting || claim.status === "FIELD_VISIT_REQUESTED" || claim.status === "PAID"}
                  className="text-xs gap-1.5 text-sky-800 border-sky-300 dark:text-sky-300 dark:border-sky-800"
                >
                  <FileSearch className="size-3.5" />
                  Request Field Visit
                </Button>

                {claim.status === "APPROVED" && (
                  <Button
                    size="sm"
                    onClick={handlePayout}
                    disabled={isSubmitting}
                    className="text-xs gap-1.5 bg-primary text-primary-foreground ml-auto"
                  >
                    <Banknote className="size-3.5" />
                    Authorize Simulated Payout ({claim.amount?.toLocaleString()} RWF)
                  </Button>
                )}
              </>
            ) : (
              <div className="flex items-center gap-2">
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <span className="inline-block">
                        <Button size="sm" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                          <Lock className="size-3" />
                          Approve Claim
                        </Button>
                      </span>
                    }
                  />
                  <TooltipContent side="top" className="max-w-xs text-xs p-2">
                    Action Disabled: Verification level must be FIELD_VERIFIED or higher.
                  </TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger
                    render={
                      <span className="inline-block">
                        <Button size="sm" variant="outline" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                          <Lock className="size-3" />
                          Decline Claim
                        </Button>
                      </span>
                    }
                  />
                  <TooltipContent side="top" className="max-w-xs text-xs p-2">
                    Action Disabled: Verification level must be FIELD_VERIFIED or higher.
                  </TooltipContent>
                </Tooltip>

                <Tooltip>
                  <TooltipTrigger
                    render={
                      <span className="inline-block">
                        <Button size="sm" variant="outline" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                          <Lock className="size-3" />
                          Authorize Payout
                        </Button>
                      </span>
                    }
                  />
                  <TooltipContent side="top" className="max-w-xs text-xs p-2">
                    Action Disabled: Payout requires level FIELD_VERIFIED and ACTIVE plot status.
                  </TooltipContent>
                </Tooltip>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Audit Trail History */}
      {caseFile.history && caseFile.history.length > 0 && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <History className="size-4 text-muted-foreground" />
              Claim Audit Trail History
            </CardTitle>
            <CardDescription className="text-xs">
              Immutable sequence of automated triggers, officer decisions, and payment events
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-0 text-xs">
            <div className="flex flex-col gap-2">
              {caseFile.history.map((entry, idx) => (
                <div
                  key={idx}
                  className="flex items-center justify-between p-2 rounded-md border bg-muted/10 font-mono text-[11px]"
                >
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-[10px]">
                      {entry.action}
                    </Badge>
                    <span className="text-muted-foreground font-sans">
                      by {entry.actorName} ({entry.actorRole})
                    </span>
                  </div>
                  <span className="text-muted-foreground text-[10px]">
                    {entry.occurredAt.replace("T", " ").slice(0, 19)}
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
