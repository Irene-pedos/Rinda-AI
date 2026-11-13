import { DashboardLayout } from "@/components/dashboard-layout"
import { VerificationBadge } from "@/components/verification-badge"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { STRINGS } from "@/lib/strings"
import {
  ShieldCheck,
  AlertTriangle,
  Info,
  Layers,
  MapPin,
  Satellite,
  Users,
  Compass,
  CheckCircle2,
  Lock,
} from "lucide-react"

export default function VerificationRulesPage() {
  return (
    <DashboardLayout title="Verification Rules & Anti-Fraud Policy">
      <div className="@container/main flex flex-1 flex-col gap-6 py-4 md:py-6 px-4 lg:px-6 max-w-5xl">
        {/* Header Banner */}
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
            <ShieldCheck className="size-6 text-primary" />
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
              Plot Verification Rules & Anti-Fraud Hierarchy
            </h1>
          </div>
          <p className="text-xs text-muted-foreground leading-relaxed">
            Institutional standards governing plot registration, boundary validation, satellite vegetation monitoring, and claim eligibility for Rinda AI in Rwanda.
          </p>
        </div>

        {/* Core Principle Callout */}
        <div className="rounded-lg border-2 border-amber-300 bg-amber-50/80 dark:border-amber-900 dark:bg-amber-950/30 p-4 text-xs text-amber-950 dark:text-amber-200 flex flex-col gap-2">
          <div className="flex items-center gap-2 font-bold text-sm text-amber-900 dark:text-amber-100">
            <AlertTriangle className="size-5 text-amber-600 shrink-0" />
            The Core Principle: Presence vs. Ownership
          </div>
          <p className="leading-relaxed">
            {STRINGS.verificationLevels.presenceNotice}
          </p>
          <p className="text-[11px] text-amber-800 dark:text-amber-300 leading-relaxed">
            A farmer who walks their neighbour&apos;s plot in the same cell can pass automatic boundary checks if the area is similar. Because the platform operates without a direct NLA parcel database connection, financial payouts and claim settlements strictly require <strong>FIELD_VERIFIED</strong> status—where a human cooperative agent confirms identity and captures a geotagged field photo on-site.
          </p>
        </div>

        {/* 4 Verification Levels Breakdown */}
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold tracking-tight text-foreground flex items-center gap-2">
            <Layers className="size-4 text-primary" />
            The Four Verification Levels
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Level 1: SELF_DECLARED */}
            <Card className="border-zinc-200 dark:border-zinc-800 bg-card">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <VerificationBadge level="SELF_DECLARED" />
                  <Badge variant="outline" className="text-[10px] font-mono">
                    DEMO ONLY
                  </Badge>
                </div>
                <CardTitle className="text-sm font-semibold mt-2">
                  1. Self Declared
                </CardTitle>
                <CardDescription className="text-xs">
                  {STRINGS.verificationLevels.SELF_DECLARED.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0 text-xs flex flex-col gap-2">
                <div className="rounded-md bg-muted/20 p-2.5 flex flex-col gap-1 text-[11px]">
                  <div><strong>Required:</strong> Farmer entered UPI code only.</div>
                  <div><strong>Allowed Use:</strong> Testing, initial onboarding mockup.</div>
                  <div className="text-rose-600 dark:text-rose-400 font-medium flex items-center gap-1">
                    <Lock className="size-3" /> Payouts and claims are strictly disabled.
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Level 2: DOCUMENT_SUPPORTED */}
            <Card className="border-blue-200 dark:border-blue-900/60 bg-card">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <VerificationBadge level="DOCUMENT_SUPPORTED" />
                  <Badge variant="outline" className="text-[10px] font-mono text-blue-700 border-blue-300">
                    LEARNING / ONBOARDING
                  </Badge>
                </div>
                <CardTitle className="text-sm font-semibold mt-2">
                  2. Document Supported
                </CardTitle>
                <CardDescription className="text-xs">
                  {STRINGS.verificationLevels.DOCUMENT_SUPPORTED.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0 text-xs flex flex-col gap-2">
                <div className="rounded-md bg-muted/20 p-2.5 flex flex-col gap-1 text-[11px]">
                  <div><strong>Required:</strong> UPI + farmer-uploaded e-title or LIIP screenshot + completed GPS boundary walk.</div>
                  <div><strong>Allowed Use:</strong> Pre-season registration, learning cohorts.</div>
                  <div className="text-rose-600 dark:text-rose-400 font-medium flex items-center gap-1">
                    <Lock className="size-3" /> Minimum for payouts not met; requires field visit.
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Level 3: FIELD_VERIFIED */}
            <Card className="border-emerald-300 dark:border-emerald-800 bg-card shadow-xs">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <VerificationBadge level="FIELD_VERIFIED" />
                  <Badge variant="outline" className="text-[10px] font-mono text-emerald-800 border-emerald-400 bg-emerald-50 dark:bg-emerald-950/40">
                    PAYOUT ELIGIBLE
                  </Badge>
                </div>
                <CardTitle className="text-sm font-semibold mt-2">
                  3. Field Verified
                </CardTitle>
                <CardDescription className="text-xs">
                  {STRINGS.verificationLevels.FIELD_VERIFIED.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0 text-xs flex flex-col gap-2">
                <div className="rounded-md bg-muted/20 p-2.5 flex flex-col gap-1 text-[11px]">
                  <div><strong>Required:</strong> Human agent visit at plot + on-site geotagged photo taken within boundary polygon.</div>
                  <div><strong>Allowed Use:</strong> Pilot production, full parametric claim eligibility.</div>
                  <div className="text-emerald-700 dark:text-emerald-400 font-medium flex items-center gap-1">
                    <CheckCircle2 className="size-3" /> Payout actions enabled (when status is ACTIVE).
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Level 4: NLA_VERIFIED */}
            <Card className="border-primary/40 bg-card">
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <VerificationBadge level="NLA_VERIFIED" />
                  <Badge variant="outline" className="text-[10px] font-mono text-primary border-primary/30">
                    FUTURE PRODUCTION
                  </Badge>
                </div>
                <CardTitle className="text-sm font-semibold mt-2">
                  4. NLA Verified
                </CardTitle>
                <CardDescription className="text-xs">
                  {STRINGS.verificationLevels.NLA_VERIFIED.description}
                </CardDescription>
              </CardHeader>
              <CardContent className="pt-0 text-xs flex flex-col gap-2">
                <div className="rounded-md bg-muted/20 p-2.5 flex flex-col gap-1 text-[11px]">
                  <div><strong>Required:</strong> Point-in-polygon matching against National Land Authority (NLA) LAIS parcel records.</div>
                  <div><strong>Allowed Use:</strong> Nationwide institutional insurance roll-out.</div>
                  <div className="text-emerald-700 dark:text-emerald-400 font-medium flex items-center gap-1">
                    <CheckCircle2 className="size-3" /> Automated verification without manual agent visits.
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>

        {/* Anti-Fraud Validation Rules */}
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold tracking-tight text-foreground flex items-center gap-2">
            <MapPin className="size-4 text-primary" />
            Automated Anti-Fraud Checks & Tolerances
          </h2>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
            <div className="rounded-lg border p-3 bg-card flex flex-col gap-1.5">
              <span className="font-semibold text-foreground flex items-center gap-1.5">
                <Compass className="size-3.5 text-primary" />
                Administrative Cell
              </span>
              <p className="text-[11px] text-muted-foreground leading-snug">
                Walked GPS coordinates must fall within the Province, District, Sector, and Cell encoded in the UPI string.
              </p>
            </div>

            <div className="rounded-lg border p-3 bg-card flex flex-col gap-1.5">
              <span className="font-semibold text-foreground flex items-center gap-1.5">
                <Layers className="size-3.5 text-amber-600" />
                15% Area Tolerance
              </span>
              <p className="text-[11px] text-muted-foreground leading-snug">
                If walked polygon area differs from declared document area by &gt; 15%, an <code>AREA_MISMATCH</code> flag is raised for review.
              </p>
            </div>

            <div className="rounded-lg border p-3 bg-card flex flex-col gap-1.5">
              <span className="font-semibold text-foreground flex items-center gap-1.5">
                <AlertTriangle className="size-3.5 text-rose-600" />
                Boundary Overlap
              </span>
              <p className="text-[11px] text-muted-foreground leading-snug">
                Any polygon intersection with an existing registered plot marks both plots <code>DISPUTED</code> and freezes payouts immediately.
              </p>
            </div>

            <div className="rounded-lg border p-3 bg-card flex flex-col gap-1.5">
              <span className="font-semibold text-foreground flex items-center gap-1.5">
                <Users className="size-3.5 text-primary" />
                Tenure & Relationships
              </span>
              <p className="text-[11px] text-muted-foreground leading-snug">
                Tenants and family members require uploaded written consent or lease agreement. Non-owners cannot claim titleholder rights.
              </p>
            </div>
          </div>
        </div>

        {/* Satellite Monitoring Rules */}
        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold tracking-tight text-foreground flex items-center gap-2">
            <Satellite className="size-4 text-primary" />
            Satellite Monitoring Rules (Sentinel-2 & CHIRPS)
          </h2>

          <Card className="bg-card">
            <CardContent className="p-4 flex flex-col gap-3 text-xs leading-relaxed">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col gap-1.5">
                  <span className="font-semibold text-foreground">Review Flag, Not Proof of Crop</span>
                  <p className="text-[11px] text-muted-foreground">
                    Multi-spectral satellite observations (NDVI vegetation index) evaluate whether vegetation development aligns with the declared maize planting date. They provide near-real-time review flags (<code>CONSISTENT</code> / <code>INCONSISTENT</code>) and must <strong>never be used to auto-reject or auto-approve claims</strong>.
                  </p>
                </div>

                <div className="flex flex-col gap-1.5">
                  <span className="font-semibold text-foreground">Updated Weekly (Never &quot;Live&quot;)</span>
                  <p className="text-[11px] text-muted-foreground">
                    Satellite data is optical reflectance with a 5-day revisit cadence and variable cloud obstruction. Wherever satellite data is shown, the date of the latest clear image is explicitly displayed alongside the notice: <em>&quot;Updated weekly — not live or real-time data.&quot;</em>
                  </p>
                </div>
              </div>

              <div className="border-t pt-3 flex items-start gap-2 text-[11px] text-muted-foreground">
                <Info className="size-4 shrink-0 text-primary mt-0.5" />
                <span>
                  <strong>Empty Monitoring States:</strong> The dashboard accounts for four real-world states: (1) No plot registered, (2) Backfill job running, (3) Cloudy parcel showing rainfall only, and (4) Clear data available. Fake satellite data is never rendered for a real parcel.
                </span>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Institutional Roadmap */}
        <div className="flex flex-col gap-2 border-t pt-4 text-xs text-muted-foreground">
          <span className="font-semibold text-foreground">Institutional Integrity Notice:</span>
          <p className="leading-relaxed">
            Rinda AI operates independently and does not connect to or scrape internal systems of the National Land Authority (NLA), Irembo, or the Land Information Inquiry Portal (LIIP). All farmer documentation is collected with explicit farmer consent.
          </p>
        </div>
      </div>
    </DashboardLayout>
  )
}
