package com.lemmingapex.trilateration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lemmingapex.trilateration.SvgWriter.LegendItem;
import com.lemmingapex.trilateration.SvgWriter.Swatch;

/**
 * Generates the README figures from real solver output. Run with {@code ./gradlew renderDocs}.
 *
 * <p>Every input is fixed, so the output is reproducible: rerunning without code changes leaves
 * the files unchanged, and CI checks that they are up to date.
 */
public final class DocsFigures {

    private DocsFigures() {
    }

    /**
     * @param args the output directory
     * @throws IOException if a file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/images");
        Files.createDirectories(out);
        write(out.resolve("solution.svg"), solution());
        write(out.resolve("ambiguous.svg"), ambiguous());
        write(out.resolve("outlier.svg"), outlier());
        write(out.resolve("solver.svg"), SolverAnimation.render());
        System.out.println("Wrote figures to " + out.toAbsolutePath());
    }

    private static void write(Path file, String svg) throws IOException {
        Files.writeString(file, svg, StandardCharsets.UTF_8);
    }

    /** Distances from {@code truth} to each anchor plus a fixed error, rounded to 0.1. */
    static double[] measure(double[][] anchors, double[] truth, double[] errors) {
        double[] distances = new double[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            distances[i] = Math.round((TrilaterationFunction.distance(anchors[i], truth) + errors[i]) * 10) / 10.0;
        }
        return distances;
    }

    /** The README's lead image: five anchors, noisy distances, the estimate and its 95% ellipse. */
    static String solution() {
        double[][] anchors = {{12, 72}, {48, 96}, {86, 70}, {74, 18}, {22, 12}};
        double[] distances = measure(anchors, new double[] {55, 52}, new double[] {1.5, -2.0, 1.0, -1.2, 2.2});
        TrilaterationProblem problem = TrilaterationProblem.of(anchors, distances);
        return TrilaterationSvg.builder().width(560).build().toSvg(problem, new TrilaterationSolver().solve(problem));
    }

    /** Nearly collinear anchors: the estimate and its mirror image fit about equally well. */
    static String ambiguous() {
        double[][] anchors = {{0, 0}, {50, 0.5}, {100, 0}, {25, -0.5}};
        double[] distances = measure(anchors, new double[] {30, 40}, new double[] {0.8, -1.4, 1.1, 0.6});
        TrilaterationProblem problem = TrilaterationProblem.of(anchors, distances);
        TrilaterationResult result = new TrilaterationSolver().solve(problem);
        if (!result.isAmbiguous()) {
            throw new IllegalStateException("The ambiguous figure's scenario is no longer ambiguous: " + result);
        }
        return TrilaterationSvg.builder().width(520).labels(true).build().toSvg(problem, result);
    }

