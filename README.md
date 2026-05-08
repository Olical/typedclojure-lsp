# Typed Clojure LSP [![Clojars Project](https://img.shields.io/clojars/v/uk.me.oli/typedclojure-lsp.svg)](https://clojars.org/uk.me.oli/typedclojure-lsp)

[Typed Clojure](https://github.com/typedclojure/typedclojure) in your editor over LSP. This project is still very fresh, feedback is greatly appreciated.

![image](https://github.com/user-attachments/assets/7ed4cfd3-8c5a-4b01-a456-6c186c1ee094)

## Editor LSP configuration

We configure our editor to attempt to invoke a known script in each directory when we open a Clojure project file. This gives us the flexibility to configure how the LSP server starts and which version we use _per-project_. First we'll get our editor invoking the script, then we can fill the script out in our project depending on the tooling it relies on.

### Neovim (Fennel)

If you're using [nfnl](https://github.com/Olical/nfnl) or a similar Fennel compiler system you can paste the following into `~/.config/nvim/lsp/typedclojure.fnl`. As long as you're calling `vim.lsp.enable` somewhere with `"typedclojure"` as an argument, everything should work automatically when you open a Clojure file.

```fennel
{:cmd [".typedclojure-lsp/start"]
 :filetypes ["clojure"]
 :root_markers ["deps.edn" "project.clj" ".git"]}
```

### Neovim (Lua)

Identical to the Fennel solution above, but you paste this Lua into `~/.config/nvim/lsp/typedclojure.lua`.

```lua
return {
    cmd = {".typedclojure-lsp/start"},
    filetypes = {"clojure"},
    root_markers = {"deps.edn", "project.clj", ".git"}
}
```

### Helix

Add the following to `~/.config/helix/languages.toml` (or `.helix/languages.toml` in your project root for per-project config).

```toml
[language-server.typedclojure-lsp]
command = ".typedclojure-lsp/start"

[[language]]
name = "clojure"
language-servers = ["clojure-lsp", "typedclojure-lsp"]
```

If you don't use `clojure-lsp`, remove it from the `language-servers` array.

### VS Code

Install from the [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=olical.typedclojure-lsp), or from the command line:

```bash
code --install-extension olical.typedclojure-lsp
```

For development, you can build and install a local `.vsix` from the `vscode-extension/` directory with `mise vscode-package` (or `npm install && npx @vscode/vsce package --allow-missing-repository`), then `code --install-extension typedclojure-lsp-<version>.vsix`.

The extension activates on Clojure files and runs an executable from the workspace root (default `.typedclojure-lsp/start`), matching the Neovim and Helix configurations above. Two settings tune the launch:

| Setting | Default | Purpose |
| --- | --- | --- |
| `typedclojure-lsp.command` | `.typedclojure-lsp/start` | Command to launch the server. Resolved relative to the workspace root if not absolute. |
| `typedclojure-lsp.args` | `[]` | Arguments passed to the launch command. |

Both can be edited from **Settings → Extensions → Typed Clojure LSP**, scoped per workspace via the **Workspace** tab. Workspace settings land in `.vscode/settings.json`, so you can commit them with the project.

## Project local start script

Once your editor is configured to invoke this script, you can add different scripts to your project depending on your tooling choices or special requirements. You might have one per project that your team share or maybe keep them entirely private if you have differing OS needs. Maybe have a shared core script that invokes a sub-script that each of your team members can customise. The possibilities are endless and dictated by each of your projects.

> **Keep stdout clean.** The script's stdout and stdin are the LSP transport (JSON-RPC framed messages). Anything that prints to stdout — `println` calls, library banners, JVM agents, `:classpath-overrides` like ClojureStorm — will corrupt the protocol and the editor will drop the connection. Send logs to stderr, write port files instead of printing them, and prefer launching debuggers like FlowStorm from a connected REPL rather than at JVM startup. If a flag is needed to suppress noisy debug tooling for the editor case, branch on `$1` in the script and pass the flag from the editor: `typedclojure-lsp.args` in VS Code, append to the `cmd` array in Neovim's `vim.lsp.config`, or the `args` key under `[language-server.typedclojure-lsp]` in Helix's `languages.toml`.

Here's some starting points for different situations, please feel free to add more here if you work out how to get this working under different tooling.

### Clojure CLI / `tools.cli`

#### `deps.edn`

```clojure
{:deps
 {org.clojure/clojure {:mvn/version "..."}

 :aliases
 {:typedclojure-lsp {:extra-deps {uk.me.oli/typedclojure-lsp {:mvn/version "RELEASE"}}
                     :main-opts ["-m" "typedclojure-lsp.main"]}}}}
```

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
exec clojure -M:typedclojure-lsp "$@"
```

`exec` replaces the shell so signals from the editor reach the JVM cleanly on shutdown. `"$@"` forwards any args the editor passes (see the keep-stdout-clean note above for the args mechanism in each editor).

### Leiningen

#### `project.clj`

```clojure
(defproject demo "0.1.0-SNAPSHOT"
  :dependencies []

  :profiles
  {:typedclojure-lsp {:dependencies [[uk.me.oli/typedclojure-lsp "RELEASE"]]}}

  :aliases
  {"typedclojure-lsp" ["with-profile" "+typedclojure-lsp" "run" "-m" "typedclojure-lsp.main"]})
```

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
exec lein typedclojure-lsp "$@"
```

## Type checking at the CLI or in CI

Although you can call Typed Clojure yourself using the official documentation, this project provides a helper that executes it against the same paths as the LSP server to keep things consistent. To execute the runner in your terminal or CI you can use the same `deps.edn` or `project.clj` setup described above but with `typedclojure-lsp.main` replaced with `typedclojure-lsp.runner`.

For example, with the Clojure CLI you may execute `clojure -M:typedclojure-lsp -m typedclojure-lsp.runner` to run the type checker outside of your editor. Please [Clojure Template](https://github.com/Olical/clojure-template) for an example project that includes this configuration and a pre-configured GitHub action.

## Typed Clojure version

The project currently depends on Typed Clojure `1.3.1-SNAPSHOT`, it also includes the [malli](https://github.com/metosin/malli) bridge and the `clojure.core` types. The latest Typed Clojure requires a metadata tag on your namespace as shown in this example (which also demonstrates the malli integration).

```clojure
;; This ^:typed.clojure keyword is required!
(ns ^:typed.clojure examples.core
  (:require [malli.experimental :as mx]))

(mx/defn add :- number?
  "If you have LSP configured correctly you should see a type error / warning if you try to type (add :foo 10) inside this buffer."
  [a :- number?
   b :- number?]
  (+ a b))

(mx/defn a-bad-fn :- number? []
  (add :foo 10)) ;; => Function add could not be applied to arguments... [would appear in your editor]
```

## Development

The repository ships a `.typedclojure-lsp/start` script geared for hacking on the tool itself. By default it runs `mise dev`, which boots the LSP plus an nREPL plus FlowStorm — perfect for [Conjure](https://github.com/Olical/conjure) to attach to for a tight feedback loop.

FlowStorm prints to stdout during JVM init, which corrupts the LSP transport, so the script also recognises a single `--no-flowstorm` argument and switches to `mise dev-no-flowstorm` (LSP + nREPL, no FlowStorm). The repo's `.vscode/settings.json` passes that flag via `typedclojure-lsp.args`, so dogfooding the VS Code extension on this codebase works out of the box. If you use a different editor and want to dogfood here, do the same:

- Neovim (`vim.lsp.config`): `cmd = {".typedclojure-lsp/start", "--no-flowstorm"}`
- Helix (`languages.toml`): `args = ["--no-flowstorm"]` under `[language-server.typedclojure-lsp]`

## Questions? Feedback?

You can reach me through issues and discussions on this repo, I highly encourage feedback, good or bad. Alternatively you can reach me on [Mastodon](https://mastodon.social/@Olical) or the [Conjure Discord](https://discord.gg/wXAMr8F). Sometimes I write things on [oli.me.uk](https://discord.gg/wXAMr8F).

## Unlicenced

Find the full [unlicense](http://unlicense.org/) in the `UNLICENSE` file, but here's a snippet.

> This is free and unencumbered software released into the public domain.
>
> Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
