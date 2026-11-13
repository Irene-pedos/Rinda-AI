import { DashboardLayout } from "@/components/dashboard-layout"
import { ClaimsTable } from "@/components/claims-table"
import { apiClient } from "@/lib/api/client"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Sparkles, Clock, FileSearch, CheckCircle2, Banknote, ShieldAlert } from "lucide-react"

export default async function ClaimsPage() {
  const { items: claims } = await apiClient.getOfficerClaims()

  const underReviewCount = claims.filter((c) => c.status === "UNDER_REVIEW").length
  const fieldVisitCount = claims.filter((c) => c.status === "FIELD_VISIT_REQUESTED").length
  const approvedUnpaidCount = claims.filter((c) => c.status === "APPROVED").length
  const paidTotal = claims
    .filter((c) => c.status === "PAID" && c.amount != null)
    .reduce((sum, c) => sum + (c.amount || 0), 0)

  return (
    <DashboardLayout title="Claims & Payout Authorization">
      <div className="@container/main flex flex-1 flex-col gap-5 py-4 md:py-6 px-4 lg:px-6">
        {/* Header Summary */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
              <Sparkles className="size-6 text-primary" />
              Parametric Claims & Payout Queue
            </h1>
            <p className="text-xs text-muted-foreground mt-0.5">
              Automated parametric trigger evaluation, evidence snapshots, and payout authorization.
            </p>
          </div>
          <Badge variant="outline" className="font-mono text-xs w-fit">
            Season 2026A • Maize Scope
          </Badge>
        </div>

        {/* Section Cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Card className="bg-card shadow-xs">
            <CardHeader className="pb-2">
              <CardDescription className="text-xs flex items-center gap-1.5 font-medium">
                <Clock className="size-3.5 text-amber-600" />
                Awaiting Decision
              </CardDescription>
              <CardTitle className="text-2xl font-bold tabular-nums text-amber-700 dark:text-amber-400">
                {underReviewCount}
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-0 text-[11px] text-muted-foreground">
              Needs insurer officer review
            </CardContent>
          </Card>

          <Card className="bg-card shadow-xs">
            <CardHeader className="pb-2">
              <CardDescription className="text-xs flex items-center gap-1.5 font-medium">
                <FileSearch className="size-3.5 text-sky-600" />
                Field Visit Required
              </CardDescription>
              <CardTitle className="text-2xl font-bold tabular-nums text-sky-700 dark:text-sky-400">
                {fieldVisitCount}
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-0 text-[11px] text-muted-foreground">
              Dispatched to cooperative agent
            </CardContent>
          </Card>

          <Card className="bg-card shadow-xs">
            <CardHeader className="pb-2">
              <CardDescription className="text-xs flex items-center gap-1.5 font-medium">
                <CheckCircle2 className="size-3.5 text-emerald-600" />
                Approved (Unpaid)
              </CardDescription>
              <CardTitle className="text-2xl font-bold tabular-nums text-emerald-700 dark:text-emerald-400">
                {approvedUnpaidCount}
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-0 text-[11px] text-muted-foreground">
              Awaiting payout execution
            </CardContent>
          </Card>

          <Card className="bg-card shadow-xs">
            <CardHeader className="pb-2">
              <CardDescription className="text-xs flex items-center gap-1.5 font-medium">
                <Banknote className="size-3.5 text-emerald-600" />
                Simulated Paid Total
              </CardDescription>
              <CardTitle className="text-2xl font-bold tabular-nums text-foreground">
                {paidTotal.toLocaleString()} RWF
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-0 text-[11px] text-muted-foreground">
              MTN MoMo simulated transfer
            </CardContent>
          </Card>
        </div>

        {/* Policy Rule Warning */}
        <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50/70 dark:border-amber-900 dark:bg-amber-950/20 p-3 text-xs text-amber-900 dark:text-amber-200">
          <ShieldAlert className="size-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
          <div className="leading-relaxed">
            <strong className="font-semibold">Payout Gate Rule: </strong>
            Claims can only be approved and paid out if the plot verification level is{" "}
            <code className="font-mono font-semibold">FIELD_VERIFIED</code> or higher AND status is{" "}
            <code className="font-mono font-semibold">ACTIVE</code>. For plots at{" "}
            <code className="font-mono">DOCUMENT_SUPPORTED</code> or in dispute, financial actions remain disabled.
          </div>
        </div>

        {/* Interactive Claims Table */}
        <ClaimsTable initialClaims={claims} />
      </div>
    </DashboardLayout>
  )
}
