package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class GeodeticFrameTest {

    private static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        return Math.sqrt(sum);
    }

    @Test
    void matchesGeographicLibReferenceValues() {
        // From the GeographicLib CartConvert documentation, printed to the centimetre.
        assertArrayEquals(new double[] {3816209.60, 3737108.55, 3485109.57}, GeodeticFrame.ecef(33.3, 44.4, 6000), 0.01);
        assertArrayEquals(new double[] {37288.97, 33374.29, 5783.64}, GeodeticFrame.at(33, 44, 20).toLocal(33.3, 44.4, 6000), 0.01);
    }

    @Test
    void matchesTheEllipsoidAxes() {
        assertArrayEquals(new double[] {GeodeticFrame.A, 0, 0}, GeodeticFrame.ecef(0, 0, 0), 1e-9);
        assertArrayEquals(new double[] {0, GeodeticFrame.A + 100, 0}, GeodeticFrame.ecef(0, 90, 100), 1e-9);
        assertArrayEquals(new double[] {0, 0, GeodeticFrame.B}, GeodeticFrame.ecef(90, 0, 0), 1e-9);
        assertArrayEquals(new double[] {-90, 0, 0}, GeodeticFrame.geodetic(0, 0, -GeodeticFrame.B), 1e-9);
    }

    @Test
    void geodeticIsTheInverseOfEcef() {
        for (double lat = -90; lat <= 90; lat += 7.5) {
            for (double lon = -180; lon < 180; lon += 22.5) {
                for (double alt : new double[] {-500, 0, 1234.5, 100_000}) {
                    double[] xyz = GeodeticFrame.ecef(lat, lon, alt);
                    double[] back = GeodeticFrame.geodetic(xyz[0], xyz[1], xyz[2]);
                    assertEquals(lat, back[0], 1e-11, "latitude at " + lat + ", " + lon + ", " + alt);
                    if (Math.abs(lat) < 90) {
                        assertEquals(lon, back[1], 1e-11, "longitude at " + lat + ", " + lon + ", " + alt);
                    }
                    assertEquals(alt, back[2], 1e-6, "altitude at " + lat + ", " + lon + ", " + alt);
                }
            }
        }
    }

    @Test
    void localCoordinatesPointEastNorthAndUp() {
        GeodeticFrame frame = GeodeticFrame.at(45, 10, 200);

        assertArrayEquals(new double[] {0, 0, 0}, frame.toLocal(45, 10, 200), 1e-8);
        assertArrayEquals(new double[] {0, 0}, frame.toLocal(45, 10), 1e-8);
        assertArrayEquals(new double[] {0, 0, 50}, frame.toLocal(45, 10, 250), 1e-8);

        double[] east = frame.toLocal(45, 10.001);
        double[] north = frame.toLocal(45.001, 10);
        assertTrue(east[0] > 78 && Math.abs(east[1]) < 1e-3, java.util.Arrays.toString(east));
        assertTrue(north[1] > 111 && Math.abs(north[0]) < 1e-9, java.util.Arrays.toString(north));
    }

    @Test
    void preservesStraightLineDistancesIn3d() {
        GeodeticFrame frame = GeodeticFrame.at(-33.86, 151.21, 30);
        double[] a = {-33.0, 150.0, 1000};
        double[] b = {-34.5, 152.5, -20};

        double inEcef = distance(GeodeticFrame.ecef(a[0], a[1], a[2]), GeodeticFrame.ecef(b[0], b[1], b[2]));

        assertEquals(inEcef, distance(frame.toLocal(a), frame.toLocal(b)), 1e-7);
    }

    @Test
    void roundTripsIn2dAnd3d() {
        Random random = new Random(7);
        for (double[] origin : new double[][] {{0, 0}, {51.48, -0.0015}, {-77.85, 166.67}, {89.99, 45}, {12.3, 179.99}}) {
            GeodeticFrame frame = GeodeticFrame.at(origin[0], origin[1], 35);
            for (int i = 0; i < 50; i++) {
                double[] local3 = {random.nextGaussian() * 20_000, random.nextGaussian() * 20_000, random.nextGaussian() * 500};
                assertArrayEquals(local3, frame.toLocal(frame.toGeodetic(local3)), 1e-6);

                double[] local2 = {local3[0], local3[1]};
                assertArrayEquals(local2, frame.toLocal(frame.toGeodetic(local2)), 1e-6);
            }
        }
    }

    @Test
    void longitudesAreNormalized() {
        assertEquals(-170, GeodeticFrame.at(0, 190).getLongitude(), 1e-12);
        assertEquals(180, GeodeticFrame.at(0, -540).getLongitude(), 1e-12);
        assertEquals(44.4, GeodeticFrame.at(0, 44.4).getLongitude());
        assertArrayEquals(GeodeticFrame.at(10, 20).toLocal(10.1, 20.1), GeodeticFrame.at(10, 20).toLocal(10.1, 380.1), 1e-6);
    }

    @Test
    void aroundCentresTheFrameEvenAcrossTheAntimeridian() {
        double[][] points = {{-17.0, 179.9}, {-17.0, -179.9}, {-17.2, 179.9}, {-17.2, -179.9}};
        GeodeticFrame frame = GeodeticFrame.around(points);

        assertEquals(-17.1, frame.getLatitude(), 1e-3);
        assertEquals(180, Math.abs(frame.getLongitude()), 1e-9);
        assertEquals(0, frame.getAltitude());

        double[][] local = frame.toLocal(points);
        // West and east of the antimeridian by the same 0.1 degrees: about 10.6 km each way.
        assertEquals(-local[0][0], local[1][0], 1e-6);
        assertEquals(local[0][1], local[1][1], 1e-6);
        assertTrue(local[0][0] < -10_000 && local[0][0] > -11_000, "east " + local[0][0]);

        GeodeticFrame withAltitude = GeodeticFrame.around(new double[][] {{1, 2, 100}, {1.01, 2.01, 300}});
        assertEquals(200, withAltitude.getAltitude(), 1e-12);
    }

    @Test
    void solvesAProblemGivenInLatitudeAndLongitude() {
        // Four anchors around a field, at different heights, and a target 1.5 m above the ellipsoid.
        double[][] anchors = {{40.00000, -105.00000, 1620}, {40.00200, -105.00010, 1625}, {40.00180, -105.00260, 1631}, {39.99990, -105.00240, 1612}};
        double[] target = {40.00105, -105.00095, 1621.5};
        double[] targetEcef = GeodeticFrame.ecef(target[0], target[1], target[2]);
        double[] distances = new double[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            distances[i] = distance(GeodeticFrame.ecef(anchors[i][0], anchors[i][1], anchors[i][2]), targetEcef);
        }

        GeodeticFrame frame = GeodeticFrame.around(anchors);
        TrilaterationResult result = new TrilaterationSolver().solve(frame.toLocal(anchors), distances);
        double[] found = frame.toGeodetic(result.getPoint());

        assertEquals(target[0], found[0], 1e-9);
        assertEquals(target[1], found[1], 1e-9);
        assertEquals(target[2], found[2], 1e-5);
    }

    @Test
    void solvesA2dProblemToMillimetresOverAKilometre() {
        double[][] anchors = {{51.5000, -0.1300}, {51.5070, -0.1290}, {51.5060, -0.1190}, {51.4990, -0.1180}};
        double[] target = {51.5031, -0.1243};
        double[] targetEcef = GeodeticFrame.ecef(target[0], target[1], 0);
        double[] distances = new double[anchors.length];
        for (int i = 0; i < anchors.length; i++) {
            distances[i] = distance(GeodeticFrame.ecef(anchors[i][0], anchors[i][1], 0), targetEcef);
        }

        GeodeticFrame frame = GeodeticFrame.around(anchors);
        double[] found = frame.toGeodetic(new TrilaterationSolver().solve(frame.toLocal(anchors), distances).getPoint());

        assertEquals(2, found.length);
        assertTrue(distance(frame.toLocal(target), frame.toLocal(found)) < 1e-3);
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.at(91, 0));
        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.at(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.at(0, 0, Double.POSITIVE_INFINITY));

        GeodeticFrame frame = GeodeticFrame.at(0, 0);
        assertThrows(IllegalArgumentException.class, () -> frame.toLocal(1.0));
        assertThrows(IllegalArgumentException.class, () -> frame.toLocal(1, 2, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> frame.toLocal(-90.5, 0));
        assertThrows(IllegalArgumentException.class, () -> frame.toGeodetic(1.0));
        assertThrows(IllegalArgumentException.class, () -> frame.toGeodetic(1, Double.NaN));
        assertThrows(NullPointerException.class, () -> frame.toLocal((double[]) null));

        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.around(new double[0][]));
        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.around(new double[][] {{0, 0}, {0, 0, 0}}));
        assertThrows(IllegalArgumentException.class, () -> GeodeticFrame.around(new double[][] {{0, 0}, {0, 180}}));
    }

    @Test
    void equalityFollowsTheOrigin() {
        assertEquals(GeodeticFrame.at(1, 2, 3), GeodeticFrame.at(1, 362, 3));
        assertEquals(GeodeticFrame.at(1, 2, 3).hashCode(), GeodeticFrame.at(1, 2, 3).hashCode());
        assertFalse(GeodeticFrame.at(1, 2, 3).equals(GeodeticFrame.at(1, 2, 4)));
        assertEquals("GeodeticFrame{latitude=1.0, longitude=2.0, altitude=3.0}", GeodeticFrame.at(1, 2, 3).toString());
    }
}
