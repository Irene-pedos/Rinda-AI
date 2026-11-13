"use client"

import * as React from "react"
import type { Plot, PlotFlag } from "@/lib/types/plot"
import { apiClient } from "@/lib/api/client"
import { STRINGS } from "@/lib/strings"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { Badge } from "@/components/ui/badge"
import { AlertTriangle, CheckCircle2, Loader2, ShieldAlert } from "lucide-react"

interface FlagResolveDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  plotId: number
  upi: string
  flag: PlotFlag
  onResolved: (updatedPlot: Plot) => void
}

export function FlagResolveDialog({
  open,
  onOpenChange,
  plotId,
  upi,
  flag,
  onResolved,
}: FlagResolveDialogProps) {
  const [note, setNote] = React.useState("")
  const [isSubmitting, setIsSubmitting] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  const handleResolve = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!note.trim()) {
      setError("A resolution note is required to explain why this flag is being cleared.")
      return
    }

    try {
      setIsSubmitting(true)
      setError(null)
      const updatedPlot = await apiClient.resolveFlag(plotId, flag.id, note.trim())
      onResolved(updatedPlot)
      onOpenChange(false)
      setNote("")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve flag")
    } finally {
      setIsSubmitting(false)
    }
  }

  const flagLabel = STRINGS.flags[flag.type] || flag.type

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleResolve} className="flex flex-col gap-4">
          <DialogHeader>
            <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400">
              <ShieldAlert className="size-5 shrink-0" />
              <DialogTitle className="text-base font-semibold">
                Resolve Verification Flag
              </DialogTitle>
            </div>
            <DialogDescription className="text-xs text-muted-foreground">
              Under platform rules, flags can only be resolved by a human officer or cooperative agent after verification.
            </DialogDescription>
          </DialogHeader>

          {/* Target Plot & Flag Summary */}
          <div className="rounded-lg border bg-muted/20 p-3 flex flex-col gap-2 text-xs">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Target Plot:</span>
              <strong className="font-mono text-primary">{upi}</strong>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground">Flag Type:</span>
              <Badge variant="outline" className="font-mono text-[10px]">
                {flag.type}
              </Badge>
            </div>
            <div className="border-t pt-2 flex flex-col gap-1">
              <span className="text-muted-foreground font-medium">Recorded Anomaly:</span>
              <p className="text-[11px] text-foreground bg-card p-2 rounded-md border font-sans">
                {flag.details || flagLabel}
              </p>
            </div>
          </div>

          {/* Mandatory Investigation Note */}
          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="resolution-note" className="text-xs font-semibold">
                Officer Investigation Note <span className="text-rose-500">*</span>
              </Label>
              <span className="text-[10px] text-muted-foreground">
                {note.length} / 1000 characters
              </span>
            </div>
            <Textarea
              id="resolution-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Record the basis for clearing this flag (e.g. Cross-referenced e-title with land registry certificate; surveyed boundaries with sector agronomist)."
              maxLength={1000}
              rows={4}
              className="text-xs resize-none"
              disabled={isSubmitting}
            />
            <p className="text-[10px] text-muted-foreground flex items-center gap-1">
              <AlertTriangle className="size-3 text-amber-500 shrink-0" />
              This resolution is logged in the permanent audit trail.
            </p>
          </div>

          {/* Error Message */}
          {error && (
            <div className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
              {error}
            </div>
          )}

          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
              className="text-xs"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isSubmitting || !note.trim()}
              className="text-xs gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  Recording Resolution...
                </>
              ) : (
                <>
                  <CheckCircle2 className="size-3.5" />
                  Confirm & Resolve Flag
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
