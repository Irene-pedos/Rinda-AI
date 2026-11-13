"use client"

import * as React from "react"
import type { PlotPolygon, PlotStatus, VerificationLevel } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  MapPin,
  Compass,
  Info,
  Maximize2,
  Layers,
} from "lucide-react"

interface PlotBoundarySvgProps {
  boundaryGeojson?: PlotPolygon
  plantedAreaGeojson?: PlotPolygon
  walkedAreaM2: number
  documentAreaM2?: number | null
  areaDiffPct?: number | null
  status: PlotStatus
  verificationLevel: VerificationLevel
}

export function PlotBoundarySvg({
  boundaryGeojson,
  plantedAreaGeojson,
  walkedAreaM2,
  documentAreaM2,
  areaDiffPct,
  status,
}: PlotBoundarySvgProps) {
  const coordinates = boundaryGeojson?.coordinates?.[0] || []
  const plantedCoordinates = plantedAreaGeojson?.coordinates?.[0] || []

  // If no polygon points exist
  if (!coordinates || coordinates.length < 3) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <MapPin className="size-4 text-primary" />
            Walked Boundary Polygon
          </CardTitle>
          <CardDescription className="text-xs">
            GPS walk boundary outline and area calculation
          </CardDescription>
        </CardHeader>
        <CardContent className="flex-1 flex flex-col items-center justify-center p-8 text-center text-muted-foreground border-t border-dashed bg-muted/10 m-4 rounded-md">
          <Layers className="size-10 text-muted-foreground/40 mb-2" />
          <p className="text-xs font-medium text-foreground">No Boundary Polygon Recorded</p>
          <p className="text-[11px] text-muted-foreground max-w-xs mt-1">
            This plot is currently self-declared by UPI. A GPS corner walk at the plot must be completed to record the polygon.
          </p>
        </CardContent>
      </Card>
    )
  }

  // Projection math: normalize [lng, lat] coordinates into 460x320 SVG viewport
  const svgWidth = 460
  const svgHeight = 320
  const padding = 36

  let minLng = Infinity
  let maxLng = -Infinity
  let minLat = Infinity
  let maxLat = -Infinity

  coordinates.forEach(([lng, lat]) => {
    if (lng < minLng) minLng = lng
    if (lng > maxLng) maxLng = lng
    if (lat < minLat) minLat = lat
    if (lat > maxLat) maxLat = lat
  })

  // Prevent division by zero
  const lngRange = maxLng - minLng || 0.001
  const latRange = maxLat - minLat || 0.001

  // Project [lng, lat] to SVG (x, y), inverting Y because latitude increases North
  const projectPoint = ([lng, lat]: [number, number]): [number, number] => {
    const x = padding + ((lng - minLng) / lngRange) * (svgWidth - 2 * padding)
    const y = padding + ((maxLat - lat) / latRange) * (svgHeight - 2 * padding)
    return [x, y]
  }

  const boundaryPoints = coordinates.map(projectPoint)
  const boundaryPointsString = boundaryPoints.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")

  const plantedPoints = plantedCoordinates.length >= 3 ? plantedCoordinates.map(projectPoint) : []
  const plantedPointsString = plantedPoints.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(" ")

  // Center / Centroid coordinates for display
  const centerLng = ((minLng + maxLng) / 2).toFixed(5)
  const centerLat = ((minLat + maxLat) / 2).toFixed(5)

  // Status-based coloring
  const isDisputed = status === "DISPUTED"
  const isFlagged = status === "FLAGGED"

  const strokeColor = isDisputed ? "#e11d48" : isFlagged ? "#d97706" : "#059669"
  const fillColor = isDisputed ? "rgba(225, 29, 72, 0.12)" : isFlagged ? "rgba(217, 119, 6, 0.12)" : "rgba(5, 150, 105, 0.12)"

  return (
    <Card className="h-full flex flex-col">
      <CardHeader className="pb-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <MapPin className="size-4 text-primary" />
              Walked Boundary Polygon
            </CardTitle>
            <CardDescription className="text-xs">
              Direct vector projection from GPS corner walk (no map tile requests)
            </CardDescription>
          </div>
          <div className="flex items-center gap-1.5">
            <Badge variant="outline" className="font-mono text-[11px] gap-1">
              <Maximize2 className="size-3 text-muted-foreground" />
              {walkedAreaM2.toLocaleString()} m² ({(walkedAreaM2 / 10000).toFixed(2)} ha)
            </Badge>
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex-1 flex flex-col gap-3 pt-0">
        {/* SVG Projection Canvas */}
        <div className="relative rounded-lg border bg-muted/20 dark:bg-muted/10 overflow-hidden flex items-center justify-center p-2">
          {/* Compass / North Arrow */}
          <div className="absolute top-3 right-3 flex flex-col items-center rounded-md bg-card/90 px-2 py-1 shadow-xs border text-[10px] font-mono text-muted-foreground backdrop-blur-xs select-none">
            <Compass className="size-3.5 text-primary animate-pulse" />
            <span className="font-bold text-[9px]">N</span>
          </div>

          {/* Centroid Coordinates Note */}
          <div className="absolute top-3 left-3 rounded-md bg-card/90 px-2 py-1 shadow-xs border text-[10px] font-mono text-muted-foreground backdrop-blur-xs">
            Centroid: {centerLng}°E, {centerLat}°S
          </div>

          {/* SVG Map */}
          <svg
            viewBox={`0 0 ${svgWidth} ${svgHeight}`}
            className="w-full h-auto max-h-[300px] select-none"
          >
            {/* Grid Pattern */}
            <defs>
              <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                <path d="M 20 0 L 0 0 0 20" fill="none" stroke="currentColor" strokeWidth="0.5" className="text-border/40" />
              </pattern>
              <pattern id="plantedHatch" width="8" height="8" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
                <line x1="0" y1="0" x2="0" y2="8" stroke="#10b981" strokeWidth="1.5" opacity="0.4" />
              </pattern>
            </defs>

            <rect width={svgWidth} height={svgHeight} fill="url(#grid)" />

            {/* Main Walked Boundary Polygon */}
            <polygon
              points={boundaryPointsString}
              fill={fillColor}
              stroke={strokeColor}
              strokeWidth="2.5"
              strokeLinejoin="round"
              strokeDasharray={isDisputed ? "6 3" : undefined}
            />

            {/* Planted Area Sub-polygon (if present) */}
            {plantedPointsString && (
              <polygon
                points={plantedPointsString}
                fill="url(#plantedHatch)"
                stroke="#059669"
                strokeWidth="1.5"
                strokeDasharray="4 2"
              />
            )}

            {/* Corner Walk GPS Waypoints */}
            {boundaryPoints.slice(0, -1).map(([x, y], idx) => (
              <g key={idx} className="group cursor-pointer">
                <circle
                  cx={x}
                  cy={y}
                  r="5"
                  fill="white"
                  stroke={strokeColor}
                  strokeWidth="2"
                />
                <circle
                  cx={x}
                  cy={y}
                  r="2"
                  fill={strokeColor}
                />
                <text
                  x={x + 7}
                  y={y - 7}
                  fontSize="9"
                  fontFamily="monospace"
                  fill="currentColor"
                  className="text-foreground font-semibold"
                >
                  P{idx + 1}
                </text>
              </g>
            ))}
          </svg>
        </div>

        {/* Legend & Area Comparison Details */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
          <div className="flex items-center gap-3 p-2 rounded-md border bg-card text-[11px]">
            <div className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-xs" style={{ backgroundColor: strokeColor }} />
              <span className="font-medium">Walked Boundary</span>
            </div>
            {plantedPointsString && (
              <div className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-xs bg-emerald-500/40 border border-emerald-600" />
                <span className="text-muted-foreground">Planted Area</span>
              </div>
            )}
            <div className="text-muted-foreground ml-auto">
              {coordinates.length - 1} GPS vertices
            </div>
          </div>

          <div className="flex items-center justify-between p-2 rounded-md border bg-card text-[11px]">
            <span className="text-muted-foreground">Document Area:</span>
            {documentAreaM2 ? (
              <div className="flex items-center gap-2">
                <span className="font-mono">{documentAreaM2.toLocaleString()} m²</span>
                {areaDiffPct !== null && areaDiffPct !== undefined && (
                  <Badge
                    variant="outline"
                    className={`text-[10px] ${
                      areaDiffPct > 15
                        ? "border-amber-300 text-amber-700 dark:border-amber-700 dark:text-amber-400 font-semibold"
                        : "text-muted-foreground"
                    }`}
                  >
                    Δ {areaDiffPct}%
                  </Badge>
                )}
              </div>
            ) : (
              <span className="text-muted-foreground italic">None uploaded</span>
            )}
          </div>
        </div>

        {/* Presence Notice Footer */}
        <div className="flex items-start gap-2 rounded-md bg-muted/40 p-2.5 text-[11px] text-muted-foreground">
          <Info className="size-3.5 shrink-0 text-primary mt-0.5" />
          <span>
            Vector outline rendered from GPS corners walked by the farmer. Free drawing is disabled.{" "}
            <strong className="text-foreground font-medium">GPS walks prove physical presence, not legal plot ownership.</strong>
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
