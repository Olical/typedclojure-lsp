# Typed Clojure LSP [![Clojars Project](https://img.shields.io/clojars/v/uk.me.oli/typedclojure-lsp.svg)](https://clojars.org/uk.me.oli/typedclojure-lsp)

[Typed Clojure](https://github.com/typedclojure/typedclojure) in your editor over LSP. This project is still very fresh, feedback is greatly appreciated.

Please let me know your setup (text editor, Clojure toolchain) and your experience. If you get this working in an editor and there's no documentation for it yet, please feel free to open a PR adding your notes to the README.

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
    cmd = {
        ".typedclojure-lsp/start"
    },
    filetypes = {"clojure"},
    root_markers = {"deps.edn", "project.clj", ".git"}
}
```

## Your project local start script

Once your editor is configured to invoke this script, you can add different scripts to your project depending on your tooling choices or special requirements. You might have one per project that your team share or maybe keep them entirely private if you have differing OS needs. Maybe have a shared core script that invokes a sub-script that each of your team members can customise. The possibilities are endless and dictated by each of your projects.

Here's some starting points for different situations, please feel free to add more here if you work out how to get this working under different tooling.

### `tools.cli` / Clojure CLI

We can invoke the start function with the right dependency right from the CLI, no need to modify your `deps.edn`. Of course you _can_ add an alias to your `deps.edn` and invoke that too, up to you!

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
clojure -Sdeps '{:deps {uk.me.oli/typedclojure-lsp {:mvn/version "${VERSION (see clojars badge)}"}}}' typedclojure-lsp.main/start!
```

### Leiningen

We have to add the dependency under a profile in `project.clj` and then invoke the start function with that profile, in this case through an alias.

#### `project.clj`

```clojure
(defproject demo "0.1.0-SNAPSHOT"
  :dependencies []

  :profiles
  {:typedclojure-lsp {:dependencies [[uk.me.oli/typedclojure-lsp "${VERSION (see clojars badge)}"]]}}

  :aliases
  {"typedclojure-lsp" ["with-profile" "+typedclojure-lsp" "run" "-m" "typedclojure-lsp.main/start!"]})
```

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
lein typedclojure-lsp
```

## Logging

Logs are written to stderr and should be visible within your LSP client (text editor) somewhere. In Neovim you can see them in `:LspLog`, in VS Code you can view them under `View > Output > Select typedclojure-lsp from the list`.

## Development

When working on the project I use `"clojure" "-X:typedclojure-lsp:test:dev" "typedclojure-lsp.dev/start!"` as my `cmd` in my LSP configuration. This starts up the server with an nREPL that [Conjure](https://github.com/Olical/conjure) will automatically connect to giving me a tight feedback loop.

## Questions? Feedback?

You can reach me through issues and discussions on this repo, I highly encourage feedback, good or bad. Alternatively you can reach me on [Mastodon](https://mastodon.social/@Olical) or the [Conjure Discord](https://discord.gg/wXAMr8F). Sometimes I write things on [oli.me.uk](https://discord.gg/wXAMr8F).

## Unlicenced

Find the full [unlicense](http://unlicense.org/) in the `UNLICENSE` file, but here's a snippet.

> This is free and unencumbered software released into the public domain.
>
> Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
