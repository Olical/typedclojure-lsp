const vscode = require("vscode");
const { LanguageClient } = require("vscode-languageclient/node");

let client;

module.exports = {
  activate(context) {
    const config = vscode.workspace.getConfiguration("typedclojure-lsp");
    const localPath = config.get("path");
    const version = config.get("version");

    const dep = localPath
      ? `typedclojure-lsp {:local/root "${localPath}"}`
      : `uk.me.oli/typedclojure-lsp {:mvn/version "${version}"}`;

    const deps = `{:deps {${dep}}}`;

    client = new LanguageClient(
      "typedclojure-lsp",
      "Typed Clojure LSP",
      { command: "clojure", args: ["-Sdeps", deps, "-M", "-m", "typedclojure-lsp.main"] },
      { documentSelector: [{ scheme: "file", language: "clojure" }] },
    );

    client.start();
    context.subscriptions.push({ dispose: () => client?.stop() });
  },

  deactivate() {
    return client?.stop();
  },
};
