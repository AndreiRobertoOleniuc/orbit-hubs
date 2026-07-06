import { createBrowserRouter } from "react-router-dom"

import { AppLayout } from "@/components/layout/AppLayout"
import { AuthGuard } from "@/features/auth/AuthGuard"
import { NotFoundPage } from "@/pages/NotFoundPage"
import { OnboardingPage } from "@/pages/OnboardingPage"
import { ProfilePage } from "@/pages/ProfilePage"
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
      // (public)
      { index: true, element: <StartPage /> },
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