    /** One distance 25 units too long: least squares is pulled away, the Huber loss is not. */
    static String outlier() {
        double[][] anchors = {{76, 28}, {67, 86}, {5, 94}, {14, 44}, {4, 10}, {42, 57}};
        double[] truth = {42, 37};
        double[] distances = measure(anchors, truth, new double[] {25.0, 0.2, -0.8, -0.4, -0.1, -0.5});
        TrilaterationProblem problem = TrilaterationProblem.of(anchors, distances);
        double[] leastSquares = new TrilaterationSolver().solve(problem).getPoint();
        double[] huber = TrilaterationSolver.builder().robustLoss(RobustLoss.HUBER).build().solve(problem).getPoint();
        int outlier = 0;

        SvgWriter.Bounds bounds = new SvgWriter.Bounds().add(leastSquares).add(huber).add(truth);
        for (double[] a : anchors) {
            bounds.add(a);
        }
        List<LegendItem> legend = List.of(
                new LegendItem(Swatch.ANCHOR, "Anchor"),
                new LegendItem(Swatch.DISTANCE, "Measured distance"),
                new LegendItem(Swatch.OUTLIER_DISTANCE, "Measured 25 too long"),
                new LegendItem(Swatch.HOLLOW_ESTIMATE, "Least squares"),
                new LegendItem(Swatch.ESTIMATE, "Huber loss"),
                new LegendItem(Swatch.TRUTH, "True position"));
        SvgWriter.Layout layout = new SvgWriter.Layout(bounds.padded(0.08, 1), 560, 14, legend, "outlier-plot");
        SvgWriter.Viewport view = layout.view;
        SvgWriter svg = new SvgWriter(560, layout.height, "Robust loss versus least squares",
                "Six anchors; one distance is 25 units too long. Least squares estimates " + SvgWriter.point(leastSquares)
                        + ", the Huber loss " + SvgWriter.point(huber) + ", the true position is " + SvgWriter.point(truth) + ".");
        svg.append(layout.open());
        for (int i = 0; i < anchors.length; i++) {
            svg.append(circle(view, anchors[i], distances[i], i == outlier ? "circle outlier" : "circle"));
        }
        for (int i = 0; i < anchors.length; i++) {
            svg.append(SvgWriter.marker(view, anchors[i], 5, "anchor", "Anchor " + (i + 1) + ", measured distance "
                    + SvgWriter.value(distances[i]) + (i == outlier ? " (25 too long)" : "")));
        }
        svg.append(SvgWriter.marker(view, leastSquares, 7, "alternative", "Least squares estimate " + SvgWriter.point(leastSquares)));
        svg.append(SvgWriter.label(view, leastSquares, "least squares", -94, -10));
        svg.append(SvgWriter.marker(view, huber, 6, "estimate", "Huber estimate " + SvgWriter.point(huber)));
        svg.append(SvgWriter.label(view, huber, "Huber", 12, -10));
        // On top of the Huber dot: the point of the figure is that they coincide.
        svg.append(SvgWriter.cross(view.x(truth[0]), view.y(truth[1]), 6)).append("\n");
        svg.append(layout.close());
        return svg.finish();
    }

    static String circle(SvgWriter.Viewport view, double[] center, double radius, String cssClass) {
        return "<circle class=\"" + cssClass + "\" cx=\"" + SvgWriter.num(view.x(center[0])) + "\" cy=\""
                + SvgWriter.num(view.y(center[1])) + "\" r=\"" + SvgWriter.num(view.length(radius)) + "\"/>\n";
    }

    /**
     * An animated SVG (SMIL) that replays one real solve: the measured distances, the closed-form
     * starting point, the Levenberg-Marquardt steps, the mirror check and the final estimate with
     * its confidence ellipse. All elements share one looping clock, so the animation repeats.
     * Without animation support the SVG shows the final frame.
     */
    static final class SolverAnimation {

        /** A scenario found by searching seeded random problems for one that shows every stage clearly. */
        static final double[][] ANCHORS = {{45, 3}, {84, 49}, {75, 76}, {51, 62}, {79, 15}};
        static final double[] DISTANCES = {33.5, 62.1, 72.4, 45.0, 55.9};

        private static final int WIDTH = 640;
        private static final double CAPTION_BAND = 46;
        private static final double FADE = 0.25;

        private final StringBuilder body = new StringBuilder();
        private final List<double[]> captions = new ArrayList<>();
        private final List<String> captionTexts = new ArrayList<>();
        private double duration;

        static String render() {
            return new SolverAnimation().build();
        }

