import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { Provider } from "react-redux"

import { ClerkProvider } from "@clerk/react"
import { deDE } from "@clerk/localizations"

import "./index.css"
import App from "./App.tsx"
import { ThemeProvider } from "@/components/theme-provider.tsx"
import { AuthProvider } from "@/features/auth/AuthProvider"
import { store } from "@/lib/store"

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Provider store={store}>
      <ClerkProvider
        publishableKey={import.meta.env.VITE_CLERK_PUBLISHABLE_KEY}
        afterSignOutUrl="/"
        localization={deDE}
      >
        <ThemeProvider defaultTheme="light" storageKey="orbit-theme">
          <AuthProvider>
            <App />
          </AuthProvider>
        </ThemeProvider>
      </ClerkProvider>
    </Provider>
  </StrictMode>
)
