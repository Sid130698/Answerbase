import { Ask } from "./answers/Ask";
import { Sources } from "./documents/Sources";
import { lazy, Suspense, useEffect, useState, type ReactNode } from "react";
import { WorkspaceGate, type Workspace } from "./workspaces/WorkspaceGate";
import { Icon } from "./components/Icon";
import "./App.css";
import { SignOut } from "./auth/AuthGate";
import { useIdentity } from "./auth/identity";

const Access = lazy(() =>
  import("./admin/Access").then((module) => ({ default: module.Access })),
);
const QueryLog = lazy(() =>
  import("./admin/QueryLog").then((module) => ({ default: module.QueryLog })),
);

const Settings = lazy(() =>
  import("./admin/Settings").then((module) => ({ default: module.Settings })),
);

function currentView() {
  return window.location.hash === "#settings"
    ? "settings"
    : window.location.hash === "#queries"
      ? "queries"
      : window.location.hash === "#access"
        ? "access"
        : window.location.hash === "#sources"
          ? "sources"
          : "ask";
}

export default function App() {
  return (
    <WorkspaceGate>
      {(workspace, selector) => (
        <WorkspaceApp
          key={workspace.id}
          workspace={workspace}
          selector={selector}
        />
      )}
    </WorkspaceGate>
  );
}

function WorkspaceApp({
  workspace,
  selector,
}: {
  workspace: Workspace;
  selector: ReactNode;
}) {
  const identity = useIdentity();
  const [requestedView, setView] = useState(currentView);
  const forbidden =
    workspace.role !== "admin" &&
    (requestedView === "access" ||
      requestedView === "queries" ||
      requestedView === "settings");
  const view = forbidden ? "ask" : requestedView;
  useEffect(() => {
    if (forbidden) window.location.replace("#ask");
  }, [forbidden]);
  useEffect(() => {
    const sync = () => setView(currentView());
    window.addEventListener("hashchange", sync);
    return () => window.removeEventListener("hashchange", sync);
  }, []);
  useEffect(() => {
    document.title = `${view === "settings" ? "Settings" : view === "queries" ? "Questions" : view === "access" ? "Access" : view === "sources" ? "Sources" : "Ask"} · Answerbase`;
  }, [view]);

  return (
    <div className="app-layout">
      <a
        className="skip-link"
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        Skip to content
      </a>
      <aside className="sidebar">
        <a href="#ask" className="brand" aria-label="Answerbase home">
          <span className="brand-mark" aria-hidden="true">
            a.
          </span>
          Answerbase
        </a>
        {selector}
        <nav aria-label="Main navigation">
          <a
            href="#ask"
            className={`nav-link ${view === "ask" ? "selected" : ""}`}
            aria-current={view === "ask" ? "page" : undefined}
          >
            <Icon name="search" />
            Ask
          </a>
          <a
            href="#sources"
            className={`nav-link ${view === "sources" ? "selected" : ""}`}
            aria-current={view === "sources" ? "page" : undefined}
          >
            <Icon name="book" />
            Sources
          </a>
          {workspace.role === "admin" && (
            <a
              href="#access"
              className={`nav-link ${view === "access" ? "selected" : ""}`}
              aria-current={view === "access" ? "page" : undefined}
            >
              Access
            </a>
          )}
          {workspace.role === "admin" && (
            <a
              href="#queries"
              className={`nav-link ${view === "queries" ? "selected" : ""}`}
              aria-current={view === "queries" ? "page" : undefined}
            >
              Questions
            </a>
          )}
          {workspace.role === "admin" && (
            <a
              href="#settings"
              className={`nav-link ${view === "settings" ? "selected" : ""}`}
              aria-current={view === "settings" ? "page" : undefined}
            >
              Settings
            </a>
          )}
        </nav>
        <div className="sidebar-note">
          <p className="account-email">{identity.email}</p>
          <SignOut />
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            {workspace.name} <span aria-hidden="true">/</span>{" "}
            <strong>
              {view === "settings"
                ? "Settings"
                : view === "queries"
                  ? "Questions"
                  : view === "access"
                    ? "Access"
                    : view === "sources"
                      ? "Sources"
                      : "Ask"}
            </strong>
          </span>
        </header>
        <main id="main-content" tabIndex={-1}>
          <Suspense fallback={<p role="status">Opening workspace view…</p>}>
            {view === "settings" && workspace.role === "admin" ? (
              <Settings workspace={workspace} />
            ) : view === "queries" && workspace.role === "admin" ? (
              <QueryLog workspace={workspace} />
            ) : view === "ask" ? (
              <Ask workspace={workspace} />
            ) : view === "access" && workspace.role === "admin" ? (
              <Access workspace={workspace} />
            ) : (
              <Sources workspace={workspace} />
            )}
          </Suspense>
        </main>
      </div>
    </div>
  );
}
