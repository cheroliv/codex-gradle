package dev.cheroliv.codex.tasks

import kotlinx.serialization.json.Json
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
        val lddNodes = Json.decodeFromString<List<LddNode>>(raw)
        return lddNodes.map { it.toDocNode() }
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
