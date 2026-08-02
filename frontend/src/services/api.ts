/**
 * API client for the URL shortener backend.
 *
 * The base URL comes from VITE_API_BASE_URL: in development that is the Vite
 * dev-server proxy path (`/api`), which keeps requests same-origin and avoids
 * CORS; in production it is the API Gateway URL, called directly from the
 * CloudFront origin the backend's CORS policy allows.
 *
 * Only POST /shorten is fetched here. GET /{code} is a plain <a> link — a normal
 * top-level browser navigation, which CORS does not apply to.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

/** Response shape of `POST /shorten` (snake_case, matching the backend JSON). */
export interface ShortenResponse {
  short_code: string
  short_url: string
  long_url: string
  created_at: string
  expires_at: string | null
}

/** Error body shape produced by the backend's GlobalExceptionHandler. */
interface ApiErrorBody {
  error?: string
}

/**
 * Shortens a long URL.
 *
 * @throws Error carrying the backend's own `error` message on a non-2xx response.
 */
export async function shortenUrl(longUrl: string): Promise<ShortenResponse> {
  const response = await fetch(`${API_BASE_URL}/shorten`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ long_url: longUrl }),
  })

  if (!response.ok) {
    // Surface the backend's actual message ({ "error": "..." }) rather than a
    // generic one. Fall back only if the body is missing or unparseable.
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as ApiErrorBody
      if (body?.error) message = body.error
    } catch {
      // Non-JSON body — keep the status-based fallback.
    }
    throw new Error(message)
  }

  return (await response.json()) as ShortenResponse
}
