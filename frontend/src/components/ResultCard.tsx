import { useState } from 'react'
import { cn } from '@/lib/utils'
import type { ShortenResponse } from '@/services/api'

interface ResultCardProps {
  /** The shorten result, or null before anything has been shortened. */
  result: ShortenResponse | null
}

/**
 * Displays a shorten result (mockup result-panel): the short URL prominently
 * with a copy button, plus the remaining metadata. Renders nothing until a
 * result exists.
 */
export function ResultCard({ result }: ResultCardProps) {
  const [copied, setCopied] = useState(false)

  if (!result) return null

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(result!.short_url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Clipboard API may be unavailable (e.g. insecure context) — ignore.
    }
  }

  return (
    <div className="mt-4 border border-primary/25 bg-primary/5 p-4 text-left duration-300 animate-in fade-in slide-in-from-bottom-1 lg:mt-5 lg:p-5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="size-1.5 shrink-0 animate-pulse bg-primary" aria-hidden />
          <div className="min-w-0">
            <span className="block font-mono text-[9px] uppercase tracking-[0.15em] text-primary/40">
              shortened url
            </span>
            <a
              href={result.short_url}
              target="_blank"
              rel="noreferrer"
              className="block truncate font-mono text-sm tracking-[0.04em] text-primary hover:underline lg:text-base"
            >
              {result.short_url}
            </a>
          </div>
        </div>

        <button
          type="button"
          onClick={handleCopy}
          className={cn(
            'shrink-0 border px-3 py-1.5 font-mono text-[10px] uppercase tracking-[0.1em] transition-colors',
            copied
              ? 'border-primary/60 text-primary'
              : 'border-primary/25 text-primary/60 hover:border-primary/60 hover:text-primary',
          )}
        >
          {copied ? 'Copied ✓' : 'Copy'}
        </button>
      </div>

      <dl className="mt-4 grid grid-cols-1 gap-3 border-t border-primary/10 pt-3 sm:grid-cols-2">
        <Meta label="short code" value={result.short_code} />
        <Meta label="expires at" value={formatDate(result.expires_at)} />
        <Meta label="original url" value={result.long_url} className="sm:col-span-2" />
        <Meta label="created at" value={formatDate(result.created_at)} />
      </dl>
    </div>
  )
}

function Meta({
  label,
  value,
  className,
}: {
  label: string
  value: string
  className?: string
}) {
  return (
    <div className={cn('min-w-0', className)}>
      <dt className="font-mono text-[10px] uppercase tracking-[0.15em] text-primary/40">{label}</dt>
      <dd className="truncate font-mono text-sm text-foreground/70">{value}</dd>
    </div>
  )
}

function formatDate(iso: string | null): string {
  if (!iso) return 'never'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}
