import { notFound } from "next/navigation"
import { DashboardLayout } from "@/components/dashboard-layout"
import { ClaimCaseFileView } from "@/components/claim-case-file-view"
import { apiClient } from "@/lib/api/client"

export default async function ClaimCaseFilePage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const claimId = parseInt(id, 10)
  if (isNaN(claimId)) notFound()

  const caseFile = await apiClient.getClaimCaseFile(claimId)
  if (!caseFile) notFound()

  return (
    <DashboardLayout title={`Claim Case File #CLM-000${caseFile.id}`}>
      <div className="@container/main flex flex-1 flex-col gap-4 py-4 md:py-6 px-4 lg:px-6">
        <ClaimCaseFileView initialCaseFile={caseFile} />
      </div>
    </DashboardLayout>
  )
}
