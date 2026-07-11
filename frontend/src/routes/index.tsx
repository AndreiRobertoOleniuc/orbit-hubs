import { createBrowserRouter } from "react-router-dom"

import { AppLayout } from "@/components/layout/AppLayout"
import { AuthGuard } from "@/features/auth/AuthGuard"
import { AdminHubsPage } from "@/pages/AdminHubsPage"
import { BoardPage } from "@/pages/BoardPage"
import { InboxPage } from "@/pages/InboxPage"
import { MyRequestsPage } from "@/pages/MyRequestsPage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { OnboardingPage } from "@/pages/OnboardingPage"
import { ProfilePage } from "@/pages/ProfilePage"
import { SearchPage } from "@/pages/SearchPage"
import { StartPage } from "@/pages/StartPage"

/**
 * (public) routes are reachable token-free.
 * (auth) routes are wrapped by AuthGuard and redirect unauthenticated users.
 */
export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      // (public) — search & board are browsable without an account
      { index: true, element: <StartPage /> },
      { path: "search", element: <SearchPage /> },
      { path: "board", element: <BoardPage /> },
      { path: "profile/:entityId", element: <ProfilePage /> },

      // (auth)
      {
        element: <AuthGuard />,
        children: [
          { path: "onboarding", element: <OnboardingPage /> },
          { path: "profile", element: <ProfilePage /> },
          { path: "requests", element: <MyRequestsPage /> },
          { path: "inbox", element: <InboxPage /> },
          { path: "admin/hubs", element: <AdminHubsPage /> },
        ],
      },

      { path: "*", element: <NotFoundPage /> },
    ],
  },
])
