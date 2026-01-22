package fulguris.bookmark

import fulguris.constant.UTF8
import fulguris.database.Bookmark
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

/**
 * An exporter that produces the Netscape Bookmark File Format (HTML).
 *
 * See https://msdn.microsoft.com/en-us/ie/aa753582(v=vs.94)
 */
class NetscapeBookmarkFormatExporter @Inject constructor() {

    /**
     * Exports bookmarks to the Netscape Bookmark File Format (HTML).
     *
     * @param bookmarks The list of bookmarks to export.
     * @param outputStream The stream to write to.
     */
    fun exportBookmarks(bookmarks: List<Bookmark.Entry>, outputStream: OutputStream) {
        OutputStreamWriter(outputStream, UTF8).use { writer ->
            // Write header
            writer.write("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
            writer.write("<!-- This is an automatically generated file.\n")
            writer.write("     It will be read and overwritten.\n")
            writer.write("     DO NOT EDIT! -->\n")
            writer.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
            writer.write("<TITLE>Bookmarks</TITLE>\n")
            writer.write("<H1>Bookmarks</H1>\n")
            writer.write("<DL><p>\n")

            // Group bookmarks by folder path
            val folderMap = mutableMapOf<String, MutableList<Bookmark.Entry>>()
            bookmarks.forEach { bookmark ->
                val folderPath = bookmark.folder.title
                folderMap.getOrPut(folderPath) { mutableListOf() }.add(bookmark)
            }

            // Write bookmarks organized by folder hierarchy
            writeFolderHierarchy(writer, folderMap, "", 1)

            // Close root
            writer.write("</DL><p>\n")
        }
    }

    /**
     * Recursively writes folder hierarchy and bookmarks.
     *
     * @param writer The writer to write to.
     * @param folderMap Map of folder paths to bookmark lists.
     * @param currentPath The current folder path being processed.
     * @param indentLevel The indentation level for formatting.
     */
    private fun writeFolderHierarchy(
        writer: OutputStreamWriter,
        folderMap: Map<String, List<Bookmark.Entry>>,
        currentPath: String,
        indentLevel: Int
    ) {
        val indent = "    ".repeat(indentLevel)

        // Write bookmarks at the current level
        folderMap[currentPath]?.forEach { bookmark ->
            writer.write("$indent<DT><A HREF=\"${escapeHtml(bookmark.url)}\">${escapeHtml(bookmark.title)}</A>\n")
        }

        // Find and process immediate subfolders
        val subfolders = findImmediateSubfolders(folderMap.keys, currentPath)

        subfolders.forEach { subfolderPath ->
            val folderName = if (currentPath.isEmpty()) {
                subfolderPath
            } else {
                subfolderPath.substringAfter("$currentPath/")
            }

            writer.write("$indent<DT><H3>${escapeHtml(folderName)}</H3>\n")
            writer.write("$indent<DL><p>\n")
            writeFolderHierarchy(writer, folderMap, subfolderPath, indentLevel + 1)
            writer.write("$indent</DL><p>\n")
        }
    }

    /**
     * Finds immediate subfolders of the current path.
     *
     * @param allPaths All folder paths in the bookmark collection.
     * @param currentPath The current folder path.
     * @return A sorted list of immediate subfolder paths.
     */
    private fun findImmediateSubfolders(allPaths: Set<String>, currentPath: String): List<String> {
        val subfolders = mutableSetOf<String>()

        allPaths.forEach { path ->
            if (path.isEmpty()) return@forEach // Skip root

            // Check if this path is under the current path
            val isChild = if (currentPath.isEmpty()) {
                !path.contains('/')
            } else if (path.startsWith("$currentPath/")) {
                val remainder = path.substring(currentPath.length + 1)
                !remainder.contains('/')
            } else {
                false
            }

            if (isChild) {
                subfolders.add(path)
            }
        }

        return subfolders.sorted()
    }

    /**
     * Escapes HTML special characters.
     *
     * @param text The text to escape.
     * @return The escaped text.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
