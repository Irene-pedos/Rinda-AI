"use client"

import * as React from "react"
import { Area, AreaChart, CartesianGrid, XAxis } from "recharts"

import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export const description = "Plots registered over time"

// Season 2026A registration progression (maize planting and plot registrations)
const chartData = [
  { date: "2026-09-01", registeredPlots: 8, fieldVerified: 0 },
  { date: "2026-09-08", registeredPlots: 19, fieldVerified: 2 },
  { date: "2026-09-15", registeredPlots: 34, fieldVerified: 8 },
  { date: "2026-09-22", registeredPlots: 58, fieldVerified: 16 },
  { date: "2026-09-29", registeredPlots: 79, fieldVerified: 29 },
  { date: "2026-10-06", registeredPlots: 95, fieldVerified: 45 },
  { date: "2026-10-13", registeredPlots: 110, fieldVerified: 62 },
  { date: "2026-10-20", registeredPlots: 118, fieldVerified: 74 },
  { date: "2026-10-27", registeredPlots: 124, fieldVerified: 81 },
  { date: "2026-11-03", registeredPlots: 126, fieldVerified: 85 },
  { date: "2026-11-10", registeredPlots: 128, fieldVerified: 87 },
]

const chartConfig = {
  registeredPlots: {
    label: "Total Registered Plots",
    color: "var(--color-primary)",
  },
  fieldVerified: {
    label: "Field Verified",
    color: "var(--color-chart-2)",
  },
} satisfies ChartConfig

export function ChartAreaInteractive() {
  const [timeRange, setTimeRange] = React.useState("season-2026a")

  return (
    <Card className="@container/card">
      <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <CardTitle className="text-lg font-semibold tracking-tight">
            Plot Registrations Over Time
          </CardTitle>
          <CardDescription className="text-xs">
            Cumulative maize plot registrations and verification progress (updated weekly)
          </CardDescription>
        </div>
        <CardAction>
          <Select value={timeRange} onValueChange={(val) => { if (val) setTimeRange(val) }}>
            <SelectTrigger className="w-40 text-xs">
              <SelectValue placeholder="Select season" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="season-2026a">Season 2026A</SelectItem>
              <SelectItem value="last-30-days">Last 30 Days</SelectItem>
            </SelectContent>
          </Select>
        </CardAction>
      </CardHeader>
      <CardContent className="px-2 pt-4 sm:px-6">
        <ChartContainer
          config={chartConfig}
          className="aspect-auto h-[250px] w-full"
        >
          <AreaChart data={chartData}>
            <defs>
              <linearGradient id="fillRegistered" x1="0" y1="0" x2="0" y2="1">
                <stop
                  offset="5%"
                  stopColor="var(--color-primary)"
                  stopOpacity={0.8}
                />
                <stop
                  offset="95%"
                  stopColor="var(--color-primary)"
                  stopOpacity={0.1}
                />
              </linearGradient>
              <linearGradient id="fillVerified" x1="0" y1="0" x2="0" y2="1">
                <stop
                  offset="5%"
                  stopColor="var(--color-chart-2)"
                  stopOpacity={0.8}
                />
                <stop
                  offset="95%"
                  stopColor="var(--color-chart-2)"
                  stopOpacity={0.1}
                />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" opacity={0.3} />
            <XAxis
              dataKey="date"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={32}
              tickFormatter={(value) => {
                const date = new Date(value)
                return date.toLocaleDateString("en-US", {
                  month: "short",
                  day: "numeric",
                })
              }}
            />
            <ChartTooltip
              cursor={false}
              content={
                <ChartTooltipContent
                  labelFormatter={(value) => {
                    return new Date(value).toLocaleDateString("en-US", {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                    })
                  }}
                  indicator="dot"
                />
              }
            />
            <Area
              dataKey="fieldVerified"
              type="natural"
              fill="url(#fillVerified)"
              stroke="var(--color-chart-2)"
              stackId="a"
            />
            <Area
              dataKey="registeredPlots"
              type="natural"
              fill="url(#fillRegistered)"
              stroke="var(--color-primary)"
              stackId="b"
            />
          </AreaChart>
        </ChartContainer>
      </CardContent>
    </Card>
  )
}
