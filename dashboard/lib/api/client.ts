import type { Plot, OverviewStats, VerificationLevel, PlotStatus, FlagType } from "@/lib/types/plot"
import type { OfficerClaimItem, ClaimCaseFile, ClaimDecisionType } from "@/lib/types/claim"
import { MOCK_PLOTS, MOCK_OVERVIEW_STATS, MOCK_CLAIMS, MOCK_CASE_FILES } from "./mock-data"

export interface GetPlotsParams {
  search?: string
  verificationLevel?: VerificationLevel | "ALL"
  status?: PlotStatus | "ALL"
  flagType?: FlagType | "ALL"
  season?: string | "ALL"
  sortBy?: "latestImageDate" | "plantingDate" | "walkedAreaM2" | "upi"
  sortDirection?: "asc" | "desc"
}

export function isMockApiEnabled(): boolean {
  if (typeof window !== "undefined") {
    // Client-side
    return process.env.NEXT_PUBLIC_USE_MOCK_API !== "false"
  }
  // Server-side
  return process.env.NEXT_PUBLIC_USE_MOCK_API !== "false"
}

export function isAuthConfigured(): boolean {
  // In serious/production mode, an auth token or session must exist.
  // Currently authentication is not decided and in placeholder mode.
  if (typeof window !== "undefined") {
    return Boolean(localStorage.getItem("rinda_auth_token"))
  }
  return false
}

export class ApiClient {
  private useMock: boolean
  private apiPrefix: string

  constructor() {
    this.useMock = isMockApiEnabled()
    this.apiPrefix = process.env.NEXT_PUBLIC_API_PREFIX || "/api/v1"
  }

  async getOverviewStats(): Promise<OverviewStats> {
    if (this.useMock) {
      return { ...MOCK_OVERVIEW_STATS }
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/plots/overview`)
    if (!res.ok) {
      throw new Error(`Failed to fetch overview stats: ${res.statusText}`)
    }
    return res.json()
  }

  async getPlots(params: GetPlotsParams = {}): Promise<{ items: Plot[]; total: number }> {
    if (this.useMock) {
      let filtered = [...MOCK_PLOTS]

      if (params.search) {
        const query = params.search.toLowerCase().trim()
        filtered = filtered.filter(
          (plot) =>
            plot.upi.toLowerCase().includes(query) ||
            plot.farmerName.toLowerCase().includes(query)
        )
      }

      if (params.verificationLevel && params.verificationLevel !== "ALL") {
        filtered = filtered.filter(
          (plot) => plot.verificationLevel === params.verificationLevel
        )
      }

      if (params.status && params.status !== "ALL") {
        filtered = filtered.filter((plot) => plot.status === params.status)
      }

      if (params.flagType && params.flagType !== "ALL") {
        filtered = filtered.filter((plot) =>
          plot.flags.some((flag) => flag.type === params.flagType)
        )
      }

      if (params.season && params.season !== "ALL") {
        filtered = filtered.filter((plot) => plot.season === params.season)
      }

      // Default sort by latest satellite image date (newest first)
      const sortDirection = params.sortDirection || "desc"
      filtered.sort((a, b) => {
        const dateA = new Date(a.latestSatelliteImageDate).getTime()
        const dateB = new Date(b.latestSatelliteImageDate).getTime()
        return sortDirection === "desc" ? dateB - dateA : dateA - dateB
      })

      return { items: filtered, total: filtered.length }
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const queryParams = new URLSearchParams()
    if (params.search) queryParams.set("search", params.search)
    if (params.verificationLevel && params.verificationLevel !== "ALL") {
      queryParams.set("verificationLevel", params.verificationLevel)
    }
    if (params.status && params.status !== "ALL") {
      queryParams.set("status", params.status)
    }

    const res = await fetch(`${this.apiPrefix}/plots?${queryParams.toString()}`)
    if (!res.ok) {
      throw new Error(`Failed to fetch plots: ${res.statusText}`)
    }
    return res.json()
  }

  async getPlotById(id: number): Promise<Plot | null> {
    if (this.useMock) {
      const found = MOCK_PLOTS.find((p) => p.id === id)
      return found ? JSON.parse(JSON.stringify(found)) : null
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/plots/${id}`)
    if (!res.ok) {
      if (res.status === 404) return null
      throw new Error(`Failed to fetch plot ${id}: ${res.statusText}`)
    }
    return res.json()
  }

