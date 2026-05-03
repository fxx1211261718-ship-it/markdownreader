package com.example.markdownreader.ui.reader

internal fun preprocessMarkdown(content: String): String {
    val normalizedLines = content.replace("\r\n", "\n").split('\n')
    val result = StringBuilder()
    var index = 0

    while (index < normalizedLines.size) {
        val line = normalizedLines[index]
        if (line.trim() == "$$") {
            index += 1
            val mathLines = mutableListOf<String>()
            while (index < normalizedLines.size && normalizedLines[index].trim() != "$$") {
                mathLines += normalizedLines[index]
                index += 1
            }
            result.appendLine("```math")
            mathLines.forEach { result.appendLine(it) }
            result.appendLine("```")
            if (index < normalizedLines.size && normalizedLines[index].trim() == "$$") {
                index += 1
            }
            continue
        }

        result.append(line)
        if (index != normalizedLines.lastIndex) {
            result.append('\n')
        }
        index += 1
    }

    return result.toString()
}
