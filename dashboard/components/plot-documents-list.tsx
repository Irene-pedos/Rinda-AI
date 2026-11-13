"use client"

import type { PlotDocument } from "@/lib/types/plot"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  FileText,
  Lock,
  ExternalLink,
  ShieldCheck,
  FileCheck2,
  Calendar,
  AlertCircle,
} from "lucide-react"

interface PlotDocumentsListProps {
  plotId: number
  documents?: PlotDocument[]
}

export function PlotDocumentsList({ plotId, documents = [] }: PlotDocumentsListProps) {
  if (documents.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <FileText className="size-4 text-muted-foreground" />
            Uploaded Documents
          </CardTitle>
          <CardDescription className="text-xs">
            Farmer-uploaded e-title, LIIP screenshot, lease agreement, or owner consent
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-0">
          <div className="flex items-center gap-2 rounded-md border border-dashed bg-muted/20 p-3 text-xs text-muted-foreground">
            <AlertCircle className="size-4 shrink-0 text-muted-foreground" />
            <span>No documents uploaded for this plot claim.</span>
          </div>
        </CardContent>
      </Card>
    )
  }

  const handleOpenRestrictedDocument = (docId: number) => {
    // Opening a document must go through the restricted endpoint.
    // Content is never embedded in client bundles or cached.
    const apiPrefix = process.env.NEXT_PUBLIC_API_PREFIX || "/api/v1"
    const restrictedUrl = `${apiPrefix}/plots/${plotId}/documents/${docId}`
    window.open(restrictedUrl, "_blank", "noopener,noreferrer")
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-sm font-semibold flex items-center gap-2">
            <FileCheck2 className="size-4 text-primary" />
            Uploaded Proof Documents ({documents.length})
          </CardTitle>
          <div className="flex items-center gap-1 text-[11px] text-muted-foreground">
            <Lock className="size-3 text-primary" />
            Restricted access
          </div>
        </div>
        <CardDescription className="text-xs">
          Documents uploaded under farmer consent; metadata extracted for automated matching
        </CardDescription>
      </CardHeader>

      <CardContent className="flex flex-col gap-3 pt-0">
        {documents.map((doc) => (
          <div
            key={doc.id}
            className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-md border p-3 bg-card shadow-xs"
          >
            <div className="flex items-start gap-3">
              <div className="size-9 rounded-md bg-primary/10 text-primary flex items-center justify-center shrink-0 mt-0.5">
                <FileText className="size-5" />
              </div>
              <div className="flex flex-col gap-1 text-xs">
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className="font-mono text-[10px]">
                    {doc.type}
                  </Badge>
                  {doc.extractedOwnerName && (
                    <span className="font-semibold text-foreground">
                      Owner: {doc.extractedOwnerName}
                    </span>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
                  {doc.extractedUpi && (
                    <span>
                      UPI: <strong className="font-mono text-foreground">{doc.extractedUpi}</strong>
                    </span>
                  )}
                  {doc.extractedAreaM2 && (
                    <span>
                      Document Area:{" "}
                      <strong className="font-mono text-foreground">
                        {doc.extractedAreaM2.toLocaleString()} m²
                      </strong>
                    </span>
                  )}
                  {doc.extractedLandUse && (
                    <span>Use: {doc.extractedLandUse}</span>
                  )}
                </div>

                <div className="text-[10px] text-muted-foreground font-mono flex items-center gap-1 pt-0.5">
                  <Calendar className="size-3" />
                  Uploaded: {new Date(doc.uploadedAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                </div>
              </div>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => handleOpenRestrictedDocument(doc.id)}
              className="text-xs gap-1.5 h-8 shrink-0 self-end sm:self-center"
            >
              <Lock className="size-3 text-muted-foreground" />
              <span>Restricted View</span>
              <ExternalLink className="size-3" />
            </Button>
          </div>
        ))}

        <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground border-t pt-2">
          <ShieldCheck className="size-3.5 text-primary shrink-0" />
          <span>
            Document contents are restricted and streamed via authorized backend endpoints. No document bytes are embedded in client bundles.
          </span>
        </div>
      </CardContent>
    </Card>
  )
}