  async resolveFlag(plotId: number, flagId: number, note: string): Promise<Plot> {
    if (this.useMock) {
      const plot = MOCK_PLOTS.find((p) => p.id === plotId)
      if (!plot) {
        throw new Error(`Plot #${plotId} not found`)
      }

      const flag = plot.flags.find((f) => f.id === flagId)
      if (!flag) {
        throw new Error(`Flag #${flagId} not found on Plot #${plotId}`)
      }

      if (flag.resolvedAt) {
        throw new Error("This flag has already been resolved.")
      }

      // Mark flag as resolved
      flag.resolvedAt = new Date().toISOString()
      flag.resolvedBy = "Insurer Officer (Demo)"
      flag.resolutionNote = note

      // Check if any open flags remain
      const hasOpenFlags = plot.flags.some((f) => !f.resolvedAt)
      if (!hasOpenFlags) {
        if (plot.status === "FLAGGED" || plot.status === "DISPUTED") {
          plot.status = "ACTIVE"
        }
      }

      plot.updatedAt = new Date().toISOString()
      return JSON.parse(JSON.stringify(plot))
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/plots/${plotId}/flags/${flagId}/resolve`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ note }),
    })

    if (!res.ok) {
      let errorMessage = `Failed to resolve flag: ${res.statusText}`
      try {
        const problem = await res.json()
        if (problem.detail) errorMessage = problem.detail
        else if (problem.message) errorMessage = problem.message
      } catch {
        // use default error message
      }
      throw new Error(errorMessage)
    }

    return res.json()
  }

  async getReviewQueue(): Promise<{ items: Plot[]; total: number }> {
    if (this.useMock) {
      // Review queue items are plots that are FLAGGED or DISPUTED, or have at least one unresolved flag
      const items = MOCK_PLOTS.filter((plot) => {
        const hasOpenFlag = plot.flags.some((f) => !f.resolvedAt)
        return plot.status === "FLAGGED" || plot.status === "DISPUTED" || hasOpenFlag
      })

      // Sort by newest flag date
      items.sort((a, b) => {
        const dateA = a.flags[0]?.createdAt || a.createdAt
        const dateB = b.flags[0]?.createdAt || b.createdAt
        return new Date(dateB).getTime() - new Date(dateA).getTime()
      })

      return { items: JSON.parse(JSON.stringify(items)), total: items.length }
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    // In production, query plots filtered by status
    const res = await fetch(`${this.apiPrefix}/plots?status=FLAGGED`)
    if (!res.ok) {
      throw new Error(`Failed to fetch review queue: ${res.statusText}`)
    }
    const data = await res.json()
    return { items: data.items || [], total: data.totalItems || 0 }
  }

  async getOfficerClaims(params: {
    status?: string
    flag?: string
    demo?: boolean
    cooperativeId?: number
    q?: string
    page?: number
    size?: number
  } = {}): Promise<{ items: OfficerClaimItem[]; total: number }> {
    if (this.useMock) {
      let filtered = [...MOCK_CLAIMS]
      if (params.status && params.status !== "ALL") {
        const statuses = params.status.split(",")
        filtered = filtered.filter((c) => statuses.includes(c.status))
      }
      if (params.q) {
        const query = params.q.toLowerCase().trim()
        filtered = filtered.filter(
          (c) =>
            c.plotName.toLowerCase().includes(query) ||
            c.farmerName.toLowerCase().includes(query) ||
            c.plotUpi.toLowerCase().includes(query)
        )
      }
      return { items: JSON.parse(JSON.stringify(filtered)), total: filtered.length }
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const queryParams = new URLSearchParams()
    if (params.status) queryParams.set("status", params.status)
    if (params.q) queryParams.set("q", params.q)

    const res = await fetch(`${this.apiPrefix}/officer/claims?${queryParams.toString()}`)
    if (!res.ok) throw new Error(`Failed to fetch claims: ${res.statusText}`)
    const data = await res.json()
    return { items: data.items || [], total: data.totalItems || 0 }
  }

  async getClaimCaseFile(id: number): Promise<ClaimCaseFile | null> {
    if (this.useMock) {
      const caseFile = MOCK_CASE_FILES[id]
      if (caseFile) return JSON.parse(JSON.stringify(caseFile))
      const claim = MOCK_CLAIMS.find((c) => c.id === id)
      if (!claim) return null
      return {
        id: claim.id,
        claim: {
          id: claim.id,
          plotId: claim.plotId,
          plotName: claim.plotName,
          seasonId: 1,
          seasonCode: claim.seasonCode,
          triggerDate: claim.triggerDate,
          status: claim.status,
          payoutRate: claim.payoutRate,
          amount: claim.amount,
        },
        farmer: {
          id: claim.farmerId,
          fullName: claim.farmerName,
          phone: claim.farmerPhone,
          cooperativeName: claim.cooperativeName,
          isDemo: claim.isDemo,
        },
        plot: {
          id: claim.plotId,
          name: claim.plotName,
          upi: claim.plotUpi,
          areaHa: claim.areaHa,
          walkedAreaM2: Math.round(claim.areaHa * 10000),
          plantingDate: "2026-09-15",
          status: "ACTIVE",
          verificationLevel: claim.verificationLevel,
          dataStatus: "CONFIRMED",
          isDemo: claim.isDemo,
        },
        cover: {
          status: "ACTIVE",
          seasonCode: claim.seasonCode,
          sumInsured: (claim.amount || 100000) * 3,
          premium: 18000,
          startDate: "2026-09-01",
          endDate: "2027-01-31",
        },
        payoutAccount: {
          provider: "MTN_MOMO",
          msisdnMasked: "+250 78 *** 001",
          verified: true,
        },
        decisions: [],
        reviews: [],
        history: [],
      }
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/officer/claims/${id}`)
    if (!res.ok) {
      if (res.status === 404) return null
      throw new Error(`Failed to fetch claim case file ${id}: ${res.statusText}`)
    }
    return res.json()
  }

