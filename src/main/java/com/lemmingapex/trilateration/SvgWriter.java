package com.lemmingapex.trilateration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Minimal SVG building blocks shared by {@link TrilaterationSvg} and the documentation figures:
 * a card-style plot layout with gridlines, ticks and a legend, a world-to-pixel viewport, number
 * formatting, and a stylesheet with light and dark colours. Output is deterministic and locale
 * independent (trigonometry uses {@link StrictMath}).
 *
 * <p>Colours have fixed roles: blue for measured distances, orange for estimates and their
 * confidence region, aqua for optimizer paths; text uses neutral ink. The three hues pass
 * colour-vision-deficiency and contrast checks on both surfaces as a set.
 */
final class SvgWriter {

    static final String FONT = "system-ui,-apple-system,'Segoe UI',sans-serif";

    /** Colours as CSS custom properties, with separately chosen values for dark mode. */
    static final String STYLE = ""
            + "svg{--surface:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--muted:#898781;--grid:#e1e0d9;--ring:rgba(11,11,11,.10);"
            + "--distance:#2a78d6;--estimate:#eb6834;--path:#1baf7a}"
            + "@media (prefers-color-scheme:dark){svg{--surface:#1a1a19;--ink:#f0efec;--ink2:#c3c2b7;--muted:#898781;"
            + "--grid:#2c2c2a;--ring:rgba(255,255,255,.10);--distance:#3987e5;--estimate:#d95926;--path:#199e70}}"
            + ".card{fill:var(--surface);stroke:var(--ring)}"
            + ".grid{stroke:var(--grid);stroke-width:1}"
            + ".tick{font:11px " + FONT + ";fill:var(--muted);font-variant-numeric:tabular-nums}"
            + ".circle{fill:var(--distance);fill-opacity:.05;stroke:var(--distance);stroke-width:1.5}"
            + ".outlier{stroke-dasharray:6 4}"
            + ".open{fill:none}"
            + ".anchor{fill:var(--ink);stroke:var(--surface);stroke-width:2}"
            + ".estimate{fill:var(--estimate);stroke:var(--surface);stroke-width:2}"
            + ".ellipse{fill:var(--estimate);fill-opacity:.14;stroke:var(--estimate);stroke-width:2}"
            + ".alternative{fill:var(--surface);fill-opacity:.6;stroke:var(--estimate);stroke-width:2;stroke-dasharray:3 2.5}"
            + ".residual{stroke:var(--ink2);stroke-width:2;stroke-linecap:round}"
            + ".path{fill:none;stroke:var(--path);stroke-width:2;stroke-linecap:round}"
            + ".start{fill:var(--surface);stroke:var(--path);stroke-width:2}"
            + ".step{fill:var(--path);stroke:var(--surface);stroke-width:2}"
            + ".axis{stroke:var(--muted);stroke-width:1.5;stroke-dasharray:6 5}"
            + ".truth{fill:none;stroke:var(--ink);stroke-width:2;stroke-linecap:round}"
            + ".route{fill:none;stroke:var(--muted);stroke-width:1.5;stroke-dasharray:5 4}"
            + ".fix{fill:none;stroke:var(--ink2);stroke-width:1.5}"
            + ".track{fill:none;stroke:var(--estimate);stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round}"
            + ".hit{fill:transparent}"
            + ".caption{font:600 15px " + FONT + ";fill:var(--ink)}"
            + ".label,.legend{font:12px " + FONT + ";fill:var(--ink2)}";

    private final StringBuilder out = new StringBuilder();