        private String build() {
            TrilaterationProblem problem = TrilaterationProblem.of(ANCHORS, DISTANCES);
            SolverTrace trace = new SolverTrace();
            TrilaterationResult result = new TrilaterationSolver().solve(problem, trace);
            if (trace.runs.size() != 2 || result.isAmbiguous()) {
                throw new IllegalStateException("The animation scenario no longer shows a mirror check: " + result);
            }
            List<double[]> primary = trace.runs.get(0);
            List<double[]> mirror = trace.runs.get(1);
            double[] estimate = result.getPoint();

            SvgWriter.Bounds bounds = new SvgWriter.Bounds();
            for (double[] a : ANCHORS) {
                bounds.add(a);
            }
            for (List<double[]> run : trace.runs) {
                for (double[] p : run) {
                    bounds.add(p);
                }
            }
            double[] ellipse = SvgWriter.ellipse(result.getCovariance().orElseThrow(),
                    TrilaterationSvg.ellipseQuantile(0.95, result.degreesOfFreedom()));
            bounds.add(estimate[0], estimate[1], ellipse[0]);
            List<LegendItem> legend = List.of(
                    new LegendItem(Swatch.ANCHOR, "Anchor"),
                    new LegendItem(Swatch.DISTANCE, "Measured distance"),
                    new LegendItem(Swatch.PATH, "Optimizer path"),
                    new LegendItem(Swatch.ESTIMATE, "Estimate"),
                    new LegendItem(Swatch.REGION, "95% confidence region"));
            SvgWriter.Layout layout = new SvgWriter.Layout(bounds.padded(0.06, 1), WIDTH, CAPTION_BAND, legend, "solver-plot");
            SvgWriter.Viewport view = layout.view;

            // Timeline, in seconds.
            double anchorsIn = 0.3;
            double circlesFrom = 0.8;
            double circlesTo = 2.3;
            double linearAt = 3.0;
            double primaryFrom = 4.4;
            double[] primaryTimes = stepTimes(primary, primaryFrom, 1.6);
            double primaryEnd = primaryTimes[primaryTimes.length - 1];
            double mirrorAt = primaryEnd + 1.0;
            double reflectedAt = mirrorAt + 0.8;
            double mirrorFrom = reflectedAt + 0.8;
            double[] mirrorTimes = stepTimes(mirror, mirrorFrom, 3.2);
            double mirrorEnd = mirrorTimes[mirrorTimes.length - 1];
            double compareAt = mirrorEnd + 0.8;
            double finalAt = compareAt + 2.2;
            duration = finalAt + 4.0;
            double end = duration - 0.4;

            caption(0, linearAt - 0.2, "Each anchor measures its distance to the unknown position");
            caption(linearAt - 0.2, primaryFrom - 0.3, "1. A closed-form linear estimate gives the starting point");
            caption(primaryFrom - 0.3, mirrorAt - 0.2, "2. Levenberg-Marquardt steps to the nearest best fit");
            caption(mirrorAt - 0.2, compareAt - 0.1, "3. Mirror check: restart from the reflected point");
            caption(compareAt - 0.1, finalAt - 0.1, String.format(Locale.ROOT,
                    "4. Keep the better fit: residual cost %.0f beats %.0f", cost(primary, problem), cost(mirror, problem)));
            caption(finalAt - 0.1, end, "The estimate, with its 95% confidence region");

            // Distance circles grow from their anchors.
            for (int i = 0; i < ANCHORS.length; i++) {
                double r = view.length(DISTANCES[i]);
                body.append("<circle class=\"circle\" cx=\"").append(SvgWriter.num(view.x(ANCHORS[i][0])))
                        .append("\" cy=\"").append(SvgWriter.num(view.y(ANCHORS[i][1])))
                        .append("\" r=\"").append(SvgWriter.num(r)).append("\">")
                        .append(animate("r", new double[] {0, circlesFrom, circlesTo, end, end + FADE, duration},
                                new String[] {"0", "0", SvgWriter.num(r), SvgWriter.num(r), "0", "0"}))
                        .append("</circle>\n");
            }

            // Mirror axis: the line through the centroid perpendicular to the flattest direction.
            double[] c = trace.centroid;
            double[] u = {-trace.flattestDirection[1], trace.flattestDirection[0]};
            double reach = 4 * (view.maxX - view.minX + view.maxY - view.minY);
            body.append("<line class=\"axis\" x1=\"").append(SvgWriter.num(view.x(c[0] - reach * u[0])))
                    .append("\" y1=\"").append(SvgWriter.num(view.y(c[1] - reach * u[1])))
                    .append("\" x2=\"").append(SvgWriter.num(view.x(c[0] + reach * u[0])))
                    .append("\" y2=\"").append(SvgWriter.num(view.y(c[1] + reach * u[1])))
                    .append("\" opacity=\"0\">").append(visible(mirrorAt, compareAt + 0.6)).append("</line>\n");

            body.append("<g opacity=\"1\">").append(visible(finalAt, end))
                    .append(TrilaterationSvg.ellipseElement(view, estimate, ellipse, ""))
                    .append("</g>\n");

            // Mirror run: reflected start, its path, dimmed once it loses.
            path(view, mirror, mirrorTimes, reflectedAt, compareAt, end);
            // Primary run: linear start and path, kept until the final estimate takes over.
            path(view, primary, primaryTimes, linearAt, finalAt + 0.8, end);

            for (int i = 0; i < ANCHORS.length; i++) {
                body.append("<g opacity=\"1\">").append(visible(anchorsIn, end))
                        .append(SvgWriter.marker(view, ANCHORS[i], 5, "anchor", "Anchor " + (i + 1) + ", measured distance "
                                + SvgWriter.value(DISTANCES[i])))
                        .append("</g>\n");
            }
            body.append("<g opacity=\"1\">").append(visible(finalAt, end))
                    .append(SvgWriter.marker(view, estimate, 6, "estimate", "Estimate " + SvgWriter.point(estimate)))
                    .append("</g>\n");

            SvgWriter svg = new SvgWriter(WIDTH, layout.height, "How the trilateration solver works",
                    "Animation of one solve with five anchors: distance circles, the closed-form starting point, "
                            + (primary.size() - 1) + " Levenberg-Marquardt steps, a mirror check that restarts from the "
                            + "reflected point and converges to a worse fit, and the final estimate "
                            + SvgWriter.point(estimate) + " with its 95% confidence region.");
            svg.append(layout.frame());
            for (int i = 0; i < captions.size(); i++) {
                double[] window = captions.get(i);
                boolean last = i == captions.size() - 1;
                svg.append("<text class=\"caption\" x=\"" + SvgWriter.num(view.left) + "\" y=\"30\" opacity=\"" + (last ? "1" : "0") + "\">"
                        + SvgWriter.escape(captionTexts.get(i)) + visible(window[0], window[1]) + "</text>\n");
            }
            svg.append(layout.beginPlot()).append(body.toString()).append(layout.close());
            return svg.finish();
        }