  async decideClaim(id: number, decision: ClaimDecisionType, reason: string): Promise<void> {
    if (this.useMock) {
      const claim = MOCK_CLAIMS.find((c) => c.id === id)
      if (claim) {
        claim.status = decision === "APPROVED" ? "APPROVED" : "DECLINED"
        if (MOCK_CASE_FILES[id]) {
          MOCK_CASE_FILES[id].claim.status = claim.status
          MOCK_CASE_FILES[id].decisions.push({
            decision,
            reason,
            officerName: "Demo Insurer Officer",
            decidedAt: new Date().toISOString(),
          })
        }
      }
      return
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/claims/${id}/decision`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision, reason }),
    })
    if (!res.ok) throw new Error(`Failed to decide claim: ${res.statusText}`)
  }

  async requestFieldVisit(id: number): Promise<void> {
    if (this.useMock) {
      const claim = MOCK_CLAIMS.find((c) => c.id === id)
      if (claim) {
        claim.status = "FIELD_VISIT_REQUESTED"
        if (MOCK_CASE_FILES[id]) {
          MOCK_CASE_FILES[id].claim.status = "FIELD_VISIT_REQUESTED"
        }
      }
      return
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/claims/${id}/field-visit-request`, {
      method: "POST",
    })
    if (!res.ok) throw new Error(`Failed to request field visit: ${res.statusText}`)
  }

  async payoutClaim(id: number): Promise<void> {
    if (this.useMock) {
      const claim = MOCK_CLAIMS.find((c) => c.id === id)
      if (claim) {
        if (claim.verificationLevel !== "FIELD_VERIFIED" && claim.verificationLevel !== "NLA_VERIFIED") {
          throw new Error("Payout rejected: Verification level must be FIELD_VERIFIED or higher.")
        }
        claim.status = "PAID"
        claim.daysOpen = null
        if (MOCK_CASE_FILES[id]) {
          MOCK_CASE_FILES[id].claim.status = "PAID"
        }
      }
      return
    }

    if (!isAuthConfigured()) {
      throw new Error(
        "Dashboard cannot connect to production API without configured authentication. Safety guard active."
      )
    }

    const res = await fetch(`${this.apiPrefix}/claims/${id}/payout`, {
      method: "POST",
    })
    if (!res.ok) throw new Error(`Failed to authorize payout: ${res.statusText}`)
  }
}

export const apiClient = new ApiClient()
