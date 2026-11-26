import { ModelSettings } from "./ModelSettings";
import { Retention } from "./Retention";
import type { Workspace } from "../workspaces/WorkspaceGate";

export function Settings({ workspace }: { workspace: Workspace }) {
  return (
    <section className="access-page">
      <h1>Workspace settings</h1>
      <p>Configure answers and document retention for {workspace.name}.</p>
      <ModelSettings workspace={workspace.id} />
      <Retention workspace={workspace.id} />
    </section>
  );
}
