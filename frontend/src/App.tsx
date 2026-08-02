import { useState } from 'react'
import { UrlForm } from '@/components/UrlForm'
import { ResultCard } from '@/components/ResultCard'
import { shortenUrl, type ShortenResponse } from '@/services/api'

type Status = 'idle' | 'loading' | 'success' | 'error'

function App() {
  const [status, setStatus] = useState<Status>('idle')
  const [result, setResult] = useState<ShortenResponse | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [count, setCount] = useState(0)

  async function handleShorten(url: string) {
    setStatus('loading')
    setErrorMessage(null)
    setResult(null)
    try {
      const res = await shortenUrl(url)
      setResult(res)
      setStatus('success')
      setCount((c) => c + 1)
    } catch (err) {
      // Show the backend's own message (from GlobalExceptionHandler) when present.
      setErrorMessage(err instanceof Error ? err.message : 'Request failed. Please try again.')
      setStatus('error')
    }
  }

  return (
    <div className="relative z-10 min-h-screen">
      {/* Decorative corner brackets (mockup framing). */}
      <div className="pointer-events-none fixed left-3 top-3 size-5 border-l border-t border-primary/30" />
      <div className="pointer-events-none fixed bottom-3 right-3 size-5 border-b border-r border-primary/30" />

      {/* Full-width container: nav spans edge-to-edge, main is centered. */}
      <div className="flex min-h-screen flex-col px-6 py-8 sm:px-10">
        <nav className="mb-16 flex items-center justify-between">
          <div className="font-mono text-base uppercase tracking-[0.12em] text-primary lg:text-lg xl:text-xl">
            SHR<span className="text-primary/40">.</span>T
          </div>
          <div className="border border-primary/20 px-2.5 py-1 font-mono text-xs tracking-[0.15em] text-primary/40 lg:px-3 lg:py-1.5 lg:text-sm">
            v1.0.0 — BETA
          </div>
        </nav>

        <main className="mx-auto mt-8 w-full max-w-[560px] text-center lg:mt-12 lg:max-w-3xl xl:max-w-4xl">
          <p className="mb-3 font-mono text-[10px] uppercase tracking-[0.2em] text-primary/50 lg:mb-4 lg:text-xs">
            // url compression utility
          </p>
          <h1 className="mb-2 font-sans text-[28px] font-bold leading-tight tracking-tight text-foreground lg:mb-3 lg:text-[38px] xl:text-[46px]">
            Paste. Shrink.
            <br />
            <em className="not-italic text-primary">Done.</em>
          </h1>
          <p className="mb-8 text-[13px] tracking-[0.02em] text-foreground/40 lg:mb-10 lg:text-base xl:mb-12 xl:text-lg">
            Paste any URL. Get a short, clean link instantly.
          </p>

          <UrlForm onSubmit={handleShorten} />

          {status === 'error' && errorMessage && (
            <p
              role="alert"
              className="mt-4 border border-destructive/40 bg-destructive/10 px-4 py-3 text-left font-mono text-xs text-destructive"
            >
              {errorMessage}
            </p>
          )}

          <ResultCard result={status === 'success' ? result : null} />

          <div className="mt-10 flex justify-center gap-8 border-t border-primary/10 pt-6 lg:mt-14 lg:gap-14 lg:pt-8">
            <Stat value={String(count)} label="Links shortened" />
            <Stat value="302" label="Redirect type" />
            <Stat value="<50ms" label="Avg. redirect" />
          </div>
        </main>
      </div>
    </div>
  )
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="text-center">
      <span className="block font-mono text-lg font-medium text-foreground/80 lg:text-2xl">{value}</span>
      <span className="mt-0.5 block text-[10px] uppercase tracking-[0.15em] text-foreground/25 lg:text-xs">
        {label}
      </span>
    </div>
  )
}

export default App
