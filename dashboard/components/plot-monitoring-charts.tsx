"use client"

import * as React from "react"
import type { Plot, SatelliteReviewFlag } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ReferenceLine,
  XAxis,
  YAxis,
} from "recharts"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { STRINGS } from "@/lib/strings"
import {
  Satellite,
  CloudRain,
  AlertCircle,
  CheckCircle2,
  HelpCircle,
  Loader2,
  CloudOff,
  Info,
} from "lucide-react"

interface PlotMonitoringChartsProps {
  plot: Plot
}

const ndviChartConfig = {
  ndviMedian: {
    label: "NDVI (Vegetation Index)",
    color: "var(--color-primary)",
  },
} satisfies ChartConfig

const rainChartConfig = {
  rainfallMm: {
    label: "Daily Rainfall (mm)",
    color: "oklch(0.627 0.194 220)",
  },
} satisfies ChartConfig

export function PlotMonitoringCharts({ plot }: PlotMonitoringChartsProps) {
  const monitoringStatus = plot.monitoringStatus || "DATA_AVAILABLE"
  const ndviData = plot.ndviTimeseries || []
  const rainData = plot.rainfallDaily || []
  const summary = plot.satelliteSummary

  // 1. UI State: No Plot Boundary Recorded
  if (monitoringStatus === "NO_PLOT" || !plot.boundaryGeojson) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <Satellite className="size-4 text-primary" />
            Satellite & Rainfall Monitoring
          </CardTitle>
          <CardDescription className="text-xs">
            Multi-spectral vegetation index & weather tracking
          </CardDescription>
        </CardHeader>
        <CardContent className="flex-1 flex flex-col items-center justify-center p-8 text-center text-muted-foreground border-t border-dashed bg-muted/10 m-4 rounded-md">
          <AlertCircle className="size-10 text-muted-foreground/40 mb-2" />
          <p className="text-xs font-medium text-foreground">{STRINGS.satellite.states.registerToStart}</p>
          <p className="text-[11px] text-muted-foreground max-w-xs mt-1">
            Monitoring starts only once a walked boundary polygon is saved.
          </p>
        </CardContent>
      </Card>
    )
  }

  // 2. UI State: Job Running (Fetching Satellite Data)
  if (monitoringStatus === "FETCHING_DATA") {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <Satellite className="size-4 text-primary" />
            Satellite & Rainfall Monitoring
          </CardTitle>
          <CardDescription className="text-xs">
            Multi-spectral vegetation index & weather tracking
          </CardDescription>
        </CardHeader>
        <CardContent className="flex-1 flex flex-col items-center justify-center p-8 text-center text-muted-foreground border-t border-dashed bg-muted/10 m-4 rounded-md">
          <Loader2 className="size-10 text-primary animate-spin mb-3" />
          <p className="text-xs font-semibold text-foreground">{STRINGS.satellite.states.fetching}</p>
          <p className="text-[11px] text-muted-foreground max-w-xs mt-1">
            Backfilling historical Sentinel-2 observations from declared planting date ({plot.plantingDate}) to present.
          </p>
        </CardContent>
      </Card>
    )
  }

  // Helper for Satellite Review Flag Badge
  const renderFlagBadge = (flag: SatelliteReviewFlag) => {
    switch (flag) {
      case "CONSISTENT":
        return (
          <Badge
            variant="outline"
            className="border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 gap-1 text-xs font-medium cursor-help"
          >
            <CheckCircle2 className="size-3 text-emerald-600 dark:text-emerald-400" />
            {STRINGS.satellite.flags.CONSISTENT.label}
          </Badge>
        )
      case "INCONSISTENT":
        return (
          <Badge
            variant="outline"
            className="border-rose-300 bg-rose-50 text-rose-800 dark:border-rose-800 dark:bg-rose-950/40 dark:text-rose-300 gap-1 text-xs font-semibold cursor-help"
          >
            <AlertCircle className="size-3 text-rose-600 dark:text-rose-400" />
            {STRINGS.satellite.flags.INCONSISTENT.label}
          </Badge>
        )
      case "NOT_ENOUGH_DATA":
        return (
          <Badge
            variant="outline"
            className="border-zinc-300 bg-zinc-100 text-zinc-700 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 gap-1 text-xs font-medium cursor-help"
          >
            <HelpCircle className="size-3 text-zinc-500" />
            {STRINGS.satellite.flags.NOT_ENOUGH_DATA.label}
          </Badge>
        )
    }
  }

  // 3. UI State: No Usable Image Yet (Showing Rainfall Only)
  const isNoClearImage = monitoringStatus === "NO_CLEAR_IMAGE"

  return (
    <Card className="flex flex-col">
      <CardHeader className="pb-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Satellite className="size-4 text-primary" />
              Satellite & Rainfall Monitoring
            </CardTitle>
            <CardDescription className="text-xs">
              Sentinel-2 optical observations (10m) & CHIRPS daily precipitation — {STRINGS.satellite.cadenceNotice}
            </CardDescription>
          </div>

          {/* Satellite Summary Indicators */}
          {summary && (
            <div className="flex flex-wrap items-center gap-2">
              {/* Flag with required Tooltip */}
              <Tooltip>
                <TooltipTrigger render={renderFlagBadge(summary.flag)} />
                <TooltipContent side="top" className="max-w-xs text-xs p-2.5">
                  <div className="font-semibold">{STRINGS.satellite.flags[summary.flag].label}</div>
                  <p className="mt-1 text-muted-foreground">{STRINGS.satellite.flags[summary.flag].description}</p>
                  <p className="mt-2 text-[11px] font-medium text-amber-600 dark:text-amber-400 border-t pt-1.5">
                    {STRINGS.satellite.reviewTooltip}
                  </p>
                </TooltipContent>
              </Tooltip>

              <Badge variant="outline" className="font-mono text-xs">
                {summary.pixelsUsed} pixels used
              </Badge>

              <div className="text-[11px] text-muted-foreground">
                Latest image: <span className="font-medium text-foreground">{summary.latestImageDate}</span>
              </div>
            </div>
          )}
        </div>
      </CardHeader>

      <CardContent className="flex flex-col gap-6 pt-0">
        {/* State 3 Warning if cloudy/no image */}
        {isNoClearImage && (
          <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50/60 dark:border-amber-900 dark:bg-amber-950/20 p-3 text-xs text-amber-900 dark:text-amber-200">
            <CloudOff className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
            <div>
              <p className="font-semibold">{STRINGS.satellite.states.noImageSince(plot.latestSatelliteImageDate)}</p>
              <p className="text-[11px] text-muted-foreground dark:text-amber-300/80 mt-0.5">
                Dense cloud cover over the parcel prevents reliable optical reflectance. Rainfall tracking remains operational below.
              </p>
            </div>
          </div>
        )}

        {/* NDVI Curve Chart (Hidden or reduced in State 3) */}
        {!isNoClearImage && ndviData.length > 0 && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium flex items-center gap-1.5">
                <span className="size-2 rounded-full bg-primary" />
                NDVI Vegetation Curve (Maize Season Progression)
              </span>
              <span className="text-[11px] text-muted-foreground font-mono">
                Declared Planting: {plot.plantingDate}
              </span>
            </div>

            <div className="h-[200px] w-full rounded-md border bg-muted/10 p-2">
              <ChartContainer config={ndviChartConfig} className="h-full w-full">
                <AreaChart data={ndviData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="ndviGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="var(--color-primary)" stopOpacity={0.7} />
                      <stop offset="95%" stopColor="var(--color-primary)" stopOpacity={0.05} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid vertical={false} strokeDasharray="3 3" opacity={0.2} />
                  <XAxis
                    dataKey="date"
                    tickLine={false}
                    axisLine={false}
                    tickMargin={6}
                    minTickGap={28}
                    tickFormatter={(val) => {
                      const d = new Date(val)
                      return d.toLocaleDateString("en-US", { month: "short", day: "numeric" })
                    }}
                    className="text-[10px]"
                  />
                  <YAxis
                    domain={[0, 1]}
                    tickLine={false}
                    axisLine={false}
                    tickMargin={4}
                    className="text-[10px]"
                  />
                  {/* Planting Date Marked */}
                  {plot.plantingDate && (
                    <ReferenceLine
                      x={plot.plantingDate}
                      stroke="#f59e0b"
                      strokeDasharray="4 4"
                      label={{
                        value: "Planted",
                        position: "insideTopLeft",
                        fill: "#f59e0b",
                        fontSize: 10,
                      }}
                    />
                  )}
                  <ChartTooltip
                    content={
                      <ChartTooltipContent
                        labelFormatter={(val) => `Date: ${val}`}
                        indicator="line"
                      />
                    }
                  />
                  <Area
                    dataKey="ndviMedian"
                    type="monotone"
                    fill="url(#ndviGradient)"
                    stroke="var(--color-primary)"
                    strokeWidth={2}
                  />
                </AreaChart>
              </ChartContainer>
            </div>
          </div>
        )}

        {/* Daily Rainfall Chart */}
        {rainData.length > 0 && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium flex items-center gap-1.5 text-sky-700 dark:text-sky-300">
                <CloudRain className="size-3.5" />
                Daily Rainfall (CHIRPS / Open-Meteo)
              </span>
              <span className="text-[11px] text-muted-foreground">
                Season Total:{" "}
                <strong className="text-foreground font-mono">
                  {rainData.reduce((acc, r) => acc + r.rainfallMm, 0).toFixed(1)} mm
                </strong>
              </span>
            </div>

            <div className="h-[120px] w-full rounded-md border bg-muted/10 p-2">
              <ChartContainer config={rainChartConfig} className="h-full w-full">
                <BarChart data={rainData} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid vertical={false} strokeDasharray="3 3" opacity={0.2} />
                  <XAxis
                    dataKey="date"
                    tickLine={false}
                    axisLine={false}
                    tickMargin={4}
                    minTickGap={28}
                    tickFormatter={(val) => {
                      const d = new Date(val)
                      return d.toLocaleDateString("en-US", { month: "short", day: "numeric" })
                    }}
                    className="text-[10px]"
                  />
                  <YAxis
                    tickLine={false}
                    axisLine={false}
                    tickMargin={4}
                    className="text-[10px]"
                  />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent
                        labelFormatter={(val) => `Date: ${val}`}
                        indicator="dot"
                      />
                    }
                  />
                  <Bar dataKey="rainfallMm" fill="#0284c7" radius={[2, 2, 0, 0]} />
                </BarChart>
              </ChartContainer>
            </div>
          </div>
        )}

        {/* Satellite Ground Truth Caution */}
        <div className="flex items-start gap-2 rounded-md bg-muted/30 p-2 text-[11px] text-muted-foreground">
          <Info className="size-3.5 shrink-0 text-primary mt-0.5" />
          <span>
            {STRINGS.satellite.reviewTooltip} Satellite monitoring checks the farmer&apos;s declared planting date against expected development curves; it does not replace human field verification.
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
