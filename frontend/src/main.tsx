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

/**
 * Clerk verwaltet die Anmeldung, nicht das Orbit-Profil — im Test wurden beide
 * verwechselt (das Trennen des Google-Kontos löschte erwartungsgemäss nichts am
 * Profil). Clerks Default-Wortwahl verstärkt das: "Konto verwalten" im Menü und
 * "Profil" als Reiter im Dialog. Beides wird hier auf die Anmeldung umbenannt,
 * damit "Profil" in Orbit eindeutig das fachliche Profil bezeichnet.
 */
const clerkLocalization = {
  ...deDE,
  userButton: {
    ...deDE.userButton,
    action__manageAccount: "Konto & Anmeldung",
  },
  userProfile: {
    ...deDE.userProfile,
    navbar: {
      ...deDE.userProfile?.navbar,
      title: "Konto & Anmeldung",
      account: "Konto",
      // Siezt, weil der übrige Clerk-Dialog aus `deDE` durchgehend siezt —
      // ein einzelnes "du" mitten darin fiele auf. Orbits eigene Oberfläche
      // duzt weiterhin.
      description:
        "Verwalten Sie Ihre Anmeldedaten und verknüpften Konten. Ihr Orbit-Profil bearbeiten Sie unter «Mein Orbit-Profil».",
    },
  },
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Provider store={store}>
      <ClerkProvider
        publishableKey={import.meta.env.VITE_CLERK_PUBLISHABLE_KEY}
        afterSignOutUrl="/"
        localization={clerkLocalization}
        appearance={{
          cssLayerName: "clerk",
          variables: {
            colorPrimary: "var(--primary)",
            colorBackground: "var(--card)",
            colorForeground: "var(--card-foreground)",
            colorMutedForeground: "var(--muted-foreground)",
            colorInput: "var(--background)",
            colorInputForeground: "var(--foreground)",
            colorBorder: "var(--border)",
            colorDanger: "var(--destructive)",
            borderRadius: "var(--radius)",
            fontFamily: "var(--font-sans)",
          },
          elements: {
            cardBox: "shadow-xl shadow-black/10 dark:shadow-black/30",
            card: "border border-border",
            modalBackdrop: "bg-foreground/20 backdrop-blur-sm",
          },
        }}
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
