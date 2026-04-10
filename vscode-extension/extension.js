const path = require("path");
const vscode = require("vscode");
const { LanguageClient } = require("vscode-languageclient/node");

let client;

module.exports = {
  activate(context) {
    const config = vscode.workspace.getConfiguration("typedclojure-lsp");
    const startScript = config.get("startScript", ".typedclojure-lsp/start");

    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
      return;
    }

    const command = path.resolve(workspaceFolder.uri.fsPath, startScript);

    client = new LanguageClient(
      "typedclojure-lsp",
      "Typed Clojure LSP",
      { command, args: [] },
      { documentSelector: [{ scheme: "file", language: "clojure" }] },
    );

    client.start();
    context.subscriptions.push({ dispose: () => client?.stop() });
  },

  deactivate() {
    return client?.stop();
  },
};
