"use client"

import * as React from "react"
import type { PlotVerification } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  UserCheck,
  Camera,
  CheckCircle2,
  Calendar,
  AlertCircle,
  X,
} from "lucide-react"

interface PlotVerificationHistoryProps {
  verifications?: PlotVerification[]
  isFieldVerified: boolean
}

export function PlotVerificationHistory({
  verifications = [],
  isFieldVerified,
}: PlotVerificationHistoryProps) {
  const [selectedPhoto, setSelectedPhoto] = React.useState<string | null>(null)

  if (verifications.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <UserCheck className="size-4 text-muted-foreground" />
            Field Verification History
          </CardTitle>
          <CardDescription className="text-xs">
            Human on-site inspections (cooperative agent / sector agronomist)
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-0">
          <div className="flex items-start gap-2.5 rounded-md border border-dashed bg-muted/20 p-3.5 text-xs text-muted-foreground">
            <AlertCircle className="size-4 shrink-0 text-amber-500 mt-0.5" />
            <div className="leading-relaxed">
              <p className="font-semibold text-foreground">Awaiting Field Inspection</p>
              <p className="text-[11px] mt-0.5">
                No human on-site visit has been recorded yet. Under platform rules, insurance claims and payouts are locked until a cooperative agent or sector agronomist verifies the parcel with a geotagged photo.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <UserCheck className="size-4 text-primary" />
            Field Verification History ({verifications.length})
          </CardTitle>
          {isFieldVerified && (
            <Badge variant="outline" className="border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 text-[11px] font-medium gap-1">
              <CheckCircle2 className="size-3 text-emerald-600" />
              Verified at Plot
            </Badge>
          )}
        </div>
        <CardDescription className="text-xs">
          Physical on-site inspections conducted with GPS-stamped evidence
        </CardDescription>
      </CardHeader>

      <CardContent className="flex flex-col gap-3 pt-0">
        {verifications.map((v) => (
          <div key={v.id} className="flex flex-col sm:flex-row gap-3 rounded-md border p-3 bg-card shadow-xs">
            {/* Geotagged Photo Thumbnail Placeholder */}
            <div
              className="relative size-20 rounded-md border bg-muted flex flex-col items-center justify-center shrink-0 overflow-hidden group cursor-pointer hover:border-primary transition-colors"
              onClick={() => setSelectedPhoto(v.geotaggedPhotoUrl || "/mock/field_photo.jpg")}
              title="Click to preview geotagged field photo"
            >
              <div className="absolute inset-0 bg-primary/10 flex items-center justify-center">
                <Camera className="size-6 text-primary group-hover:scale-110 transition-transform" />
              </div>
              <span className="absolute bottom-1 text-[9px] font-mono text-muted-foreground bg-background/80 px-1 rounded-xs">
                Photo
              </span>
            </div>

            {/* Verification Details */}
            <div className="flex-1 flex flex-col justify-between gap-1 text-xs">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-foreground">{v.verifierName}</span>
                  <Badge variant="secondary" className="font-mono text-[10px]">
                    {v.method}
                  </Badge>
                </div>
                <Badge
                  variant="outline"
                  className={
                    v.result === "PASSED"
                      ? "border-emerald-300 bg-emerald-50 text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 text-[10px]"
                      : "border-amber-300 bg-amber-50 text-amber-800 text-[10px]"
                  }
                >
                  {v.result}
                </Badge>
              </div>

              {v.notes && (
                <p className="text-[11px] text-muted-foreground leading-snug">
                  &ldquo;{v.notes}&rdquo;
                </p>
              )}

              <div className="flex items-center gap-3 text-[10px] text-muted-foreground font-mono pt-1">
                <span className="flex items-center gap-1">
                  <Calendar className="size-3" />
                  {new Date(v.verifiedAt).toLocaleDateString("en-US", {
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                  })}
                </span>
                <span>Verifier ID: #{v.verifierId}</span>
              </div>
            </div>
          </div>
        ))}

        {/* Geotagged Photo Preview Banner */}
        {selectedPhoto && (
          <div className="mt-2 rounded-md border border-primary/20 bg-primary/5 p-3 flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-foreground">
                <Camera className="size-3.5 text-primary" />
                Geotagged Photo Preview (Simulated Ground Truth)
              </div>
              <button
                onClick={() => setSelectedPhoto(null)}
                className="text-muted-foreground hover:text-foreground text-xs"
              >
                <X className="size-4" />
              </button>
            </div>
            <div className="h-40 rounded-md border bg-zinc-900 flex flex-col items-center justify-center text-white/80 text-xs p-4 text-center">
              <Camera className="size-8 text-primary mb-1" />
              <p className="font-medium">Geotagged Maize Parcel Photograph</p>
              <p className="text-[10px] text-zinc-400 font-mono mt-1">
                EXIF Metadata: GPS -2.5958°S, 29.7224°E | Compass Heading: 142° SE | Timestamp: 2026-09-28 14:15:02 UTC
              </p>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
