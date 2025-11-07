# MCP Inspector Lite (IntelliJ/PyCharm Plugin)

Minimal plugin that connects to an MCP server over WebSocket, lists available tools, shows details, and invokes a tool with user-provided parameters.

## Features
- Tool window: MCP Inspector Lite
- 3 panes: Connection, Tools list, Details & Results
- WebSocket JSON-RPC 2.0 client for MCP
- Supports `tools/list` and `tools/call`

## Requirements
- JDK 17+
- Gradle (wrapper will be bootstrapped automatically)

## Running a local MCP server (example)
You can use any MCP server that exposes a WebSocket endpoint compatible with MCP JSON-RPC 2.0.

Example (Node.js-based servers that support WebSocket):

```bash
# Install a reference MCP server (example: filesystem)
npx @modelcontextprotocol/server-filesystem --help

# Many servers support a WebSocket transport; typical usage resembles:
# (Adjust based on the server's CLI flags)
#
# npx @modelcontextprotocol/server-filesystem \
#   --transport ws \
#   --port 3000 \
#   --root .
#
# After start, the server is reachable at ws://localhost:3000/
```

If your chosen server does not support WebSocket, you can run (or write) a small bridge that translates between stdio and WebSocket, then point the plugin to that ws:// URL.

## Build and Run the Plugin

```bash
# From the project root
./gradlew runIde
```

This launches a sandbox IDE with the plugin installed. Open the tool window named "MCP Inspector Lite" (Right side → Tool Windows).

## Using the Plugin
1. Enter your server WebSocket URL (e.g., `ws://localhost:3000/`).
2. Click Connect.
3. The Tools pane will populate via `tools/list`.
4. Select a tool to see details.
5. Enter a JSON object for parameters (if any) and click "Invoke Tool" to call `tools/call`.
6. The result will be shown in the output area.

## Notes
- This MVP uses Swing for the UI to reduce complexity. It can be migrated to Compose for Desktop or Compose for IntelliJ Platform if desired.
- JSON parsing is handled via kotlinx-serialization.
