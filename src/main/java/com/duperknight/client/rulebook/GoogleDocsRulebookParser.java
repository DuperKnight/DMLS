package com.duperknight.client.rulebook;

import com.duperknight.client.rulebook.DocumentBlock.ImageBlock;
import com.duperknight.client.rulebook.DocumentBlock.InlineItem;
import com.duperknight.client.rulebook.DocumentBlock.RichText;
import com.duperknight.client.rulebook.DocumentBlock.RuleBox;
import com.duperknight.client.rulebook.DocumentBlock.Spacer;
import com.duperknight.client.rulebook.DocumentBlock.TableBlock;
import com.duperknight.client.rulebook.DocumentBlock.TableCell;
import com.duperknight.client.rulebook.DocumentBlock.TextBlock;
import com.duperknight.client.rulebook.DocumentBlock.TextKind;
import com.duperknight.client.rulebook.DocumentBlock.TextRun;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the safe, anonymous Google Docs HTML export into renderer-neutral blocks. */
public final class GoogleDocsRulebookParser {
    private static final Pattern RULE_ID = Pattern.compile("(?:[a-z]+)?\\d+(?:\\.\\d+)+(?:\\.[a-z])?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_RULE = Pattern.compile("\\.([\\w-]+)\\{([^}]*)}");
    private static final Pattern DATA_IMAGE = Pattern.compile("data:(image/(?:png|jpe?g));base64,(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_777_216L;
    private static final long MAX_DOCUMENT_IMAGE_PIXELS = 33_554_432L;
    private static final int MAX_DOCUMENT_IMAGES = 128;

    private GoogleDocsRulebookParser() {
    }

    public static RulebookDocument parse(String html) throws IOException {
        if (html == null || html.isBlank()) throw new IOException("Rulebook HTML is empty");
        org.jsoup.nodes.Document source = Jsoup.parse(html);
        Element body = source.body();
        if (body == null) throw new IOException("Rulebook HTML has no body");

        String rawCss = source.select("style").stream().map(Element::data).reduce("", (a, b) -> a + b);
        Css css = new Css(rawCss);
        List<DocumentBlock> blocks = new ArrayList<>();
        List<RulebookRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String section = "Stoneworks Rules";

        for (Element element : body.children()) {
            String tag = element.normalName();
            if (element.hasClass("title")) {
                TextBlock heading = textBlock(element, TextKind.TITLE, 0, css);
                if (!heading.text().isBlank()) {
                    section = heading.text().plainText().trim();
                    blocks.add(heading);
                }
                element.select("img").forEach(image -> blocks.add(parseImage(image)));
            } else if (tag.matches("h[1-6]")) {
                TextBlock heading = textBlock(element, headingKind(tag), 0, css);
                if (!heading.text().isBlank()) {
                    section = heading.text().plainText().trim();
                    blocks.add(heading);
                }
                element.select("img").forEach(image -> blocks.add(parseImage(image)));
            } else if (tag.equals("table")) {
                section = parseTable(element, section, css, blocks, rules, ids);
            } else {
                blocks.addAll(parseElement(element, css, 0));
            }
        }
        if (rules.isEmpty()) throw new IOException("No rule boxes were found in the Google document");
        whitenFirstTopLevelImage(blocks);
        long[] imageBudget = new long[2];
        blocks.forEach(block -> countImages(block, imageBudget));
        if (imageBudget[0] > MAX_DOCUMENT_IMAGES || imageBudget[1] > MAX_DOCUMENT_IMAGE_PIXELS) {
            throw new IOException("Rulebook images exceed the safe document limit");
        }
        try {
            return new RulebookDocument(blocks, rules);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid rulebook structure: " + exception.getMessage(), exception);
        }
    }

    private static void countImages(DocumentBlock block, long[] budget) {
        if (block instanceof ImageBlock image) {
            budget[0]++;
            budget[1] += (long) image.pixelWidth() * image.pixelHeight();
        } else if (block instanceof RuleBox rule) {
            rule.body().forEach(child -> countImages(child, budget));
        } else if (block instanceof TableBlock table) {
            table.rows().forEach(row -> row.forEach(cell -> cell.content()
                    .forEach(child -> countImages(child, budget))));
        }
    }

    private static String parseTable(Element table, String section, Css css, List<DocumentBlock> blocks,
                                     List<RulebookRule> rules, Set<String> ids) throws IOException {
        List<List<TableCell>> ordinaryRows = new ArrayList<>();
        List<Float> ordinaryWidths = new ArrayList<>();
        for (Element row : table.select("tr")) {
            List<Element> cells = directCells(row);
            String possibleId = cells.isEmpty() ? "" : cells.getFirst().text().trim();
            if (cells.size() == 2 && RULE_ID.matcher(possibleId).matches()) {
                flushTable(ordinaryRows, ordinaryWidths, blocks);
                String normalized = possibleId.toLowerCase(Locale.ROOT);
                if (!ids.add(normalized)) throw new IOException("Duplicate rule id: " + possibleId);
                int color = ruleColor(cells.getFirst(), css);
                List<DocumentBlock> body = parseContainer(cells.get(1), css, 0);
                int blockIndex = blocks.size();
                blocks.add(new RuleBox(possibleId, color, body));
                String title = deriveTitle(body);
                String punishment = derivePunishment(body);
                String searchable = plainText(body);
                rules.add(new RulebookRule(possibleId, section, title, punishment, color, blockIndex, searchable));
            } else if (!cells.isEmpty()) {
                ordinaryRows.add(cells.stream().map(cell -> tableCell(cell, css)).toList());
                updateColumnWidths(ordinaryWidths, cells, css);
            }
        }
        flushTable(ordinaryRows, ordinaryWidths, blocks);
        return section;
    }

    private static void flushTable(List<List<TableCell>> rows, List<Float> widths, List<DocumentBlock> blocks) {
        if (!rows.isEmpty()) {
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            normalizeColumnWidths(widths, columns);
            blocks.add(new TableBlock(List.copyOf(rows), List.copyOf(widths)));
            rows.clear();
            widths.clear();
        }
    }

    private static List<Element> directCells(Element row) {
        return row.children().stream().filter(cell -> cell.normalName().equals("td") || cell.normalName().equals("th")).toList();
    }

    private static List<DocumentBlock> parseContainer(Element container, Css css, int indent) {
        List<DocumentBlock> result = new ArrayList<>();
        for (Element child : container.children()) result.addAll(parseElement(child, css, indent));
        if (result.isEmpty() && !container.text().isBlank()) result.add(textBlock(container, TextKind.PARAGRAPH, indent, css));
        return result;
    }

    private static List<DocumentBlock> parseElement(Element element, Css css, int indent) {
        String tag = element.normalName();
        if (tag.equals("script") || tag.equals("style") || tag.equals("noscript")) return List.of();
        if (tag.equals("img")) return List.of(parseImage(element));
        if (tag.matches("h[1-6]")) {
            List<DocumentBlock> result = new ArrayList<>();
            TextBlock heading = textBlock(element, headingKind(tag), indent, css);
            if (!heading.text().isBlank()) result.add(heading);
            element.select("img").forEach(image -> result.add(parseImage(image)));
            return result;
        }
        if (tag.equals("ol") || tag.equals("ul")) return parseList(element, css, indent);
        if (tag.equals("table")) {
            List<List<Element>> cellRows = element.select("tr").stream()
                    .map(GoogleDocsRulebookParser::directCells)
                    .filter(cells -> !cells.isEmpty())
                    .toList();
            List<List<TableCell>> rows = cellRows.stream()
                    .map(cells -> cells.stream().map(cell -> tableCell(cell, css)).toList())
                    .toList();
            List<Float> widths = new ArrayList<>();
            cellRows.forEach(cells -> updateColumnWidths(widths, cells, css));
            normalizeColumnWidths(widths, rows.stream().mapToInt(List::size).max().orElse(0));
            List<DocumentBlock> result = new ArrayList<>();
            if (!rows.isEmpty()) result.add(new TableBlock(rows, widths));
            return result;
        }

        List<DocumentBlock> result = new ArrayList<>();
        RichText text = richTextWithoutImages(element, css);
        if (!text.isBlank()) result.add(new TextBlock(TextKind.PARAGRAPH, text, indent));
        for (Element image : element.select("img")) result.add(parseImage(image));
        if (result.isEmpty() && tag.equals("p")) result.add(new Spacer(10));
        if (result.isEmpty() && (tag.equals("hr") || tag.equals("br"))) result.add(new Spacer(8));
        return result;
    }

    private static List<DocumentBlock> parseList(Element list, Css css, int indent) {
        List<DocumentBlock> result = new ArrayList<>();
        boolean ordered = list.normalName().equals("ol");
        int number = parsePositiveInt(list.attr("start"), 1);
        int visualIndent = Math.max(indent, css.listLevel(list));
        String style = css.listStyle(list, ordered, visualIndent);
        for (Element child : list.children()) {
            if (!child.normalName().equals("li")) continue;
            Element own = child.clone();
            own.select("ol,ul").remove();
            String marker = marker(style, number++);
            RichText content = richText(own, css);
            List<TextRun> runs = new ArrayList<>();
            runs.add(new TextRun(marker + " ", true, false, false, false, null, null, null));
            runs.addAll(content.runs());
            List<Element> inlineImages = own.select("img");
            inlineImages.forEach(image -> result.add(parseImage(image)));
            result.add(new TextBlock(TextKind.LIST_ITEM, new RichText(runs), visualIndent));
            for (Element nested : child.children()) {
                if (nested.normalName().equals("ol") || nested.normalName().equals("ul")) {
                    result.addAll(parseList(nested, css, visualIndent + 1));
                }
            }
        }
        return result;
    }

    private static String marker(String style, int number) {
        return switch (style) {
            case "bullet" -> "•";
            case "lower-latin" -> alpha(number, false) + ".";
            case "upper-latin" -> alpha(number, true) + ".";
            case "lower-roman" -> roman(number).toLowerCase(Locale.ROOT) + ".";
            case "upper-roman" -> roman(number) + ".";
            default -> number + ".";
        };
    }

    private static String alpha(int number, boolean upper) {
        StringBuilder value = new StringBuilder();
        int current = Math.max(1, number);
        while (current > 0) {
            current--;
            value.append((char) ('a' + current % 26));
            current /= 26;
        }
        String result = value.reverse().toString();
        return upper ? result.toUpperCase(Locale.ROOT) : result;
    }

    private static String roman(int number) {
        int value = Math.clamp(number, 1, 3999);
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) while (value >= values[i]) { result.append(numerals[i]); value -= values[i]; }
        return result.toString();
    }

