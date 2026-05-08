const path = require("path");
const fs = require("fs");
const vscode = require("vscode");
const { LanguageClient } = require("vscode-languageclient/node");

let client;

module.exports = {
  activate(context) {
    const folders = vscode.workspace.workspaceFolders;
    if (!folders || folders.length === 0) {
      vscode.window.showErrorMessage(
        "Typed Clojure LSP: open a folder or workspace to use this extension.",
      );
      return;
    }

    const root = folders[0].uri.fsPath;
    const config = vscode.workspace.getConfiguration("typedclojure-lsp");
    const configuredCommand = config.get("command");
    const args = config.get("args") ?? [];

    const command = path.isAbsolute(configuredCommand)
      ? configuredCommand
      : path.join(root, configuredCommand);

    try {
      fs.accessSync(command, fs.constants.X_OK);
    } catch {
      vscode.window.showErrorMessage(
        `Typed Clojure LSP: ${command} is missing or not executable. Set typedclojure-lsp.command or see the README.`,
      );
      return;
    }

    client = new LanguageClient(
      "typedclojure-lsp",
      "Typed Clojure LSP",
      { command, args, options: { cwd: root } },
      { documentSelector: [{ scheme: "file", language: "clojure" }] },
    );

    client.start();
    context.subscriptions.push({ dispose: () => client?.stop() });
  },

  deactivate() {
    return client?.stop();
  },
};
