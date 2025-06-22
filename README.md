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

## Project local start script

Once your editor is configured to invoke this script, you can add different scripts to your project depending on your tooling choices or special requirements. You might have one per project that your team share or maybe keep them entirely private if you have differing OS needs. Maybe have a shared core script that invokes a sub-script that each of your team members can customise. The possibilities are endless and dictated by each of your projects.

Here's some starting points for different situations, please feel free to add more here if you work out how to get this working under different tooling.

### Clojure CLI / `tools.cli`

#### `deps.edn`

```clojure
{:deps
 {org.clojure/clojure {:mvn/version "..."}

 :aliases
 {:typedclojure-lsp {:extra-deps {uk.me.oli/typedclojure-lsp {:mvn/version "${VERSION (see clojars badge)}"}}
                     :main-opts ["-m" "typedclojure-lsp.main"]}}}}
```

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
clojure -M:typedclojure-lsp
```

### Leiningen

#### `project.clj`

```clojure
(defproject demo "0.1.0-SNAPSHOT"
  :dependencies []

  :profiles
  {:typedclojure-lsp {:dependencies [[uk.me.oli/typedclojure-lsp "${VERSION (see clojars badge)}"]]}}

  :aliases
  {"typedclojure-lsp" ["with-profile" "+typedclojure-lsp" "run" "-m" "typedclojure-lsp.main"]})
```

#### `.typedclojure-lsp/start`

```bash
#!/usr/bin/env bash
lein typedclojure-lsp
```

## Typed Clojure version

The project currently depends on Typed Clojure `1.4.0-SNAPSHOT`, it also includes the [malli](https://github.com/metosin/malli) bridge and the `clojure.core` types. The latest Typed Clojure requires a metadata tag on your namespace as shown in this example (which also demonstrates the malli integration).

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

The repository already has `.typedclojure-lsp/start` configured for local development on the tool itself. This starts up the server with an nREPL that [Conjure](https://github.com/Olical/conjure) will automatically connect to giving you a tight feedback loop.

## Questions? Feedback?

You can reach me through issues and discussions on this repo, I highly encourage feedback, good or bad. Alternatively you can reach me on [Mastodon](https://mastodon.social/@Olical) or the [Conjure Discord](https://discord.gg/wXAMr8F). Sometimes I write things on [oli.me.uk](https://discord.gg/wXAMr8F).

## Unlicenced

Find the full [unlicense](http://unlicense.org/) in the `UNLICENSE` file, but here's a snippet.

> This is free and unencumbered software released into the public domain.
>
> Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
