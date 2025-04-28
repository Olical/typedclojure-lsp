# Background Check

Run [Typed Clojure](https://github.com/typedclojure/typedclojure) against your system as part of your [Kaocha](https://github.com/lambdaisland/kaocha) test suite.

## Status

Extremely early and experimental. Unusable right now and just a thing I'm tinkering with. Check back later or follow me on [Mastodon](https://mastodon.social/@Olical).

## Goals

- Check your types alongside your logic, slot seamlessly into existing developer workflows and CI.
- Re-check on file changes thanks to `kaocha --watch`.
- Output results to the terminal with some concise and pretty formatting.
- Output results as data into files on disk that any other program or editor can consume.
- Build a Neovim plugin that reads these files and displays the issues via the diagnostics API.
