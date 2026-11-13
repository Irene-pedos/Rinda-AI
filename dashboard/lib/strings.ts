/**
 * Centralized UI strings for Rinda AI - Insurer Dashboard.
 * English strings are defined here in one place so that Kinyarwanda (rw)
 * or French (fr) translations can be cleanly introduced.
 */

export const STRINGS = {
  app: {
    title: "Rinda AI",
    subtitle: "Insurer Dashboard",
    tagline: "Maize Crop-Insurance Platform — Rwanda",
    cropScopeNotice: "Crop scope: Maize only",
  },
  demo: {
    bannerTitle: "DEMO DATA ACTIVE",
    bannerDescription:
      "Displaying synthetic mock data with simulated Rwandan UPIs and test farmer records. Real farmer data is never committed or logged.",
    mockBadge: "Demo Data",
    sessionLabel: "Demo session (Role: Insurer)",
  },
  authGuard: {
    title: "Authentication Required",
    message:
      "NEXT_PUBLIC_USE_MOCK_API is disabled, but no production authentication is configured. To safeguard farmer personal data, dashboard access to real data is blocked until authentication is initialized.",
  },
  verificationLevels: {
    SELF_DECLARED: {
      label: "SELF_DECLARED",
      shortLabel: "Self Declared",
      description: "Farmer typed a UPI only. Unverified, permitted for demo only.",
    },
    DOCUMENT_SUPPORTED: {
      label: "DOCUMENT_SUPPORTED",
      shortLabel: "Document Supported",
      description:
        "UPI plus uploaded e-title or portal screenshot; GPS walk completed and automatic checks passed. For onboarding and learning.",
    },
    FIELD_VERIFIED: {
      label: "FIELD_VERIFIED",
      shortLabel: "Field Verified",
      description:
        "Human verification completed at the plot (cooperative agent / sector agronomist) with geotagged photo. Minimum level required for claims and payouts.",
    },
    NLA_VERIFIED: {
      label: "NLA_VERIFIED",
      shortLabel: "NLA Verified",
      description: "Direct match against National Land Authority (NLA) parcel polygon data.",
    },
    presenceNotice:
      "Important: A GPS walk proves physical presence at a location, not legal ownership of the plot. Payouts require human Field Verification.",
  },
  satellite: {
    reviewTooltip:
      "Review flag from satellite data, not proof of crop. Near-real-time indicator; never use to auto-approve or auto-reject claims.",
    cadenceNotice: "Updated weekly — not live or real-time data.",
    flags: {
      CONSISTENT: {
        label: "Consistent",
        description: "Vegetation index curve matches expected maize growth cycle.",
      },
      INCONSISTENT: {
        label: "Inconsistent",
        description: "Vegetation profile deviates significantly from declared planting date.",
      },
      NOT_ENOUGH_DATA: {
        label: "Not Enough Data",
        description: "Insufficient cloud-free pixels to evaluate vegetation index with confidence.",
      },
    },
    states: {
      fetching: "Fetching satellite data...",
      noImageSince: (date: string) => `No clear image since ${date}; showing rainfall only.`,
      registerToStart: "Register plot boundary to initiate satellite monitoring.",
    },
  },
  statuses: {
    ACTIVE: {
      label: "Active",
      description: "Plot is in good standing with active crop insurance monitoring.",
    },
    FLAGGED: {
      label: "Flagged",
      description: "One or more automatic validation checks raised an anomaly requiring human review.",
    },
    DISPUTED: {
      label: "Disputed",
      description: "Boundary overlaps with another registered plot claim. Payouts are paused.",
    },
    REJECTED: {
      label: "Rejected",
      description: "Plot claim failed verification or was formally declined.",
    },
  },
  flags: {
    OUTSIDE_ADMIN_AREA: "Walked polygon falls outside the administrative cell encoded in UPI",
    AREA_MISMATCH: "Walked area differs from document area by more than tolerance threshold (>15%)",
    AREA_UNCHECKED: "No document area provided to verify walked polygon against",
    GPS_ACCURACY_UNCHECKED: "GPS signal accuracy was not logged during boundary walk",
    MOCK_LOCATION: "GPS coordinates were flagged by mock-location detection",
    OVERLAP: "Boundary polygon overlaps with an existing registered plot",
    DUPLICATE_UPI: "Another active plot is already registered with this UPI",
    NON_OWNER: "Farmer is not plot owner and requires written consent or lease",
    LEGACY_DRAWN_BOUNDARY: "Boundary was drawn on screen rather than walked with GPS",
    SATELLITE_INCONSISTENT: "Satellite vegetation curve inconsistent with maize declaration",
    OTHER: "Manual review flag",
  },
  claimEligibility: {
    eligible: "Eligible for Claim & Payout Processing",
    ineligibleFieldVerified:
      "Ineligible: Plot must be at verification level FIELD_VERIFIED or higher to process claims.",
    ineligibleStatus: "Ineligible: Plot status must be ACTIVE (currently not active).",
    payoutPaused: "Payout actions are paused because this plot has an open dispute or flag.",
  },
  relationships: {
    OWNER: "Owner",
    FAMILY_MEMBER: "Family Member",
    TENANT: "Tenant",
  },
} as const
