import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Workspace } from '../workspaces/WorkspaceGate'
import { request } from '../api'
import { DocumentPermissions } from './DocumentPermissions'
import './sources.css'

type Version = { id: string; documentId: string; versionNumber: number; filename: string; mediaType: string; byteSize: number; status: string; error: string | null; createdAt: string }
type Document = { id: string; title: string; latest: Version; processedVersionId: string | null }
type Chunk = { ordinal: number; pageNumber: number | null; startLine: number | null; endLine: number | null; content: string }
const statusLabel: Record<string, string> = { queued: 'Queued', processing: 'Extracting text', processed: 'Text processed', failed: 'Needs attention' }
const problem = (error: unknown) => error instanceof Error ? error.message : 'Request failed. Please retry.'
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
  const [duplicates, setDuplicates] = useState<{title: string; similarTitle: string; kind: string; similarity: number}[] | null>(null)
  const [refresh, setRefresh] = useState(0)
  const [selected, setSelected] = useState(documentId)
  const [files, setFiles] = useState<File[]>([])
  const [uploadResults, setUploadResults] = useState<{name: string; status: string}[]>([])
  const keys = useRef(new Map<File, string>())
  const [replacement, setReplacement] = useState('')
  const [busy, setBusy] = useState(false)
  function chooseFiles(selected: File[]) { setFiles(selected.slice(0, 20)); setUploadResults([]); setError(selected.length > 20 ? 'Choose up to 20 files per batch.' : ''); keys.current.clear() }
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
    event.preventDefault(); if (!files.length || busy) return
    if (replacement && files.length !== 1) { setError('Choose one file when uploading a new version.'); return }
    setBusy(true); setError(''); setNotice(''); setUploadResults([])
    const failed: File[] = []; let saved = 0
    for (const file of files) {
      try {
        if (file.size === 0 || file.size > 5 * 1024 * 1024) throw new Error('Choose a non-empty file no larger than 5 MB.')
        const body = new FormData(); body.append('file', file)
        if (!keys.current.has(file)) keys.current.set(file, crypto.randomUUID())
        const response = await request(`${base}${replacement ? `/${replacement}/versions` : ''}`, { method: 'POST', headers: { 'Idempotency-Key': keys.current.get(file)! }, body })
        const version: Version = await response.json(); saved++
        setUploadResults(rows => [...rows, { name: file.name, status: 'Saved privately' }])
        if (files.length === 1) selectDocument(version.documentId)
      } catch (cause) {
        failed.push(file); setUploadResults(rows => [...rows, { name: file.name, status: problem(cause) }])
      }
    }
    setFiles(failed); if (input.current) input.current.value = ''
    if (!failed.length) setReplacement('')
    setRefresh(value => value + 1); setNotice(`${saved} document${saved === 1 ? '' : 's'} saved. ${failed.length ? 'Failed files remain selected for retry.' : 'Processing continues in the background.'}`); setBusy(false)
  }
  return <section className="sources-page" aria-labelledby="sources-title">
    <div className="page-heading"><div><div className="eyebrow">WORKSPACE KNOWLEDGE</div><h1 id="sources-title">Sources</h1><p>Private documents in {workspace.name}. Access follows workspace membership.</p></div><button className="button secondary" onClick={() => setRefresh(value => value + 1)}>Refresh</button></div>
    {canWrite ? <form className="source-upload" onSubmit={upload} onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); if (!busy) chooseFiles(Array.from(event.dataTransfer.files)) }}>
      <div><label htmlFor="source-file">Add a source</label><input ref={input} id="source-file" type="file" accept=".pdf,.docx,.html,.htm,.txt,.md" multiple disabled={busy} onChange={event => chooseFiles(Array.from(event.target.files || []))} aria-describedby="upload-help" /><p id="upload-help" className="field-help">Drop files here or choose up to 20 PDF, DOCX, HTML, TXT, or Markdown files. Up to 5 MB each; PDFs up to 200 pages. Scans need OCR and are not supported yet.</p></div>
      <div><label htmlFor="source-target">Save as</label><select id="source-target" value={replacement} disabled={busy} onChange={event => { setReplacement(event.target.value); keys.current.clear() }}><option value="">New document</option>{documents.map(doc => <option key={doc.id} value={doc.id}>New version of {doc.title}</option>)}</select></div>
      <button className="button primary" disabled={busy || !files.length}>{busy ? 'Uploading…' : files.length > 1 ? `Upload ${files.length} documents` : 'Upload document'}</button>
    </form> : <p className="preview-note">You have viewer access. A workspace editor or admin can add documents.</p>}
    {error && <p className="source-error" role="alert">{error} <button className="text-link" onClick={() => setRefresh(value => value + 1)}>Retry loading</button></p>}
    {notice && <p className="source-notice" role="status">{notice}</p>}{uploadResults.length > 0 && <ul className="upload-results" aria-label="Upload results">{uploadResults.map((result, index) => <li key={index}><strong>{result.name}</strong> — {result.status}</li>)}</ul>}
    {canWrite && <div><button className="button secondary" onClick={async () => { try { setDuplicates(await (await request(`${base}/duplicates`)).json()) } catch (cause) { setError(problem(cause)) } }}>Check for duplicates</button>{duplicates && <div role="status">{duplicates.length ? <ul>{duplicates.map((pair, index) => <li key={index}>{pair.title} / {pair.similarTitle}: {pair.kind} ({Math.round(pair.similarity * 100)}% overlap). Review before removing either source.</li>)}</ul> : <p>No potential duplicates found.</p>}</div>}</div>}
    <p className="field-help">Text processing preserves page or line references. Question answering is not available yet.</p>
    {loading ? <p role="status">Loading sources…</p> : !documents.length ? <div className="sources-empty"><h2>Your knowledge starts here</h2><p>Add your first document to this workspace. Original files remain available for download.</p></div> : <div className="source-table-scroll"><table className="source-table"><caption className="visually-hidden">Documents in {workspace.name}</caption><thead><tr><th>Document</th><th>Latest version</th><th>Processing</th><th>Size</th></tr></thead><tbody>{documents.map(doc => <tr key={doc.id}><td><button className="text-link source-name" onClick={() => selectDocument(doc.id)}>{doc.title}</button></td><td>v{doc.latest.versionNumber}</td><td><span className={`source-status ${doc.latest.status}`}>{statusLabel[doc.latest.status]}</span>{doc.processedVersionId && doc.processedVersionId !== doc.latest.id && <small>Previous processed version available</small>}</td><td>{Math.max(1, Math.ceil(doc.latest.byteSize / 1024))} KB</td></tr>)}</tbody></table></div>}
    {selected && <DocumentDetail key={selected} base={base} id={selected} canWrite={canWrite} canManage={workspace.role === 'admin'} workspace={workspace.id} refresh={refresh} changed={() => setRefresh(value => value + 1)} />}
  </section>
}
function DocumentDetail({ base, id, canWrite, canManage, workspace, refresh, changed }: { base: string; id: string; canWrite: boolean; canManage: boolean; workspace: string; refresh: number; changed: () => void }) {
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
    {canManage && <DocumentPermissions workspace={workspace} document={id} />}
    {version && <><div className="source-detail-tools"><div><label htmlFor="document-version">Version history</label><select id="document-version" value={selectedVersion} onChange={event => { setSelectedVersion(event.target.value); setChunks([]); setOffset(0); setReading(true) }}>{versions.map(value => <option key={value.id} value={value.id}>v{value.versionNumber} · {statusLabel[value.status]} · {new Date(value.createdAt).toLocaleString()}</option>)}</select></div><button className="button secondary" disabled={busy} onClick={() => void act('original')}>Download original</button>{canWrite && version.status === 'failed' && <button className="button primary" disabled={busy} onClick={() => void act('retry')}>Retry processing</button>}</div>
      <p role="status">{statusLabel[version.status]}{version.error && ` — ${version.error}`}</p>
      {version.status === 'processed' && <><h3>Extracted passages</h3>{reading && <p role="status">Loading passages…</p>}{chunks.map(chunk => <article className="source-passage" key={`${version.id}-${chunk.ordinal}`}><p className="field-help">{chunk.pageNumber ? `Page ${chunk.pageNumber}` : `Lines ${chunk.startLine}–${chunk.endLine}`} · Passage {chunk.ordinal + 1}</p><pre>{chunk.content}</pre></article>)}<div className="source-pagination"><button className="button secondary" disabled={offset === 0 || reading} onClick={() => { setReading(true); setOffset(value => Math.max(0, value - 50)) }}>Previous passages</button><button className="button secondary" disabled={chunks.length < 50 || reading} onClick={() => { setReading(true); setOffset(value => value + 50) }}>Next passages</button></div></>}
    </>}
  </section>
}