        /** Sum of squared range errors at the last point of a run. */
        private static double cost(List<double[]> run, TrilaterationProblem problem) {
            double[] point = run.get(run.size() - 1);
            double sum = 0;
            for (double r : new TrilaterationFunction(problem.positions(), problem.distances()).residuals(point)) {
                sum += r * r;
            }
            return sum;
        }

        /** Captions fade in only after the previous one has faded out, so they never overlap. */
        private void caption(double from, double to, String text) {
            captions.add(new double[] {captions.isEmpty() ? from : from + FADE, to});
            captionTexts.add(text);
        }

        /**
         * One optimizer run: a ring at its start, a dot that moves step by step, and the path drawn
         * segment by segment as the dot travels. The run dims at {@code dimAt} and disappears at
         * {@code end}.
         */
        private void path(SvgWriter.Viewport view, List<double[]> run, double[] stepTimes,
                          double startAt, double dimAt, double end) {
            double[] start = run.get(0);
            for (int k = 1; k < run.size(); k++) {
                double[] a = run.get(k - 1);
                double[] b = run.get(k);
                double departs = stepTimes[k - 1];
                double arrives = stepTimes[k];
                String ax = SvgWriter.num(view.x(a[0]));
                String ay = SvgWriter.num(view.y(a[1]));
                String bx = SvgWriter.num(view.x(b[0]));
                String by = SvgWriter.num(view.y(b[1]));
                // The segment grows from a to b while the dot travels it.
                double[] growth = {0, departs, arrives, duration};
                body.append("<line class=\"path\" x1=\"").append(ax).append("\" y1=\"").append(ay)
                        .append("\" x2=\"").append(bx).append("\" y2=\"").append(by).append("\" opacity=\"0\">")
                        .append(animate("x2", growth, new String[] {ax, ax, bx, bx}))
                        .append(animate("y2", growth, new String[] {ay, ay, by, by}))
                        .append(instant(departs, Math.max(dimAt, departs), end)).append("</line>\n");
            }
            body.append("<circle class=\"start\" cx=\"").append(SvgWriter.num(view.x(start[0])))
                    .append("\" cy=\"").append(SvgWriter.num(view.y(start[1]))).append("\" r=\"6\" opacity=\"0\">")
                    .append(dimmed(startAt, dimAt, end)).append("</circle>\n");
            // The moving dot: hold at the start, then visit each iterate in turn.
            int n = run.size();
            double[] times = new double[n + 2];
            String[] xs = new String[n + 2];
            String[] ys = new String[n + 2];
            times[0] = 0;
            xs[0] = SvgWriter.num(view.x(start[0]));
            ys[0] = SvgWriter.num(view.y(start[1]));
            for (int k = 0; k < n; k++) {
                times[k + 1] = stepTimes[k];
                xs[k + 1] = SvgWriter.num(view.x(run.get(k)[0]));
                ys[k + 1] = SvgWriter.num(view.y(run.get(k)[1]));
            }
            times[n + 1] = duration;
            xs[n + 1] = xs[n];
            ys[n + 1] = ys[n];
            body.append("<circle class=\"step\" cx=\"").append(xs[n]).append("\" cy=\"").append(ys[n])
                    .append("\" r=\"5\" opacity=\"0\">")
                    .append(animate("cx", times, xs)).append(animate("cy", times, ys))
                    .append(dimmed(stepTimes[0], dimAt, end)).append("</circle>\n");
        }

