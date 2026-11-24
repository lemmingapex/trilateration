package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class TrilaterationSvgTest {

    private static final TrilaterationSolver SOLVER = new TrilaterationSolver();
    private static final double[][] ANCHORS = {{0, 0}, {100, 0}, {0, 100}, {100, 100}};
    private static final TrilaterationProblem PROBLEM = TrilaterationProblem.of(ANCHORS, new double[] {50.5, 72.3, 63.0, 80.6});

    private static Document parse(String svg) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static int countByClass(Document document, String tag, String cssClass) {
        NodeList nodes = document.getElementsByTagName(tag);
        int count = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            if (cssClass.equals(((Element) nodes.item(i)).getAttribute("class"))) {
                count++;
            }
        }
        return count;
    }

    @Test
    void rendersWellFormedSvgWithEveryElement() throws Exception {
        TrilaterationResult result = SOLVER.solve(PROBLEM);

        Document svg = parse(TrilaterationSvg.builder().legend(false).build().toSvg(PROBLEM, result));

        Element root = svg.getDocumentElement();
        assertEquals("svg", root.getTagName());
        assertEquals("480", root.getAttribute("width"));
        assertEquals(1, countByClass(svg, "rect", "card"));
        assertEquals("Trilateration result", svg.getElementsByTagName("title").item(0).getTextContent());
        assertTrue(svg.getElementsByTagName("desc").item(0).getTextContent().startsWith("Estimated position ("));
        assertEquals(4, countByClass(svg, "circle", "circle"));
        assertEquals(4, countByClass(svg, "circle", "anchor"));
        assertEquals(1, countByClass(svg, "circle", "estimate"));
        assertEquals(1, countByClass(svg, "ellipse", "ellipse"));
        assertEquals(0, countByClass(svg, "circle", "alternative"));
        assertEquals(0, countByClass(svg, "text", "label"));
        assertEquals(0, countByClass(svg, "text", "legend"));
        assertEquals(4, countByClass(svg, "line", "residual"));
    }

    @Test
    void legendListsOnlyWhatIsDrawn() throws Exception {
        TrilaterationResult result = SOLVER.solve(PROBLEM);

        Document withResiduals = parse(TrilaterationSvg.render(PROBLEM, result));
        Document without = parse(TrilaterationSvg.builder().residuals(false).confidence(0.9).build().toSvg(PROBLEM, result));

        assertEquals(List.of("Anchor", "Measured distance", "Residual", "Estimate", "95% confidence region"), legendTexts(withResiduals));
        assertEquals(List.of("Anchor", "Measured distance", "Estimate", "90% confidence region"), legendTexts(without));
        assertEquals(0, countByClass(without, "line", "residual"));
    }

    private static List<String> legendTexts(Document document) {
        List<String> texts = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("text");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            if ("legend".equals(e.getAttribute("class"))) {
                texts.add(e.getTextContent());
            }
        }
        return texts;
    }

    @Test
    void gridTicksUseRoundNumbers() {
        String svg = TrilaterationSvg.render(PROBLEM, SOLVER.solve(PROBLEM));

        // The anchors span 0..100, so the ticks step by 20.
        assertTrue(svg.contains(">0</text>") && svg.contains(">20</text>") && svg.contains(">100</text>"), svg);
        assertFalse(svg.contains(">10</text>"));
    }

    @Test
    void anchorTooltipsReportDistanceAndResidual() throws Exception {
        TrilaterationResult result = SOLVER.solve(PROBLEM);

        String svg = TrilaterationSvg.render(PROBLEM, result);

        assertTrue(svg.contains("<title>Anchor 1 at (0.000, 0.000), distance 50.50, residual "), svg);
    }

    @Test
    void drawsTheAlternativeOfAnAmbiguousResult() throws Exception {
        TrilaterationProblem collinear = TrilaterationProblem.of(new double[][] {{0, 0}, {50, 0}, {100, 0}}, new double[] {50, 40, Math.sqrt(70 * 70 + 40 * 40)});
        TrilaterationResult result = SOLVER.solve(collinear);
        assertTrue(result.isAmbiguous());

        Document shown = parse(TrilaterationSvg.builder().labels(true).legend(false).build().toSvg(collinear, result));
        Document hidden = parse(TrilaterationSvg.builder().showAlternative(false).legend(false).build().toSvg(collinear, result));

        assertEquals(1, countByClass(shown, "circle", "alternative"));
        assertEquals(0, countByClass(hidden, "circle", "alternative"));
        // Three anchor numbers, "alternative" and "estimate".
        assertEquals(5, countByClass(shown, "text", "label"));
    }

    @Test
    void omitsTheEllipseWithoutCovariance() throws Exception {
        TrilaterationProblem twoAnchors = TrilaterationProblem.of(new double[][] {{0, 0}, {10, 0}}, new double[] {6, 6});
        TrilaterationResult result = SOLVER.solve(twoAnchors);
        assertTrue(result.getCovariance().isEmpty());

        assertEquals(0, countByClass(parse(TrilaterationSvg.builder().legend(false).build().toSvg(twoAnchors, result)), "ellipse", "ellipse"));
    }

    @Test
    void wholeCirclesEnlargeTheFrame() throws Exception {
        TrilaterationResult result = SOLVER.solve(PROBLEM);

        double cropped = scale(TrilaterationSvg.builder().legend(false).build().toSvg(PROBLEM, result));
        double whole = scale(TrilaterationSvg.builder().legend(false).wholeCircles(true).build().toSvg(PROBLEM, result));

        assertTrue(whole < cropped, "whole " + whole + " cropped " + cropped);
    }

    /** Pixels per world unit, from the first anchor-to-anchor spacing (anchors 1 and 2 are 100 apart). */
    private static double scale(String svg) throws Exception {
        Document document = parse(svg);
        NodeList circles = document.getElementsByTagName("circle");
        double first = Double.NaN;
        for (int i = 0; i < circles.getLength(); i++) {
            Element e = (Element) circles.item(i);
            if ("anchor".equals(e.getAttribute("class"))) {
                double x = Double.parseDouble(e.getAttribute("cx"));
                if (Double.isNaN(first)) {
                    first = x;
                } else {
                    return (x - first) / 100;
                }
            }
        }
        throw new AssertionError("no anchors");
    }

    @Test
    void outputIsDeterministicAndLocaleIndependent() {
        TrilaterationResult result = SOLVER.solve(PROBLEM);
        String reference = TrilaterationSvg.render(PROBLEM, result);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String german = TrilaterationSvg.render(PROBLEM, result);
            assertEquals(reference, german);
            assertFalse(german.matches("(?s).*cx=\"\\d+,\\d+\".*"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void rejectsInvalidInput() {
        TrilaterationProblem threeD = TrilaterationProblem.of(new double[][] {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, new double[] {1, 1, 1, 1});
        TrilaterationResult threeDResult = SOLVER.solve(threeD);
        assertThrows(IllegalArgumentException.class, () -> TrilaterationSvg.render(threeD, threeDResult));

        TrilaterationResult otherResult = SOLVER.solve(new double[][] {{0, 0}, {10, 0}, {0, 10}}, new double[] {5, 7, 8});
        assertThrows(IllegalArgumentException.class, () -> TrilaterationSvg.render(PROBLEM, otherResult));

        assertThrows(NullPointerException.class, () -> TrilaterationSvg.render(null, otherResult));
        assertThrows(IllegalArgumentException.class, () -> TrilaterationSvg.builder().width(100));
        assertThrows(IllegalArgumentException.class, () -> TrilaterationSvg.builder().confidence(1));
    }

    @Test
    void ellipseAxesFollowTheCovariance() {
        assertArrayEquals(new double[] {2 * Math.sqrt(9), Math.sqrt(9), 0}, SvgWriter.ellipse(new double[][] {{4, 0}, {0, 1}}, 9), 1e-12);

        // Eigenvalues 4 and 1, major axis at 45 degrees.
        double[] rotated = SvgWriter.ellipse(new double[][] {{2.5, 1.5}, {1.5, 2.5}}, 1);
        assertArrayEquals(new double[] {2, 1, Math.PI / 4}, rotated, 1e-12);
    }

    @Test
    void ellipseQuantilesMatchChiSquareAndF() {
        // Chi-square with 2 degrees of freedom, 95%: -2 ln(0.05).
        assertEquals(5.991465, TrilaterationSvg.ellipseQuantile(0.95, Double.POSITIVE_INFINITY), 1e-6);
        // 2 F(2, 2) at 95% is 2 * 19.
        assertEquals(38.0, TrilaterationSvg.ellipseQuantile(0.95, 2), 1e-6);
    }

    @Test
    void viewportKeepsEqualScaleAndFlipsY() {
        SvgWriter.Viewport view = new SvgWriter.Viewport(new double[] {0, 100, 0, 100}, 10, 10, 200);

        assertEquals(2.0, view.scale, 1e-12);
        assertEquals(200, view.plotHeight(), 1e-12);
        assertEquals(10, view.x(0), 1e-12);
        assertEquals(210, view.y(0), 1e-12);
        assertEquals(10, view.y(100), 1e-12);
    }
}
