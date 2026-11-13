import Link from "next/link"
import { notFound } from "next/navigation"
import { DashboardLayout } from "@/components/dashboard-layout"
import { VerificationBadge } from "@/components/verification-badge"
import { PlotStatusBadge } from "@/components/plot-status-badge"
import { PlotBoundarySvg } from "@/components/plot-boundary-svg"
import { PlotMonitoringCharts } from "@/components/plot-monitoring-charts"
import { PlotFlagsList } from "@/components/plot-flags-list"
import { PlotVerificationHistory } from "@/components/plot-verification-history"
import { PlotDocumentsList } from "@/components/plot-documents-list"
import { ClaimEligibilityCard } from "@/components/claim-eligibility-card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { apiClient } from "@/lib/api/client"
import { STRINGS } from "@/lib/strings"
import {
  ChevronLeft,
  Calendar,
  Layers,
  Sprout,
  User,
  ShieldCheck,
  ChevronRight,
} from "lucide-react"

export default async function PlotDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const plotId = parseInt(id, 10)
  if (isNaN(plotId)) notFound()

  const plot = await apiClient.getPlotById(plotId)
  if (!plot) notFound()

  // Fetch adjacent plots for quick navigation
  const { items: allPlots } = await apiClient.getPlots()
  const currentIndex = allPlots.findIndex((p) => p.id === plotId)
  const prevPlot = currentIndex > 0 ? allPlots[currentIndex - 1] : null
  const nextPlot = currentIndex < allPlots.length - 1 ? allPlots[currentIndex + 1] : null

  const isFieldVerified =
    plot.verificationLevel === "FIELD_VERIFIED" || plot.verificationLevel === "NLA_VERIFIED"

  return (
    <DashboardLayout title={`Plot ${plot.upi}`}>
      <div className="@container/main flex flex-1 flex-col gap-5 py-4 md:py-6 px-4 lg:px-6">
        {/* Navigation & Test Plot Switcher */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <Button
            variant="ghost"
            size="sm"
            nativeButton={false}
            render={<Link href="/dashboard/plots" />}
            className="gap-1 text-xs -ml-2 text-muted-foreground w-fit"
          >
            <ChevronLeft className="size-4" />
            Back to Plots Directory
          </Button>

          {/* Quick Plot Cycler (for inspecting test states) */}
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span>Inspect test plots:</span>
            {prevPlot && (
              <Button
                variant="outline"
                size="sm"
                nativeButton={false}
                render={<Link href={`/dashboard/plots/${prevPlot.id}`} />}
                className="h-7 text-[11px] px-2 gap-1"
              >
                <ChevronLeft className="size-3" />
                Plot #{prevPlot.id}
              </Button>
            )}
            <Badge variant="secondary" className="font-mono text-xs px-2 py-0.5">
              #{plot.id} of {allPlots.length}
            </Badge>
            {nextPlot && (
              <Button
                variant="outline"
                size="sm"
                nativeButton={false}
                render={<Link href={`/dashboard/plots/${nextPlot.id}`} />}
                className="h-7 text-[11px] px-2 gap-1"
              >
                Plot #{nextPlot.id}
                <ChevronRight className="size-3" />
              </Button>
            )}
          </div>
        </div>

        {/* Plot Header Card */}
        <div className="rounded-lg border bg-card p-4 shadow-xs flex flex-col gap-3">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b pb-3">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="text-2xl font-bold font-mono tracking-tight text-primary">
                {plot.upi}
              </h1>
              <VerificationBadge level={plot.verificationLevel} />
              <PlotStatusBadge status={plot.status} />
              <Badge variant="outline" className="text-xs font-mono">
                {plot.season}
              </Badge>
            </div>

            <div className="flex items-center gap-2 text-xs">
              <span className="text-muted-foreground">Crop:</span>
              <Badge variant="secondary" className="gap-1 text-xs font-normal">
                <Sprout className="size-3 text-emerald-600" />
                {plot.crop}
              </Badge>
              <span className="text-muted-foreground ml-2">Area:</span>
              <span className="font-mono font-semibold">{plot.walkedAreaM2.toLocaleString()} m²</span>
            </div>
          </div>

          {/* Farmer & Plot Metadata Row */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div className="flex flex-col gap-0.5">
              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                <User className="size-3" />
                Farmer
              </span>
              <span className="font-semibold text-foreground">{plot.farmerName}</span>
              {plot.farmerPhoneMasked && (
                <span className="font-mono text-[11px] text-muted-foreground">
                  {plot.farmerPhoneMasked}
                </span>
              )}
            </div>

            <div className="flex flex-col gap-0.5">
              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                <ShieldCheck className="size-3" />
                Relationship to Plot
              </span>
              <span className="font-semibold text-foreground">
                {STRINGS.relationships[plot.relationshipToPlot]}
              </span>
              <span className="text-[11px] text-muted-foreground">
                {plot.relationshipToPlot === "OWNER" ? "Registered Titleholder" : "Requires Consent/Lease"}
              </span>
            </div>

            <div className="flex flex-col gap-0.5">
              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                <Calendar className="size-3" />
                Planting Date
              </span>
              <span className="font-semibold text-foreground font-mono">
                {plot.plantingDate || "Not recorded"}
              </span>
              <span className="text-[11px] text-muted-foreground">Season 2026A</span>
            </div>

            <div className="flex flex-col gap-0.5">
              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                <Layers className="size-3" />
                Latest Satellite Update
              </span>
              <span className="font-semibold text-foreground font-mono">
                {plot.latestSatelliteImageDate}
              </span>
              <span className="text-[11px] text-muted-foreground">Updated weekly</span>
            </div>
          </div>
        </div>

        {/* Section 1: Walked Boundary Polygon (SVG) + Satellite Monitoring & Rainfall */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-stretch">
          <PlotBoundarySvg
            boundaryGeojson={plot.boundaryGeojson}
            plantedAreaGeojson={plot.plantedAreaGeojson}
            walkedAreaM2={plot.walkedAreaM2}
            documentAreaM2={plot.documentAreaM2}
            areaDiffPct={plot.areaDiffPct}
            status={plot.status}
            verificationLevel={plot.verificationLevel}
          />

          <PlotMonitoringCharts plot={plot} />
        </div>

        {/* Section 2: Claim Eligibility Indicator & Payout Actions */}
        <ClaimEligibilityCard plot={plot} />

        {/* Section 3: Flags, Field Verification History, and Proof Documents */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          {/* Flags */}
          <div className="lg:col-span-1">
            <PlotFlagsList flags={plot.flags} />
          </div>

          {/* Verification History */}
          <div className="lg:col-span-1">
            <PlotVerificationHistory
              verifications={plot.verifications}
              isFieldVerified={isFieldVerified}
            />
          </div>

          {/* Proof Documents */}
          <div className="lg:col-span-1">
            <PlotDocumentsList plotId={plot.id} documents={plot.documents} />
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
