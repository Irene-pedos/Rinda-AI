"use client"

import * as React from "react"
import { useRouter } from "next/navigation"
import type { Plot, VerificationLevel, PlotStatus, FlagType } from "@/lib/types/plot"
import { VerificationBadge } from "@/components/verification-badge"
import { PlotStatusBadge } from "@/components/plot-status-badge"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { STRINGS } from "@/lib/strings"
import {
  Search,
  ArrowUpDown,
  AlertTriangle,
  RotateCcw,
  ChevronRight,
  Satellite,
} from "lucide-react"

interface PlotsTableProps {
  initialPlots: Plot[]
}

export function PlotsTable({ initialPlots }: PlotsTableProps) {
  const router = useRouter()

  const [search, setSearch] = React.useState("")
  const [levelFilter, setLevelFilter] = React.useState<VerificationLevel | "ALL">("ALL")
  const [statusFilter, setStatusFilter] = React.useState<PlotStatus | "ALL">("ALL")
  const [flagTypeFilter, setFlagTypeFilter] = React.useState<FlagType | "ALL">("ALL")
  const [seasonFilter, setSeasonFilter] = React.useState<string>("ALL")
  const [sortOrder, setSortOrder] = React.useState<"asc" | "desc">("desc")

  // Filter and sort plots in-memory
  const filteredPlots = React.useMemo(() => {
    let result = [...initialPlots]

    if (search.trim()) {
      const q = search.toLowerCase().trim()
      result = result.filter(
        (p) =>
          p.upi.toLowerCase().includes(q) ||
          p.farmerName.toLowerCase().includes(q)
      )
    }

    if (levelFilter !== "ALL") {
      result = result.filter((p) => p.verificationLevel === levelFilter)
    }

    if (statusFilter !== "ALL") {
      result = result.filter((p) => p.status === statusFilter)
    }

    if (flagTypeFilter !== "ALL") {
      result = result.filter((p) =>
        p.flags.some((f) => f.type === flagTypeFilter)
      )
    }

    if (seasonFilter !== "ALL") {
      result = result.filter((p) => p.season === seasonFilter)
    }

    result.sort((a, b) => {
      const timeA = new Date(a.latestSatelliteImageDate).getTime()
      const timeB = new Date(b.latestSatelliteImageDate).getTime()
      return sortOrder === "desc" ? timeB - timeA : timeA - timeB
    })

    return result
  }, [initialPlots, search, levelFilter, statusFilter, flagTypeFilter, seasonFilter, sortOrder])

  const hasActiveFilters =
    search.trim() !== "" ||
    levelFilter !== "ALL" ||
    statusFilter !== "ALL" ||
    flagTypeFilter !== "ALL" ||
    seasonFilter !== "ALL"

  const handleResetFilters = () => {
    setSearch("")
    setLevelFilter("ALL")
    setStatusFilter("ALL")
    setFlagTypeFilter("ALL")
    setSeasonFilter("ALL")
    setSortOrder("desc")
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Controls / Filter Bar */}
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-3 shadow-xs">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
          {/* Search Box */}
          <div className="relative flex-1">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
            <Input
              placeholder="Search by UPI (e.g. 1/01/01/01/0001) or Farmer..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 text-xs"
            />
          </div>

          {/* Reset Filters */}
          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleResetFilters}
              className="text-xs gap-1.5 h-9 shrink-0 text-muted-foreground hover:text-foreground"
            >
              <RotateCcw className="size-3.5" />
              Reset Filters
            </Button>
          )}
        </div>

        {/* Filter Dropdowns */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1 text-xs">
          {/* Verification Level */}
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium text-muted-foreground">Verification Level</span>
            <Select
              value={levelFilter}
              onValueChange={(val) => {
                if (val) setLevelFilter(val as VerificationLevel | "ALL")
              }}
            >
              <SelectTrigger className="w-full text-xs h-8">
                <SelectValue placeholder="All Levels" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Levels</SelectItem>
                <SelectItem value="SELF_DECLARED">SELF_DECLARED</SelectItem>
                <SelectItem value="DOCUMENT_SUPPORTED">DOCUMENT_SUPPORTED</SelectItem>
                <SelectItem value="FIELD_VERIFIED">FIELD_VERIFIED</SelectItem>
                <SelectItem value="NLA_VERIFIED">NLA_VERIFIED</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Plot Status */}
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium text-muted-foreground">Plot Status</span>
            <Select
              value={statusFilter}
              onValueChange={(val) => {
                if (val) setStatusFilter(val as PlotStatus | "ALL")
              }}
            >
              <SelectTrigger className="w-full text-xs h-8">
                <SelectValue placeholder="All Statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Statuses</SelectItem>
                <SelectItem value="ACTIVE">ACTIVE</SelectItem>
                <SelectItem value="FLAGGED">FLAGGED</SelectItem>
                <SelectItem value="DISPUTED">DISPUTED</SelectItem>
                <SelectItem value="REJECTED">REJECTED</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Flag Type */}
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium text-muted-foreground">Flag Type</span>
            <Select
              value={flagTypeFilter}
              onValueChange={(val) => {
                if (val) setFlagTypeFilter(val as FlagType | "ALL")
              }}
            >
              <SelectTrigger className="w-full text-xs h-8">
                <SelectValue placeholder="All Flag Types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Flags</SelectItem>
                <SelectItem value="AREA_MISMATCH">Area Mismatch (&gt;15%)</SelectItem>
                <SelectItem value="OVERLAP">Boundary Overlap</SelectItem>
                <SelectItem value="OUTSIDE_ADMIN_AREA">Outside Admin Cell</SelectItem>
                <SelectItem value="SATELLITE_INCONSISTENT">Satellite Inconsistent</SelectItem>
                <SelectItem value="MOCK_LOCATION">Mock Location</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Season */}
          <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium text-muted-foreground">Season</span>
            <Select
              value={seasonFilter}
              onValueChange={(val) => {
                if (val) setSeasonFilter(val)
              }}
            >
              <SelectTrigger className="w-full text-xs h-8">
                <SelectValue placeholder="All Seasons" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Seasons</SelectItem>
                <SelectItem value="2026A">Season 2026A (Current)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      {/* Table Results Bar */}
      <div className="flex items-center justify-between px-1 text-xs text-muted-foreground">
        <div>
          Showing <span className="font-semibold text-foreground">{filteredPlots.length}</span> of{" "}
          <span className="font-semibold text-foreground">{initialPlots.length}</span> plots
        </div>
        <div className="flex items-center gap-2">
          <span>Sort by latest satellite image:</span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setSortOrder(sortOrder === "desc" ? "asc" : "desc")}
            className="h-7 text-xs gap-1"
          >
            <ArrowUpDown className="size-3" />
            {sortOrder === "desc" ? "Newest first" : "Oldest first"}
          </Button>
        </div>
      </div>

      {/* Plots Table */}
      <div className="rounded-md border bg-card overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 hover:bg-muted/40">
              <TableHead className="font-semibold text-xs">UPI</TableHead>
              <TableHead className="font-semibold text-xs">Farmer Name</TableHead>
              <TableHead className="font-semibold text-xs">Crop</TableHead>
              <TableHead className="font-semibold text-xs">Season</TableHead>
              <TableHead className="font-semibold text-xs">Planting Date</TableHead>
              <TableHead className="font-semibold text-xs">Verification Level</TableHead>
              <TableHead className="font-semibold text-xs">Status</TableHead>
              <TableHead className="font-semibold text-xs text-center">Open Flags</TableHead>
              <TableHead className="font-semibold text-xs">
                <div className="flex items-center gap-1">
                  <span>Latest Satellite Image</span>
                  <Tooltip>
                    <TooltipTrigger>
                      <Satellite className="size-3 text-muted-foreground" />
                    </TooltipTrigger>
                    <TooltipContent side="top" className="text-[11px]">
                      {STRINGS.satellite.cadenceNotice}
                    </TooltipContent>
                  </Tooltip>
                </div>
              </TableHead>
              <TableHead className="font-semibold text-xs text-right">Area (m²)</TableHead>
              <TableHead className="w-8"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredPlots.length === 0 ? (
              <TableRow>
                <TableCell colSpan={11} className="h-32 text-center text-xs text-muted-foreground">
                  No plots found matching current criteria.
                </TableCell>
              </TableRow>
            ) : (
              filteredPlots.map((plot) => (
                <TableRow
                  key={plot.id}
                  onClick={() => router.push(`/dashboard/plots/${plot.id}`)}
                  className="cursor-pointer hover:bg-muted/60 transition-colors"
                >
                  {/* UPI */}
                  <TableCell className="font-mono text-xs font-semibold text-primary">
                    {plot.upi}
                  </TableCell>

                  {/* Farmer Name */}
                  <TableCell className="text-xs font-medium">
                    {plot.farmerName}
                  </TableCell>

                  {/* Crop Scope (Maize only) */}
                  <TableCell className="text-xs">
                    <Badge variant="secondary" className="text-[11px] font-normal">
                      {plot.crop}
                    </Badge>
                  </TableCell>

                  {/* Season */}
                  <TableCell className="text-xs text-muted-foreground">
                    {plot.season}
                  </TableCell>

                  {/* Planting Date */}
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    {plot.plantingDate}
                  </TableCell>

                  {/* Verification Level (Required everywhere) */}
                  <TableCell>
                    <VerificationBadge level={plot.verificationLevel} size="sm" />
                  </TableCell>

                  {/* Status */}
                  <TableCell>
                    <PlotStatusBadge status={plot.status} size="sm" />
                  </TableCell>

                  {/* Open Flags */}
                  <TableCell className="text-center">
                    {plot.flags.length > 0 ? (
                      <Badge
                        variant="outline"
                        className="border-amber-300 bg-amber-50 text-amber-800 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-300 text-[10px] gap-1 font-mono inline-flex items-center"
                      >
                        <AlertTriangle className="size-3" />
                        {plot.flags.length}
                      </Badge>
                    ) : (
                      <span className="text-xs text-muted-foreground font-mono">0</span>
                    )}
                  </TableCell>

                  {/* Latest Satellite Image Date */}
                  <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                    <div className="flex flex-col">
                      <span>{plot.latestSatelliteImageDate}</span>
                      <span className="text-[10px] text-muted-foreground/80">updated weekly</span>
                    </div>
                  </TableCell>

                  {/* Walked Area */}
                  <TableCell className="text-right text-xs font-mono tabular-nums">
                    {plot.walkedAreaM2.toLocaleString()} m²
                  </TableCell>

                  {/* Row chevron */}
                  <TableCell className="w-8 text-muted-foreground">
                    <ChevronRight className="size-4" />
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
