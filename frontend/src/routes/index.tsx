import { createBrowserRouter, Navigate } from "react-router-dom"

import { AppLayout } from "@/components/layout/AppLayout"
import { AuthGuard } from "@/features/auth/AuthGuard"
import { BoardPage } from "@/pages/BoardPage"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { OnboardingPage } from "@/pages/OnboardingPage"
import { ProfilePage } from "@/pages/ProfilePage"
import { SearchPage } from "@/pages/SearchPage"

/**
 * (public) routes are reachable token-free.
 * (auth) routes are wrapped by AuthGuard and redirect unauthenticated users.
 */
export const router = createBrowserRouter([
  {
    path: "/",
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/search" replace /> },

      // (public)
      { path: "search", element: <SearchPage /> },
      { path: "board", element: <BoardPage /> },
      { path: "profile/:entityId", element: <ProfilePage /> },

      // (auth)
      {
        element: <AuthGuard />,
        children: [
          { path: "onboarding", element: <OnboardingPage /> },
          { path: "profile", element: <ProfilePage /> },
        ],
      },

      { path: "*", element: <NotFoundPage /> },
    ],
  },
])
