package com.cetvocab.app.domain.importer

/**
 * A small, dependency-free RFC 4180 style CSV reader/writer.
 *
 * Implemented by hand (instead of pulling in a library) because the app must validate what
 * the user selected very defensively: quoted fields, escaped quotes, embedded commas and
 * embedded line breaks are all handled, and a malformed file produces a readable error
 * instead of an exception. Pure Kotlin, so it is unit tested on the JVM.
 */
object CsvCodec {

    /**
     * Parses [text] into rows of cells.
     *
     * A trailing newline does not create an extra empty row. Unclosed quotes are tolerated by
     * treating the remainder of the file as one cell, which keeps one broken row from
     * destroying the whole import.
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        var rowHasContent = false

        fun endCell() {
            row.add(cell.toString())
            cell.setLength(0)
        }

        fun endRow() {
            endCell()
            rows.add(row)
            row = mutableListOf()
            rowHasContent = false
        }

        while (index < text.length) {
            val ch = text[index]
            when {
                inQuotes -> when {
                    ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        cell.append('"')
                        index++
                    }

                    ch == '"' -> inQuotes = false
                    else -> cell.append(ch)
                }

                ch == '"' -> {
                    inQuotes = true
                    rowHasContent = true
                }

                ch == ',' -> endCell()

                ch == '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') index++
                    endRow()
                }

                ch == '\n' -> endRow()

                else -> {
                    cell.append(ch)
                    if (!ch.isWhitespace()) rowHasContent = true
                }
            }
            index++
        }

        // Flush the last cell/row unless the file ended with a newline.
        if (cell.isNotEmpty() || row.isNotEmpty() || rowHasContent) {
            endRow()
        }
        return rows.filter { it.any { cellText -> cellText.isNotEmpty() } }
    }

    /** Writes [rows] as CSV with CRLF line endings and minimal quoting. */
    fun write(rows: List<List<String>>): String = buildString {
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { cellIndex, value ->
                if (cellIndex > 0) append(',')
                append(escape(value))
            }
            if (rowIndex != rows.lastIndex) append("\r\n")
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (!needsQuotes) value else "\"" + value.replace("\"", "\"\"") + "\""
    }

    /** Turns a header row into lower-case, punctuation-stripped column keys. */
    fun headerKeys(header: List<String>): List<String> = header.map { normalizeKey(it) }

    fun normalizeKey(raw: String): String = raw
        .trim()
        .lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")

    /** Splits a cell that holds several values (`a | b | c`, `a; b`, `a；b`). */
    fun splitValues(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split('|', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
