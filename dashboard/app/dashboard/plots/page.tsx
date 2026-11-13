import { DashboardLayout } from "@/components/dashboard-layout"
import { PlotsTable } from "@/components/plots-table"
import { apiClient } from "@/lib/api/client"
import { Layers } from "lucide-react"

export default async function PlotsPage() {
  const { items: plots } = await apiClient.getPlots()

  return (
    <DashboardLayout title="Plots Directory">
      <div className="@container/main flex flex-1 flex-col gap-2">
        <div className="flex flex-col gap-4 py-4 md:py-6 px-4 lg:px-6">
          <div className="flex flex-col gap-1">
            <h1 className="text-xl font-semibold tracking-tight flex items-center gap-2">
              <Layers className="size-5 text-primary" />
              Registered Maize Plots
            </h1>
            <p className="text-xs text-muted-foreground">
              Search and filter all registered farmer plots, verify status, and inspect satellite review indicators.
            </p>
          </div>

          <PlotsTable initialPlots={plots} />
        </div>
      </div>
    </DashboardLayout>
  )
}
