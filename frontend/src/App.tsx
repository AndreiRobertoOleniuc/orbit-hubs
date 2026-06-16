import { Button } from "@/components/ui/button"
import { Show, SignInButton, SignUpButton, UserButton } from "@clerk/react"
import { useApi } from "@/lib/useApi"

export function App() {
  const { request } = useApi()

  async function probeAuth() {
    try {
      await request("/api/v1/me")
      console.log("Authenticated backend request succeeded")
    } catch (e) {
      console.error("Authenticated backend request failed:", e)
    }
  }

  return (
    <div className="flex min-h-svh flex-col p-6">
      <header className="mb-6 flex items-center justify-between gap-4 border-b pb-4">
        <div className="font-medium">Orbit</div>
        <div className="flex items-center gap-2">
          <Show when="signed-out">
            <SignInButton>
              <Button variant="outline" size="sm">
                Sign in
              </Button>
            </SignInButton>
            <SignUpButton>
              <Button size="sm">Sign up</Button>
            </SignUpButton>
          </Show>
          <Show when="signed-in">
            <UserButton />
            <Button size="sm" onClick={probeAuth}>
              Probe /me
            </Button>
          </Show>
        </div>
      </header>

      <main className="flex max-w-md min-w-0 flex-col gap-4 text-sm leading-loose">
        <div>
          <h1 className="font-medium">Project ready!</h1>
          <p>You may now add components and start building.</p>
          <p>We&apos;ve already added the button component for you.</p>
          <Button className="mt-2">Button</Button>
        </div>
        <div className="font-mono text-xs text-muted-foreground">
          (Press <kbd>d</kbd> to toggle dark mode)
        </div>
      </main>
    </div>
  )
}

export default App
