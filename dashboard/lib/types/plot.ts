export type VerificationLevel =
  | "SELF_DECLARED"
  | "DOCUMENT_SUPPORTED"
  | "FIELD_VERIFIED"
  | "NLA_VERIFIED"

export type PlotStatus = "ACTIVE" | "FLAGGED" | "DISPUTED" | "REJECTED"

export type RelationshipToPlot = "OWNER" | "FAMILY_MEMBER" | "TENANT"

export type CropType = "MAIZE"

export type FlagType =
  | "OUTSIDE_ADMIN_AREA"
  | "AREA_MISMATCH"
  | "AREA_UNCHECKED"
  | "GPS_ACCURACY_UNCHECKED"
  | "MOCK_LOCATION"
  | "OVERLAP"
  | "DUPLICATE_UPI"
  | "NON_OWNER"
  | "LEGACY_DRAWN_BOUNDARY"
  | "SATELLITE_INCONSISTENT"
  | "OTHER"

export type SatelliteReviewFlag = "CONSISTENT" | "INCONSISTENT" | "NOT_ENOUGH_DATA"

export type DocumentType = "E_TITLE" | "PORTAL_SCREENSHOT" | "LEASE" | "OWNER_CONSENT"

export interface PlotFlag {
  id: number
  plotId: number
  type: FlagType
  details: string
  relatedPlotId?: number | null
  createdAt: string
  resolvedAt?: string | null
  resolvedBy?: string | null
  resolutionNote?: string | null
}

export interface PlotDocument {
  id: number
  plotId: number
  type: DocumentType
  storagePath: string
  extractedOwnerName?: string
  extractedUpi?: string
  extractedAreaM2?: number
  extractedLandUse?: string
  uploadedAt: string
}

export interface PlotVerification {
  id: number
  plotId: number
  verifierId: number
  verifierName: string
  method: "AGENT_VISIT" | "COOPERATIVE" | "AGRONOMIST_CHECK"
  geotaggedPhotoUrl?: string
  result: "PASSED" | "REJECTED" | "REQUIRES_FOLLOWUP"
  verifiedAt: string
  notes?: string
}

export interface SatelliteObservation {
  date: string
  ndviMedian: number
  pixelCount: number
  cloudFraction: number
  source: string
}

export interface RainfallObservation {
  date: string
  rainfallMm: number
  source: string
}

export interface SatelliteSummary {
  latestImageDate: string
  flag: SatelliteReviewFlag
  pixelsUsed: number
  notes?: string
}

export interface PlotPolygon {
  type: "Polygon"
  coordinates: [number, number][][] // [lng, lat] GeoJSON format
}

export interface Plot {
  id: number
  farmerId: number
  farmerName: string
  farmerPhoneMasked?: string
  upi: string
  verificationLevel: VerificationLevel
  status: PlotStatus
  relationshipToPlot: RelationshipToPlot
  crop: CropType
  plantingDate: string // YYYY-MM-DD
  season: string // e.g. "2026A"
  walkedAreaM2: number
  documentAreaM2?: number | null
  areaDiffPct?: number | null
  latestSatelliteImageDate: string // YYYY-MM-DD
  monitoringStatus?: "DATA_AVAILABLE" | "FETCHING_DATA" | "NO_CLEAR_IMAGE" | "NO_PLOT"
  satelliteSummary?: SatelliteSummary
  ndviTimeseries?: SatelliteObservation[]
  rainfallDaily?: RainfallObservation[]
  flags: PlotFlag[]
  documents?: PlotDocument[]
  verifications?: PlotVerification[]
  boundaryGeojson?: PlotPolygon
  plantedAreaGeojson?: PlotPolygon
  financialActionsAllowed?: boolean
  dataStatus?: "CONFIRMED" | "TO_CONFIRM"
  isDemo?: boolean
  createdAt: string
  updatedAt: string
}

export interface OverviewStats {
  totalPlots: number
  byVerificationLevel: {
    SELF_DECLARED: number
    DOCUMENT_SUPPORTED: number
    FIELD_VERIFIED: number
    NLA_VERIFIED: number
  }
  flaggedPlotsCount: number
  disputedPlotsCount: number
  awaitingFieldVerificationCount: number
  activePlotsCount: number
  registrationsOverTime: {
    date: string
    registeredPlots: number
    fieldVerified: number
  }[]
}
