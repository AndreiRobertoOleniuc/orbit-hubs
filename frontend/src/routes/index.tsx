import { createBrowserRouter } from "react-router"

import { AppLayout } from "@/components/layout/AppLayout"
import { AuthGuard } from "@/features/auth/AuthGuard"
import { AdminHubsPage } from "@/pages/AdminHubsPage"
import { BoardPage } from "@/pages/BoardPage"
import { HowItWorksPage } from "@/pages/HowItWorksPage"
import { InboxPage } from "@/pages/InboxPage"
import { MyRequestsPage } from "@/pages/MyRequestsPage"
import { NewRequestPage } from "@/pages/NewRequestPage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { OnboardingPage } from "@/pages/OnboardingPage"
import { ProfilePage } from "@/pages/ProfilePage"
import { RequestDetailPage } from "@/pages/RequestDetailPage"
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
      { path: "info", element: <HowItWorksPage /> },
      { path: "board", element: <BoardPage /> },
      { path: "board/:requestId", element: <RequestDetailPage /> },
      { path: "profile/:entityId", element: <ProfilePage /> },

      // (auth)
      {
        element: <AuthGuard />,
        children: [
          { path: "onboarding", element: <OnboardingPage /> },
          { path: "profile", element: <ProfilePage /> },
          { path: "requests", element: <MyRequestsPage /> },
          { path: "requests/new", element: <NewRequestPage /> },
          { path: "inbox", element: <InboxPage /> },
          { path: "admin/hubs", element: <AdminHubsPage /> },
        ],
      },

      { path: "*", element: <NotFoundPage /> },
    ],
  },
])