    private static DocumentBlock parseImage(Element image) {
        String source = image.attr("src");
        Matcher matcher = DATA_IMAGE.matcher(source);
        if (!matcher.matches()) return unavailableImage();
        try {
            byte[] bytes = Base64.getDecoder().decode(matcher.group(2));
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return unavailableImage();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0
                    || (long) decoded.getWidth() * decoded.getHeight() > MAX_IMAGE_PIXELS) return unavailableImage();
            String mediaType = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!mediaType.equals("image/png")) {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, "png", png)) return unavailableImage();
                bytes = png.toByteArray();
                mediaType = "image/png";
            }
            int displayWidth = imageDimension(image, "width", decoded.getWidth());
            int displayHeight = imageDimension(image, "height", decoded.getHeight());
            if (displayWidth == decoded.getWidth() && displayHeight != decoded.getHeight()) {
                displayWidth = Math.max(1, Math.round((float) displayHeight * decoded.getWidth() / decoded.getHeight()));
            } else if (displayHeight == decoded.getHeight() && displayWidth != decoded.getWidth()) {
                displayHeight = Math.max(1, Math.round((float) displayWidth * decoded.getHeight() / decoded.getWidth()));
            }
            return new ImageBlock(bytes, mediaType, decoded.getWidth(), decoded.getHeight(),
                    displayWidth, displayHeight, image.attr("alt"));
        } catch (IllegalArgumentException | IOException exception) {
            return unavailableImage();
        }
    }

    private static TextBlock unavailableImage() {
        return new TextBlock(TextKind.PARAGRAPH, new RichText(List.of(
                new TextRun("[Image unavailable]", false, true, false, false, 0xAAAAAA, null, null))), 0);
    }

    /** The document's opening crest is authored in black for a white page; make it legible on the dark reader. */
    private static void whitenFirstTopLevelImage(List<DocumentBlock> blocks) {
        for (int index = 0; index < blocks.size(); index++) {
            if (!(blocks.get(index) instanceof ImageBlock image)) continue;
            try {
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.encoded()));
                if (decoded == null) return;
                for (int y = 0; y < decoded.getHeight(); y++) {
                    for (int x = 0; x < decoded.getWidth(); x++) {
                        int alpha = decoded.getRGB(x, y) >>> 24;
                        if (alpha != 0) decoded.setRGB(x, y, alpha << 24 | 0xFFFFFF);
                    }
                }
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, "png", png)) return;
                blocks.set(index, new ImageBlock(png.toByteArray(), "image/png",
                        image.pixelWidth(), image.pixelHeight(), image.displayWidth(), image.displayHeight(),
                        image.altText()));
            } catch (IOException ignored) {
                // The image was already validated; leave it untouched if this display-only conversion fails.
            }
            return;
        }
    }

    private static int imageDimension(Element image, String property, int fallback) {
        Matcher inline = Pattern.compile("(?:^|;)\\s*" + property + "\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)px",
                Pattern.CASE_INSENSITIVE).matcher(image.attr("style"));
        String value = inline.find() ? inline.group(1) : image.attr(property);
        try {
            float parsed = Float.parseFloat(value);
            return parsed > 0 && Float.isFinite(parsed) ? Math.max(1, Math.round(parsed)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static TextBlock textBlock(Element element, TextKind kind, int indent, Css css) {
        return new TextBlock(kind, richText(element, css), indent);
    }

    private static RichText richTextWithoutImages(Element element, Css css) {
        Element clone = element.clone();
        clone.select("img,ol,ul,table").remove();
        return richText(clone, css);
    }

    private static RichText richText(Element element, Css css) {
        List<TextRun> runs = new ArrayList<>();
        appendInline(element, StyleState.EMPTY, css, runs);
        return new RichText(mergeRuns(runs));
    }

    private static TableCell tableCell(Element cell, Css css) {
        return new TableCell(parseContainer(cell, css, 0), css.background(cell));
    }

    private static void updateColumnWidths(List<Float> widths, List<Element> cells, Css css) {
        while (widths.size() < cells.size()) widths.add(0.0F);
        for (int index = 0; index < cells.size(); index++) {
            widths.set(index, Math.max(widths.get(index), css.width(cells.get(index))));
        }
    }

    private static void normalizeColumnWidths(List<Float> widths, int columns) {
        while (widths.size() < columns) widths.add(1.0F);
        for (int index = 0; index < widths.size(); index++) {
            if (widths.get(index) <= 0) widths.set(index, 1.0F);
        }
    }

    private static void appendInline(Node node, StyleState inherited, Css css, List<TextRun> runs) {
        if (node instanceof TextNode textNode) {
            String text = textNode.getWholeText().replace('\u00A0', ' ')
                    .replace("\uFE0E", "").replace("\uFE0F", "")
                    .replaceAll("[\\t\\r\\n ]+", " ");
            appendTextWithItems(text, inherited, runs);
            return;
        }
        if (!(node instanceof Element element)) return;
        String tag = element.normalName();
        if (tag.equals("script") || tag.equals("style") || tag.equals("img") || tag.equals("ol") || tag.equals("ul")) return;
        if (tag.equals("br")) {
            runs.add(inherited.run("\n"));
            return;
        }
        StyleState current = css.apply(element, inherited);
        for (Node child : element.childNodes()) appendInline(child, current, css, runs);
    }

    private static void appendTextWithItems(String text, StyleState style, List<TextRun> runs) {
        int plainStart = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            InlineItem item = switch (codePoint) {
                case 0x1F3C5, 0x1F947 -> InlineItem.GOLD_INGOT;
                case 0x1F948 -> InlineItem.IRON_INGOT;
                case 0x1F949 -> InlineItem.COPPER_INGOT;
                default -> null;
            };
            int next = offset + Character.charCount(codePoint);
            if (item != null) {
                if (plainStart < offset) runs.add(style.run(text.substring(plainStart, offset)));
                runs.add(style.itemRun(text.substring(offset, next), item));
                plainStart = next;
            }
            offset = next;
        }
        if (plainStart < text.length()) runs.add(style.run(text.substring(plainStart)));
    }

    private static List<TextRun> mergeRuns(List<TextRun> runs) {
        List<TextRun> merged = new ArrayList<>();
        for (TextRun run : runs) {
            if (run.text().isEmpty()) continue;
            if (!merged.isEmpty()) {
                TextRun previous = merged.getLast();
                if (sameStyle(previous, run)) {
                    merged.set(merged.size() - 1, new TextRun(previous.text() + run.text(), previous.bold(), previous.italic(),
                            previous.underlined(), previous.strikethrough(), previous.color(), previous.backgroundColor(),
                            previous.href(), previous.inlineItem()));
                    continue;
                }
            }
            merged.add(run);
        }
        return merged;
    }

    private static boolean sameStyle(TextRun a, TextRun b) {
        return a.bold() == b.bold() && a.italic() == b.italic() && a.underlined() == b.underlined()
                && a.strikethrough() == b.strikethrough() && java.util.Objects.equals(a.color(), b.color())
                && java.util.Objects.equals(a.backgroundColor(), b.backgroundColor()) && java.util.Objects.equals(a.href(), b.href())
                && a.inlineItem() == b.inlineItem();
    }

    private static int ruleColor(Element cell, Css css) {
        RichText text = richText(cell, css);
        for (TextRun run : text.runs()) {
            if (run.color() != null && luminance(run.color()) > 55) return run.color();
        }
        return 0xFFFFFF;
    }

    private static int luminance(int color) {
        return ((color >> 16 & 0xFF) * 299 + (color >> 8 & 0xFF) * 587 + (color & 0xFF) * 114) / 1000;
    }

    private static String deriveTitle(List<DocumentBlock> body) {
        for (DocumentBlock block : body) {
            if (!(block instanceof TextBlock text) || text.text().isBlank()) continue;
            StringBuilder bold = new StringBuilder();
            for (TextRun run : text.text().runs()) {
                if (!run.bold() && !bold.isEmpty()) break;
                if (run.bold()) bold.append(run.text());
            }
            String candidate = bold.toString().trim().replaceFirst(":$", "");
            if (!candidate.isEmpty()) return candidate;
            String plain = text.text().plainText().trim();
            int colon = plain.indexOf(':');
            return (colon > 0 && colon < 140 ? plain.substring(0, colon) : plain).trim();
        }
        throw new IllegalArgumentException("Rule has no title");
    }

    private static String derivePunishment(List<DocumentBlock> body) {
        List<String> trailing = new ArrayList<>();
        for (int i = body.size() - 1; i >= 0; i--) {
            DocumentBlock block = body.get(i);
            if (block instanceof Spacer) continue;
            if (block instanceof TextBlock text && allItalic(text.text())) {
                trailing.addFirst(text.text().plainText().trim());
            } else if (!trailing.isEmpty()) break;
        }
        return String.join("\n", trailing).trim();
    }

    private static boolean allItalic(RichText text) {
        boolean sawText = false;
        for (TextRun run : text.runs()) {
            if (run.text().isBlank()) continue;
            sawText = true;
            if (!run.italic()) return false;
        }
        return sawText;
    }

    private static String plainText(List<DocumentBlock> blocks) {
        StringBuilder text = new StringBuilder();
        for (DocumentBlock block : blocks) {
            if (block instanceof TextBlock line) text.append(line.text().plainText()).append(' ');
            else if (block instanceof TableBlock table) table.rows().forEach(row -> row.forEach(cell -> {
                text.append(plainText(cell.content())).append(' ');
            }));
        }
        return text.toString().trim();
    }

    private static TextKind headingKind(String tag) {
        return switch (tag) {
            case "h1" -> TextKind.HEADING_1;
            case "h2" -> TextKind.HEADING_2;
            default -> TextKind.HEADING_3;
        };
    }

    private static int parsePositiveInt(String input, int fallback) {
        try { return Math.max(1, Integer.parseInt(input)); } catch (NumberFormatException ignored) { return fallback; }
    }

    private record StyleState(boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                              Integer color, Integer background, String href) {
        private static final StyleState EMPTY = new StyleState(false, false, false, false, null, null, null);

        private TextRun run(String text) {
            return new TextRun(text, bold, italic, underlined, strikethrough, color, background, href);
        }

        private TextRun itemRun(String source, InlineItem item) {
            return new TextRun(source, bold, italic, underlined, strikethrough, color, background, href, item);
        }
    }

    private static final class Css {
        private final String raw;
        private final Map<String, Map<String, String>> classes = new HashMap<>();

        private Css(String raw) {
            this.raw = raw;
            Matcher matcher = CLASS_RULE.matcher(raw);
            while (matcher.find()) classes.putIfAbsent(matcher.group(1), declarations(matcher.group(2)));
        }

        private StyleState apply(Element element, StyleState inherited) {
            Map<String, String> combined = new HashMap<>();
            for (String className : element.classNames()) combined.putAll(classes.getOrDefault(className, Map.of()));
            combined.putAll(declarations(element.attr("style")));
            boolean bold = inherited.bold || element.normalName().matches("b|strong") || weight(combined.get("font-weight"));
            boolean italic = inherited.italic || element.normalName().matches("i|em") || "italic".equals(combined.get("font-style"));
            String decoration = combined.getOrDefault("text-decoration", "");
            boolean underlined = inherited.underlined || element.normalName().equals("u") || decoration.contains("underline");
            boolean strike = inherited.strikethrough || element.normalName().matches("s|strike|del") || decoration.contains("line-through");
            Integer color = combined.containsKey("color") ? parseColor(combined.get("color")) : inherited.color;
            Integer background = combined.containsKey("background-color") ? parseColor(combined.get("background-color")) : inherited.background;
            String href = element.normalName().equals("a") ? safeHref(element.attr("href")) : inherited.href;
            return new StyleState(bold, italic, underlined, strike, color, background, href);
        }

        private String listStyle(Element list, boolean ordered, int indent) {
            if (!ordered) return "bullet";
            for (String className : list.classNames()) {
                Pattern style = Pattern.compile("\\." + Pattern.quote(className) + ">li:before\\{[^}]*?(lower-latin|upper-latin|lower-roman|upper-roman|decimal)");
                Matcher matcher = style.matcher(raw);
                if (matcher.find()) return matcher.group(1);
            }
            return indent > 0 ? "lower-latin" : "decimal";
        }

        private int listLevel(Element list) {
            int level = 0;
            for (String className : list.classNames()) {
                Matcher matcher = Pattern.compile("-(\\d+)$").matcher(className);
                if (matcher.find()) {
                    try {
                        level = Math.max(level, Integer.parseInt(matcher.group(1)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return level;
        }

        private int background(Element element) {
            Map<String, String> combined = new HashMap<>();
            for (String className : element.classNames()) combined.putAll(classes.getOrDefault(className, Map.of()));
            combined.putAll(declarations(element.attr("style")));
            Integer color = parseColor(combined.get("background-color"));
            return color == null ? -1 : color;
        }

        private float width(Element element) {
            Map<String, String> combined = new HashMap<>();
            for (String className : element.classNames()) combined.putAll(classes.getOrDefault(className, Map.of()));
            combined.putAll(declarations(element.attr("style")));
            String value = combined.getOrDefault("width", "").trim();
            Matcher number = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(value);
            if (!number.find()) return 0.0F;
            try {
                return Float.parseFloat(number.group(1));
            } catch (NumberFormatException ignored) {
                return 0.0F;
            }
        }

        private static boolean weight(String value) {
            if (value == null) return false;
            if (value.equals("bold") || value.equals("bolder")) return true;
            try { return Integer.parseInt(value) >= 600; } catch (NumberFormatException ignored) { return false; }
        }

        private static Map<String, String> declarations(String source) {
            Map<String, String> result = new HashMap<>();
            for (String declaration : source.split(";")) {
                int colon = declaration.indexOf(':');
                if (colon <= 0) continue;
                result.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        declaration.substring(colon + 1).trim().toLowerCase(Locale.ROOT));
            }
            return result;
        }

        private static Integer parseColor(String value) {
            if (value == null) return null;
            String clean = value.trim().toLowerCase(Locale.ROOT);
            try {
                if (clean.matches("#[0-9a-f]{6}")) return Integer.parseInt(clean.substring(1), 16);
                if (clean.matches("#[0-9a-f]{3}")) {
                    int r = Integer.parseInt(clean.substring(1, 2), 16) * 17;
                    int g = Integer.parseInt(clean.substring(2, 3), 16) * 17;
                    int b = Integer.parseInt(clean.substring(3, 4), 16) * 17;
                    return r << 16 | g << 8 | b;
                }
                Matcher rgb = Pattern.compile("rgb\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)").matcher(clean);
                if (rgb.matches()) return Math.clamp(Integer.parseInt(rgb.group(1)), 0, 255) << 16
                        | Math.clamp(Integer.parseInt(rgb.group(2)), 0, 255) << 8
                        | Math.clamp(Integer.parseInt(rgb.group(3)), 0, 255);
            } catch (NumberFormatException ignored) {
            }
            return null;
        }

        private static String safeHref(String href) {
            try {
                URI uri = URI.create(href);
                String scheme = uri.getScheme();
                return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) ? uri.toString() : null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
