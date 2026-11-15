package com.cresteditor.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Descriptor containing Monaco language ID and Compose UI icon styling for a file type.
 */
data class FileTypeDescriptor(
    val languageId: String,
    val icon: ImageVector,
    val iconColor: Color
)

/**
 * Single source of truth for file extensions, Monaco language identifiers, and UI icon styling.
 */
object FileTypeRegistry {
    // Theme Colors for File Icons
    val ColorFolder = Color(0xFFDCDCAA)
    val ColorKotlin = Color(0xFFFFA657)
    val ColorJava = Color(0xFFE76F00)
    val ColorJavaScript = Color(0xFFF7DF1E)
    val ColorTypeScript = Color(0xFF3178C6)
    val ColorPython = Color(0xFF3776AB)
    val ColorJson = Color(0xFFCBCB41)
    val ColorHtml = Color(0xFFE34C26)
    val ColorCss = Color(0xFF388BFD)
    val ColorMarkdown = Color(0xFF519ABA)
    val ColorXml = Color(0xFF4EC9B0)
    val ColorSql = Color(0xFFCC292B)
    val ColorShell = Color(0xFF89E051)
    val ColorDefault = Color(0xFFCCCCCC)

    /**
     * Resolves the [FileTypeDescriptor] corresponding to the given file name or extension.
     */
    fun getDescriptor(filename: String): FileTypeDescriptor {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> FileTypeDescriptor("kotlin", Icons.Default.Code, ColorKotlin)
            "java" -> FileTypeDescriptor("java", Icons.Default.Code, ColorJava)
            "js", "jsx", "mjs", "cjs" -> FileTypeDescriptor("javascript", Icons.Default.Javascript, ColorJavaScript)
            "ts", "tsx" -> FileTypeDescriptor("typescript", Icons.Default.Code, ColorTypeScript)
            "py" -> FileTypeDescriptor("python", Icons.Default.Code, ColorPython)
            "json" -> FileTypeDescriptor("json", Icons.Default.DataObject, ColorJson)
            "html", "htm" -> FileTypeDescriptor("html", Icons.Default.Html, ColorHtml)
            "css", "scss", "less" -> FileTypeDescriptor("css", Icons.Default.Css, ColorCss)
            "md", "markdown" -> FileTypeDescriptor("markdown", Icons.Default.Description, ColorMarkdown)
            "xml" -> FileTypeDescriptor("xml", Icons.Default.Code, ColorXml)
            "sql" -> FileTypeDescriptor("sql", Icons.Default.Code, ColorSql)
            "sh", "bash", "zsh" -> FileTypeDescriptor("shell", Icons.Default.Terminal, ColorShell)
            else -> FileTypeDescriptor("plaintext", Icons.Default.InsertDriveFile, ColorDefault)
        }
    }

    /**
     * Detects the Monaco language ID for syntax highlighting.
     */
    fun detectLanguage(filename: String): String = getDescriptor(filename).languageId

    /**
     * Returns the pair of (ImageVector, Color) for UI file tree rendering.
     */
    fun getFileIcon(filename: String): Pair<ImageVector, Color> {
        val descriptor = getDescriptor(filename)
        return Pair(descriptor.icon, descriptor.iconColor)
    }
}
