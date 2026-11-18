package com.zerorizz.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val INLINE = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|`([^`]+)`|\[\[([^\]]+)]]|(?<![\w#])#([\w/-]+)|\*(.+?)\*""")
private val LINK = Regex("""\[\[([^\]]+)]]""")
private val TAG = Regex("""(?<![\w#])#([\w/-]+)""")

fun extractLinks(text: String): List<String> = LINK.findAll(text).map { it.groupValues[1].trim() }.toList()
fun extractTags(text: String): List<String> = TAG.findAll(text).map { it.groupValues[1] }.toList()

/** Plain-text preview without markdown symbols. */
fun stripMarkdown(text: String): String = text.lines().joinToString(" ") { l ->
    l.trim().removePrefix("### ").removePrefix("## ").removePrefix("# ")
        .removePrefix("- [ ] ").removePrefix("- [x] ").removePrefix("- ").removePrefix("> ")
}.replace("**", "").replace("~~", "").replace("[[", "").replace("]]", "").replace("`", "").trim()

fun toggleTaskLine(text: String, lineIndex: Int): String {
    val lines = text.lines().toMutableList()
    if (lineIndex !in lines.indices) return text
    val l = lines[lineIndex]
    lines[lineIndex] = when {
        l.contains("- [ ] ") -> l.replaceFirst("- [ ] ", "- [x] ")
        l.contains("- [x] ") -> l.replaceFirst("- [x] ", "- [ ] ")
        l.contains("- [X] ") -> l.replaceFirst("- [X] ", "- [ ] ")
        else -> l
    }
    return lines.joinToString("\n")
}

fun inlineMarkdown(
    text: String,
    linkColor: Color,
    tagColor: Color,
    codeBg: Color,
    onLink: ((String) -> Unit)?,
    onTag: ((String) -> Unit)?,
): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
            g[2].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[2]) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(" ${g[3]} ") }
            g[4].isNotEmpty() -> {
                val target = g[4]
                val style = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)
                if (onLink != null) {
                    withLink(LinkAnnotation.Clickable("link:$target", TextLinkStyles(style)) { onLink(target) }) { append(target) }
                } else withStyle(style) { append(target) }
            }
            g[5].isNotEmpty() -> {
                val tag = g[5]
                val style = SpanStyle(color = tagColor, fontWeight = FontWeight.Medium, background = tagColor.copy(alpha = 0.12f))
                if (onTag != null) {
                    withLink(LinkAnnotation.Clickable("tag:$tag", TextLinkStyles(style)) { onTag(tag) }) { append("#$tag") }
                } else withStyle(style) { append("#$tag") }
            }
            g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownView(
    text: String,
    modifier: Modifier = Modifier,
    onLink: ((String) -> Unit)? = null,
    onTag: ((String) -> Unit)? = null,
    onToggleTask: ((Int) -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    fun inl(s: String) = inlineMarkdown(s, cs.primary, cs.tertiary, cs.surfaceVariant, onLink, onTag)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        text.lines().forEachIndexed { idx, raw ->
            val indent = (raw.takeWhile { it == ' ' }.length / 2).dp * 16
            val t = raw.trim()
            when {
                t.startsWith("### ") -> Text(inl(t.drop(4)), style = ty.titleMedium, fontWeight = FontWeight.SemiBold)
                t.startsWith("## ") -> Text(inl(t.drop(3)), style = ty.titleLarge, fontWeight = FontWeight.SemiBold)
                t.startsWith("# ") -> Text(inl(t.drop(2)), style = ty.headlineSmall, fontWeight = FontWeight.Bold)
                t.startsWith("- [ ] ") || t.startsWith("- [x] ", ignoreCase = true) -> {
                    val checked = t[3].lowercaseChar() == 'x'
                    Row(Modifier.padding(start = indent), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = onToggleTask?.let { f -> { _: Boolean -> f(idx) } },
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            inl(t.drop(6)),
                            style = ty.bodyLarge,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                            color = if (checked) cs.onSurfaceVariant else Color.Unspecified,
                        )
                    }
                }
                t.startsWith("- ") || t.startsWith("* ") -> Row(Modifier.padding(start = indent + 6.dp)) {
                    Text("•", style = ty.bodyLarge, color = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(inl(t.drop(2)), style = ty.bodyLarge)
                }
                t.startsWith("> ") -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
                    Spacer(Modifier.width(10.dp))
                    Text(inl(t.drop(2)), style = ty.bodyLarge.copy(fontStyle = FontStyle.Italic), color = cs.onSurfaceVariant)
                }
                t == "---" || t == "***" -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
                t.isEmpty() -> Spacer(Modifier.height(4.dp))
                else -> Text(inl(t), style = ty.bodyLarge)
            }
        }
    }
}

fun insertMarkdown(v: TextFieldValue, before: String, after: String): TextFieldValue {
    val s = v.selection.min
    val e = v.selection.max
    return if (after.isEmpty() && before.endsWith(" ")) {
        val lineStart = v.text.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0) 0 else it + 1 }
        val text = v.text.substring(0, lineStart) + before + v.text.substring(lineStart)
        TextFieldValue(text, TextRange(s + before.length))
    } else {
        val text = v.text.substring(0, s) + before + v.text.substring(s, e) + after + v.text.substring(e)
        TextFieldValue(text, TextRange(s + before.length, e + before.length))
    }
}

private val TOOLBAR = listOf(
    "H1" to ("# " to ""),
    "H2" to ("## " to ""),
    "B" to ("**" to "**"),
    "I" to ("*" to "*"),
    "☐ Task" to ("- [ ] " to ""),
    "• List" to ("- " to ""),
    "[[Link]]" to ("[[" to "]]"),
    "#tag" to ("#" to ""),
    "❝ Quote" to ("> " to ""),
    "Code" to ("`" to "`"),
    "Divider" to ("\n---\n" to ""),
)

@Composable
fun MarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Write in markdown…",
    minLines: Int = 6,
) {
    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TOOLBAR.forEach { (label, wrap) ->
                AssistChip(
                    onClick = { onValueChange(insertMarkdown(value, wrap.first, wrap.second)) },
                    label = { Text(label) },
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            placeholder = { Text(placeholder) },
        )
    }
}
