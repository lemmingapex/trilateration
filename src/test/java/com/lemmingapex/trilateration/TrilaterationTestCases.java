package com.lemmingapex.trilateration;

import org.junit.Test;

/**
 * Test class which is initialized with different predefined test cases.
 * All test cases were defined by @author scott
 *
 * @author burfi
 */
public class TrilaterationTestCases {

    @Test
    public void trilateration1DExact1() throws Exception {
        double[][] positions = new double[][]{{1.0}, {2.0}, {3.0}};
        double[] distances = new double[]{1.1, 0.1, 0.9};
        double[] expectedPosition = new double[]{2.1};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration1DExact2() throws Exception {
        double[][] positions = new double[][]{{1000.0}, {2000.0}, {3000.0}};
        double[] distances = new double[]{1100, 100, 900};
        double[] expectedPosition = new double[]{2100.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration1DInexact() throws Exception {
        double[][] positions = new double[][]{{1000.0}, {2000.0}, {3000.0}};
        double[] distances = new double[]{1110, 110, 910};
        double[] expectedPosition = new double[]{2100.0};
        double acceptedDelta = 30;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DExact1() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}};
        double[] distances = new double[]{1.0, 1.0, 1.0};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DZeroDistance() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {2.0, 1.0}};
        double[] distances = new double[]{0.0, 1.0};
        double[] expectedPosition = new double[]{1.0, 1.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DExact2() throws Exception {
        double[][] positions = new double[][]{{0.0, 0.0}, {-1.0, 0.0}, {0.0, -1.0}};
        double[] distances = new double[]{Math.sqrt(2.0), 1.0, 1.0};
        double[] expectedPosition = new double[]{-1.0, -1.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DExact3() throws Exception {
        double[][] positions = new double[][]{{0.0, 0.0}, {1000.0, 0.0}, {0.0, 1000.0}};
        double[] distances = new double[]{Math.sqrt(2.0) * 1000.0, 1000.0, 1000.0};
        double[] expectedPosition = new double[]{1000.0, 1000.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DExact4() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {1.0, 3.0}, {8.0, 8.0}, {2.0, 2.0}};
        double[] distances = new double[]{5.0, 5.0, 6.36, 3.9};
        double[] expectedPosition = new double[]{5.9, 2.0};
        double acceptedDelta = 0.01;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DExact5() throws Exception {
        double[][] positions = new double[][]{{5.0, -6.0}, {13.0, -15.0}, {21.0, -3.0}};
        double[] distances = new double[]{8.06, 13.97, 23.32};
        double[] expectedPosition = new double[]{-0.6, -11.8};
        double acceptedDelta = 0.01;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DInexact1() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}};
        double[] distances = new double[]{0.9, 1.0, 1.0};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 0.1;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DInexact2() throws Exception {
        double[][] positions = new double[][]{{5.0, -6.0}, {13.0, -15.0}, {21.0, -3.0}, {12.42, -21.2}};
        double[] distances = new double[]{8.06, 13.97, 23.32, 15.31};
        double[] expectedPosition = new double[]{-0.6, -11.8};
        double acceptedDelta = 1.0;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DNonIntersecting() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}};
        double[] distances = new double[]{0.5, 0.5, 0.5};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 0.25;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DOverIntersecting() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}};
        double[] distances = new double[]{2.0, 2.0, 2.0};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 2.0;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DDegenerateCase1() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {1.0, 1.0}, {3.0, 1.0}};
        double[] distances = new double[]{1.0, 1.0, 1.0};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 0.5;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DDegenerateCase2() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {1.0, 1.0}, {1.0, 1.0}};
        double[] distances = new double[]{1.0, 1.0, 1.0};
        double[] expectedPosition = new double[]{1.0, 1.0};
        double acceptedDelta = 0.5;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration2DUnderdertermined() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0}, {3.0, 1.0}};
        double[] distances = new double[]{1.0, 1.0};
        double[] expectedPosition = new double[]{2.0, 1.0};
        double acceptedDelta = 0.5;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration3DExact() throws Exception {
        double[][] positions = new double[][]{{1.0, 1.0, 1.0}, {3.0, 1.0, 1.0}, {2.0, 2.0, 1.0}};
        double[] distances = new double[]{1.0, 1.0, 1.0};
        double[] expectedPosition = new double[]{2.0, 1.0, 1.0};
        double acceptedDelta = 0.0001;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration3DInexact() throws Exception {
        double[][] positions = new double[][]{{0.0, 0.0, 0.0}, {8.84, 4.57, 12.59}, {0.0, -8.84, 8.84}, {10.72, -8.96, 8.84}};
        double[] distances = new double[]{8.84, 8.84, 8.84, 8.84};
        double[] expectedPosition = new double[]{5.2, -1.2, 7.7};
        double acceptedDelta = 1.0;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }

    @Test
    public void trilateration4DInexact() throws Exception {
        double[][] positions = new double[][]{{0.0, 0.0, 0.0, 0.0}, {8.84, 4.57, 12.59, 9.2}, {0.0, -8.84, 8.84, 9.2}, {10.72, -8.96, 8.84, 9.2}};
        double[] distances = new double[]{8.84, 8.84, 8.84, 8.84};
        double[] expectedPosition = new double[]{5.2, -1.5, 7.7, 5.9};
        double acceptedDelta = 1.0;
        new TrilaterationTest(positions, distances, expectedPosition, acceptedDelta);
    }
}