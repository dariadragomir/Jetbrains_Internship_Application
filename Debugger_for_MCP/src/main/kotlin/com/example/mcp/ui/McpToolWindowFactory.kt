package com.example.mcp.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class McpToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = McpInspectorPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class McpInspectorPanel : JPanel(BorderLayout()) {
    private val connectionPanel = ConnectionPanel()
    private val toolsPanel = ToolsPanel()
    private val detailsPanel = DetailsPanel()

    init {
        val splitMain = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        val left = JPanel(BorderLayout())
        left.add(connectionPanel, BorderLayout.NORTH)
        left.add(JBScrollPane(toolsPanel), BorderLayout.CENTER)

        splitMain.leftComponent = left
        splitMain.rightComponent = JBScrollPane(detailsPanel)
        splitMain.resizeWeight = 0.35

        add(splitMain, BorderLayout.CENTER)

        connectionPanel.onConnected = { client ->
            toolsPanel.setClient(client)
            detailsPanel.setClient(client)
            toolsPanel.refreshTools { tools ->
                detailsPanel.showMessage("Loaded ${'$'}{tools.size} tools.")
            }
        }
        toolsPanel.onToolSelected = { tool ->
            detailsPanel.showTool(tool)
        }
        detailsPanel.onInvoke = { toolName, params ->
            toolsPanel.client?.let { client ->
                detailsPanel.showMessage("Invoking ${'$'}toolName...")
                client.callTool(toolName, params) { result, error ->
                    if (error != null) {
                        detailsPanel.showMessage("Error: ${'$'}error")
                    } else {
                        detailsPanel.showMessage(result ?: "<no result>")
                    }
                }
            }
        }
    }
}

class ConnectionPanel : JPanel() {
    var onConnected: ((com.example.mcp.ws.McpWebSocketClient) -> Unit)? = null

    private val urlField = JTextField("ws://localhost:3000/")
    private val connectButton = JButton("Connect")
    private val statusLabel = JLabel("Disconnected")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createTitledBorder("Connection")

        val row1 = JPanel()
        row1.layout = BoxLayout(row1, BoxLayout.X_AXIS)
        row1.add(JLabel("Server WS URL: "))
        row1.add(urlField)
        urlField.maximumSize = Dimension(Int.MAX_VALUE, urlField.preferredSize.height)

        val row2 = JPanel()
        row2.layout = BoxLayout(row2, BoxLayout.X_AXIS)
        row2.add(connectButton)
        row2.add(Box.createHorizontalStrut(8))
        row2.add(statusLabel)

        add(row1)
        add(Box.createVerticalStrut(6))
        add(row2)

        connectButton.addActionListener {
            connectButton.isEnabled = false
            statusLabel.text = "Connecting..."
            val client = com.example.mcp.ws.McpWebSocketClient(urlField.text) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "Connected"
                    onConnected?.invoke(it)
                }
            }
            client.connect({ err ->
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error: ${'$'}err"
                    connectButton.isEnabled = true
                }
            })
        }
    }
}

class ToolsPanel : JPanel(BorderLayout()) {
    var client: com.example.mcp.ws.McpWebSocketClient? = null
        private set

    private val listModel = DefaultListModel<McpTool>()
    private val list = JList(listModel)
    var onToolSelected: ((McpTool) -> Unit)? = null

    init {
        border = BorderFactory.createTitledBorder("Tools")
        add(JBScrollPane(list), BorderLayout.CENTER)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addListSelectionListener {
            val tool = list.selectedValue
            if (tool != null) onToolSelected?.invoke(tool)
        }
    }

    fun setClient(client: com.example.mcp.ws.McpWebSocketClient) {
        this.client = client
    }

    fun refreshTools(onDone: (List<McpTool>) -> Unit = {}) {
        val c = client ?: return
        c.listTools { tools, error ->
            SwingUtilities.invokeLater {
                listModel.clear()
                if (error != null) {
                    listModel.addElement(McpTool("<error>", "${'$'}error", emptyList()))
                    onDone(emptyList())
                } else {
                    tools.forEach { listModel.addElement(it) }
                    onDone(tools)
                }
            }
        }
    }
}

data class McpTool(
    val name: String,
    val description: String?,
    val inputKeys: List<String>
) {
    override fun toString(): String = name
}

class DetailsPanel : JPanel() {
    private val title = JLabel("Select a tool")
    private val descriptionArea = JTextArea(5, 40)
    private val paramsArea = JTextArea(6, 40)
    private val invokeButton = JButton("Invoke Tool")
    private val outputArea = JTextArea(10, 40)

    private var currentTool: McpTool? = null
    private var client: com.example.mcp.ws.McpWebSocketClient? = null

    var onInvoke: ((String, Map<String, Any?>) -> Unit)? = null

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createTitledBorder("Details & Results")

        descriptionArea.isEditable = false
        outputArea.isEditable = false

        add(title)
        add(Box.createVerticalStrut(6))
        add(JLabel("Description:"))
        add(JBScrollPane(descriptionArea))
        add(Box.createVerticalStrut(6))
        add(JLabel("Parameters (JSON object):"))
        add(JBScrollPane(paramsArea))
        add(Box.createVerticalStrut(6))
        add(invokeButton)
        add(Box.createVerticalStrut(6))
        add(JLabel("Result:"))
        add(JBScrollPane(outputArea))

        invokeButton.addActionListener {
            val tool = currentTool ?: return@addActionListener
            val paramsText = paramsArea.text.trim()
            val params = try {
                if (paramsText.isBlank()) emptyMap() else com.example.mcp.ws.Json.parseObject(paramsText)
            } catch (e: Exception) {
                showMessage("Invalid JSON: ${'$'}{e.message}")
                return@addActionListener
            }
            onInvoke?.invoke(tool.name, params)
        }
    }

    fun setClient(client: com.example.mcp.ws.McpWebSocketClient) {
        this.client = client
    }

    fun showTool(tool: McpTool) {
        currentTool = tool
        title.text = tool.name
        descriptionArea.text = tool.description ?: ""
        paramsArea.text = if (tool.inputKeys.isNotEmpty()) {
            val entries = tool.inputKeys.joinToString(",\n") { "\t\"${'$'}it\": null" }
            "{\n${'$'}entries\n}"
        } else "{}"
        outputArea.text = ""
    }

    fun showMessage(message: String) {
        outputArea.text = message
    }
}

