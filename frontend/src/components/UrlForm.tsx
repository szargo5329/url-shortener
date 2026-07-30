import { useState, type FormEvent } from 'react'
import { cn } from '@/lib/utils'

interface UrlFormProps {
  /** Called with the trimmed URL on a valid submit. Awaited to drive loading. */
  onSubmit: (url: string) => Promise<void>
}

/**
 * The long-URL input + shorten button (mockup form-wrap). Owns the input value,
 * a client-side validation error, and a loading state that animates the button
 * while the submit promise is in flight.
 */
export function UrlForm({ onSubmit }: UrlFormProps) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()

    const trimmed = value.trim()
    if (!trimmed) {
      setError('Enter a URL to shorten.')
      return
    }

    setError(null)
    setLoading(true)
    try {
      await onSubmit(trimmed)
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div className="flex border border-primary/30 bg-primary/5 transition-colors focus-within:border-primary/70">
        <input
          type="text"
          value={value}
          onChange={(e) => {
            setValue(e.target.value)
            if (error) setError(null)
          }}
          disabled={loading}
          placeholder="https://your-very-long-url.com/goes/here"
          aria-label="Long URL"
          aria-invalid={error ? true : undefined}
          className="min-w-0 flex-1 bg-transparent px-4 py-3 font-mono text-xs tracking-[0.02em] text-foreground/80 caret-primary outline-none placeholder:text-foreground/20 lg:px-5 lg:py-4 lg:text-sm xl:px-6 xl:py-5 xl:text-base"
        />
        <button
          type="submit"
          disabled={loading}
          className={cn(
            'shrink-0 px-5 py-3 font-sans text-xs font-semibold uppercase tracking-[0.1em] transition-[background,transform] duration-150 lg:px-7 lg:py-4 lg:text-sm xl:px-8 xl:py-5 xl:text-base',
            loading
              ? 'animate-pulse cursor-wait bg-primary/30 text-primary'
              : 'bg-primary text-primary-foreground hover:bg-[#33f7ff] active:scale-[0.97]',
          )}
        >
          {loading ? 'Processing' : 'Shorten'}
        </button>
      </div>

      {error && (
        <p
          role="alert"
          className="mt-2 text-left font-mono text-[10px] uppercase tracking-[0.15em] text-destructive"
        >
          {error}
        </p>
      )}
    </form>
  )
}
