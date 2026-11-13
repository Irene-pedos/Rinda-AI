import Link from "next/link"
import { DashboardLayout } from "@/components/dashboard-layout"
import { SectionCards } from "@/components/section-cards"
import { ChartAreaInteractive } from "@/components/chart-area-interactive"
import { VerificationInfoPanel } from "@/components/verification-info-panel"
import { PlotsTable } from "@/components/plots-table"
import { Button } from "@/components/ui/button"
import { apiClient } from "@/lib/api/client"
import { ArrowRight, Layers } from "lucide-react"

export default async function OverviewPage() {
  const stats = await apiClient.getOverviewStats()
  const { items: plots } = await apiClient.getPlots()

  return (
    <DashboardLayout title="Overview">
      <div className="@container/main flex flex-1 flex-col gap-2">
        <div className="flex flex-col gap-6 py-4 md:py-6">
          {/* Section Cards (Total, Flagged, Disputed, Awaiting Field Visit, Breakdown by Level) */}
          <SectionCards stats={stats} />

          {/* Verification Hierarchy & Presence Notice */}
          <div className="px-4 lg:px-6">
            <VerificationInfoPanel />
          </div>

          {/* Plots Registered Over Time Chart */}
          <div className="px-4 lg:px-6">
            <ChartAreaInteractive />
          </div>

          {/* Quick Plots Preview */}
          <div className="px-4 lg:px-6 flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-semibold tracking-tight flex items-center gap-2">
                  <Layers className="size-4 text-primary" />
                  Registered Plots
                </h2>
                <p className="text-xs text-muted-foreground">
                  Latest registered maize plots requiring insurer monitoring or verification
                </p>
              </div>
              <Button
                variant="outline"
                size="sm"
                render={<Link href="/dashboard/plots" />}
                className="text-xs gap-1"
              >
                View All Plots
                <ArrowRight className="size-3.5" />
              </Button>
            </div>
            <PlotsTable initialPlots={plots} />
          </div>
        </div>
      </div>
    </DashboardLayout>
  )
}
