export type OnboardingMode = "supply" | "demand"

export type EntityKind = "person" | "organization"

/** Organization picked from the Handelsregister search (or captured manually). */
export interface FirmDraft {
  name: string
  uid?: string
  legalSeat?: string
  canton?: string
  legalForm?: string
  /** True when the user typed the firm instead of picking a Zefix hit. */
  manual?: boolean
}
