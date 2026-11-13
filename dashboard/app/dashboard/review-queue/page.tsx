import { DashboardLayout } from "@/components/dashboard-layout"
import { ReviewQueueView } from "@/components/review-queue-view"
import { apiClient } from "@/lib/api/client"

export default async function ReviewQueuePage() {
  const { items: reviewPlots } = await apiClient.getReviewQueue()

  return (
    <DashboardLayout title="Review & Dispute Queue">
      <div className="@container/main flex flex-1 flex-col gap-4 py-4 md:py-6 px-4 lg:px-6">
        <ReviewQueueView initialPlots={reviewPlots} />
      </div>
    </DashboardLayout>
  )
}