    SvgWriter(int width, int height, String title, String description) {
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" role=\"img\">\n");
        out.append("<title>").append(escape(title)).append("</title>\n");
        out.append("<desc>").append(escape(description)).append("</desc>\n");
        out.append("<style>").append(STYLE).append("</style>\n");
    }

    SvgWriter append(String svg) {
        out.append(svg);
        return this;
    }

    String finish() {
        return out.append("</svg>\n").toString();
    }

    /** Formats a pixel coordinate with two decimals, without locale effects or negative zero. */
    static String num(double value) {
        String s = String.format(Locale.ROOT, "%.2f", value);
        return s.equals("-0.00") ? "0.00" : s;
    }

    /** Formats a data value for titles and descriptions. */
    static String value(double value) {
        return String.format(Locale.ROOT, "%.4g", value + 0.0); // + 0.0 turns -0.0 into 0.0
    }

    static String point(double[] p) {
        return "(" + value(p[0]) + ", " + value(p[1]) + ")";
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** A dot at a world point, with a larger invisible hit target and a tooltip. */
    static String marker(Viewport view, double[] at, double radius, String cssClass, String tooltip) {
        String x = num(view.x(at[0]));
        String y = num(view.y(at[1]));
        return "<g><title>" + escape(tooltip) + "</title><circle class=\"hit\" cx=\"" + x + "\" cy=\"" + y
                + "\" r=\"" + num(Math.max(10, radius + 5)) + "\"/><circle class=\"" + cssClass + "\" cx=\"" + x
                + "\" cy=\"" + y + "\" r=\"" + num(radius) + "\"/></g>\n";
    }

    /** A text label offset by ({@code dx}, {@code dy}) pixels from a world point. */
    static String label(Viewport view, double[] at, String text, double dx, double dy) {
        return "<text class=\"label\" x=\"" + num(view.x(at[0]) + dx) + "\" y=\"" + num(view.y(at[1]) + dy) + "\">"
                + escape(text) + "</text>\n";
    }

    /** Maps world coordinates (y up) to pixels (y down) with equal scale on both axes. */
    static final class Viewport {
        final double minX;
        final double maxX;
        final double minY;
        final double maxY;
        final double scale;
        final double left;
        final double top;

        /**
         * @param bounds     {minX, maxX, minY, maxY} of the region to show; widened so the shorter
         *                   side is at least half the longer one
         * @param left       pixel x of the plot area's left edge
         * @param top        pixel y of the plot area's top edge
         * @param plotWidth  plot area width in pixels
         */
        Viewport(double[] bounds, double left, double top, double plotWidth) {
            double spanX = bounds[1] - bounds[0];
            double spanY = bounds[3] - bounds[2];
            double span = Math.max(Math.max(spanX, spanY), 1e-12);
            double padX = Math.max(0, span / 2 - spanX) / 2;
            double padY = Math.max(0, span / 2 - spanY) / 2;
            this.minX = bounds[0] - padX;
            this.maxX = bounds[1] + padX;
            this.minY = bounds[2] - padY;
            this.maxY = bounds[3] + padY;
            this.left = left;
            this.top = top;
            this.scale = plotWidth / (maxX - minX);
        }

        double plotWidth() {
            return (maxX - minX) * scale;
        }

        double plotHeight() {
            return (maxY - minY) * scale;
        }

        double x(double worldX) {
            return left + (worldX - minX) * scale;
        }

        double y(double worldY) {
            return top + (maxY - worldY) * scale;
        }

        double length(double worldLength) {
            return worldLength * scale;
        }
    }

    /** Growable {minX, maxX, minY, maxY} bounding box. */
    static final class Bounds {
        private final double[] box = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        Bounds add(double x, double y, double radius) {
            box[0] = Math.min(box[0], x - radius);
            box[1] = Math.max(box[1], x + radius);
            box[2] = Math.min(box[2], y - radius);
            box[3] = Math.max(box[3], y + radius);
            return this;
        }

        Bounds add(double[] p) {
            return add(p[0], p[1], 0);
        }

        /** The box grown by {@code fraction} of its larger side on every side (at least {@code minimum}). */
        double[] padded(double fraction, double minimum) {
            double span = Math.max(box[1] - box[0], box[3] - box[2]);
            double pad = Math.max(span * fraction, minimum);
            return new double[] {box[0] - pad, box[1] + pad, box[2] - pad, box[3] + pad};
        }
    }

    /** Kinds of legend entry; each draws its own swatch. */
    enum Swatch { ANCHOR, DISTANCE, OUTLIER_DISTANCE, ESTIMATE, HOLLOW_ESTIMATE, REGION, ALTERNATIVE, RESIDUAL, PATH, TRUTH,
        ROUTE, FIX, TRACK }

    /** One legend entry. */
    record LegendItem(Swatch swatch, String text) {
    }

    /**
     * The gridlines of one axis: their positions in world coordinates and their tick labels.
     *
     * @param at     world coordinates of the gridlines
     * @param labels one label per gridline
     */
    record Ticks(double[] at, String[] labels) {
    }

    /**
     * A card with a plot area, gridlines and tick labels on the left and bottom, and a legend that
     * flows below. The plot area is clipped, so large circles never cover the ticks or legend.
     */
    static final class Layout {
        private static final double OUTER = 14;
        private static final double TICK_GAP = 6;
        private static final double TICK_HEIGHT = 16;
        private static final double LEGEND_ROW = 20;
        private static final double SWATCH = 18;

        final int width;
        final int height;
        final Viewport view;
        private final String clipId;
        private final List<LegendItem> legend;
        private final double legendTop;
        private final Ticks xTicks;
        private final Ticks yTicks;

        /**
         * @param bounds  world region to show
         * @param width   image width in pixels
         * @param top     pixels above the plot area (room for a caption)
         * @param legend  legend entries, in order
         * @param clipId  document-unique id for the plot clip path
         */
        Layout(double[] bounds, int width, double top, List<LegendItem> legend, String clipId) {
            this(bounds, width, top, legend, clipId,
                    view -> niceTicks(view, view.minX, view.maxX), view -> niceTicks(view, view.minY, view.maxY));
        }

        /**
         * A layout with custom gridlines, such as lines of latitude and longitude.
         *
         * @param xRule chooses the vertical gridlines for a viewport
         * @param yRule chooses the horizontal gridlines for a viewport
         */
        Layout(double[] bounds, int width, double top, List<LegendItem> legend, String clipId,
               Function<Viewport, Ticks> xRule, Function<Viewport, Ticks> yRule) {
            this.width = width;
            this.legend = legend;
            this.clipId = clipId;

            // Two passes: tick labels decide the left inset, which changes the scale slightly.
            Viewport trial = new Viewport(bounds, 48, top, width - 48 - OUTER);
            double labelWidth = 0;
            for (String label : yRule.apply(trial).labels()) {
                labelWidth = Math.max(labelWidth, textWidth(label, 6.6));
            }
            double left = OUTER + labelWidth + TICK_GAP;
            this.view = new Viewport(bounds, left, top, width - left - OUTER);
            this.xTicks = xRule.apply(view);
            this.yTicks = yRule.apply(view);
            this.legendTop = top + view.plotHeight() + TICK_GAP + TICK_HEIGHT + 6;
            int rows = legendRows(legend, left, width - OUTER).size();
            this.height = (int) Math.ceil(legendTop + rows * LEGEND_ROW + (rows > 0 ? 6 : 0) + OUTER - 6);
        }

        /** Card, gridlines, ticks and the opening of the clipped plot group. */
        String open() {
            return frame() + beginPlot();
        }

        /** Opens the group clipped to the plot area. */
        String beginPlot() {
            return "<g clip-path=\"url(#" + clipId + ")\">\n";
        }

        /** Card, gridlines and ticks. */
        String frame() {
            double left = view.left;
            double top = view.top;
            double right = left + view.plotWidth();
            double bottom = top + view.plotHeight();
            StringBuilder s = new StringBuilder();
            s.append("<defs><clipPath id=\"").append(clipId).append("\"><rect x=\"").append(num(left)).append("\" y=\"")
                    .append(num(top)).append("\" width=\"").append(num(right - left)).append("\" height=\"")
                    .append(num(bottom - top)).append("\"/></clipPath></defs>\n");
            s.append("<rect class=\"card\" x=\"0.5\" y=\"0.5\" width=\"").append(width - 1).append("\" height=\"")
                    .append(height - 1).append("\" rx=\"10\"/>\n<g class=\"grid\">");
            for (double t : xTicks.at()) {
                String x = num(view.x(t));
                s.append("<line x1=\"").append(x).append("\" y1=\"").append(num(top)).append("\" x2=\"").append(x)
                        .append("\" y2=\"").append(num(bottom)).append("\"/>");
            }
            for (double t : yTicks.at()) {
                String y = num(view.y(t));
                s.append("<line x1=\"").append(num(left)).append("\" y1=\"").append(y).append("\" x2=\"").append(num(right))
                        .append("\" y2=\"").append(y).append("\"/>");
            }
            s.append("</g>\n<g class=\"tick\">");
            for (int i = 0; i < xTicks.at().length; i++) {
                s.append("<text x=\"").append(num(view.x(xTicks.at()[i]))).append("\" y=\"").append(num(bottom + TICK_GAP + 11))
                        .append("\" text-anchor=\"middle\">").append(escape(xTicks.labels()[i])).append("</text>");
            }
            for (int i = 0; i < yTicks.at().length; i++) {
                s.append("<text x=\"").append(num(left - TICK_GAP)).append("\" y=\"").append(num(view.y(yTicks.at()[i]) + 4))
                        .append("\" text-anchor=\"end\">").append(escape(yTicks.labels()[i])).append("</text>");
            }
            return s.append("</g>\n").toString();
        }

        /** Closes the plot group and draws the legend. */
        String close() {
            StringBuilder s = new StringBuilder("</g>\n");
            if (legend.isEmpty()) {
                return s.toString();
            }
            s.append("<g class=\"legend-items\">");
            List<List<double[]>> rows = legendRows(legend, view.left, width - OUTER);
            int index = 0;
            for (int r = 0; r < rows.size(); r++) {
                double cy = legendTop + r * LEGEND_ROW + LEGEND_ROW / 2;
                for (double[] slot : rows.get(r)) {
                    LegendItem item = legend.get(index++);
                    s.append(swatch(item.swatch(), slot[0] + SWATCH / 2, cy));
                    s.append("<text class=\"legend\" x=\"").append(num(slot[0] + SWATCH + 6)).append("\" y=\"")
                            .append(num(cy + 4)).append("\">").append(escape(item.text())).append("</text>");
                }
            }
            return s.append("</g>\n").toString();
        }

        /** Flows legend entries into rows; each slot is {x}. */
        private static List<List<double[]>> legendRows(List<LegendItem> legend, double left, double right) {
            List<List<double[]>> rows = new ArrayList<>();
            List<double[]> row = new ArrayList<>();
            double x = left;
            for (LegendItem item : legend) {
                double w = SWATCH + 6 + textWidth(item.text(), 6.9);
                if (!row.isEmpty() && x + w > right) {
                    rows.add(row);
                    row = new ArrayList<>();
                    x = left;
                }
                row.add(new double[] {x});
                x += w + 18;
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
            return rows;
        }

        private static String swatch(Swatch swatch, double cx, double cy) {
            String x = num(cx);
            String y = num(cy);
            return switch (swatch) {
                case ANCHOR -> "<circle class=\"anchor\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"5\"/>";
                case DISTANCE -> "<circle class=\"circle\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"7\"/>";
                case OUTLIER_DISTANCE -> "<circle class=\"circle outlier\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"7\"/>";
                case ESTIMATE -> "<circle class=\"estimate\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"6\"/>";
                case HOLLOW_ESTIMATE, ALTERNATIVE -> "<circle class=\"alternative\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"6\"/>";
                case REGION -> "<ellipse class=\"ellipse\" cx=\"" + x + "\" cy=\"" + y + "\" rx=\"8.5\" ry=\"6\"/>";
                case RESIDUAL -> "<line class=\"residual\" x1=\"" + num(cx - 7) + "\" y1=\"" + y + "\" x2=\"" + num(cx + 7) + "\" y2=\"" + y + "\"/>";
                case PATH -> "<line class=\"path\" x1=\"" + num(cx - 8) + "\" y1=\"" + num(cy + 3) + "\" x2=\"" + num(cx + 6)
                        + "\" y2=\"" + num(cy - 3) + "\"/><circle class=\"step\" cx=\"" + num(cx + 6) + "\" cy=\""
                        + num(cy - 3) + "\" r=\"4\"/>";
                case TRUTH -> cross(cx, cy, 5);
                case ROUTE -> "<line class=\"route\" x1=\"" + num(cx - 9) + "\" y1=\"" + y + "\" x2=\"" + num(cx + 9) + "\" y2=\"" + y + "\"/>";
                case FIX -> "<circle class=\"fix\" cx=\"" + x + "\" cy=\"" + y + "\" r=\"3.5\"/>";
                case TRACK -> "<line class=\"track\" x1=\"" + num(cx - 8) + "\" y1=\"" + num(cy + 3) + "\" x2=\"" + num(cx + 8)
                        + "\" y2=\"" + num(cy - 3) + "\"/>";
            };
        }

        /** Evenly spaced round numbers, with the same step on both axes so grid cells are square. */
        static Ticks niceTicks(Viewport view, double min, double max) {
            double step = niceStep(view);
            int places = decimals(step);
            double[] at = ticks(min, max, step);
            String[] labels = new String[at.length];
            for (int i = 0; i < at.length; i++) {
                labels[i] = format(at[i], places);
            }
            return new Ticks(at, labels);
        }

        private static double niceStep(Viewport view) {
            double span = Math.max(view.maxX - view.minX, view.maxY - view.minY);
            double raw = span / 6;
            double magnitude = StrictMath.pow(10, StrictMath.floor(StrictMath.log10(raw)));
            double normalized = raw / magnitude;
            double nice = normalized < 1.5 ? 1 : normalized < 3.5 ? 2 : normalized < 7.5 ? 5 : 10;
            return nice * magnitude;
        }

        private static double[] ticks(double min, double max, double step) {
            long first = (long) StrictMath.ceil(min / step);
            long last = (long) StrictMath.floor(max / step);
            double[] ticks = new double[(int) Math.max(0, last - first + 1)];
            for (int i = 0; i < ticks.length; i++) {
                ticks[i] = (first + i) * step;
            }
            return ticks;
        }

        static int decimals(double step) {
            return (int) Math.max(0, -StrictMath.floor(StrictMath.log10(step) + 1e-9));
        }

        private static String format(double value, int decimals) {
            return String.format(Locale.ROOT, "%." + decimals + "f", value + 0.0);
        }

        /** A conservative width estimate for system sans text. */
        private static double textWidth(String text, double perCharacter) {
            return text.length() * perCharacter;
        }
    }

    /** An x-shaped cross centred on a pixel position. */
    static String cross(double cx, double cy, double half) {
        return "<path class=\"truth\" d=\"M" + num(cx - half) + " " + num(cy - half) + "L" + num(cx + half) + " "
                + num(cy + half) + "M" + num(cx - half) + " " + num(cy + half) + "L" + num(cx + half) + " "
                + num(cy - half) + "\"/>";
    }

    /**
     * Error ellipse of a 2x2 covariance: semi-axes and the rotation of the first axis.
     *
     * @param covariance 2x2 covariance matrix
     * @param quantile   squared Mahalanobis radius of the region
     * @return {semiAxis1, semiAxis2, angleRadians}
     */
    static double[] ellipse(double[][] covariance, double quantile) {
        double a = covariance[0][0];
        double b = covariance[0][1];
        double c = covariance[1][1];
        double mean = (a + c) / 2;
        double radius = StrictMath.sqrt((a - c) * (a - c) / 4 + b * b);
        double major = Math.max(0, mean + radius);
        double minor = Math.max(0, mean - radius);
        double angle = 0.5 * StrictMath.atan2(2 * b, a - c);
        return new double[] {StrictMath.sqrt(quantile * major), StrictMath.sqrt(quantile * minor), angle};
    }
}
