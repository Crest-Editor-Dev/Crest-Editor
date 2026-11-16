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
    // Theme Colors for File Icons matching VS Code Material standards
    val ColorFolder = Color(0xFFDCDCAA)
    val ColorKotlin = Color(0xFFFFA657)
    val ColorJava = Color(0xFFE76F00)
    val ColorJavaScript = Color(0xFFF7DF1E)
    val ColorTypeScript = Color(0xFF3178C6)
    val ColorPython = Color(0xFF3776AB)
    val ColorJson = Color(0xFFCBCB41)
    val ColorYaml = Color(0xFFCB171E)
    val ColorHtml = Color(0xFFE34C26)
    val ColorCss = Color(0xFF388BFD)
    val ColorMarkdown = Color(0xFF519ABA)
    val ColorXml = Color(0xFF4EC9B0)
    val ColorSql = Color(0xFFCC292B)
    val ColorShell = Color(0xFF89E051)
    val ColorRust = Color(0xFFDEA584)
    val ColorCpp = Color(0xFF00599C)
    val ColorC = Color(0xFF555555)
    val ColorCSharp = Color(0xFF178600)
    val ColorGo = Color(0xFF00ADD8)
    val ColorPhp = Color(0xFF4F5D95)
    val ColorRuby = Color(0xFF701516)
    val ColorSwift = Color(0xFFF05138)
    val ColorDart = Color(0xFF00B4AB)
    val ColorLua = Color(0xFF000080)
    val ColorToml = Color(0xFF9C4221)
    val ColorVue = Color(0xFF41B883)
    val ColorSvelte = Color(0xFFFF3E00)
    val ColorDocker = Color(0xFF2496ED)
    val ColorDefault = Color(0xFFCCCCCC)

    /**
     * Resolves the [FileTypeDescriptor] corresponding to the given file name or extension.
     */
    fun getDescriptor(filename: String): FileTypeDescriptor {
        val lower = filename.lowercase()
        // Special filenames
        if (lower == "dockerfile" || lower.startsWith("dockerfile.")) {
            return FileTypeDescriptor("dockerfile", Icons.Default.Code, ColorDocker)
        }
        if (lower == "makefile" || lower == "gnumakefile") {
            return FileTypeDescriptor("makefile", Icons.Default.Build, ColorDefault)
        }
        if (lower == ".gitignore" || lower == ".gitattributes" || lower == ".gitmodules") {
            return FileTypeDescriptor("ignore", Icons.Default.Description, ColorDefault)
        }
        if (lower == ".env" || lower.startsWith(".env.")) {
            return FileTypeDescriptor("ini", Icons.Default.Settings, ColorDefault)
        }

        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> FileTypeDescriptor("kotlin", Icons.Default.Code, ColorKotlin)
            "java" -> FileTypeDescriptor("java", Icons.Default.Code, ColorJava)
            "js", "jsx", "mjs", "cjs" -> FileTypeDescriptor("javascript", Icons.Default.Javascript, ColorJavaScript)
            "ts", "tsx", "mts", "cts" -> FileTypeDescriptor("typescript", Icons.Default.Code, ColorTypeScript)
            "py", "pyw", "ipynb" -> FileTypeDescriptor("python", Icons.Default.Code, ColorPython)
            "json" -> FileTypeDescriptor("json", Icons.Default.DataObject, ColorJson)
            "jsonc", "json5" -> FileTypeDescriptor("jsonc", Icons.Default.DataObject, ColorJson)
            "yml", "yaml" -> FileTypeDescriptor("yaml", Icons.Default.DataObject, ColorYaml)
            "html", "htm", "xhtml" -> FileTypeDescriptor("html", Icons.Default.Html, ColorHtml)
            "css" -> FileTypeDescriptor("css", Icons.Default.Css, ColorCss)
            "scss", "sass" -> FileTypeDescriptor("scss", Icons.Default.Css, ColorCss)
            "less" -> FileTypeDescriptor("less", Icons.Default.Css, ColorCss)
            "md", "markdown", "mdx" -> FileTypeDescriptor("markdown", Icons.Default.Description, ColorMarkdown)
            "xml", "xsd", "svg", "plist" -> FileTypeDescriptor("xml", Icons.Default.Code, ColorXml)
            "sql", "dsql" -> FileTypeDescriptor("sql", Icons.Default.Code, ColorSql)
            "sh", "bash", "zsh", "fish", "ksh" -> FileTypeDescriptor("shell", Icons.Default.Terminal, ColorShell)
            "ps1", "psm1", "psd1" -> FileTypeDescriptor("powershell", Icons.Default.Terminal, ColorShell)
            "bat", "cmd" -> FileTypeDescriptor("bat", Icons.Default.Terminal, ColorShell)
            "c", "h" -> FileTypeDescriptor("c", Icons.Default.Code, ColorC)
            "cpp", "cc", "cxx", "hpp", "hh", "hxx", "inl" -> FileTypeDescriptor("cpp", Icons.Default.Code, ColorCpp)
            "cs", "csx" -> FileTypeDescriptor("csharp", Icons.Default.Code, ColorCSharp)
            "rs" -> FileTypeDescriptor("rust", Icons.Default.Code, ColorRust)
            "go" -> FileTypeDescriptor("go", Icons.Default.Code, ColorGo)
            "php", "phtml" -> FileTypeDescriptor("php", Icons.Default.Code, ColorPhp)
            "rb", "erb", "rake", "gemspec" -> FileTypeDescriptor("ruby", Icons.Default.Code, ColorRuby)
            "swift" -> FileTypeDescriptor("swift", Icons.Default.Code, ColorSwift)
            "dart" -> FileTypeDescriptor("dart", Icons.Default.Code, ColorDart)
            "lua" -> FileTypeDescriptor("lua", Icons.Default.Code, ColorLua)
            "toml" -> FileTypeDescriptor("toml", Icons.Default.Settings, ColorToml)
            "ini", "cfg", "conf", "properties" -> FileTypeDescriptor("ini", Icons.Default.Settings, ColorDefault)
            "dockerfile" -> FileTypeDescriptor("dockerfile", Icons.Default.Code, ColorDocker)
            "vue" -> FileTypeDescriptor("vue", Icons.Default.Code, ColorVue)
            "svelte" -> FileTypeDescriptor("svelte", Icons.Default.Code, ColorSvelte)
            "graphql", "gql" -> FileTypeDescriptor("graphql", Icons.Default.Code, ColorJson)
            "r", "rmd" -> FileTypeDescriptor("r", Icons.Default.Code, ColorCpp)
            "zig" -> FileTypeDescriptor("zig", Icons.Default.Code, ColorRust)
            "diff", "patch" -> FileTypeDescriptor("diff", Icons.Default.Description, ColorDefault)
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
