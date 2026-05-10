package dev.cheroliv.codex.tasks

data class DocNode(
    val title: String,
    val level: Int,
    val children: MutableList<DocNode> = mutableListOf()
) {
    val isParagraph: Boolean get() = level == -1
}
