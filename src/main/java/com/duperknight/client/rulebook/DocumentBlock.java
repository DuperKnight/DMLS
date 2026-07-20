package com.duperknight.client.rulebook;

import java.util.List;
import java.util.Objects;

/** Renderer-neutral blocks parsed from the Google Docs HTML export. */
public sealed interface DocumentBlock permits DocumentBlock.TextBlock, DocumentBlock.ImageBlock,
        DocumentBlock.RuleBox, DocumentBlock.TableBlock, DocumentBlock.Spacer {

    enum InlineItem { GOLD_INGOT, IRON_INGOT, COPPER_INGOT }

    record TextRun(String text, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                   Integer color, Integer backgroundColor, String href, InlineItem inlineItem) {
        public TextRun(String text, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                       Integer color, Integer backgroundColor, String href) {
            this(text, bold, italic, underlined, strikethrough, color, backgroundColor, href, null);
        }

        public TextRun {
            text = Objects.requireNonNullElse(text, "");
            href = href == null || href.isBlank() ? null : href;
        }
    }

    record RichText(List<TextRun> runs) {
        public RichText {
            runs = List.copyOf(runs);
        }

        public String plainText() {
            StringBuilder result = new StringBuilder();
            runs.forEach(run -> result.append(run.text()));
            return result.toString();
        }

        public boolean isBlank() {
            return plainText().isBlank();
        }
    }

    enum TextKind { TITLE, HEADING_1, HEADING_2, HEADING_3, PARAGRAPH, LIST_ITEM }

    record TextBlock(TextKind kind, RichText text, int indent) implements DocumentBlock {
        public TextBlock {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            indent = Math.max(0, indent);
        }
    }

    record ImageBlock(byte[] encoded, String mediaType, int pixelWidth, int pixelHeight,
                      int displayWidth, int displayHeight, String altText) implements DocumentBlock {
        public ImageBlock(byte[] encoded, String mediaType, int pixelWidth, int pixelHeight, String altText) {
            this(encoded, mediaType, pixelWidth, pixelHeight, pixelWidth, pixelHeight, altText);
        }

        public ImageBlock {
            encoded = encoded.clone();
            mediaType = Objects.requireNonNullElse(mediaType, "image/png");
            altText = Objects.requireNonNullElse(altText, "Image");
            if (pixelWidth <= 0 || pixelHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
                throw new IllegalArgumentException("Invalid image dimensions");
            }
        }

        @Override
        public byte[] encoded() {
            return encoded.clone();
        }
    }

    record RuleBox(String ruleId, int ruleColor, List<DocumentBlock> body) implements DocumentBlock {
        public RuleBox {
            ruleId = Objects.requireNonNull(ruleId, "ruleId");
            body = List.copyOf(body);
        }
    }

    record TableCell(List<DocumentBlock> content, int background) {
        public TableCell {
            content = List.copyOf(content);
        }

        public String plainText() {
            StringBuilder text = new StringBuilder();
            for (DocumentBlock block : content) {
                if (block instanceof TextBlock line) text.append(line.text().plainText());
                else if (block instanceof ImageBlock image && !image.altText().isBlank()) text.append(image.altText());
                else if (block instanceof RuleBox rule) {
                    text.append(new TableCell(rule.body(), -1).plainText());
                } else if (block instanceof TableBlock table) {
                    table.rows().forEach(row -> row.forEach(cell -> text.append(cell.plainText()).append(' ')));
                }
                text.append('\n');
            }
            return text.toString().stripTrailing();
        }
    }

    record TableBlock(List<List<TableCell>> rows, List<Float> columnWeights) implements DocumentBlock {
        public TableBlock {
            rows = rows.stream().map(List::copyOf).toList();
            columnWeights = List.copyOf(columnWeights);
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            if (columnWeights.size() != columns || columnWeights.stream().anyMatch(weight -> weight == null || weight <= 0)) {
                throw new IllegalArgumentException("Table column widths do not match");
            }
        }
    }

    record Spacer(int height) implements DocumentBlock {
        public Spacer {
            height = Math.max(1, height);
        }
    }
}
