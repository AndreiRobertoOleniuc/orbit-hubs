/**
 * API DTOs matching currently implemented backend endpoints only.
 * Types for future endpoints stay out until the backend contract is real.
 * IDs are human-readable string slugs; they are typed as string, never UUID.
 */

export interface MeDto {
  userId: string
  email: string
}
