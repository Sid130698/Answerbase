# Document permissions

Workspace membership is required for every read. Unrestricted documents are available to all workspace members. Restricted documents are available to members of an explicitly allowed named group; workspace admins can read/manage all workspace documents. Organization roles do not bypass workspace membership. Restricted documents with no allowed groups are admin-only.

`answerbase.can_read_document(user,document)` is the shared SQL predicate for listings and document reads. Retrieval must use this predicate inside candidate queries before ranking. Group/access foreign keys bind organization, workspace and document together. Removing a workspace member also removes their group memberships. Delegated staff actions resolve the customer and use the same service checks; successful actions are audited.

Workspace admins manage groups in Access and restrict documents through Document access in Sources. Permission changes do not require re-embedding because the query predicate uses current memberships. Original downloads, version history, passages, replacement uploads and duplicate reports also enforce document access. A request already in flight can complete; subsequent requests recheck permissions.

Validation: PostgreSQL/JWT integration tests exercise restricted original/passage/list denial, group grant/revocation, admin access, non-admin group management denial and cross-organization group membership rejection. Backend/core 17 tests and frontend lint/build pass.
