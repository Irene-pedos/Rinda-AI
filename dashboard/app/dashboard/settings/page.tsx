import { DashboardLayout } from "@/components/dashboard-layout"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Settings2,
  ShieldCheck,
  Server,
  Sprout,
  AlertCircle,
  Lock,
  Cpu,
  CheckCircle2,
} from "lucide-react"

export default function SettingsPage() {
  const isMock = process.env.NEXT_PUBLIC_USE_MOCK_API !== "false"
  const backendUrl = process.env.BACKEND_URL || "http://localhost:8080"
  const apiPrefix = process.env.NEXT_PUBLIC_API_PREFIX || "/api/v1"

  return (
    <DashboardLayout title="Platform & Policy Settings">
      <div className="@container/main flex flex-1 flex-col gap-6 py-4 md:py-6 px-4 lg:px-6 max-w-4xl">
        {/* Header Banner */}
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
            <Settings2 className="size-6 text-primary" />
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
              Insurer Platform Configuration & Policy Thresholds
            </h1>
          </div>
          <p className="text-xs text-muted-foreground leading-relaxed">
            Core parameters governing automatic validation thresholds, insurance season codes, backend connectivity, and security guards.
          </p>
        </div>

        {/* Section 1: Underwriting & Validation Thresholds */}
        <Card className="bg-card shadow-xs">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-sm font-semibold flex items-center gap-2">
                  <ShieldCheck className="size-4 text-primary" />
                  Parametric Underwriting & Validation Thresholds
                </CardTitle>
                <CardDescription className="text-xs">
                  Values configured in agreement with the insurer. Changes require administrative privileges.
                </CardDescription>
              </div>
              <Badge variant="outline" className="font-mono text-xs">
                Season 2026A
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 pt-0 text-xs">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="area-tolerance" className="text-xs font-semibold">
                  Area Mismatch Tolerance Threshold
                </Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="area-tolerance"
                    defaultValue="15%"
                    disabled
                    className="font-mono text-xs max-w-[140px]"
                  />
                  <span className="text-[11px] text-muted-foreground">
                    Flags difference between walk & doc area
                  </span>
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="pixel-threshold" className="text-xs font-semibold">
                  Minimum Clear Satellite Pixels
                </Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="pixel-threshold"
                    defaultValue="60 pixels"
                    disabled
                    className="font-mono text-xs max-w-[140px]"
                  />
                  <span className="text-[11px] text-muted-foreground">
                    Required for high-confidence NDVI curve
                  </span>
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="dryspell-threshold" className="text-xs font-semibold">
                  Parametric Dry-Spell Trigger
                </Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="dryspell-threshold"
                    defaultValue="21 days"
                    disabled
                    className="font-mono text-xs max-w-[140px]"
                  />
                  <span className="text-[11px] text-muted-foreground">
                    Consecutive dry days in vegetative stage
                  </span>
                </div>
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="gps-accuracy" className="text-xs font-semibold">
                  Max GPS Corner Walk Inaccuracy
                </Label>
                <div className="flex items-center gap-2">
                  <Input
                    id="gps-accuracy"
                    defaultValue="5.0 meters"
                    disabled
                    className="font-mono text-xs max-w-[140px]"
                  />
                  <span className="text-[11px] text-muted-foreground">
                    Requires re-walk if accuracy exceeds limit
                  </span>
                </div>
              </div>
            </div>

            <div className="rounded-md border bg-muted/10 p-2.5 text-[11px] text-muted-foreground flex items-center justify-between">
              <span>Thresholds synced from backend <code>/api/v1/admin/thresholds</code>.</span>
              <Button size="sm" variant="ghost" disabled className="h-6 text-[10px] gap-1 opacity-60">
                <Lock className="size-3" />
                Admin Locked
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Section 2: Crop & Regional Scope */}
        <Card className="bg-card shadow-xs">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Sprout className="size-4 text-emerald-600" />
              Crop & Geographic Scope
            </CardTitle>
            <CardDescription className="text-xs">
              Operational coverage boundaries for Rinda AI Rwanda
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 pt-0 text-xs">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="p-3 rounded-md border bg-muted/10 flex flex-col gap-1">
                <span className="text-muted-foreground text-[10px]">Active Crop Scope</span>
                <span className="font-bold text-sm text-foreground flex items-center gap-1.5">
                  <Sprout className="size-4 text-emerald-600" />
                  Maize (Zea mays)
                </span>
                <span className="text-[10px] text-muted-foreground">
                  Strictly maize only. Crop selectors are intentionally disabled.
                </span>
              </div>

              <div className="p-3 rounded-md border bg-muted/10 flex flex-col gap-1">
                <span className="text-muted-foreground text-[10px]">Active Season</span>
                <span className="font-bold text-sm text-foreground flex items-center gap-1.5 font-mono">
                  Season 2026A
                </span>
                <span className="text-[10px] text-muted-foreground">
                  September 2026 – January 2027
                </span>
              </div>

              <div className="p-3 rounded-md border bg-muted/10 flex flex-col gap-1">
                <span className="text-muted-foreground text-[10px]">Territory Jurisdiction</span>
                <span className="font-bold text-sm text-foreground flex items-center gap-1.5">
                  Republic of Rwanda
                </span>
                <span className="text-[10px] text-muted-foreground">
                  National UPI format (Province / District / Sector / Cell)
                </span>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Section 3: Environment & Integration Architecture */}
        <Card className="bg-card shadow-xs">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Server className="size-4 text-primary" />
              API Proxy & Environment Integration
            </CardTitle>
            <CardDescription className="text-xs">
              Next.js server-side rewrites and data source configuration
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 pt-0 text-xs">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3 rounded-md border flex flex-col gap-1">
                <span className="text-muted-foreground text-[10px]">Backend Proxy Target</span>
                <span className="font-mono font-semibold text-foreground break-all">
                  {backendUrl}
                </span>
                <span className="text-[10px] text-muted-foreground">
                  Rewrites in <code>next.config.ts</code> bypass browser CORS restrictions.
                </span>
              </div>

              <div className="p-3 rounded-md border flex flex-col gap-1">
                <span className="text-muted-foreground text-[10px]">API Prefix</span>
                <span className="font-mono font-semibold text-foreground">
                  {apiPrefix}
                </span>
                <span className="text-[10px] text-muted-foreground">
                  Target endpoints conform to <code>docs/openapi.json</code>.
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-2 p-3 rounded-md border bg-amber-50/50 dark:bg-amber-950/20 border-amber-200 dark:border-amber-900">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-xs text-amber-900 dark:text-amber-200 flex items-center gap-1.5">
                  <AlertCircle className="size-3.5 text-amber-600" />
                  Active Data Mode: {isMock ? "Mock Synthetic Layer" : "Live Production Server"}
                </span>
                <Badge
                  variant="outline"
                  className="font-mono text-[10px] border-amber-300 text-amber-900 bg-amber-100 dark:bg-amber-950 dark:text-amber-300"
                >
                  {isMock ? "MOCK_API=true" : "MOCK_API=false"}
                </Badge>
              </div>
              <p className="text-[11px] text-amber-800 dark:text-amber-300">
                Mock mode uses obviously synthetic names and test UPIs (e.g. <code>1/01/01/01/0001</code>). When set to false, the dashboard enforces the authentication safety blocking screen before rendering any real farmer data.
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Section 4: Current Session Identity */}
        <Card className="bg-card shadow-xs">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-semibold flex items-center gap-2">
              <Cpu className="size-4 text-primary" />
              Active Session Details
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0 text-xs flex flex-col gap-2">
            <div className="flex items-center justify-between p-2.5 rounded-md border bg-muted/10 font-mono text-[11px]">
              <span className="text-muted-foreground">Session Name:</span>
              <span className="font-semibold text-foreground">Demo session</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-md border bg-muted/10 font-mono text-[11px]">
              <span className="text-muted-foreground">Assigned Role:</span>
              <span className="font-semibold text-primary">insurer (Insurance Officer)</span>
            </div>
            <div className="flex items-center justify-between p-2.5 rounded-md border bg-muted/10 font-mono text-[11px]">
              <span className="text-muted-foreground">Security Guard:</span>
              <span className="font-semibold text-emerald-700 dark:text-emerald-400 flex items-center gap-1">
                <CheckCircle2 className="size-3" />
                Auth Guard Active
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  )
}
