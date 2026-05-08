# Typed Clojure LSP

[Typed Clojure](https://github.com/typedclojure/typedclojure) type checking inside VS Code over LSP. Get inline diagnostics for type errors as you save Clojure files.

The extension is a thin client — the actual server runs from your project. By default it executes `.typedclojure-lsp/start` from the workspace root, matching the [Neovim and Helix configurations](https://github.com/Olical/typedclojure-lsp#editor-lsp-configuration) and giving the project full control over how the LSP launches (Clojure CLI, Leiningen, custom JVM tuning, etc.). See the [project repository](https://github.com/Olical/typedclojure-lsp) for example start scripts and `deps.edn` / `project.clj` snippets.

## Settings

| Setting | Default | Purpose |
| --- | --- | --- |
| `typedclojure-lsp.command` | `.typedclojure-lsp/start` | Command to launch the server. Resolved relative to the workspace root if not absolute. |
| `typedclojure-lsp.args` | `[]` | Arguments passed to the launch command. |

Edit them from **Settings → Extensions → Typed Clojure LSP** and scope to the project via the **Workspace** tab — workspace settings land in `.vscode/settings.json` so a team can share them.

## Keep stdout clean

The launched process talks to VS Code over stdio using LSP's JSON-RPC framing protocol. Anything printed to stdout that isn't a valid LSP message will corrupt the transport and the editor will drop the connection. Send all logging to stderr, and prefer launching debug tooling (FlowStorm, instrumentation, REPL banners) from a connected REPL rather than at JVM startup. The `typedclojure-lsp.args` setting lets your start script branch on flags so the editor case can opt out of stdout-noisy tooling.

## License

Released into the public domain via the [Unlicense](LICENSE).
