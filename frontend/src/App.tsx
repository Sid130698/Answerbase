import { useEffect, useState } from 'react'
import './App.css'

type Connection = 'checking' | 'ready' | 'unavailable'
const apiBase = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export default function App() {
  const [connection, setConnection] = useState<Connection>('checking')

  useEffect(() => {
    let disposed = false
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 5000)
    fetch(`${apiBase}/actuator/health/readiness`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('Backend unavailable')
        const body: { status?: string } = await response.json()
        if (!disposed) setConnection(body.status === 'UP' ? 'ready' : 'unavailable')
      })
      .catch(() => { if (!disposed) setConnection('unavailable') })
      .finally(() => window.clearTimeout(timeout))
    return () => {
      disposed = true
      controller.abort()
      window.clearTimeout(timeout)
    }
  }, [])

  return (
    <main>
      <header><a className="brand" href="/">Answerbase<span> / development</span></a></header>
      <section className="intro">
        <p className="eyebrow">Repository foundation</p>
        <h1>From documents<br />to verifiable answers.</h1>
        <p className="lede">The application scaffold is ready. Document ingestion, workspace access,
          and cited question answering are the next implementation steps.</p>
      </section>
      <section className="status" aria-label="System status">
        <div><h2>Backend readiness</h2><p>Live check of the API and its database connection.</p></div>
        <span className={`badge ${connection}`} role="status">
          {connection === 'checking' ? 'Checking connection…' : connection === 'ready' ? 'Connected' : 'Unavailable'}
        </span>
      </section>
      {connection === 'unavailable' && <p className="help">Start the local stack with <code>docker compose up --build -d --wait</code>, then reload.</p>}
      <section className="modules" aria-label="Planned product capabilities">
        <article><span>01</span><h2>Workspace access</h2><p>Verified identities, membership, and document permissions.</p><small>Not implemented</small></article>
        <article><span>02</span><h2>Document ingestion</h2><p>Versioned uploads with asynchronous parsing and indexing.</p><small>Not implemented</small></article>
        <article><span>03</span><h2>Cited answers</h2><p>Permission-aware retrieval, evidence, and measured quality.</p><small>Not implemented</small></article>
      </section>
      <footer>Local scaffold · No LLM calls · No customer documents</footer>
    </main>
  )
}