        /**
         * When the moving dot reaches each iterate of {@code run}: the first at {@code from}, then
         * each step takes time proportional to the distance it covers (at least 0.04 s), so long
         * steps are easy to follow and tiny final refinements pass quickly.
         */
        static double[] stepTimes(List<double[]> run, double from, double budget) {
            double total = 0;
            for (int k = 1; k < run.size(); k++) {
                total += TrilaterationFunction.distance(run.get(k - 1), run.get(k));
            }
            double[] times = new double[run.size()];
            times[0] = from;
            for (int k = 1; k < run.size(); k++) {
                double share = total > 0 ? TrilaterationFunction.distance(run.get(k - 1), run.get(k)) / total : 1.0 / run.size();
                times[k] = times[k - 1] + Math.max(0.04, budget * share);
            }
            return times;
        }

        /** Like {@link #dimmed}, but appears instantly so a growing segment is visible from its first pixel. */
        private String instant(double on, double dim, double off) {
            return animate("opacity", new double[] {0, on, on, dim, dim + FADE, off, off + FADE, duration},
                    new String[] {"0", "0", "1", "1", "0.25", "0.25", "0", "0"});
        }

        /** Opacity 0 → 1 at {@code on}, → 0 at {@code off}. */
        private String visible(double on, double off) {
            return animate("opacity", new double[] {0, on, on + FADE, off, off + FADE, duration},
                    new String[] {"0", "0", "1", "1", "0", "0"});
        }

        /** Opacity 0 → 1 at {@code on}, → 0.25 at {@code dim}, → 0 at {@code off}. */
        private String dimmed(double on, double dim, double off) {
            if (dim >= off) {
                return visible(on, off);
            }
            return animate("opacity", new double[] {0, on, on + FADE, dim, dim + FADE, off, off + FADE, duration},
                    new String[] {"0", "0", "1", "1", "0.25", "0.25", "0", "0"});
        }

        /** A looping SMIL animation of {@code attribute} through {@code values} at {@code times} (seconds). */
        private String animate(String attribute, double[] times, String[] values) {
            StringBuilder keyTimes = new StringBuilder();
            double previous = 0;
            for (int i = 0; i < times.length; i++) {
                // Key times must be non-decreasing and end at exactly 1.
                double t = i == times.length - 1 ? 1 : Math.min(1, Math.max(previous, times[i] / duration));
                previous = t;
                keyTimes.append(i == 0 ? "" : ";").append(String.format(Locale.ROOT, "%.4f", t));
            }
            return "<animate attributeName=\"" + attribute + "\" dur=\"" + String.format(Locale.ROOT, "%.2f", duration)
                    + "s\" repeatCount=\"indefinite\" keyTimes=\"" + keyTimes + "\" values=\"" + String.join(";", values) + "\"/>";
        }
    }
}
