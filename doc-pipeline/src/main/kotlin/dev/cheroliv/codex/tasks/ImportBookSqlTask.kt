package dev.cheroliv.codex.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ImportBookSqlTask : DefaultTask() {

    @get:InputFile
    abstract val jsonFile: RegularFileProperty

    @get:OutputFile
    abstract val sqlFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val input = jsonFile.asFile.get()
        val output = sqlFile.asFile.get()

        logger.lifecycle("[codex] importBookSql : ${input.name} -> ${output.name}")

        val tree = parseJsonLdd(input)
        val sql = generateSql(tree)
        output.writeText(sql)
        logger.lifecycle("[codex] Done - SQL : ${sql.length} octets, ${tree.size} noeuds racine")
    }

    private fun parseJsonLdd(file: File): List<DocNode> {
        val raw = file.readText().trim()
        return parseArray(raw, 0).first
    }

    private fun parseArray(json: String, start: Int): Pair<List<DocNode>, Int> {
        val nodes = mutableListOf<DocNode>()
        var i = skipWhitespace(json, start)
        check(json[i] == '[') { "Attendu '[', trouvé '${json[i]}' à position $i" }
        i = skipWhitespace(json, i + 1)

        if (json[i] == ']') return nodes to (i + 1)

        while (true) {
            val (node, next) = parseObject(json, i)
            nodes.add(node)
            i = skipWhitespace(json, next)

            if (i >= json.length) break
            when (json[i]) {
                ',' -> i = skipWhitespace(json, i + 1)
                ']' -> return nodes to (i + 1)
                else -> error("Attendu ',' ou ']', trouvé '${json[i]}' à position $i")
            }
        }
        return nodes to i
    }

    private fun parseObject(json: String, start: Int): Pair<DocNode, Int> {
        var i = skipWhitespace(json, start)
        check(json[i] == '{') { "Attendu '{', trouvé '${json[i]}' à position $i" }
        i = skipWhitespace(json, i + 1)

        var title = ""
        var level = -1
        var isParagraph = false
        var text = ""
        val children = mutableListOf<DocNode>()

        while (i < json.length) {
            if (json[i] == '}') return buildNode(title, level, text, children) to (i + 1)

            check(json[i] == '"') { "Attendu '\"', trouvé '${json[i]}' à position $i" }
            val key = readString(json, i)
            i = skipWhitespace(json, key.second)
            check(json[i] == ':') { "Attendu ':', trouvé '${json[i]}' à position $i" }
            i = skipWhitespace(json, i + 1)

            when (key.first) {
                "title" -> {
                    check(json[i] == '"') { "Attendu string pour title, trouvé '${json[i]}'" }
                    title = readString(json, i).first
                    i = readString(json, i).second
                }
                "level" -> {
                    level = readInt(json, i).first
                    i = readInt(json, i).second
                }
                "type" -> {
                    isParagraph = readString(json, i).first == "paragraph"
                    i = readString(json, i).second
                }
                "text" -> {
                    text = readString(json, i).first
                    i = readString(json, i).second
                }
                "children" -> {
                    val (parsedChildren, nextI) = parseArray(json, i)
                    children.addAll(parsedChildren)
                    i = nextI
                }
                else -> {
                    skipValue(json, i).let { i = it }
                }
            }

            i = skipWhitespace(json, i)
            if (i < json.length && json[i] == ',') i = skipWhitespace(json, i + 1)
        }

        return buildNode(title, level, text, children) to i
    }

    private fun skipValue(json: String, start: Int): Int {
        var i = start
        when {
            json[i] == '"' -> i = readString(json, i).second
            json[i].isDigit() || json[i] == '-' -> i = readInt(json, i).second
            json[i] == '{' -> i = parseObject(json, i).second
            json[i] == '[' -> i = parseArray(json, i).second
            json[i] == 'n' -> i += 4 // null
            json[i] == 't' -> i += 4 // true
            json[i] == 'f' -> i += 5 // false
        }
        return i
    }

    private fun buildNode(title: String, level: Int, text: String, children: List<DocNode>): DocNode {
        return if (text.isNotEmpty()) {
            DocNode(title = text, level = -1, children = mutableListOf())
        } else {
            DocNode(title = title, level = level, children = children.toMutableList())
        }
    }

    private fun readString(json: String, start: Int): Pair<String, Int> {
        var i = start
        check(json[i] == '"') { "Attendu '\"', trouvé '${json[i]}' à position $i" }
        i++
        val sb = StringBuilder()
        while (i < json.length && json[i] != '"') {
            if (json[i] == '\\' && i + 1 < json.length) {
                i++
                when (json[i]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(json[i])
                }
            } else {
                sb.append(json[i])
            }
            i++
        }
        check(i < json.length) { "String non terminée à position $start" }
        return sb.toString() to (i + 1)
    }

    private fun readInt(json: String, start: Int): Pair<Int, Int> {
        var i = start
        val neg = if (i < json.length && json[i] == '-') { i++; true } else false
        var value = 0
        while (i < json.length && json[i].isDigit()) {
            value = value * 10 + (json[i] - '0')
            i++
        }
        return (if (neg) -value else value) to i
    }

    private fun skipWhitespace(json: String, start: Int): Int {
        var i = start
        while (i < json.length && json[i].isWhitespace()) i++
        return i
    }

    private fun generateSql(roots: List<DocNode>): String {
        val sb = StringBuilder()
        sb.appendLine("-- codex: importBookSql — JSON LDD → DDL + INSERT PostgreSQL")
        sb.appendLine("-- Généré automatiquement par ImportBookSqlTask")
        sb.appendLine()

        sb.appendLine("CREATE TABLE IF NOT EXISTS book (")
        sb.appendLine("    id          SERIAL PRIMARY KEY,")
        sb.appendLine("    title       TEXT NOT NULL,")
        sb.appendLine("    created_at  TIMESTAMP DEFAULT now()")
        sb.appendLine(");")
        sb.appendLine()

        sb.appendLine("CREATE TABLE IF NOT EXISTS documents (")
        sb.appendLine("    id          SERIAL PRIMARY KEY,")
        sb.appendLine("    book_id     INTEGER REFERENCES book(id) ON DELETE CASCADE,")
        sb.appendLine("    title       TEXT NOT NULL,")
        sb.appendLine("    level       INTEGER NOT NULL DEFAULT 0,")
        sb.appendLine("    parent_id   INTEGER REFERENCES documents(id) ON DELETE CASCADE,")
        sb.appendLine("    sort_order  INTEGER NOT NULL DEFAULT 0")
        sb.appendLine(");")
        sb.appendLine()

        sb.appendLine("CREATE TABLE IF NOT EXISTS paragraphs (")
        sb.appendLine("    id          SERIAL PRIMARY KEY,")
        sb.appendLine("    doc_id      INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,")
        sb.appendLine("    text        TEXT NOT NULL,")
        sb.appendLine("    position    INTEGER NOT NULL DEFAULT 0")
        sb.appendLine(");")
        sb.appendLine()

        sb.appendLine("BEGIN;")
        sb.appendLine()

        val bookTitle = roots.firstOrNull { it.level > 0 }?.title ?: "unknown"
        sb.appendLine("INSERT INTO book (title) VALUES ('${escSql(bookTitle)}');")
        sb.appendLine("SELECT setval('book_id_seq', 1, true);")
        sb.appendLine()

        var docCounter = 0
        var paraCounter = 0

        for ((idx, root) in roots.withIndex()) {
            docCounter++
            val docId = docCounter
            sb.append("INSERT INTO documents (book_id, title, level, parent_id, sort_order) ")
            sb.appendLine("VALUES (1, '${escSql(root.title)}', ${root.level}, NULL, $idx);")
            val result = traverseForInsert(root, docId, sb, docCounter, paraCounter)
            docCounter = result.first
            paraCounter = result.second
        }

        sb.appendLine()
        sb.appendLine("COMMIT;")
        return sb.toString()
    }

    private fun traverseForInsert(
        node: DocNode,
        parentDocId: Int,
        sb: StringBuilder,
        currentDocCounter: Int,
        currentParaCounter: Int
    ): Pair<Int, Int> {
        var docCounter = currentDocCounter
        var paraCounter = currentParaCounter

        for ((idx, child) in node.children.withIndex()) {
            if (child.isParagraph) {
                paraCounter++
                sb.append("INSERT INTO paragraphs (doc_id, text, position) ")
                sb.appendLine("VALUES ($parentDocId, '${escSql(child.title)}', ${idx + 1});")
            } else {
                docCounter++
                val childDocId = docCounter
                sb.append("INSERT INTO documents (book_id, title, level, parent_id, sort_order) ")
                sb.appendLine("VALUES (1, '${escSql(child.title)}', ${child.level}, $parentDocId, $idx);")
                val (newDoc, newPara) = traverseForInsert(child, childDocId, sb, docCounter, paraCounter)
                docCounter = newDoc
                paraCounter = newPara
            }
        }
        return docCounter to paraCounter
    }

    private fun escSql(s: String): String {
        return s
            .replace("'", "''")
            .replace("\\", "\\\\")
    }
}
