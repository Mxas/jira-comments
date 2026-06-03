package eu.mxtool.jiracomments.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for building Atlassian Document Format (ADF) node trees.
 *
 * <p>ADF is the structured JSON format used by Jira Cloud for rich-text content
 * (comments, descriptions, etc.) when posted via REST API v3.
 *
 * <p>Usage example:
 * <pre>{@code
 * Map<String, Object> doc = AdfBuilder.doc(List.of(
 *     AdfBuilder.heading(1, "Title"),
 *     AdfBuilder.expand("Section (3)", List.of(
 *         AdfBuilder.table(List.of(
 *             AdfBuilder.tr(List.of(AdfBuilder.th("Col A"), AdfBuilder.th("Col B"))),
 *             AdfBuilder.tr(List.of(
 *                 AdfBuilder.td(List.of(AdfBuilder.text("value"))),
 *                 AdfBuilder.td(List.of(AdfBuilder.code("mono")))
 *             ))
 *         ))
 *     ))
 * ));
 * }</pre>
 */
public final class AdfBuilder {

    private AdfBuilder() {}

    // ── Block nodes ───────────────────────────────────────────────────────────

    /** Root ADF document node. */
    public static Map<String, Object> doc(List<Map<String, Object>> content) {
        Map<String, Object> node = new HashMap<>();
        node.put("version", 1);
        node.put("type", "doc");
        node.put("content", content);
        return node;
    }

    /** Heading (level 1–6). */
    public static Map<String, Object> heading(int level, String text) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "heading");
        node.put("attrs", Map.of("level", level));
        node.put("content", List.of(text(text)));
        return node;
    }

    /** Collapsible expand panel with a title. */
    public static Map<String, Object> expand(String title, List<Map<String, Object>> content) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "expand");
        node.put("attrs", Map.of("title", title));
        node.put("content", content);
        return node;
    }

    /** Paragraph block. */
    public static Map<String, Object> para(List<Map<String, Object>> inlineContent) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "paragraph");
        node.put("content", inlineContent != null ? inlineContent : new ArrayList<>());
        return node;
    }

    // ── Table nodes ───────────────────────────────────────────────────────────

    /** Table with default layout, no number column. */
    public static Map<String, Object> table(List<Map<String, Object>> rows) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("isNumberColumnEnabled", false);
        attrs.put("layout", "default");
        Map<String, Object> node = new HashMap<>();
        node.put("type", "table");
        node.put("attrs", attrs);
        node.put("content", rows);
        return node;
    }

    /** Table row. */
    public static Map<String, Object> tr(List<Map<String, Object>> cells) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "tableRow");
        node.put("content", cells);
        return node;
    }

    /** Table header cell with a single plain-text label. */
    public static Map<String, Object> th(String label) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "tableHeader");
        node.put("attrs", new HashMap<>());
        node.put("content", List.of(para(List.of(text(label)))));
        return node;
    }

    /** Table data cell containing one or more inline nodes. */
    public static Map<String, Object> td(List<Map<String, Object>> inlineNodes) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "tableCell");
        node.put("attrs", new HashMap<>());
        node.put("content", List.of(para(inlineNodes)));
        return node;
    }

    // ── Inline nodes ──────────────────────────────────────────────────────────

    /** Plain text. */
    public static Map<String, Object> text(String t) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", t != null ? t : "");
        return node;
    }

    /** Text rendered in monospace (code mark). */
    public static Map<String, Object> code(String t) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", t != null ? t : "");
        node.put("marks", List.of(Map.of("type", "code")));
        return node;
    }

    /** Text rendered in italic (em mark). */
    public static Map<String, Object> em(String t) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", t != null ? t : "");
        node.put("marks", List.of(Map.of("type", "em")));
        return node;
    }

    /** Text rendered in bold (strong mark). */
    public static Map<String, Object> strong(String t) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", t != null ? t : "");
        node.put("marks", List.of(Map.of("type", "strong")));
        return node;
    }

    /**
     * Hyperlink text. Falls back to plain {@link #text} when {@code href} is null/blank.
     */
    public static Map<String, Object> link(String label, String href) {
        if (href == null || href.isBlank()) return text(label != null ? label : "");
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", label != null ? label : "");
        node.put("marks", List.of(Map.of("type", "link", "attrs", Map.of("href", href))));
        return node;
    }

    /**
     * Monospace hyperlink text. Falls back to plain {@link #code} when {@code href} is null/blank.
     */
    public static Map<String, Object> codeLink(String label, String href) {
        if (href == null || href.isBlank()) return code(label != null ? label : "");
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", label != null ? label : "");
        node.put("marks", List.of(
                Map.of("type", "link", "attrs", Map.of("href", href)),
                Map.of("type", "code")
        ));
        return node;
    }

    /**
     * Bold hyperlink text. Falls back to plain {@link #strong} when {@code href} is null/blank.
     */
    public static Map<String, Object> boldLink(String label, String href) {
        if (href == null || href.isBlank()) return strong(label != null ? label : "");
        Map<String, Object> node = new HashMap<>();
        node.put("type", "text");
        node.put("text", label != null ? label : "");
        node.put("marks", List.of(
                Map.of("type", "strong"),
                Map.of("type", "link", "attrs", Map.of("href", href))
        ));
        return node;
    }
}

