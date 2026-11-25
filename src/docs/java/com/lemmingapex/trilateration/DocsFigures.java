package com.lemmingapex.trilateration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

import org.hipparchus.filtering.kalman.Measurement;
import org.hipparchus.filtering.kalman.ProcessEstimate;
import org.hipparchus.filtering.kalman.linear.LinearEvolution;
import org.hipparchus.filtering.kalman.linear.LinearKalmanFilter;
import org.hipparchus.filtering.kalman.linear.LinearProcess;
import org.hipparchus.linear.ArrayRealVector;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.QRDecomposer;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.RealVector;

import com.lemmingapex.trilateration.SvgWriter.LegendItem;
import com.lemmingapex.trilateration.SvgWriter.Swatch;

/**
 * Generates the README figures from real solver output, each as an SVG and a PNG copy (see
 * {@link PngFigures}). Run with {@code ./gradlew renderDocs}.
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
        write(out.resolve("tracking.svg"), TrackingAnimation.render());
        System.out.println("Wrote figures to " + out.toAbsolutePath());
    }

    /** Writes an SVG figure, and a PNG copy beside it unless that is already up to date. */
    private static void write(Path file, String svg) throws IOException {
        Files.writeString(file, svg, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        Path png = file.resolveSibling(name.substring(0, name.length() - ".svg".length()) + ".png");
        if (PngFigures.update(png, svg)) {
            System.out.println("Rendered " + png.getFileName());
        }
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
    static final class SolverAnimation extends Animation {

        /** A scenario found by searching seeded random problems for one that shows every stage clearly. */
        static final double[][] ANCHORS = {{45, 3}, {84, 49}, {75, 76}, {51, 62}, {79, 15}};
        static final double[] DISTANCES = {33.5, 62.1, 72.4, 45.0, 55.9};

        private static final int WIDTH = 640;
        private static final double CAPTION_BAND = 46;

        private final StringBuilder body = new StringBuilder();
        private final List<double[]> captions = new ArrayList<>();
        private final List<String> captionTexts = new ArrayList<>();

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


        /** Opacity 0 → 1 at {@code on}, → 0.25 at {@code dim}, → 0 at {@code off}. */
        private String dimmed(double on, double dim, double off) {
            if (dim >= off) {
                return visible(on, off);
            }
            return animate("opacity", new double[] {0, on, on + FADE, dim, dim + FADE, off, off + FADE, duration},
                    new String[] {"0", "0", "1", "1", "0.25", "0.25", "0", "0"});
        }
    }

    /** Shared SMIL helpers: every element loops on one clock of {@code duration} seconds. */
    abstract static class Animation {

        static final double FADE = 0.25;

        double duration;

        /** Opacity 0 → 1 at {@code on}, → 0 at {@code off}. */
        String visible(double on, double off) {
            return animate("opacity", new double[] {0, on, on + FADE, off, off + FADE, duration},
                    new String[] {"0", "0", "1", "1", "0", "0"});
        }

        /** A looping SMIL animation of {@code attribute} through {@code values} at {@code times} (seconds). */
        String animate(String attribute, double[] times, String[] values) {
            return "<animate attributeName=\"" + attribute + "\"" + timing(times, values) + "/>";
        }

        /** A looping SMIL transform of {@code type}, such as translate or rotate, through {@code values} at {@code times}. */
        String animateTransform(String type, double[] times, String[] values) {
            return "<animateTransform attributeName=\"transform\" type=\"" + type + "\"" + timing(times, values) + "/>";
        }

        private String timing(double[] times, String[] values) {
            StringBuilder keyTimes = new StringBuilder();
            double previous = 0;
            for (int i = 0; i < times.length; i++) {
                // Key times must be non-decreasing and end at exactly 1.
                double t = i == times.length - 1 ? 1 : Math.min(1, Math.max(previous, times[i] / duration));
                previous = t;
                keyTimes.append(i == 0 ? "" : ";").append(String.format(Locale.ROOT, "%.4f", t));
            }
            return " dur=\"" + String.format(Locale.ROOT, "%.2f", duration) + "s\" repeatCount=\"indefinite\" keyTimes=\""
                    + keyTimes + "\" values=\"" + String.join(";", values) + "\"";
        }
    }

    /**
     * An animated SVG of a target lapping a running-track-shaped route among the four anchors of
     * the README's latitude and longitude example. Twice a second its ranges to the anchors are
     * measured with noise; each set is solved on its own (a single fix), and a constant-velocity
     * Kalman filter combines the fixes, using each fix's covariance as its measurement noise. The
     * plot is in metres east and north of the anchors' centre, with gridlines at round latitudes
     * and longitudes.
     */
    static final class TrackingAnimation extends Animation {

        static final double[][] ANCHORS = {
                {40.01490, -105.26970}, {40.01520, -105.26890}, {40.01430, -105.26850}, {40.01410, -105.26960}};
        /** Fixes per second. */
        static final int RATE = 2;
        /** Seconds per lap of the route. */
        static final int LAP = 90;
        static final int FIXES = RATE * LAP;
        /** Straight length and turn radius of the route, in metres. */
        static final double STRAIGHT = 44;
        static final double RADIUS = 18;
        /** Standard deviation of each measured range, in metres. */
        static final double SIGMA = 3.0;
        /** Spectral density of the filter's white-noise acceleration, in m²/s³. */
        static final double ACCELERATION_NOISE = 0.2;
        private static final long SEED = 7;
        private static final int WIDTH = 640;
        private static final double CAPTION_BAND = 46;
        /** Animation seconds per fix: playback at six times real speed. */
        private static final double STEP = 1.0 / (6 * RATE);

        private final StringBuilder body = new StringBuilder();

        static String render() {
            return new TrackingAnimation().build();
        }

        /**
         * The route at a fraction of a lap, at constant speed: two straights joined by semicircles,
         * centred on the anchors' centre, in metres east and north. It starts at the middle of the
         * southern straight, heading east.
         */
        static double[] route(double lap) {
            double turn = StrictMath.PI * RADIUS;
            double s = (lap - StrictMath.floor(lap)) * (2 * STRAIGHT + 2 * turn) + STRAIGHT / 2;
            if (s < STRAIGHT) {
                return new double[] {s - STRAIGHT / 2, -RADIUS};
            }
            s -= STRAIGHT;
            if (s < turn) {
                double a = -StrictMath.PI / 2 + s / RADIUS;
                return new double[] {STRAIGHT / 2 + RADIUS * StrictMath.cos(a), RADIUS * StrictMath.sin(a)};
            }
            s -= turn;
            if (s < STRAIGHT) {
                return new double[] {STRAIGHT / 2 - s, RADIUS};
            }
            s -= STRAIGHT;
            if (s < turn) {
                double a = StrictMath.PI / 2 + s / RADIUS;
                return new double[] {-STRAIGHT / 2 + RADIUS * StrictMath.cos(a), RADIUS * StrictMath.sin(a)};
            }
            return new double[] {s - turn - STRAIGHT / 2, -RADIUS};
        }

        private String build() {
            GeodeticFrame frame = GeodeticFrame.around(ANCHORS);
            double[][] anchors = frame.toLocal(ANCHORS);
            double[] sigmas = new double[anchors.length];
            Arrays.fill(sigmas, SIGMA);
            Random noise = new Random(SEED);
            TrilaterationSolver solver = new TrilaterationSolver();
            double quantile = TrilaterationSvg.ellipseQuantile(0.95, Double.POSITIVE_INFINITY);

            double[][] truth = new double[FIXES][];
            double[][] ranges = new double[FIXES][anchors.length];
            double[][] fixes = new double[FIXES][];
            double[][] filtered = new double[FIXES][];
            double[][] ellipses = new double[FIXES][];
            LinearKalmanFilter<Fix> filter = null;
            for (int k = 0; k < FIXES; k++) {
                truth[k] = route((double) k / FIXES);
                for (int i = 0; i < anchors.length; i++) {
                    ranges[k][i] = TrilaterationFunction.distance(anchors[i], truth[k]) + SIGMA * noise.nextGaussian();
                }
                TrilaterationProblem problem = TrilaterationProblem.of(anchors, ranges[k]).withStandardDeviations(sigmas);
                if (k > 0) {
                    problem = problem.withInitialGuess(fixes[k - 1]);
                }
                TrilaterationResult fix = solver.solve(problem);
                fixes[k] = fix.getPoint();
                double[][] r = fix.getCovariance().orElseThrow();
                double time = (double) k / RATE;
                ProcessEstimate estimate;
                if (filter == null) {
                    // Start at the first fix, with an unknown velocity (standard deviation 10 m/s).
                    RealMatrix covariance = MatrixUtils.createRealMatrix(4, 4);
                    covariance.setSubMatrix(r, 0, 0);
                    covariance.setEntry(2, 2, 100);
                    covariance.setEntry(3, 3, 100);
                    estimate = new ProcessEstimate(time, new ArrayRealVector(new double[] {fixes[k][0], fixes[k][1], 0, 0}), covariance);
                    filter = new LinearKalmanFilter<>(new QRDecomposer(1e-15), new ConstantVelocity(ACCELERATION_NOISE, time), estimate);
                } else {
                    estimate = filter.estimationStep(new Fix(time, fixes[k], r));
                }
                filtered[k] = new double[] {estimate.getState().getEntry(0), estimate.getState().getEntry(1)};
                ellipses[k] = SvgWriter.ellipse(estimate.getCovariance().getSubMatrix(0, 1, 0, 1).getData(), quantile);
            }
            double fixError = rms(fixes, truth);
            double filterError = rms(filtered, truth);

            SvgWriter.Bounds bounds = new SvgWriter.Bounds();
            for (double[] a : anchors) {
                bounds.add(a);
            }
            for (int k = 0; k < FIXES; k++) {
                bounds.add(fixes[k]).add(filtered[k]).add(truth[k]);
            }
            List<LegendItem> legend = List.of(
                    new LegendItem(Swatch.ANCHOR, "Anchor"),
                    new LegendItem(Swatch.DISTANCE, "Measured distance"),
                    new LegendItem(Swatch.ROUTE, "True route"),
                    new LegendItem(Swatch.FIX, "Single fix"),
                    new LegendItem(Swatch.TRACK, "Kalman filter"),
                    new LegendItem(Swatch.REGION, "95% confidence region"));
            SvgWriter.Layout layout = new SvgWriter.Layout(bounds.padded(0.06, 1), WIDTH, CAPTION_BAND, legend, "tracking-plot",
                    view -> degreeTicks(view, view.minX, view.maxX, x -> frame.toGeodetic(x, 0)[1],
                            longitude -> frame.toLocal(frame.getLatitude(), longitude)[0], "E", "W"),
                    view -> degreeTicks(view, view.minY, view.maxY, y -> frame.toGeodetic(0, y)[0],
                            latitude -> frame.toLocal(latitude, frame.getLongitude())[1], "N", "S"));
            SvgWriter.Viewport view = layout.view;

            // Timeline, in seconds.
            double[] at = new double[FIXES];
            for (int k = 0; k < FIXES; k++) {
                at[k] = 1.0 + k * STEP;
            }
            double first = at[0];
            double last = at[FIXES - 1];
            double end = last + 3.0;
            duration = end + 0.6;

            // The true route, closed.
            StringBuilder points = new StringBuilder();
            for (int j = 0; j <= 240; j++) {
                double[] p = route(j / 240.0);
                points.append(j == 0 ? "" : " ").append(SvgWriter.num(view.x(p[0]))).append(',').append(SvgWriter.num(view.y(p[1])));
            }
            body.append("<polyline class=\"route\" points=\"").append(points).append("\" opacity=\"1\">")
                    .append(visible(0.3, end)).append("</polyline>\n");

            // The confidence region's trail: every filter step's ellipse stays once the estimate
            // moves on. The ellipses only shape a mask, through which one translucent rectangle is
            // drawn, so the trail is one even, light shade even where they overlap.
            body.append("<mask id=\"tracking-trail\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"").append(WIDTH)
                    .append("\" height=\"").append(SvgWriter.num(layout.height)).append("\">\n");
            for (int k = 0; k < FIXES; k++) {
                body.append(TrilaterationSvg.ellipseElement(view, filtered[k], ellipses[k],
                                animate("opacity", new double[] {0, at[k], at[k], end, end + FADE, duration},
                                        new String[] {"0", "0", "1", "1", "0", "0"}))
                        .replace("class=\"ellipse\"", "fill=\"#fff\""));
            }
            body.append("</mask>\n<rect x=\"0\" y=\"0\" width=\"").append(WIDTH).append("\" height=\"")
                    .append(SvgWriter.num(layout.height)).append("\" style=\"fill:var(--estimate);fill-opacity:.1\" mask=\"url(#tracking-trail)\"/>\n");

            // Distance circles, resized at every fix. Unfilled: four large overlapping fills would
            // wash out the comparison between fixes and track.
            double[] circleTimes = new double[FIXES + 2];
            circleTimes[FIXES + 1] = duration;
            System.arraycopy(at, 0, circleTimes, 1, FIXES);
            for (int i = 0; i < anchors.length; i++) {
                String[] radii = new String[FIXES + 2];
                for (int k = 0; k < FIXES; k++) {
                    radii[k + 1] = SvgWriter.num(view.length(ranges[k][i]));
                }
                radii[0] = radii[1];
                radii[FIXES + 1] = radii[FIXES];
                body.append("<circle class=\"circle open\" cx=\"").append(SvgWriter.num(view.x(anchors[i][0])))
                        .append("\" cy=\"").append(SvgWriter.num(view.y(anchors[i][1])))
                        .append("\" r=\"").append(radii[FIXES]).append("\" opacity=\"1\">")
                        .append(animate("r", circleTimes, radii)).append(visible(first - FADE, end)).append("</circle>\n");
            }

            // Single fixes: each flashes in, then stays as a faint trail.
            for (int k = 0; k < FIXES; k++) {
                body.append("<circle class=\"fix\" cx=\"").append(SvgWriter.num(view.x(fixes[k][0])))
                        .append("\" cy=\"").append(SvgWriter.num(view.y(fixes[k][1]))).append("\" r=\"3.5\" opacity=\"0.4\">")
                        .append(animate("opacity", new double[] {0, at[k], at[k], at[k] + 1.5, end, end + FADE, duration},
                                new String[] {"0", "0", "1", "0.4", "0.4", "0", "0"}))
                        .append("</circle>\n");
            }

            // The filtered track: one polyline, revealed up to the current estimate by sliding a
            // single dash along it.
            StringBuilder track = new StringBuilder();
            double[] travelled = new double[FIXES];
            for (int k = 0; k < FIXES; k++) {
                double x = view.x(filtered[k][0]);
                double y = view.y(filtered[k][1]);
                if (k > 0) {
                    travelled[k] = travelled[k - 1] + Math.hypot(x - view.x(filtered[k - 1][0]), y - view.y(filtered[k - 1][1]));
                }
                track.append(k == 0 ? "" : " ").append(SvgWriter.num(x)).append(',').append(SvgWriter.num(y));
            }
            double dash = Math.ceil(travelled[FIXES - 1]) + 10;
            String[] offsets = new String[FIXES + 2];
            for (int k = 0; k < FIXES; k++) {
                offsets[k + 1] = SvgWriter.num(dash - travelled[k]);
            }
            offsets[0] = offsets[1];
            offsets[FIXES + 1] = offsets[FIXES];
            body.append("<polyline class=\"track\" points=\"").append(track).append("\" stroke-dasharray=\"")
                    .append(SvgWriter.num(dash)).append(' ').append(SvgWriter.num(dash)).append("\" stroke-dashoffset=\"")
                    .append(offsets[FIXES]).append("\" opacity=\"1\">")
                    .append(animate("stroke-dashoffset", circleTimes, offsets)).append(visible(first, end))
                    .append("</polyline>\n");

            // The current estimate and its confidence region, moving together.
            String[] positions = new String[FIXES + 2];
            String[] rx = new String[FIXES + 2];
            String[] ry = new String[FIXES + 2];
            String[] angles = new String[FIXES + 2];
            double previousAngle = 0;
            for (int k = 0; k < FIXES; k++) {
                positions[k + 1] = SvgWriter.num(view.x(filtered[k][0])) + " " + SvgWriter.num(view.y(filtered[k][1]));
                rx[k + 1] = SvgWriter.num(view.length(ellipses[k][0]));
                ry[k + 1] = SvgWriter.num(view.length(ellipses[k][1]));
                // Pixel y points down, so a counter-clockwise world angle is a clockwise pixel rotation.
                // An ellipse looks the same turned by 180°, so take the turn closest to the previous one.
                double angle = -Math.toDegrees(ellipses[k][2]);
                angle += 180 * Math.rint((previousAngle - angle) / 180);
                previousAngle = angle;
                angles[k + 1] = SvgWriter.num(angle);
            }
            for (String[] values : List.of(positions, rx, ry, angles)) {
                values[0] = values[1];
                values[FIXES + 1] = values[FIXES];
            }
            body.append("<g transform=\"translate(").append(positions[FIXES]).append(")\" opacity=\"1\">")
                    .append(animateTransform("translate", circleTimes, positions)).append(visible(first, end))
                    .append("<ellipse class=\"ellipse\" cx=\"0\" cy=\"0\" rx=\"").append(rx[FIXES]).append("\" ry=\"").append(ry[FIXES])
                    .append("\" transform=\"rotate(").append(angles[FIXES]).append(")\">")
                    .append(animate("rx", circleTimes, rx)).append(animate("ry", circleTimes, ry))
                    .append(animateTransform("rotate", circleTimes, angles)).append("</ellipse>")
                    .append("<circle class=\"estimate\" cx=\"0\" cy=\"0\" r=\"6\"/></g>\n");

            for (int i = 0; i < anchors.length; i++) {
                body.append("<g opacity=\"1\">").append(visible(0.3, end))
                        .append(SvgWriter.marker(view, anchors[i], 5, "anchor", "Anchor " + (i + 1) + " at "
                                + degrees(ANCHORS[i][0], "N", "S") + ", " + degrees(ANCHORS[i][1], "E", "W")))
                        .append("</g>\n");
            }

            String caption = String.format(Locale.ROOT, "RMS error: single fixes %.1f m, Kalman filter %.1f m", fixError, filterError);
            double[] finalLatLon = frame.toGeodetic(filtered[FIXES - 1]);
            SvgWriter svg = new SvgWriter(WIDTH, layout.height, "Tracking a moving target with a Kalman filter",
                    "Animation of a target lapping an oval route among four anchors given by latitude and longitude. "
                            + "Every " + 1000 / RATE + " ms its distances to the anchors are measured with "
                            + String.format(Locale.ROOT, "%.1f", SIGMA) + " m of noise and solved for a single fix; a "
                            + "constant-velocity Kalman filter smooths the fixes into a track. Root-mean-square error: "
                            + String.format(Locale.ROOT, "%.2f m for single fixes, %.2f m for the filter.", fixError, filterError)
                            + " Last filtered position "
                            + degrees(finalLatLon[0], "N", "S") + ", " + degrees(finalLatLon[1], "E", "W") + ".");
            svg.append(layout.frame());
            svg.append("<text class=\"caption\" x=\"" + SvgWriter.num(view.left) + "\" y=\"30\">" + SvgWriter.escape(caption) + "</text>\n");
            svg.append(layout.beginPlot()).append(body.toString()).append(layout.close());
            return svg.finish();
        }

        /** Root-mean-square distance between matching points. */
        static double rms(double[][] points, double[][] truth) {
            double sum = 0;
            for (int k = 0; k < points.length; k++) {
                double d = TrilaterationFunction.distance(points[k], truth[k]);
                sum += d * d;
            }
            return StrictMath.sqrt(sum / points.length);
        }

        static String degrees(double value, String positive, String negative) {
            return String.format(Locale.ROOT, "%.5f° %s", Math.abs(value), value >= 0 ? positive : negative);
        }

        /**
         * Gridlines at round latitudes or longitudes: the smallest 1-2-5 step in degrees that puts
         * them at least 90 pixels apart.
         *
         * @param toDegrees maps a world coordinate on this axis to degrees
         * @param toWorld   maps degrees back to a world coordinate
         */
        static SvgWriter.Ticks degreeTicks(SvgWriter.Viewport view, double min, double max, DoubleUnaryOperator toDegrees,
                                           DoubleUnaryOperator toWorld, String positive, String negative) {
            double lo = toDegrees.applyAsDouble(min);
            double hi = toDegrees.applyAsDouble(max);
            double step = 0;
            search:
            for (int exponent = -6; ; exponent++) {
                for (double mantissa : new double[] {1, 2, 5}) {
                    step = mantissa * StrictMath.pow(10, exponent);
                    if (view.length(toWorld.applyAsDouble(lo + step) - toWorld.applyAsDouble(lo)) >= 90) {
                        break search;
                    }
                }
            }
            int places = SvgWriter.Layout.decimals(step);
            long from = (long) StrictMath.ceil(lo / step);
            long to = (long) StrictMath.floor(hi / step);
            double[] at = new double[(int) Math.max(0, to - from + 1)];
            String[] labels = new String[at.length];
            for (int i = 0; i < at.length; i++) {
                double value = (from + i) * step;
                at[i] = toWorld.applyAsDouble(value);
                labels[i] = String.format(Locale.ROOT, "%." + places + "f° %s", Math.abs(value), value >= 0 ? positive : negative);
            }
            return new SvgWriter.Ticks(at, labels);
        }
    }

    /** A position fix from the solver, as a measurement for Hipparchus's Kalman filter. */
    record Fix(double time, double[] position, double[][] covariance) implements Measurement {

        @Override
        public double getTime() {
            return time;
        }

        @Override
        public RealVector getValue() {
            return new ArrayRealVector(position);
        }

        @Override
        public RealMatrix getCovariance() {
            return MatrixUtils.createRealMatrix(covariance);
        }
    }

    /**
     * Constant-velocity motion in two dimensions, for Hipparchus's linear Kalman filter. The state
     * is east, north, east velocity and north velocity; acceleration is modelled as white noise.
     * The filter asks for the evolution once per fix, so the time step is measured from the
     * previous fix.
     */
    static final class ConstantVelocity implements LinearProcess<Fix> {

        /** A fix observes the position, not the velocity. */
        private static final RealMatrix OBSERVED = MatrixUtils.createRealMatrix(new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}});

        private final double accelerationNoise;
        private double previousTime;

        /**
         * @param accelerationNoise spectral density of the white-noise acceleration, in m²/s³
         * @param startTime         time of the initial estimate, in seconds
         */
        ConstantVelocity(double accelerationNoise, double startTime) {
            this.accelerationNoise = accelerationNoise;
            this.previousTime = startTime;
        }

        @Override
        public LinearEvolution getEvolution(Fix fix) {
            double dt = fix.getTime() - previousTime;
            previousTime = fix.getTime();
            RealMatrix transition = MatrixUtils.createRealIdentityMatrix(4);
            transition.setEntry(0, 2, dt);
            transition.setEntry(1, 3, dt);
            double q = accelerationNoise;
            RealMatrix noise = MatrixUtils.createRealMatrix(4, 4);
            for (int axis = 0; axis < 2; axis++) {
                noise.setEntry(axis, axis, q * dt * dt * dt / 3);
                noise.setEntry(axis, axis + 2, q * dt * dt / 2);
                noise.setEntry(axis + 2, axis, q * dt * dt / 2);
                noise.setEntry(axis + 2, axis + 2, q * dt);
            }
            return new LinearEvolution(transition, null, null, noise, OBSERVED);
        }
    }
}
