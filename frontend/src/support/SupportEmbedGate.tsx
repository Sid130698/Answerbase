import { useEffect, useState, type ReactNode } from 'react'
import { IdentityContext, type Identity } from '../auth/identity'
import { delegatedFetch, endSupportView, setSupportGrant, supportOrigin } from './transport'

export function SupportEmbedGate({ children }: { children: ReactNode }) {
  const [identity, setIdentity] = useState<Identity | null>(null)
  const [error, setError] = useState(window.parent === window ? 'Open this customer view from the Answerbase support console.' : 'Waiting for the support console…')
  useEffect(() => {
    let generation = 0
    let timer: ReturnType<typeof setTimeout> | undefined
    const ended = () => { generation++; setIdentity(null); setError('Support access has ended. Return to the console to start a new session.') }
    const receive = async (event: MessageEvent) => {
      if (event.origin !== supportOrigin || event.source !== window.parent || window.parent === window) return
      if (event.data?.type === 'answerbase:end') { setSupportGrant(null); ended(); return }
      if (event.data?.type !== 'answerbase:grant' || typeof event.data.token !== 'string' || typeof event.data.expiresAt !== 'string') return
      const remaining = Date.parse(event.data.expiresAt) - Date.now()
      if (!Number.isFinite(remaining) || remaining <= 0) { ended(); return }
      const current = ++generation
      setIdentity(null); setSupportGrant(event.data.token)
      clearTimeout(timer)
      timer = setTimeout(() => { setSupportGrant(null); ended() }, remaining)
      try {
        const response = await delegatedFetch('/me')
        if (!response.ok) throw new Error('Unable to open this support session.')
        const data = await response.json() as Identity
        if (current === generation) setIdentity(data)
      } catch (cause) { if (current === generation) setError(cause instanceof Error ? cause.message : 'Unable to open session.') }
    }
    window.addEventListener('message', receive)
    window.addEventListener('support-ended', ended)
    if (window.parent !== window) window.parent.postMessage({ type: 'answerbase:ready' }, supportOrigin)
    return () => { generation++; clearTimeout(timer); setSupportGrant(null); window.removeEventListener('message', receive); window.removeEventListener('support-ended', ended) }
  }, [])
  if (!identity) return <main><h1>Support customer view</h1><p role="status">{error}</p></main>
  return <IdentityContext.Provider value={identity}><div className="support-view-banner">Support session · acting as {identity.id}<button className="button secondary" onClick={endSupportView}>End customer view</button></div>{children}</IdentityContext.Provider>
}
