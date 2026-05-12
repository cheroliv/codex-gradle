package dev.cheroliv.codex.tasks

import kotlinx.serialization.Serializable

@Serializable
data class LddNode(
    val title: String = "",
    val level: Int = 0,
    val type: String? = null,
    val text: String? = null,
    val children: List<LddNode> = emptyList()
)

data class DocNode(
    val title: String,
    val level: Int,
    val children: MutableList<DocNode> = mutableListOf()
) {
    val isParagraph: Boolean get() = level == -1
}

fun LddNode.toDocNode(): DocNode {
    return if (type == "paragraph" || text != null) {
        DocNode(title = text ?: "", level = -1)
    } else {
        val docChildren = children.map { it.toDocNode() }.toMutableList()
        DocNode(title = title, level = level, children = docChildren)
    }
}

fun DocNode.toLddNode(): LddNode {
    if (isParagraph) {
        return LddNode(type = "paragraph", text = title)
    }
    return LddNode(
        title = title,
        level = level,
        children = if (children.isEmpty()) emptyList()
        else children.map { it.toLddNode() }
    )
}
