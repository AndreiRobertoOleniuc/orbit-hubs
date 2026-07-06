import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

/** A titled card section used across the profile views. */
export function ProfileSectionCard({
  title,
  children,
}: {
  title: string
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}