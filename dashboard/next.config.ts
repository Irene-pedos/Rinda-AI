import type { NextConfig } from "next"

const backendUrl = process.env.BACKEND_URL || "http://localhost:8080"
const apiPrefix = process.env.NEXT_PUBLIC_API_PREFIX || "/api/v1"

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: `${apiPrefix}/:path*`,
        destination: `${backendUrl}${apiPrefix}/:path*`,
      },
    ]
  },
}

export default nextConfig
