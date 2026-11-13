import type { VerificationLevel, PlotStatus } from "./plot"

export type ClaimStatus =
  | "UNDER_REVIEW"
  | "FIELD_VISIT_REQUESTED"
  | "APPROVED"
  | "DECLINED"
  | "PAID"
  | "REVIEW_REQUESTED"
  | "FINAL"

export type ClaimDecisionType = "APPROVED" | "DECLINED"

export interface OfficerClaimItem {
  id: number
  status: ClaimStatus
  triggerDate: string
  daysOpen: number | null
  plotId: number
  plotName: string
  plotUpi: string
  areaHa: number
  farmerId: number
  farmerName: string
  farmerPhone: string
  cooperativeName: string
  seasonCode: string
  reviewFlags: string[]
  problemReports: number
  fieldVisits: number
  openReview: boolean
  payoutRate: number | null
  amount: number | null
  modelVersion: string
  isDemo: boolean
  verificationLevel: VerificationLevel
  triggerCause?: string
}

export interface ClaimDecisionRecord {
  decision: ClaimDecisionType
  reason: string
  officerName: string
  decidedAt: string
  answeredReview?: boolean
}

export interface ClaimReviewRequest {
  message: string
  status: string
  answer?: string | null
  createdAt: string
  answeredAt?: string | null
}

export interface ClaimAuditEntry {
  action: string
  actorName: string
  actorRole: string
  before?: string | null
  after?: string | null
  occurredAt: string
}

export interface ClaimCaseFile {
  id: number
  claim: {
    id: number
    plotId: number
    plotName: string
    seasonId: number
    seasonCode: string
    triggerDate: string
    status: ClaimStatus
    payoutRate: number | null
    amount: number | null
    evidenceSnapshot?: {
      drySpellDays?: number
      rainfallDeficitPct?: number
      satelliteNdviDropPct?: number
      triggerIndex?: string
    } | null
    decision?: ClaimDecisionRecord | null
  }
  farmer: {
    id: number
    fullName: string
    phone: string
    cooperativeName: string
    isDemo: boolean
  }
  plot: {
    id: number
    name: string
    upi: string
    areaHa: number
    walkedAreaM2: number
    plantingDate: string | null
    status: PlotStatus
    verificationLevel: VerificationLevel
    dataStatus: string
    isDemo: boolean
  }
  cover: {
    status: string
    seasonCode: string
    sumInsured: number
    premium: number
    startDate: string
    endDate: string
  } | null
  payoutAccount: {
    provider: string
    msisdnMasked: string
    verified: boolean
  } | null
  decisions: ClaimDecisionRecord[]
  reviews: ClaimReviewRequest[]
  history: ClaimAuditEntry[]
}
