"use client"

import * as React from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import type { OfficerClaimItem } from "@/lib/types/claim"
import { VerificationBadge } from "@/components/verification-badge"
import { ClaimStatusBadge } from "@/components/claim-status-badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
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
import {
  Search,
  RotateCcw,
  ExternalLink,
  Lock,
  AlertTriangle,
} from "lucide-react"

interface ClaimsTableProps {
  initialClaims: OfficerClaimItem[]
}

export function ClaimsTable({ initialClaims }: ClaimsTableProps) {
  const router = useRouter()
  const [claims] = React.useState<OfficerClaimItem[]>(initialClaims)
  const [search, setSearch] = React.useState("")
  const [statusFilter, setStatusFilter] = React.useState<string>("ALL")
  const [levelFilter, setLevelFilter] = React.useState<string>("ALL")

  const filteredClaims = React.useMemo(() => {
    return claims.filter((claim) => {
      const matchesSearch =
        !search.trim() ||
        claim.plotUpi.toLowerCase().includes(search.toLowerCase().trim()) ||
        claim.farmerName.toLowerCase().includes(search.toLowerCase().trim()) ||
        claim.plotName.toLowerCase().includes(search.toLowerCase().trim()) ||
        (claim.triggerCause && claim.triggerCause.toLowerCase().includes(search.toLowerCase().trim()))

      const matchesStatus = statusFilter === "ALL" || claim.status === statusFilter
      const matchesLevel = levelFilter === "ALL" || claim.verificationLevel === levelFilter

      return matchesSearch && matchesStatus && matchesLevel
    })
  }, [claims, search, statusFilter, levelFilter])

  return (
    <div className="flex flex-col gap-4">
      {/* Search and Filters Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
          <Input
            placeholder="Search claim, UPI, farmer..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 h-9 text-xs"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Status Filter */}
          <Select
            value={statusFilter}
            onValueChange={(val) => {
              if (val) setStatusFilter(val)
            }}
          >
            <SelectTrigger className="w-[170px] h-9 text-xs">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              <SelectItem value="UNDER_REVIEW">Under Review</SelectItem>
              <SelectItem value="FIELD_VISIT_REQUESTED">Field Visit Requested</SelectItem>
              <SelectItem value="APPROVED">Approved (Unpaid)</SelectItem>
              <SelectItem value="PAID">Paid</SelectItem>
              <SelectItem value="DECLINED">Declined</SelectItem>
            </SelectContent>
          </Select>

          {/* Verification Level Filter */}
          <Select
            value={levelFilter}
            onValueChange={(val) => {
              if (val) setLevelFilter(val)
            }}
          >
            <SelectTrigger className="w-[180px] h-9 text-xs">
              <SelectValue placeholder="Verification Level" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Levels</SelectItem>
              <SelectItem value="FIELD_VERIFIED">Field Verified (Eligible)</SelectItem>
              <SelectItem value="NLA_VERIFIED">NLA Verified (Eligible)</SelectItem>
              <SelectItem value="DOCUMENT_SUPPORTED">Document Supported</SelectItem>
              <SelectItem value="SELF_DECLARED">Self Declared</SelectItem>
            </SelectContent>
          </Select>

          {(search || statusFilter !== "ALL" || levelFilter !== "ALL") && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearch("")
                setStatusFilter("ALL")
                setLevelFilter("ALL")
              }}
              className="h-9 px-2 text-xs text-muted-foreground"
            >
              <RotateCcw className="size-3 mr-1" />
              Reset
            </Button>
          )}
        </div>
      </div>

      {/* Table Container */}
      <div className="rounded-lg border bg-card shadow-xs overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/30">
              <TableHead className="text-xs font-semibold">Claim ID</TableHead>
              <TableHead className="text-xs font-semibold">Trigger Date</TableHead>
              <TableHead className="text-xs font-semibold">Plot UPI</TableHead>
              <TableHead className="text-xs font-semibold">Farmer</TableHead>
              <TableHead className="text-xs font-semibold">Verification Level</TableHead>
              <TableHead className="text-xs font-semibold">Status</TableHead>
              <TableHead className="text-xs font-semibold">Parametric Cause</TableHead>
              <TableHead className="text-xs font-semibold text-right">Payout Amount</TableHead>
              <TableHead className="text-xs font-semibold text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredClaims.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="h-32 text-center text-muted-foreground text-xs">
                  No claims found matching the filter criteria.
                </TableCell>
              </TableRow>
            ) : (
              filteredClaims.map((claim) => {
                const isLevelEligible =
                  claim.verificationLevel === "FIELD_VERIFIED" ||
                  claim.verificationLevel === "NLA_VERIFIED"

                return (
                  <TableRow
                    key={claim.id}
                    onClick={() => router.push(`/dashboard/claims/${claim.id}`)}
                    className="cursor-pointer hover:bg-muted/40 transition-colors"
                  >
                    <TableCell className="font-mono text-xs font-bold text-primary">
                      #CLM-000{claim.id}
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {claim.triggerDate}
                      {claim.daysOpen != null && (
                        <span className="block text-[10px] text-muted-foreground">
                          {claim.daysOpen}d open
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="font-mono text-xs font-semibold">
                      {claim.plotUpi}
                    </TableCell>
                    <TableCell className="text-xs">
                      <span className="font-medium">{claim.farmerName}</span>
                      <span className="block text-[10px] text-muted-foreground font-mono">
                        {claim.farmerPhone}
                      </span>
                    </TableCell>
                    <TableCell>
                      <VerificationBadge level={claim.verificationLevel} />
                    </TableCell>
                    <TableCell>
                      <ClaimStatusBadge status={claim.status} />
                    </TableCell>
                    <TableCell className="text-xs max-w-xs">
                      <span className="text-foreground leading-snug line-clamp-1 font-sans">
                        {claim.triggerCause || "Parametric drought trigger"}
                      </span>
                      {claim.reviewFlags.length > 0 && (
                        <div className="flex items-center gap-1 mt-0.5">
                          <AlertTriangle className="size-3 text-amber-500" />
                          <span className="text-[10px] font-mono text-amber-700 dark:text-amber-400">
                            {claim.reviewFlags.join(", ")}
                          </span>
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-right font-mono text-xs">
                      {claim.amount != null ? (
                        <span className="font-semibold text-foreground">
                          {claim.amount.toLocaleString()} RWF
                        </span>
                      ) : (
                        <span className="text-muted-foreground text-[11px] italic">
                          Not confirmed
                        </span>
                      )}
                      {claim.payoutRate != null && (
                        <span className="block text-[10px] text-muted-foreground">
                          Rate: {(claim.payoutRate * 100).toFixed(0)}%
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                      <div className="flex items-center justify-end gap-1.5">
                        {isLevelEligible ? (
                          <Button
                            size="sm"
                            variant="outline"
                            nativeButton={false}
                            render={<Link href={`/dashboard/claims/${claim.id}`} />}
                            className="h-7 text-[11px] gap-1"
                          >
                            <ExternalLink className="size-3" />
                            Case File
                          </Button>
                        ) : (
                          <Tooltip>
                            <TooltipTrigger
                              render={
                                <span className="inline-block">
                                  <Button
                                    size="sm"
                                    variant="outline"
                                    disabled
                                    className="h-7 text-[11px] gap-1 opacity-50 cursor-not-allowed"
                                  >
                                    <Lock className="size-3" />
                                    Case File
                                  </Button>
                                </span>
                              }
                            />
                            <TooltipContent side="top" className="max-w-xs text-xs p-2">
                              Payouts and decisions locked: Plot is at level {claim.verificationLevel}. Under platform rules, payouts strictly require level FIELD_VERIFIED or higher.
                            </TooltipContent>
                          </Tooltip>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <div className="text-xs text-muted-foreground flex items-center justify-between px-1">
        <span>Showing {filteredClaims.length} of {claims.length} claims</span>
        <span className="font-mono text-[11px]">Parametric Policy Code: RINDA-2026A-MZ</span>
      </div>
    </div>
  )
}
