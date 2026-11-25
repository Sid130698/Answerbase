import { Access } from './admin/Access'
import { Sources } from './documents/Sources'
import { useEffect, useState, type ReactNode } from 'react'
import { WorkspaceGate, type Workspace } from './workspaces/WorkspaceGate'
import { Icon } from './components/Icon'
import './App.css'
import { SignOut } from './auth/AuthGate'
import { useIdentity } from './auth/identity'

function currentView() { return window.location.hash === '#access' ? 'access' : window.location.hash === '#sources' ? 'sources' : 'ask' }

export default function App() {
  return <WorkspaceGate>{(workspace, selector) => <WorkspaceApp key={workspace.id} workspace={workspace} selector={selector} />}</WorkspaceGate>
}

function WorkspaceApp({ workspace, selector }: { workspace: Workspace; selector: ReactNode }) {
  const identity = useIdentity()
  const [view, setView] = useState(currentView)
  const [question, setQuestion] = useState('')
  useEffect(() => {
    const sync = () => setView(currentView())
    window.addEventListener('hashchange', sync)
    return () => window.removeEventListener('hashchange', sync)
  }, [])
  useEffect(() => { document.title = `${view === 'access' ? 'Access' : view === 'sources' ? 'Sources' : 'Ask'} · Answerbase` }, [view])

  return <div className="app-layout">
    <a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); document.getElementById('main-content')?.focus() }}>Skip to content</a>
    <aside className="sidebar">
      <a href="#ask" className="brand" aria-label="Answerbase home"><span className="brand-mark" aria-hidden="true">a.</span>Answerbase</a>
      {selector}
      <nav aria-label="Main navigation">
        <a href="#ask" className={`nav-link ${view === 'ask' ? 'selected' : ''}`} aria-current={view === 'ask' ? 'page' : undefined}><Icon name="search" />Ask</a>
        <a href="#sources" className={`nav-link ${view === 'sources' ? 'selected' : ''}`} aria-current={view === 'sources' ? 'page' : undefined}><Icon name="book" />Sources</a>
        {workspace.role === 'admin' && <a href="#access" className={`nav-link ${view === 'access' ? 'selected' : ''}`} aria-current={view === 'access' ? 'page' : undefined}>Access</a>}
      </nav>
      <div className="sidebar-note"><p className="account-email">{identity.email}</p><SignOut /></div>
    </aside>
    <div className="workspace">
      <header className="topbar"><span>{workspace.name} <span aria-hidden="true">/</span> <strong>{view === 'access' ? 'Access' : view === 'sources' ? 'Sources' : 'Ask'}</strong></span><span className="preview-label">Product preview</span></header>
      <main id="main-content" tabIndex={-1}>
        {view === 'ask' ? <div className="ask-layout">
          <section className="question-space" aria-labelledby="ask-title">
            <div className="eyebrow">ASK YOUR KNOWLEDGE</div>
            <h1 id="ask-title">Find the answer.<br /><span>Know where it came from.</span></h1>
            <p className="intro">Ask a question about your team's documents. Follow each answer back to its source.</p>
            <div className="composer">
              <label className="visually-hidden" htmlFor="question">Your question</label>
              <textarea id="question" value={question} onChange={event => setQuestion(event.target.value)} placeholder="What would you like to know?" rows={3} aria-describedby="question-help" />
              <div className="composer-footer"><span><Icon name="book" />Workspace sources</span><button className="button primary" disabled aria-describedby="question-help">Ask question <span aria-hidden="true">↑</span></button></div>
            </div>
            <p className="field-help" id="question-help">Answers will be available once search and answer generation are connected. You can draft a question here.</p>
            <div className="first-step"><div className="step-mark"><Icon name="book" size={22} /></div><div><h2>Start with your team's knowledge</h2><p>Add the documents your team relies on, then ask questions in plain language.</p><a className="text-link" href="#sources">View sources <span aria-hidden="true">→</span></a></div></div>
          </section>
          <aside className="evidence-panel" aria-labelledby="evidence-title"><div className="eyebrow">EVIDENCE</div><h2 id="evidence-title">The source behind the answer</h2><div className="document-symbol" aria-hidden="true"><Icon name="book" size={32} /></div><p>When you ask a question, supporting passages will appear here so you can check the original context.</p><div className="evidence-rule" /><p className="small-note">No answer yet. No sources to cite.</p></aside>
        </div> : view === 'access' && workspace.role === 'admin' ? <Access workspace={workspace} /> : <Sources workspace={workspace} />}
      </main>
    </div>
  </div>
}
