"use client"

import * as React from "react"
import Link from "next/link"
import type { Plot, PlotFlag } from "@/lib/types/plot"
import { DualBoundarySvg } from "@/components/dual-boundary-svg"
import { VerificationBadge } from "@/components/verification-badge"
import { PlotStatusBadge } from "@/components/plot-status-badge"
import { FlagResolveDialog } from "@/components/flag-resolve-dialog"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { STRINGS } from "@/lib/strings"
import {
  AlertOctagon,
  ArrowRight,
  Calendar,
  CheckCircle2,
  ExternalLink,
  FileText,
  Lock,
  Maximize2,
  ShieldAlert,
  ShieldCheck,
  User,
} from "lucide-react"

interface DisputeComparisonViewProps {
  plotA: Plot
  plotB: Plot
  onPlotUpdated?: (updatedPlot: Plot) => void
}

export function DisputeComparisonView({
  plotA,
  plotB,
  onPlotUpdated,
}: DisputeComparisonViewProps) {
  const [resolvingFlag, setResolvingFlag] = React.useState<{
    plotId: number
    upi: string
    flag: PlotFlag
  } | null>(null)

  const flagA = plotA.flags.find((f) => f.type === "OVERLAP" && !f.resolvedAt) || plotA.flags[0]
  const flagB = plotB.flags.find((f) => f.type === "OVERLAP" && !f.resolvedAt) || plotB.flags[0]

  const handleOpenRestrictedDoc = (plotId: number, docId: number) => {
    const apiPrefix = process.env.NEXT_PUBLIC_API_PREFIX || "/api/v1"
    const url = `${apiPrefix}/plots/${plotId}/documents/${docId}/file`
    window.open(url, "_blank", "noopener,noreferrer")
  }

  return (
    <div className="flex flex-col gap-5">
      {/* Dispute Alert Banner */}
      <div className="flex items-start gap-3 rounded-lg border border-rose-300 bg-rose-50/80 p-4 text-xs text-rose-950 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-200">
        <AlertOctagon className="size-5 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
        <div className="flex-1 flex flex-col gap-1">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-semibold text-sm text-rose-900 dark:text-rose-100">
              Active Boundary Dispute — Overlapping GPS Claims
            </h3>
            <Badge variant="destructive" className="font-mono text-[10px]">
              PAYOUTS FROZEN
            </Badge>
          </div>
          <p className="leading-relaxed">
            Claims on UPI <strong className="font-mono">{plotA.upi}</strong> and UPI{" "}
            <strong className="font-mono">{plotB.upi}</strong> intersect by an estimated 28%
            in Season 2026A. Under non-negotiable insurance rules, neither claim can be auto-resolved;
            both sides must be reviewed against their uploaded titles and investigated with cooperative agronomists.
          </p>
        </div>
      </div>

      {/* Dual Vector Boundary Projection */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-sm font-semibold flex items-center gap-2">
                <Maximize2 className="size-4 text-primary" />
                Dual Walked Boundary Vector Projection
              </CardTitle>
              <CardDescription className="text-xs">
                Overlapping GPS walk vectors plotted on a unified local coordinate grid (pure SVG, no external tile leaks)
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-0">
          <DualBoundarySvg
            plotA={{
              id: plotA.id,
              upi: plotA.upi,
              farmerName: plotA.farmerName,
              boundaryGeojson: plotA.boundaryGeojson,
              walkedAreaM2: plotA.walkedAreaM2,
            }}
            plotB={{
              id: plotB.id,
              upi: plotB.upi,
              farmerName: plotB.farmerName,
              boundaryGeojson: plotB.boundaryGeojson,
              walkedAreaM2: plotB.walkedAreaM2,
            }}
            overlapPct={28}
          />
        </CardContent>
      </Card>

      {/* Side-by-Side Comparison Columns */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {/* Column A */}
        <Card className="border-blue-200 dark:border-blue-900/60 shadow-xs flex flex-col justify-between">
          <div>
            <CardHeader className="bg-blue-50/50 dark:bg-blue-950/20 border-b pb-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Badge className="bg-blue-600 text-white hover:bg-blue-700 text-xs font-mono">
                    Claim A
                  </Badge>
                  <h3 className="font-bold font-mono text-base">{plotA.upi}</h3>
                </div>
                <div className="flex items-center gap-1.5">
                  <VerificationBadge level={plotA.verificationLevel} />
                  <PlotStatusBadge status={plotA.status} />
                </div>
              </div>
            </CardHeader>

            <CardContent className="flex flex-col gap-4 p-4 text-xs">
              {/* Farmer Profile */}
              <div className="grid grid-cols-2 gap-2 p-2.5 rounded-md border bg-muted/10">
                <div className="flex flex-col">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <User className="size-3" /> Farmer
                  </span>
                  <span className="font-semibold text-foreground">{plotA.farmerName}</span>
                  <span className="font-mono text-[10px] text-muted-foreground">{plotA.farmerPhoneMasked}</span>
                </div>
                <div className="flex flex-col">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <ShieldCheck className="size-3" /> Relationship
                  </span>
                  <span className="font-semibold text-foreground">
                    {STRINGS.relationships[plotA.relationshipToPlot]}
                  </span>
                  <span className="text-[10px] text-muted-foreground">Declared Titleholder</span>
                </div>
              </div>

              {/* Area & Planting */}
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="flex flex-col p-2 rounded-md border">
                  <span className="text-[10px] text-muted-foreground">Walked Area</span>
                  <span className="font-mono font-semibold">{plotA.walkedAreaM2.toLocaleString()} m²</span>
                  <span className="text-[10px] text-muted-foreground">Doc: {plotA.documentAreaM2?.toLocaleString() ?? "N/A"} m²</span>
                </div>
                <div className="flex flex-col p-2 rounded-md border">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <Calendar className="size-3" /> Declared Planting
                  </span>
                  <span className="font-mono font-semibold">{plotA.plantingDate}</span>
                  <span className="text-[10px] text-muted-foreground">Season {plotA.season}</span>
                </div>
              </div>

              {/* Uploaded Documents */}
              <div className="flex flex-col gap-2">
                <span className="font-semibold text-xs flex items-center justify-between">
                  <span>Uploaded Proof Documents ({plotA.documents?.length || 0})</span>
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <Lock className="size-3 text-primary" /> Restricted
                  </span>
                </span>

                {plotA.documents && plotA.documents.length > 0 ? (
                  <div className="flex flex-col gap-2">
                    {plotA.documents.map((doc) => (
                      <div
                        key={doc.id}
                        className="flex items-center justify-between p-2 rounded-md border bg-card text-xs shadow-2xs"
                      >
                        <div className="flex items-center gap-2">
                          <FileText className="size-4 text-primary shrink-0" />
                          <div className="flex flex-col">
                            <span className="font-semibold font-mono text-[11px]">{doc.type}</span>
                            <span className="text-[10px] text-muted-foreground">
                              Owner on doc: {doc.extractedOwnerName || "Not extracted"}
                            </span>
                          </div>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleOpenRestrictedDoc(plotA.id, doc.id)}
                          className="h-7 text-[11px] gap-1"
                        >
                          <ExternalLink className="size-3" />
                          Inspect
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-[11px] text-muted-foreground italic">No proof documents attached.</p>
                )}
              </div>

              {/* Open Flag */}
              {flagA && (
                <div className="flex flex-col gap-1 rounded-md border border-amber-200 bg-amber-50/60 p-2.5 dark:border-amber-900 dark:bg-amber-950/20">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-amber-900 dark:text-amber-200 text-[11px] flex items-center gap-1">
                      <ShieldAlert className="size-3.5 text-amber-600" />
                      {flagA.type}
                    </span>
                    <span className="text-[10px] font-mono text-muted-foreground">
                      Flag #{flagA.id}
                    </span>
                  </div>
                  <p className="text-[11px] text-amber-800 dark:text-amber-300 leading-snug">
                    {flagA.details}
                  </p>
                </div>
              )}
            </CardContent>
          </div>

          <div className="p-4 pt-0 border-t mt-4 flex items-center justify-between gap-2">
            <Button
              size="sm"
              variant="outline"
              nativeButton={false}
              render={<Link href={`/dashboard/plots/${plotA.id}`} />}
              className="text-xs gap-1"
            >
              Full Plot Detail
              <ArrowRight className="size-3" />
            </Button>

            {flagA && !flagA.resolvedAt ? (
              <Button
                size="sm"
                onClick={() =>
                  setResolvingFlag({
                    plotId: plotA.id,
                    upi: plotA.upi,
                    flag: flagA,
                  })
                }
                className="text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <CheckCircle2 className="size-3.5" />
                Resolve Claim A Flag
              </Button>
            ) : (
              <Badge variant="outline" className="text-emerald-700 border-emerald-300 text-xs">
                Resolved
              </Badge>
            )}
          </div>
        </Card>

        {/* Column B */}
        <Card className="border-rose-200 dark:border-rose-900/60 shadow-xs flex flex-col justify-between">
          <div>
            <CardHeader className="bg-rose-50/50 dark:bg-rose-950/20 border-b pb-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Badge className="bg-rose-600 text-white hover:bg-rose-700 text-xs font-mono">
                    Claim B
                  </Badge>
                  <h3 className="font-bold font-mono text-base">{plotB.upi}</h3>
                </div>
                <div className="flex items-center gap-1.5">
                  <VerificationBadge level={plotB.verificationLevel} />
                  <PlotStatusBadge status={plotB.status} />
                </div>
              </div>
            </CardHeader>

            <CardContent className="flex flex-col gap-4 p-4 text-xs">
              {/* Farmer Profile */}
              <div className="grid grid-cols-2 gap-2 p-2.5 rounded-md border bg-muted/10">
                <div className="flex flex-col">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <User className="size-3" /> Farmer
                  </span>
                  <span className="font-semibold text-foreground">{plotB.farmerName}</span>
                  <span className="font-mono text-[10px] text-muted-foreground">{plotB.farmerPhoneMasked}</span>
                </div>
                <div className="flex flex-col">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <ShieldCheck className="size-3" /> Relationship
                  </span>
                  <span className="font-semibold text-foreground">
                    {STRINGS.relationships[plotB.relationshipToPlot]}
                  </span>
                  <span className="text-[10px] text-muted-foreground">Lease Agreement Provided</span>
                </div>
              </div>

              {/* Area & Planting */}
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="flex flex-col p-2 rounded-md border">
                  <span className="text-[10px] text-muted-foreground">Walked Area</span>
                  <span className="font-mono font-semibold">{plotB.walkedAreaM2.toLocaleString()} m²</span>
                  <span className="text-[10px] text-muted-foreground">Doc: {plotB.documentAreaM2?.toLocaleString() ?? "N/A"} m²</span>
                </div>
                <div className="flex flex-col p-2 rounded-md border">
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <Calendar className="size-3" /> Declared Planting
                  </span>
                  <span className="font-mono font-semibold">{plotB.plantingDate}</span>
                  <span className="text-[10px] text-muted-foreground">Season {plotB.season}</span>
                </div>
              </div>

              {/* Uploaded Documents */}
              <div className="flex flex-col gap-2">
                <span className="font-semibold text-xs flex items-center justify-between">
                  <span>Uploaded Proof Documents ({plotB.documents?.length || 0})</span>
                  <span className="text-[10px] text-muted-foreground flex items-center gap-1">
                    <Lock className="size-3 text-primary" /> Restricted
                  </span>
                </span>

                {plotB.documents && plotB.documents.length > 0 ? (
                  <div className="flex flex-col gap-2">
                    {plotB.documents.map((doc) => (
                      <div
                        key={doc.id}
                        className="flex items-center justify-between p-2 rounded-md border bg-card text-xs shadow-2xs"
                      >
                        <div className="flex items-center gap-2">
                          <FileText className="size-4 text-primary shrink-0" />
                          <div className="flex flex-col">
                            <span className="font-semibold font-mono text-[11px]">{doc.type}</span>
                            <span className="text-[10px] text-muted-foreground">
                              Owner on doc: {doc.extractedOwnerName || "Not extracted"}
                            </span>
                          </div>
                        </div>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleOpenRestrictedDoc(plotB.id, doc.id)}
                          className="h-7 text-[11px] gap-1"
                        >
                          <ExternalLink className="size-3" />
                          Inspect
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-[11px] text-muted-foreground italic">No proof documents attached.</p>
                )}
              </div>

              {/* Open Flag */}
              {flagB && (
                <div className="flex flex-col gap-1 rounded-md border border-amber-200 bg-amber-50/60 p-2.5 dark:border-amber-900 dark:bg-amber-950/20">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-amber-900 dark:text-amber-200 text-[11px] flex items-center gap-1">
                      <ShieldAlert className="size-3.5 text-amber-600" />
                      {flagB.type}
                    </span>
                    <span className="text-[10px] font-mono text-muted-foreground">
                      Flag #{flagB.id}
                    </span>
                  </div>
                  <p className="text-[11px] text-amber-800 dark:text-amber-300 leading-snug">
                    {flagB.details}
                  </p>
                </div>
              )}
            </CardContent>
          </div>

          <div className="p-4 pt-0 border-t mt-4 flex items-center justify-between gap-2">
            <Button
              size="sm"
              variant="outline"
              nativeButton={false}
              render={<Link href={`/dashboard/plots/${plotB.id}`} />}
              className="text-xs gap-1"
            >
              Full Plot Detail
              <ArrowRight className="size-3" />
            </Button>

            {flagB && !flagB.resolvedAt ? (
              <Button
                size="sm"
                onClick={() =>
                  setResolvingFlag({
                    plotId: plotB.id,
                    upi: plotB.upi,
                    flag: flagB,
                  })
                }
                className="text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
              >
                <CheckCircle2 className="size-3.5" />
                Resolve Claim B Flag
              </Button>
            ) : (
              <Badge variant="outline" className="text-emerald-700 border-emerald-300 text-xs">
                Resolved
              </Badge>
            )}
          </div>
        </Card>
      </div>

      {/* Disallowed Spec Actions (Explicitly disabled with tooltips as instructed) */}
      <Card className="bg-muted/10">
        <CardContent className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
          <div className="flex flex-col">
            <span className="font-semibold text-foreground">
              Automated Polygon Actions Policy
            </span>
            <span className="text-[11px] text-muted-foreground">
              Under backend API constraints, automated geometry modifications are prohibited. Actions require verified human decisions.
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Tooltip>
              <TooltipTrigger
                render={
                  <span className="inline-block">
                    <Button size="sm" variant="outline" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                      <Lock className="size-3" />
                      Auto-Merge Boundaries
                    </Button>
                  </span>
                }
              />
              <TooltipContent side="top" className="max-w-xs text-xs p-2">
                Backend does not provide an automated merge endpoint. Boundary alterations require a physical corner re-walk via POST /plots/{`{id}`}/walk.
              </TooltipContent>
            </Tooltip>

            <Tooltip>
              <TooltipTrigger
                render={
                  <span className="inline-block">
                    <Button size="sm" variant="outline" disabled className="text-xs gap-1.5 opacity-50 cursor-not-allowed">
                      <Lock className="size-3" />
                      Auto-Split Overlap
                    </Button>
                  </span>
                }
              />
              <TooltipContent side="top" className="max-w-xs text-xs p-2">
                Backend does not provide an automated geometry split endpoint. Overlaps must be resolved by cooperative verifiers on-site.
              </TooltipContent>
            </Tooltip>
          </div>
        </CardContent>
      </Card>

      {/* Resolution Dialog */}
      {resolvingFlag && (
        <FlagResolveDialog
          open={Boolean(resolvingFlag)}
          onOpenChange={(open) => {
            if (!open) setResolvingFlag(null)
          }}
          plotId={resolvingFlag.plotId}
          upi={resolvingFlag.upi}
          flag={resolvingFlag.flag}
          onResolved={(updatedPlot) => {
            if (onPlotUpdated) onPlotUpdated(updatedPlot)
          }}
        />
      )}
    </div>
  )
}
