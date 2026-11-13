"use client"

import * as React from "react"
import Link from "next/link"
import type { Plot, PlotFlag } from "@/lib/types/plot"
import { VerificationBadge } from "@/components/verification-badge"
import { PlotStatusBadge } from "@/components/plot-status-badge"
import { FlagResolveDialog } from "@/components/flag-resolve-dialog"
import { DisputeComparisonView } from "@/components/dispute-comparison-view"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { STRINGS } from "@/lib/strings"
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Filter,
  Info,
  RotateCcw,
  Search,
  ShieldAlert,
  Split,
} from "lucide-react"

interface ReviewQueueViewProps {
  initialPlots: Plot[]
}

export function ReviewQueueView({ initialPlots }: ReviewQueueViewProps) {
  const [plots, setPlots] = React.useState<Plot[]>(initialPlots)
  const [search, setSearch] = React.useState("")
  const [flagTypeFilter, setFlagTypeFilter] = React.useState<string>("ALL")
  const [activeTab, setActiveTab] = React.useState<"all" | "disputes" | "flags">("all")
  const [resolvingFlag, setResolvingFlag] = React.useState<{
    plotId: number
    upi: string
    flag: PlotFlag
  } | null>(null)
  const [selectedDisputePlots, setSelectedDisputePlots] = React.useState<{
    plotA: Plot
    plotB: Plot
  } | null>(null)

  // Disputed plots
  const disputedPlots = plots.filter((p) => p.status === "DISPUTED")

  // Standard flagged plots
  const flaggedPlots = plots.filter(
    (p) => p.status === "FLAGGED" || (p.status !== "DISPUTED" && p.flags.some((f) => !f.resolvedAt))
  )

  // Filter logic
  const filterPlots = (list: Plot[]) => {
    return list.filter((plot) => {
      // Search
      const matchesSearch =
        !search.trim() ||
        plot.upi.toLowerCase().includes(search.toLowerCase().trim()) ||
        plot.farmerName.toLowerCase().includes(search.toLowerCase().trim()) ||
        plot.flags.some((f) => f.details.toLowerCase().includes(search.toLowerCase().trim()))

      // Flag type
      const matchesType =
        flagTypeFilter === "ALL" ||
        plot.flags.some((f) => f.type === flagTypeFilter && !f.resolvedAt)

      return matchesSearch && matchesType
    })
  }

  const handlePlotUpdated = (updatedPlot: Plot) => {
    setPlots((prev) =>
      prev.map((p) => (p.id === updatedPlot.id ? updatedPlot : p))
    )
    // Also update selected dispute plots if open
    if (selectedDisputePlots) {
      if (selectedDisputePlots.plotA.id === updatedPlot.id) {
        setSelectedDisputePlots({
          ...selectedDisputePlots,
          plotA: updatedPlot,
        })
      } else if (selectedDisputePlots.plotB.id === updatedPlot.id) {
        setSelectedDisputePlots({
          ...selectedDisputePlots,
          plotB: updatedPlot,
        })
      }
    }
  }

  // Pre-select mock dispute pair (Plot 3 & 4) if present
  const disputeA = plots.find((p) => p.id === 3)
  const disputeB = plots.find((p) => p.id === 4)

  const openDisputeModal = () => {
    if (disputeA && disputeB) {
      setSelectedDisputePlots({ plotA: disputeA, plotB: disputeB })
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header Banner */}
      <div className="rounded-lg border bg-card p-4 sm:p-6 shadow-xs flex flex-col gap-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
                <ShieldAlert className="size-6 text-amber-600 dark:text-amber-400" />
                Review & Dispute Queue
              </h1>
              <Badge variant="outline" className="font-mono text-xs">
                Season 2026A
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground max-w-2xl">
              Consolidated triage queue for automatic check anomalies, boundary overlaps, and tenure inconsistencies.
              Under platform rules, resolving a flag requires investigation note and cooperative agent confirmation.
            </p>
          </div>

          <div className="flex items-center gap-2">
            {disputeA && disputeB && (
              <Button
                size="sm"
                onClick={openDisputeModal}
                className="gap-1.5 text-xs bg-rose-600 hover:bg-rose-700 text-white"
              >
                <Split className="size-3.5" />
                Inspect Active Dispute (#{disputeA.id} vs #{disputeB.id})
              </Button>
            )}
          </div>
        </div>

        {/* Presence Notice Callout */}
        <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50/70 dark:border-amber-900 dark:bg-amber-950/20 p-3 text-xs text-amber-900 dark:text-amber-200">
          <Info className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
          <div className="leading-relaxed">
            <strong className="font-semibold">Verification Rule: </strong>
            {STRINGS.verificationLevels.presenceNotice} Payouts strictly require level{" "}
            <code className="font-mono font-semibold">FIELD_VERIFIED</code> and active status.
            Do not auto-resolve disputed or flagged claims.
          </div>
        </div>
      </div>

      {/* Disputed Claim Side-by-Side Modal / View (if selected) */}
      {selectedDisputePlots && (
        <div className="rounded-xl border-2 border-rose-300 dark:border-rose-900/80 bg-card p-4 sm:p-6 shadow-md flex flex-col gap-4">
          <div className="flex items-center justify-between border-b pb-3">
            <div className="flex items-center gap-2">
              <Split className="size-5 text-rose-600" />
              <h2 className="text-base font-bold text-foreground">
                Side-by-Side Overlapping Claims Comparison
              </h2>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSelectedDisputePlots(null)}
              className="text-xs"
            >
              Close Comparison
            </Button>
          </div>

          <DisputeComparisonView
            plotA={selectedDisputePlots.plotA}
            plotB={selectedDisputePlots.plotB}
            onPlotUpdated={handlePlotUpdated}
          />
        </div>
      )}

      {/* Filter and Tab Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <Tabs
          value={activeTab}
          onValueChange={(val) => {
            if (val === "all" || val === "disputes" || val === "flags") {
              setActiveTab(val)
            }
          }}
          className="w-full sm:w-auto"
        >
          <TabsList className="grid grid-cols-3 w-full sm:w-auto">
            <TabsTrigger value="all" className="text-xs">
              All Items ({plots.length})
            </TabsTrigger>
            <TabsTrigger value="disputes" className="text-xs gap-1.5">
              Disputes ({disputedPlots.length})
            </TabsTrigger>
            <TabsTrigger value="flags" className="text-xs gap-1.5">
              Flags ({flaggedPlots.length})
            </TabsTrigger>
          </TabsList>
        </Tabs>

        {/* Search & Flag Filter */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative w-full sm:w-60">
            <Search className="absolute left-2.5 top-2.5 size-3.5 text-muted-foreground" />
            <Input
              placeholder="Search UPI, farmer, flag..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 h-8 text-xs"
            />
          </div>

          <Select
            value={flagTypeFilter}
            onValueChange={(val) => {
              if (val) setFlagTypeFilter(val)
            }}
          >
            <SelectTrigger className="w-[180px] h-8 text-xs">
              <Filter className="size-3 text-muted-foreground mr-1" />
              <SelectValue placeholder="Filter flag type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Flag Types</SelectItem>
              <SelectItem value="OVERLAP">OVERLAP (Boundary)</SelectItem>
              <SelectItem value="AREA_MISMATCH">AREA_MISMATCH (&gt;15%)</SelectItem>
              <SelectItem value="SATELLITE_INCONSISTENT">SATELLITE_INCONSISTENT</SelectItem>
              <SelectItem value="MOCK_LOCATION">MOCK_LOCATION</SelectItem>
              <SelectItem value="OUTSIDE_ADMIN_AREA">OUTSIDE_ADMIN_AREA</SelectItem>
              <SelectItem value="DUPLICATE_UPI">DUPLICATE_UPI</SelectItem>
              <SelectItem value="NON_OWNER">NON_OWNER</SelectItem>
            </SelectContent>
          </Select>

          {(search || flagTypeFilter !== "ALL") && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearch("")
                setFlagTypeFilter("ALL")
              }}
              className="h-8 px-2 text-xs text-muted-foreground"
            >
              <RotateCcw className="size-3 mr-1" />
              Reset
            </Button>
          )}
        </div>
      </div>

      {/* Review Queue Items List */}
      {(() => {
        const displayList =
          activeTab === "disputes"
            ? filterPlots(disputedPlots)
            : activeTab === "flags"
            ? filterPlots(flaggedPlots)
            : filterPlots(plots)

        if (displayList.length === 0) {
          return (
            <div className="rounded-lg border border-dashed p-12 text-center flex flex-col items-center justify-center bg-card">
              <CheckCircle2 className="size-10 text-emerald-500 mb-2" />
              <h3 className="font-semibold text-sm">No Pending Items In Queue</h3>
              <p className="text-xs text-muted-foreground max-w-sm mt-1">
                All validation checks have passed or existing review flags have been cleared by verifiers.
              </p>
            </div>
          )
        }

        return (
          <div className="flex flex-col gap-3">
            {displayList.map((plot) => {
              const openFlags = plot.flags.filter((f) => !f.resolvedAt)
              const resolvedFlags = plot.flags.filter((f) => Boolean(f.resolvedAt))
              const isDisputed = plot.status === "DISPUTED"

              return (
                <Card
                  key={plot.id}
                  className={`transition-colors shadow-2xs ${
                    isDisputed
                      ? "border-rose-300 dark:border-rose-900 bg-rose-50/20 dark:bg-rose-950/10"
                      : "border-border hover:border-primary/40"
                  }`}
                >
                  <CardContent className="p-4 flex flex-col gap-3">
                    {/* Header Row */}
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b pb-3">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <span className="font-mono font-bold text-base text-primary">
                          {plot.upi}
                        </span>
                        <VerificationBadge level={plot.verificationLevel} />
                        <PlotStatusBadge status={plot.status} />
                        <span className="text-xs text-muted-foreground">
                          {STRINGS.relationships[plot.relationshipToPlot]}
                        </span>
                      </div>

                      <div className="flex items-center gap-2 text-xs">
                        <span className="text-muted-foreground">Farmer:</span>
                        <strong className="text-foreground">{plot.farmerName}</strong>
                        {plot.farmerPhoneMasked && (
                          <span className="font-mono text-[11px] text-muted-foreground">
                            ({plot.farmerPhoneMasked})
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Middle Details Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
                      {/* Open Flags */}
                      <div className="md:col-span-2 flex flex-col gap-2">
                        <span className="font-semibold text-xs flex items-center gap-1.5">
                          <AlertTriangle className="size-3.5 text-amber-500" />
                          Open Validation Flags ({openFlags.length})
                        </span>

                        {openFlags.length > 0 ? (
                          <div className="flex flex-col gap-2">
                            {openFlags.map((flag) => (
                              <div
                                key={flag.id}
                                className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-md border bg-card p-2.5 shadow-2xs"
                              >
                                <div className="flex flex-col gap-0.5">
                                  <div className="flex items-center gap-2">
                                    <Badge
                                      variant="outline"
                                      className="font-mono text-[10px] border-amber-300 text-amber-900 bg-amber-50 dark:border-amber-800 dark:text-amber-200 dark:bg-amber-950/40"
                                    >
                                      {flag.type}
                                    </Badge>
                                    <span className="text-[10px] text-muted-foreground">
                                      Raised {flag.createdAt.slice(0, 10)}
                                    </span>
                                  </div>
                                  <p className="text-[11px] text-foreground leading-snug">
                                    {flag.details}
                                  </p>
                                </div>

                                <Button
                                  size="sm"
                                  onClick={() =>
                                    setResolvingFlag({
                                      plotId: plot.id,
                                      upi: plot.upi,
                                      flag,
                                    })
                                  }
                                  className="text-xs gap-1 h-7 shrink-0 bg-emerald-600 hover:bg-emerald-700 text-white"
                                >
                                  <CheckCircle2 className="size-3" />
                                  Resolve Flag
                                </Button>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <div className="text-[11px] text-muted-foreground italic flex items-center gap-1">
                            <CheckCircle2 className="size-3.5 text-emerald-500" />
                            All recorded flags on this plot have been resolved.
                          </div>
                        )}

                        {/* Resolved Flags History */}
                        {resolvedFlags.length > 0 && (
                          <div className="flex items-center gap-2 text-[10px] text-muted-foreground pt-1">
                            <span>{resolvedFlags.length} resolved flag(s) in audit trail.</span>
                          </div>
                        )}
                      </div>

                      {/* Parcel Metrics & Quick Actions */}
                      <div className="flex flex-col justify-between rounded-md border bg-muted/10 p-3 text-xs gap-3">
                        <div className="flex flex-col gap-1.5">
                          <span className="text-[11px] font-semibold text-muted-foreground">
                            Parcel Metrics
                          </span>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Walked Area:</span>
                            <span className="font-mono font-semibold">
                              {plot.walkedAreaM2.toLocaleString()} m²
                            </span>
                          </div>
                          <div className="flex items-center justify-between">
                            <span className="text-muted-foreground">Document Area:</span>
                            <span className="font-mono">
                              {plot.documentAreaM2?.toLocaleString() ?? "N/A"} m²
                            </span>
                          </div>
                          {plot.areaDiffPct != null && (
                            <div className="flex items-center justify-between">
                              <span className="text-muted-foreground">Area Difference:</span>
                              <Badge
                                variant={plot.areaDiffPct > 15 ? "destructive" : "outline"}
                                className="font-mono text-[10px]"
                              >
                                {plot.areaDiffPct > 0 ? `+${plot.areaDiffPct}%` : `${plot.areaDiffPct}%`}
                              </Badge>
                            </div>
                          )}
                        </div>

                        <div className="flex flex-col gap-2 pt-2 border-t">
                          {isDisputed ? (
                            <Button
                              size="sm"
                              onClick={() => {
                                const targetOther =
                                  plot.id === 3 ? disputeB : plot.id === 4 ? disputeA : null
                                if (targetOther) {
                                  setSelectedDisputePlots({
                                    plotA: plot,
                                    plotB: targetOther,
                                  })
                                }
                              }}
                              className="w-full text-xs gap-1.5 bg-rose-600 hover:bg-rose-700 text-white"
                            >
                              <Split className="size-3.5" />
                              Side-by-Side Dispute
                            </Button>
                          ) : null}

                          <Button
                            size="sm"
                            variant="outline"
                            nativeButton={false}
                            render={<Link href={`/dashboard/plots/${plot.id}`} />}
                            className="w-full text-xs gap-1"
                          >
                            Inspect Detail
                            <ArrowRight className="size-3" />
                          </Button>
                        </div>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )
      })()}

      {/* Resolution Dialog for any flag */}
      {resolvingFlag && (
        <FlagResolveDialog
          open={Boolean(resolvingFlag)}
          onOpenChange={(open) => {
            if (!open) setResolvingFlag(null)
          }}
          plotId={resolvingFlag.plotId}
          upi={resolvingFlag.upi}
          flag={resolvingFlag.flag}
          onResolved={handlePlotUpdated}
        />
      )}
    </div>
  )
}
