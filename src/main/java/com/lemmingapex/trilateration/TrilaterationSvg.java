package com.lemmingapex.trilateration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hipparchus.distribution.continuous.FDistribution;

/**
 * Draws a two-dimensional {@link TrilaterationResult} as an SVG image: a circle of measured distance
 * around each anchor, the anchors, the estimated position with its confidence ellipse, the residual
 * of each measurement and, when the result is {@linkplain TrilaterationResult#isAmbiguous()
 * ambiguous}, the alternative position, on a grid with coordinates and a legend.
 *
 * <pre>{@code
 * TrilaterationProblem problem = TrilaterationProblem.of(positions, distances);
 * TrilaterationResult result = new TrilaterationSolver().solve(problem);
 *
 * String svg = TrilaterationSvg.render(problem, result);
 * Files.write(Paths.get("solution.svg"), svg.getBytes(StandardCharsets.UTF_8));
 * }</pre>
 *
 * <p>The SVG is self-contained, adapts its colours to light and dark mode, and has tooltips with
 * each anchor's position, distance and residual. It needs no graphics libraries, so it also works
 * on servers without a display and on Android. Output is deterministic and does not depend on the
 * default locale.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class TrilaterationSvg {

    private static final double ANCHOR_RADIUS = 5;
    private static final double ESTIMATE_RADIUS = 6;

    private final int width;
    private final double confidence;
    private final boolean showAlternative;
    private final boolean wholeCircles;
    private final boolean labels;
    private final boolean residuals;
    private final boolean legend;

    private TrilaterationSvg(Builder builder) {
        this.width = builder.width;
        this.confidence = builder.confidence;
        this.showAlternative = builder.showAlternative;
        this.wholeCircles = builder.wholeCircles;
        this.labels = builder.labels;
        this.residuals = builder.residuals;
        this.legend = builder.legend;
    }

    /**
     * {@return a builder for a renderer with non-default settings}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Renders a result with default settings: 480 pixels wide, a 95% confidence ellipse,
     * residuals, a legend, and the alternative position when the result is ambiguous.
     *
     * @param problem the solved problem; must be two-dimensional
     * @param result  the solver's result for {@code problem}
     * @return the SVG document
     * @throws IllegalArgumentException if the problem is not two-dimensional
     */
    public static String render(TrilaterationProblem problem, TrilaterationResult result) {
        return builder().build().toSvg(problem, result);
    }

    /**
     * Renders a result.
     *
     * @param problem the solved problem; must be two-dimensional
     * @param result  the solver's result for {@code problem}
     * @return the SVG document
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the problem is not two-dimensional, or the result does not
     *                                  belong to it
     */
    public String toSvg(TrilaterationProblem problem, TrilaterationResult result) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(result, "result");
        if (problem.getDimension() != 2) {
            throw new IllegalArgumentException("Only two-dimensional problems can be drawn, but this one has "
                    + problem.getDimension() + " dimensions.");
        }
        double[][] positions = problem.positions();
        double[] distances = problem.distances();
        double[] residualValues = result.getResiduals();
        if (residualValues.length != positions.length) {
            throw new IllegalArgumentException("The result has " + residualValues.length
                    + " residuals but the problem has " + positions.length + " measurements.");
        }
        double[] point = result.getPoint();
        double[] alternative = showAlternative ? result.getAlternativePoint().orElse(null) : null;
        double[] ellipse = result.getCovariance()
                .map(covariance -> SvgWriter.ellipse(covariance, ellipseQuantile(result)))
                .orElse(null);

        SvgWriter.Bounds bounds = new SvgWriter.Bounds().add(point);
        double largest = 0;
        for (int i = 0; i < positions.length; i++) {
            bounds.add(positions[i][0], positions[i][1], wholeCircles ? distances[i] : 0);
            largest = Math.max(largest, distances[i]);
        }
        if (alternative != null) {
            bounds.add(alternative);
        }
        if (ellipse != null) {
            bounds.add(point[0], point[1], ellipse[0]);
        }

        String percent = formatPercent(confidence);
        List<SvgWriter.LegendItem> items = new ArrayList<>();
        if (legend) {
            items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.ANCHOR, "Anchor"));
            items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.DISTANCE, "Measured distance"));
            if (residuals) {
                items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.RESIDUAL, "Residual"));
            }
            items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.ESTIMATE, "Estimate"));
            if (ellipse != null) {
                items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.REGION, percent + " confidence region"));
            }
            if (alternative != null) {
                items.add(new SvgWriter.LegendItem(SvgWriter.Swatch.ALTERNATIVE, "Alternative (mirror image)"));
            }
        }

        String description = "Estimated position " + SvgWriter.point(point) + " from " + positions.length
                + " anchors, RMS residual " + SvgWriter.value(result.getRms())
                + (alternative == null ? "" : "; ambiguous, alternative position " + SvgWriter.point(alternative)) + ".";
        SvgWriter.Layout layout = new SvgWriter.Layout(bounds.padded(0.12, largest > 0 ? largest * 0.05 : 1), width, 14, items,
                "trilateration-plot-" + Integer.toHexString(description.hashCode()));
        SvgWriter.Viewport view = layout.view;
        SvgWriter svg = new SvgWriter(width, layout.height, "Trilateration result", description);
        svg.append(layout.open());

        for (int i = 0; i < positions.length; i++) {
            svg.append("<circle class=\"circle\" cx=\"" + SvgWriter.num(view.x(positions[i][0]))
                    + "\" cy=\"" + SvgWriter.num(view.y(positions[i][1]))
                    + "\" r=\"" + SvgWriter.num(view.length(distances[i])) + "\"/>\n");
        }
        if (ellipse != null) {
            svg.append(ellipseElement(view, point, ellipse, "<title>" + percent + " confidence region</title>"));
        }
        if (residuals) {
            for (int i = 0; i < positions.length; i++) {
                svg.append(residual(view, positions[i], distances[i], point, i, residualValues[i]));
            }
        }
        for (int i = 0; i < positions.length; i++) {
            svg.append(SvgWriter.marker(view, positions[i], ANCHOR_RADIUS, "anchor", "Anchor " + (i + 1) + " at "
                    + SvgWriter.point(positions[i]) + ", distance " + SvgWriter.value(distances[i])
                    + ", residual " + SvgWriter.value(residualValues[i])));
            if (labels) {
                svg.append(SvgWriter.label(view, positions[i], String.valueOf(i + 1), 8, -8));
            }
        }
        if (alternative != null) {
            svg.append(SvgWriter.marker(view, alternative, ESTIMATE_RADIUS + 1, "alternative",
                    "Alternative position " + SvgWriter.point(alternative)));
            if (labels) {
                svg.append(SvgWriter.label(view, alternative, "alternative", 10, -10));
            }
        }
        svg.append(SvgWriter.marker(view, point, ESTIMATE_RADIUS, "estimate", "Estimated position " + SvgWriter.point(point)));
        if (labels) {
            svg.append(SvgWriter.label(view, point, "estimate", 10, -10));
        }
        svg.append(layout.close());
        return svg.finish();
    }

    /**
     * The residual of one measurement as a segment along the line from the anchor through the
     * estimate, from where the measured distance ends to the estimate. Empty if under a pixel.
     */
    private static String residual(SvgWriter.Viewport view, double[] anchor, double distance, double[] point,
                                   int index, double residual) {
        double range = TrilaterationFunction.distance(anchor, point);
        if (range == 0 || view.length(Math.abs(residual)) < 1) {
            return "";
        }
        double[] end = {anchor[0] + (point[0] - anchor[0]) * distance / range, anchor[1] + (point[1] - anchor[1]) * distance / range};
        return "<line class=\"residual\" x1=\"" + SvgWriter.num(view.x(end[0])) + "\" y1=\"" + SvgWriter.num(view.y(end[1]))
                + "\" x2=\"" + SvgWriter.num(view.x(point[0])) + "\" y2=\"" + SvgWriter.num(view.y(point[1]))
                + "\"><title>Residual of anchor " + (index + 1) + ": " + SvgWriter.value(residual) + "</title></line>\n";
    }

    /** Ellipse element for {@code {semiAxis1, semiAxis2, angle}} centred on {@code center}, or "" if invisibly small. */
    static String ellipseElement(SvgWriter.Viewport view, double[] center, double[] ellipse, String content) {
        double rx = view.length(ellipse[0]);
        double ry = view.length(ellipse[1]);
        if (rx < 0.25 && ry < 0.25) {
            return "";
        }
        double cx = view.x(center[0]);
        double cy = view.y(center[1]);
        // Pixel y points down, so a counter-clockwise world angle is a clockwise pixel rotation.
        double degrees = -Math.toDegrees(ellipse[2]);
        return "<ellipse class=\"ellipse\" cx=\"" + SvgWriter.num(cx) + "\" cy=\"" + SvgWriter.num(cy)
                + "\" rx=\"" + SvgWriter.num(rx) + "\" ry=\"" + SvgWriter.num(ry)
                + "\" transform=\"rotate(" + SvgWriter.num(degrees) + " " + SvgWriter.num(cx) + " " + SvgWriter.num(cy) + ")\">"
                + content + "</ellipse>\n";
    }

    private static String formatPercent(double confidence) {
        double percent = Math.round(confidence * 1000) / 10.0;
        return (percent == Math.rint(percent) ? String.valueOf((long) percent) : String.valueOf(percent)) + "%";
    }

    /**
     * Squared radius of the joint 2D confidence region: the chi-square quantile with 2 degrees of
     * freedom when the noise is known, or {@code 2 F(2, m - n)} when it is estimated from residuals.
     */
    private double ellipseQuantile(TrilaterationResult result) {
        return ellipseQuantile(confidence, result.degreesOfFreedom());
    }

    static double ellipseQuantile(double confidence, double degreesOfFreedom) {
        if (Double.isInfinite(degreesOfFreedom)) {
            return -2 * StrictMath.log(1 - confidence);
        }
        return 2 * new FDistribution(2, degreesOfFreedom).inverseCumulativeProbability(confidence);
    }

    /**
     * Builder for {@link TrilaterationSvg}.
     */
    public static final class Builder {

        private int width = 480;
        private double confidence = 0.95;
        private boolean showAlternative = true;
        private boolean wholeCircles;
        private boolean labels;
        private boolean residuals = true;
        private boolean legend = true;

        private Builder() {
        }

        /**
         * Sets the image width in pixels (default 480). The height follows from the data.
         *
         * @param width at least 160 pixels
         * @return this builder
         */
        public Builder width(int width) {
            if (width < 160) {
                throw new IllegalArgumentException("width must be at least 160 pixels but was " + width + ".");
            }
            this.width = width;
            return this;
        }

        /**
         * Sets the probability covered by the confidence ellipse (default 0.95). No ellipse is drawn
         * when the result has no covariance.
         *
         * @param confidence strictly between 0 and 1
         * @return this builder
         */
        public Builder confidence(double confidence) {
            if (!(confidence > 0 && confidence < 1)) {
                throw new IllegalArgumentException("confidence must be strictly between 0 and 1 but was " + confidence + ".");
            }
            this.confidence = confidence;
            return this;
        }

        /**
         * Whether to draw the alternative position of an ambiguous result (default {@code true}).
         *
         * @param show {@code true} to draw it as a dashed circle
         * @return this builder
         */
        public Builder showAlternative(boolean show) {
            this.showAlternative = show;
            return this;
        }

        /**
         * Whether to fit every distance circle completely in the image (default {@code false}).
         * By default the image frames the anchors and the solution, and large circles are cropped,
         * which keeps the interesting region large.
         *
         * @param whole {@code true} to show whole circles
         * @return this builder
         */
        public Builder wholeCircles(boolean whole) {
            this.wholeCircles = whole;
            return this;
        }

        /**
         * Whether to label the anchors with their index (from 1) and the estimated and alternative
         * positions with text (default {@code false}).
         *
         * @param labels {@code true} to draw labels
         * @return this builder
         */
        public Builder labels(boolean labels) {
            this.labels = labels;
            return this;
        }

        /**
         * Whether to draw each measurement's residual (default {@code true}): a segment from where
         * the measured distance ends, along the line from the anchor, to the estimate. Its length
         * is the range error.
         *
         * @param residuals {@code true} to draw residuals
         * @return this builder
         */
        public Builder residuals(boolean residuals) {
            this.residuals = residuals;
            return this;
        }

        /**
         * Whether to draw a legend below the plot (default {@code true}).
         *
         * @param legend {@code true} to draw the legend
         * @return this builder
         */
        public Builder legend(boolean legend) {
            this.legend = legend;
            return this;
        }

        /**
         * {@return a new, immutable renderer}
         */
        public TrilaterationSvg build() {
            return new TrilaterationSvg(this);
        }
    }
}
