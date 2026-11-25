import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Workspace } from '../workspaces/WorkspaceGate'
import { supabase } from '../auth/client'
import { delegatedFetch, supportMode } from '../support/transport'
import './sources.css'

type Version = { id: string; documentId: string; versionNumber: number; filename: string; mediaType: string; byteSize: number; status: string; error: string | null; createdAt: string }
type Document = { id: string; title: string; latest: Version; processedVersionId: string | null }
type Chunk = { ordinal: number; pageNumber: number | null; startLine: number | null; endLine: number | null; content: string }
const statusLabel: Record<string, string> = { queued: 'Queued', processing: 'Extracting text', processed: 'Text processed', failed: 'Needs attention' }
const problem = (error: unknown) => error instanceof Error ? error.message : 'Request failed. Please retry.'
async function request(path: string, init?: RequestInit) {
  let response: Response
  if (supportMode) response = await delegatedFetch(path, init)
  else {
    const { data } = await supabase!.auth.getSession()
    if (!data.session) throw new Error('Sign in to continue.')
    response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/v1${path}`, { ...init, signal: AbortSignal.timeout(60000), headers: { ...init?.headers, Authorization: `Bearer ${data.session.access_token}` } })
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(response.status === 413 ? 'Choose a file no larger than 5 MB.' : body?.detail || 'Unable to complete this request. Please retry.')
  }
  return response
}
function documentId() { return new URLSearchParams(window.location.search).get('document') }
function selectDocument(id: string | null) {
  const url = new URL(window.location.href)
  if (id) url.searchParams.set('document', id); else url.searchParams.delete('document')
  window.history.pushState(null, '', url); window.dispatchEvent(new PopStateEvent('popstate'))
}
export function Sources({ workspace }: { workspace: Workspace }) {
  const [documents, setDocuments] = useState<Document[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [selected, setSelected] = useState(documentId)
  const [file, setFile] = useState<File | null>(null)
  const [replacement, setReplacement] = useState('')
  const [busy, setBusy] = useState(false)
  const key = useRef(crypto.randomUUID())
  const input = useRef<HTMLInputElement>(null)
  const canWrite = ['admin', 'editor'].includes(workspace.role)
  const base = `/workspaces/${workspace.id}/documents`
  useEffect(() => { const sync = () => setSelected(documentId()); window.addEventListener('popstate', sync); return () => window.removeEventListener('popstate', sync) }, [])
  useEffect(() => {
    let active = true
    request(base).then(response => response.json()).then((rows: Document[]) => { if (active) { setDocuments(rows); setError('') } }).catch(cause => { if (active) setError(problem(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [base, refresh])
  const processing = documents.some(doc => ['queued', 'processing'].includes(doc.latest.status))
  useEffect(() => {
    if (!processing) return
    const timer = setInterval(() => setRefresh(value => value + 1), 3000)
    return () => clearInterval(timer)
  }, [processing])
  async function upload(event: FormEvent) {
    event.preventDefault(); if (!file || busy) return
    if (file.size === 0 || file.size > 5 * 1024 * 1024) { setError('Choose a non-empty file no larger than 5 MB.'); return }
    setBusy(true); setError(''); setNotice('')
    const body = new FormData(); body.append('file', file)
    try {
      const response = await request(`${base}${replacement ? `/${replacement}/versions` : ''}`, { method: 'POST', headers: { 'Idempotency-Key': key.current }, body })
      const version: Version = await response.json()
      setFile(null); if (input.current) input.current.value = ''; key.current = crypto.randomUUID(); setReplacement('')
      selectDocument(version.documentId); setRefresh(value => value + 1); setNotice('Original saved privately. Text extraction is queued.')
    } catch (cause) { setError(problem(cause)) } finally { setBusy(false) }
  }
  return <section className="sources-page" aria-labelledby="sources-title">
    <div className="page-heading"><div><div className="eyebrow">WORKSPACE KNOWLEDGE</div><h1 id="sources-title">Sources</h1><p>Private documents in {workspace.name}. Access follows workspace membership.</p></div><button className="button secondary" onClick={() => setRefresh(value => value + 1)}>Refresh</button></div>
    {canWrite ? <form className="source-upload" onSubmit={upload}>
      <div><label htmlFor="source-file">Add a source</label><input ref={input} id="source-file" type="file" accept=".pdf,.txt,.md" disabled={busy} onChange={event => { setFile(event.target.files?.[0] || null); key.current = crypto.randomUUID(); setError('') }} aria-describedby="upload-help" /><p id="upload-help" className="field-help">PDF with selectable text, UTF-8 TXT, or Markdown. Up to 5 MB; PDFs up to 200 pages. Scans need OCR and are not supported yet.</p></div>
      <div><label htmlFor="source-target">Save as</label><select id="source-target" value={replacement} disabled={busy} onChange={event => { setReplacement(event.target.value); key.current = crypto.randomUUID() }}><option value="">New document</option>{documents.map(doc => <option key={doc.id} value={doc.id}>New version of {doc.title}</option>)}</select></div>
      <button className="button primary" disabled={busy || !file}>{busy ? 'Uploading…' : 'Upload document'}</button>
    </form> : <p className="preview-note">You have viewer access. A workspace editor or admin can add documents.</p>}
    {error && <p className="source-error" role="alert">{error} <button className="text-link" onClick={() => setRefresh(value => value + 1)}>Retry loading</button></p>}
    {notice && <p className="source-notice" role="status">{notice}</p>}
    <p className="field-help">Text processing preserves page or line references. Question answering is not available yet.</p>
    {loading ? <p role="status">Loading sources…</p> : !documents.length ? <div className="sources-empty"><h2>Your knowledge starts here</h2><p>Add your first document to this workspace. Original files remain available for download.</p></div> : <div className="source-table-scroll"><table className="source-table"><caption className="visually-hidden">Documents in {workspace.name}</caption><thead><tr><th>Document</th><th>Latest version</th><th>Processing</th><th>Size</th></tr></thead><tbody>{documents.map(doc => <tr key={doc.id}><td><button className="text-link source-name" onClick={() => selectDocument(doc.id)}>{doc.title}</button></td><td>v{doc.latest.versionNumber}</td><td><span className={`source-status ${doc.latest.status}`}>{statusLabel[doc.latest.status]}</span>{doc.processedVersionId && doc.processedVersionId !== doc.latest.id && <small>Previous processed version available</small>}</td><td>{Math.max(1, Math.ceil(doc.latest.byteSize / 1024))} KB</td></tr>)}</tbody></table></div>}
    {selected && <DocumentDetail key={selected} base={base} id={selected} canWrite={canWrite} refresh={refresh} changed={() => setRefresh(value => value + 1)} />}
  </section>
}
function DocumentDetail({ base, id, canWrite, refresh, changed }: { base: string; id: string; canWrite: boolean; refresh: number; changed: () => void }) {
  const [versions, setVersions] = useState<Version[]>([])
  const [selectedVersion, setSelectedVersion] = useState('')
  const [chunks, setChunks] = useState<Chunk[]>([])
  const [offset, setOffset] = useState(0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [reading, setReading] = useState(false)
  const [loaded, setLoaded] = useState(false)
  useEffect(() => { let active = true; request(`${base}/${id}/versions`).then(r => r.json()).then((rows: Version[]) => { if (active) { setVersions(rows); setSelectedVersion(current => current || rows[0]?.id || ''); setError(''); setLoaded(true) } }).catch(cause => { if (active) { setError(problem(cause)); setLoaded(true) } }); return () => { active = false } }, [base, id, refresh])
  const version = versions.find(value => value.id === selectedVersion)
  useEffect(() => {
    if (!version || version.status !== 'processed') return
    let active = true
    request(`${base}/${id}/versions/${version.id}/chunks?offset=${offset}`).then(r => r.json()).then((rows: Chunk[]) => { if (active) { setChunks(rows); setReading(false) } }).catch(cause => { if (active) { setError(problem(cause)); setReading(false) } })
    return () => { active = false }
  }, [base, id, version, offset])
  async function act(action: 'original' | 'retry') {
    if (!version || busy) return
    setBusy(true); setError('')
    try {
      const response = await request(`${base}/${id}/versions/${version.id}/${action}`, action === 'retry' ? { method: 'POST' } : undefined)
      if (action === 'original') {
        const url = URL.createObjectURL(await response.blob()); const link = document.createElement('a'); link.href = url; link.download = version.filename; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
      } else changed()
    } catch (cause) { setError(problem(cause)) } finally { setBusy(false) }
  }
  return <section className="source-detail" aria-label="Document details"><div className="page-heading"><h2>{version?.filename || 'Document details'}</h2><button className="button secondary" onClick={() => selectDocument(null)}>Close details</button></div>
    {error && <p role="alert" className="source-error">{error}</p>}{!loaded && <p role="status">Loading versions…</p>}
    {version && <><div className="source-detail-tools"><div><label htmlFor="document-version">Version history</label><select id="document-version" value={selectedVersion} onChange={event => { setSelectedVersion(event.target.value); setChunks([]); setOffset(0); setReading(true) }}>{versions.map(value => <option key={value.id} value={value.id}>v{value.versionNumber} · {statusLabel[value.status]} · {new Date(value.createdAt).toLocaleString()}</option>)}</select></div><button className="button secondary" disabled={busy} onClick={() => void act('original')}>Download original</button>{canWrite && version.status === 'failed' && <button className="button primary" disabled={busy} onClick={() => void act('retry')}>Retry processing</button>}</div>
      <p role="status">{statusLabel[version.status]}{version.error && ` — ${version.error}`}</p>
      {version.status === 'processed' && <><h3>Extracted passages</h3>{reading && <p role="status">Loading passages…</p>}{chunks.map(chunk => <article className="source-passage" key={`${version.id}-${chunk.ordinal}`}><p className="field-help">{chunk.pageNumber ? `Page ${chunk.pageNumber}` : `Lines ${chunk.startLine}–${chunk.endLine}`} · Passage {chunk.ordinal + 1}</p><pre>{chunk.content}</pre></article>)}<div className="source-pagination"><button className="button secondary" disabled={offset === 0 || reading} onClick={() => { setReading(true); setOffset(value => Math.max(0, value - 50)) }}>Previous passages</button><button className="button secondary" disabled={chunks.length < 50 || reading} onClick={() => { setReading(true); setOffset(value => value + 50) }}>Next passages</button></div></>}
    </>}
  </section>
}
