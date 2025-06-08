# Typed Clojure LSP

[Typed Clojure](https://github.com/typedclojure/typedclojure) in your editor over LSP. This project is still very fresh, feedback is greatly appreciated.

Please let me know your setup (text editor, Clojure toolchain) and your experience. If you get this working in an editor and there's no documentation for it yet, please feel free to open a PR adding your notes to the README.

## Distribution and versions

We currently publish through GitHub with tagged releases. We start this server through the [Clojure CLI](https://clojure.org/guides/deps_and_cli) (for now, see the issues section below), there are no other published artifacts yet. Please subscribe to the releases on GitHub if you'd like to keep up to date. When there's a new version you'll need to bump your tag and sha in your LSP configuration, shown below.

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

### Neovim (Fennel)

If you're using [nfnl](https://github.com/Olical/nfnl) or a similar Fennel compiler system you can paste the following into `~/.config/nvim/lsp/typedclojure.fnl`. As long as you're calling `vim.lsp.enable` somewhere with `"typedclojure"` as an argument, everything should work automatically when you open a Clojure file.

```fennel
{:cmd ["clojure" "-Sdeps"
       "{:deps {io.github.Olical/typedclojure-lsp {:git/tag \"v0.0.1\", :git/sha \"05bfd50\"}}}"
       "-X:typedclojure-lsp" "typedclojure-lsp.main/start!"
       ;; Optional: ":logging?" "false"
       ]
 :filetypes ["clojure"]
 :root_markers ["deps.edn" "project.clj" ".git"]}
```

### Neovim (Lua)

Identical to the Fennel solution above, but you paste this Lua into `~/.config/nvim/lsp/typedclojure.lua`.

```lua
return {
    cmd = {
        "clojure",
        "-Sdeps",
        '{:deps {io.github.Olical/typedclojure-lsp {:git/tag "v0.0.1", :git/sha "05bfd50"}}}',
        "-X:typedclojure-lsp",
        "typedclojure-lsp.main/start!"
        -- Optional: ":logging?" "false"
    },
    filetypes = {"clojure"},
    root_markers = {"deps.edn", "project.clj", ".git"}
}
```

## Customising the Clojure environment

As you can see in the recommended configuration, we provide `-X:typedclojure-lsp` as an argument to Clojure. This means that a `:typedclojure-lsp` alias in your project local `deps.edn` file can add extra paths or dependencies where required.

You can obviously also modify the `cmd` to apply other aliases where required if your project requires a very specific combination of dependencies and paths in order to load.

## Issues, unknowns and things to address

Leiningen projects are obviously a big one, this assumes `deps.edn` for now but I'd like to find a way to have it support any kind of Clojure project shape. It'd also be great to have some more project local configuration, maybe have it look for a script like `.typedclojure-lsp/start.sh` - if found that is executed which invokes the command documented above.

Ideas on how to make this whole thing extremely flexible while also being very easy to configure are appreciated! Obviously it's not ideal to have to change your global LSP configuration as you hop between Clojure projects, so something project local is a must eventually.

## Logging

Logs are written to `.typedclojure-lsp/logs/typedclojure-lsp.log` by default, you can turn that off with the optional `:logging?` configuration shown above. I recommend keeping it on for now just in case you run into an issue. When reporting issues please include some of your relevant logs to help us diagnose and fix problems.

## Development

When working on the project I use `"clojure" "-X:typedclojure-lsp:test:dev" "typedclojure-lsp.dev/start!"` as my `cmd` in my LSP configuration. This starts up the server with an nREPL that [Conjure](https://github.com/Olical/conjure) will automatically connect to giving me a tight feedback loop.

## Questions? Feedback?

You can reach me through issues and discussions on this repo, I highly encourage feedback, good or bad. Alternatively you can reach me on [Mastodon](https://mastodon.social/@Olical) or the [Conjure Discord](https://discord.gg/wXAMr8F). Sometimes I write things on [oli.me.uk](https://discord.gg/wXAMr8F).

## Unlicenced

Find the full [unlicense](http://unlicense.org/) in the `UNLICENSE` file, but here's a snippet.

> This is free and unencumbered software released into the public domain.
>
> Anyone is free to copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.
