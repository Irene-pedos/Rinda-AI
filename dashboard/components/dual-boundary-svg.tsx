"use client"

import * as React from "react"
import type { PlotPolygon } from "@/lib/types/plot"
import { Badge } from "@/components/ui/badge"
import { Compass, Layers } from "lucide-react"

interface DualBoundarySvgProps {
  plotA: {
    id: number
    upi: string
    farmerName: string
    boundaryGeojson?: PlotPolygon
    walkedAreaM2: number
  }
  plotB: {
    id: number
    upi: string
    farmerName: string
    boundaryGeojson?: PlotPolygon
    walkedAreaM2: number
  }
  overlapPct?: number
}

export function DualBoundarySvg({
  plotA,
  plotB,
  overlapPct = 28,
}: DualBoundarySvgProps) {
  const coordsA = plotA.boundaryGeojson?.coordinates?.[0] || []
  const coordsB = plotB.boundaryGeojson?.coordinates?.[0] || []

  const hasCoordsA = coordsA.length >= 3
  const hasCoordsB = coordsB.length >= 3

  if (!hasCoordsA && !hasCoordsB) {
    return (
      <div className="flex flex-col items-center justify-center p-8 text-center text-muted-foreground border border-dashed rounded-lg bg-muted/10 h-64">
        <Layers className="size-8 text-muted-foreground/40 mb-2" />
        <p className="text-xs font-medium text-foreground">No Boundary Coordinates</p>
        <p className="text-[11px] text-muted-foreground max-w-xs mt-1">
          Neither plot has recorded a walked polygon.
        </p>
      </div>
    )
  }

  // Calculate combined bounding box
  const allCoords = [...coordsA, ...coordsB]
  let minLng = Infinity
  let maxLng = -Infinity
  let minLat = Infinity
  let maxLat = -Infinity

  allCoords.forEach(([lng, lat]) => {
    if (lng < minLng) minLng = lng
    if (lng > maxLng) maxLng = lng
    if (lat < minLat) minLat = lat
    if (lat > maxLat) maxLat = lat
  })

  const lngRange = maxLng - minLng || 0.001
  const latRange = maxLat - minLat || 0.001

  const svgWidth = 500
  const svgHeight = 320
  const padding = 40

  const projectPoint = ([lng, lat]: [number, number]): [number, number] => {
    const x = padding + ((lng - minLng) / lngRange) * (svgWidth - 2 * padding)
    const y = padding + ((maxLat - lat) / latRange) * (svgHeight - 2 * padding)
    return [x, y]
  }

  const pointsA = coordsA.map(projectPoint)
  const pointsB = coordsB.map(projectPoint)

  const pointsStringA = pointsA.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")
  const pointsStringB = pointsB.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")

  const centerLng = ((minLng + maxLng) / 2).toFixed(5)
  const centerLat = ((minLat + maxLat) / 2).toFixed(5)

  return (
    <div className="relative rounded-lg border bg-muted/20 dark:bg-muted/10 overflow-hidden flex flex-col p-3 gap-3">
      {/* Top Map Overlays */}
      <div className="flex items-center justify-between z-10">
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="font-mono text-[10px] bg-card/90">
            Center: {centerLng}°E, {centerLat}°S
          </Badge>
          <Badge
            variant="outline"
            className="border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/60 dark:text-amber-200 text-[10px] font-semibold"
          >
            ~{overlapPct}% Overlap Intersection
          </Badge>
        </div>

        <div className="flex items-center gap-1 rounded-md bg-card/90 px-2 py-0.5 shadow-xs border text-[10px] font-mono text-muted-foreground">
          <Compass className="size-3 text-primary animate-pulse" />
          <span className="font-bold text-[9px]">N</span>
        </div>
      </div>

      {/* SVG Canvas */}
      <svg
        viewBox={`0 0 ${svgWidth} ${svgHeight}`}
        className="w-full h-auto max-h-[300px] select-none"
      >
        <defs>
          <pattern id="dualGrid" width="20" height="20" patternUnits="userSpaceOnUse">
            <path d="M 20 0 L 0 0 0 20" fill="none" stroke="currentColor" strokeWidth="0.5" className="text-border/40" />
          </pattern>
          {/* Overlap hatched pattern */}
          <pattern id="overlapHatch" width="8" height="8" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
            <line x1="0" y1="0" x2="0" y2="8" stroke="#f59e0b" strokeWidth="2" opacity="0.6" />
          </pattern>
        </defs>

        <rect width={svgWidth} height={svgHeight} fill="url(#dualGrid)" />

        {/* Polygon A (Claim 1) */}
        {hasCoordsA && (
          <polygon
            points={pointsStringA}
            fill="rgba(37, 99, 235, 0.15)"
            stroke="#2563eb"
            strokeWidth="2.5"
            strokeDasharray="4 2"
            strokeLinejoin="round"
          />
        )}

        {/* Polygon B (Claim 2) */}
        {hasCoordsB && (
          <polygon
            points={pointsStringB}
            fill="rgba(225, 29, 72, 0.15)"
            stroke="#e11d48"
            strokeWidth="2.5"
            strokeLinejoin="round"
          />
        )}

        {/* Corner GPS Waypoints for Claim A */}
        {pointsA.slice(0, -1).map(([x, y], idx) => (
          <g key={`pointA-${idx}`}>
            <circle cx={x} cy={y} r="3.5" fill="#2563eb" stroke="#ffffff" strokeWidth="1.5" />
            <text x={x + 6} y={y - 4} fontSize="9" fontWeight="600" fill="#2563eb" fontFamily="monospace">
              A{idx + 1}
            </text>
          </g>
        ))}

        {/* Corner GPS Waypoints for Claim B */}
        {pointsB.slice(0, -1).map(([x, y], idx) => (
          <g key={`pointB-${idx}`}>
            <circle cx={x} cy={y} r="3.5" fill="#e11d48" stroke="#ffffff" strokeWidth="1.5" />
            <text x={x + 6} y={y + 11} fontSize="9" fontWeight="600" fill="#e11d48" fontFamily="monospace">
              B{idx + 1}
            </text>
          </g>
        ))}
      </svg>

      {/* Bottom Map Legend */}
      <div className="grid grid-cols-2 gap-2 text-xs border-t pt-2 z-10">
        <div className="flex items-center gap-2">
          <div className="size-3 rounded-xs border-2 border-blue-600 bg-blue-500/20" />
          <div className="flex flex-col">
            <span className="font-semibold text-blue-700 dark:text-blue-400 font-mono text-[11px]">
              Claim A: {plotA.upi}
            </span>
            <span className="text-[10px] text-muted-foreground">
              {plotA.farmerName} • {plotA.walkedAreaM2.toLocaleString()} m²
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="size-3 rounded-xs border-2 border-rose-600 bg-rose-500/20" />
          <div className="flex flex-col">
            <span className="font-semibold text-rose-700 dark:text-rose-400 font-mono text-[11px]">
              Claim B: {plotB.upi}
            </span>
            <span className="text-[10px] text-muted-foreground">
              {plotB.farmerName} • {plotB.walkedAreaM2.toLocaleString()} m²
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
