/**
 * API types and client for the URL shortener backend.
 *
 * NOTE: `shortenUrl` is currently a MOCK that returns fake data after a short
 * delay so the UI flow (submit → loading → result) can be exercised. Real API
 * integration (fetch to VITE_API_BASE_URL) replaces the body in the next step.
 */

/** Response shape of `POST /shorten` (snake_case, matching the backend JSON). */
export interface ShortenResponse {
  short_code: string
  short_url: string
  long_url: string
  created_at: string
  expires_at: string | null
}

/** Mock: pretends to shorten a URL, resolving with fake data after ~900ms. */
export async function shortenUrl(longUrl: string): Promise<ShortenResponse> {
  await new Promise((resolve) => setTimeout(resolve, 900))

  const code = Math.random().toString(36).slice(2, 9)
  const now = new Date()
  const expires = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)

  return {
    short_code: code,
    short_url: `https://shrt.io/${code}`,
    long_url: longUrl,
    created_at: now.toISOString(),
    expires_at: expires.toISOString(),
  }
}
