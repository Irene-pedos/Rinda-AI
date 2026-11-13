"use client"

import * as React from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"

import { NavMain } from "@/components/nav-main"
import { NavSecondary } from "@/components/nav-secondary"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import {
  LayoutDashboard,
  Layers,
  ShieldAlert,
  ShieldCheck,
  Settings2,
  Sparkles,
} from "lucide-react"

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const pathname = usePathname()

  const data = {
    user: {
      name: "Demo session",
      email: "Role: insurer",
      avatar: "",
    },
    navMain: [
      {
        title: "Overview",
        url: "/dashboard",
        icon: <LayoutDashboard className="size-4" />,
        isActive: pathname === "/dashboard",
      },
      {
        title: "Plots Directory",
        url: "/dashboard/plots",
        icon: <Layers className="size-4" />,
        isActive: pathname?.startsWith("/dashboard/plots"),
      },
      {
        title: "Review Queue",
        url: "/dashboard/review-queue",
        icon: <ShieldAlert className="size-4" />,
        isActive: pathname?.startsWith("/dashboard/review-queue"),
      },
      {
        title: "Claims & Payouts",
        url: "/dashboard/claims",
        icon: <Sparkles className="size-4" />,
        isActive: pathname?.startsWith("/dashboard/claims"),
      },
    ],
    navSecondary: [
      {
        title: "Verification Rules",
        url: "/dashboard/rules",
        icon: <ShieldCheck className="size-4" />,
      },
      {
        title: "Platform Settings",
        url: "/dashboard/settings",
        icon: <Settings2 className="size-4" />,
      },
    ],
  }

  return (
    <Sidebar collapsible="offcanvas" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              className="data-[slot=sidebar-menu-button]:p-2"
              render={<Link href="/dashboard" />}
            >
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <ShieldCheck className="size-5" />
              </div>
              <div className="grid flex-1 text-left leading-tight">
                <span className="truncate font-semibold tracking-tight">Rinda AI</span>
                <span className="truncate text-xs text-muted-foreground">Insurer Dashboard</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={data.navMain} />
        <NavSecondary items={data.navSecondary} className="mt-auto" />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={data.user} />
      </SidebarFooter>
    </Sidebar>
  )
}
