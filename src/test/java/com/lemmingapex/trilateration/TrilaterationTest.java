package com.lemmingapex.trilateration;

import static org.junit.Assert.assertEquals;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.junit.Test;

public class TrilaterationTest {

	@Test
	public void trilateration1DExact1() throws Exception {
		double[][] positions = new double[][] { { 1.0 }, { 2.0 }, { 3.0 } };
		double[] distances = new double[] { 1.1, 0.1, 0.9 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.1 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration1DExact2() throws Exception {
		double[][] positions = new double[][] { { 1000.0 }, { 2000.0 }, { 3000.0 } };
		double[] distances = new double[] { 1100, 100, 900 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2100.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration1DInexact() throws Exception {
		double[][] positions = new double[][] { { 1000.0 }, { 2000.0 }, { 3000.0 } };
		double[] distances = new double[] { 1110, 110, 910 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2100.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 30, optimum);
	}

	@Test
	public void trilateration2DExact1() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 3.0, 1.0 }, { 2.0, 2.0 } };
		double[] distances = new double[] { 1.0, 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration2DZeroDistance() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 2.0, 1.0 } };
		double[] distances = new double[] { 0.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 1.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration2DExact2() throws Exception {
		double[][] positions = new double[][] { { 0.0, 0.0 }, { -1.0, 0.0 }, { 0.0, -1.0 } };
		double[] distances = new double[] { Math.sqrt(2.0), 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { -1.0, -1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration2DExact3() throws Exception {
		double[][] positions = new double[][] { { 0.0, 0.0 }, { 1000.0, 0.0 }, { 0.0, 1000.0 } };
		double[] distances = new double[] { Math.sqrt(2.0) * 1000.0, 1000.0, 1000.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 1000.0, 1000.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration2DExact4() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 1.0, 3.0 }, { 8.0, 8.0 }, { 2.0, 2.0 } };
		double[] distances = new double[] { 5.0, 5.0, 6.36, 3.9 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 5.9, 2.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.01, optimum);
	}

	@Test
	public void trilateration2DExact5() throws Exception {
		double[][] positions = new double[][] { { 5.0, -6.0 }, { 13.0, -15.0 }, { 21.0, -3.0 } };
		double[] distances = new double[] { 8.06, 13.97, 23.32 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { -0.6, -11.8 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.01, optimum);
	}

	@Test
	public void trilateration2DInexact1() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 3.0, 1.0 }, { 2.0, 2.0 } };
		double[] distances = new double[] { 0.9, 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.1, optimum);
	}

	@Test
	public void trilateration2DInexact2() throws Exception {
		double[][] positions = new double[][] { { 5.0, -6.0 }, { 13.0, -15.0 }, { 21.0, -3.0 }, { 12.42, -21.2 } };
		double[] distances = new double[] { 8.06, 13.97, 23.32, 15.31 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { -0.6, -11.8 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 1.0, optimum);
	}

	@Test
	public void trilateration2DNonIntersecting() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 3.0, 1.0 }, { 2.0, 2.0 } };
		double[] distances = new double[] { 0.5, 0.5, 0.5 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.25, optimum);
	}

	@Test
	public void trilateration2DOverIntersecting() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 3.0, 1.0 }, { 2.0, 2.0 } };
		double[] distances = new double[] { 2.0, 2.0, 2.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 2.0, optimum);
	}

	@Test
	public void trilateration2DDegenerateCase1() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 1.0, 1.0 }, { 3.0, 1.0 } };
		double[] distances = new double[] { 1.0, 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.5, optimum);
	}

	@Test
	public void trilateration2DDegenerateCase2() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 1.0, 1.0 }, { 1.0, 1.0 } };
		double[] distances = new double[] { 1.0, 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 1.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.5, optimum);
	}

	@Test
	public void trilateration2DUnderdertermined() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0 }, { 3.0, 1.0 } };
		double[] distances = new double[] { 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.5, optimum);
	}

	@Test
	public void trilateration3DExact() throws Exception {
		double[][] positions = new double[][] { { 1.0, 1.0, 1.0 }, { 3.0, 1.0, 1.0 }, { 2.0, 2.0, 1.0 } };
		double[] distances = new double[] { 1.0, 1.0, 1.0 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 2.0, 1.0, 1.0 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 0.0001, optimum);
	}

	@Test
	public void trilateration3DInexact() throws Exception {
		double[][] positions = new double[][] { { 0.0, 0.0, 0.0 }, { 8.84, 4.57, 12.59 }, { 0.0, -8.84, 8.84 }, { 10.72, -8.96, 8.84 } };
		double[] distances = new double[] { 8.84, 8.84, 8.84, 8.84 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 5.2, -1.2, 7.7 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 1.0, optimum);
	}

	@Test
	public void trilateration4DInexact() throws Exception {
		double[][] positions = new double[][] { { 0.0, 0.0, 0.0, 0.0 }, { 8.84, 4.57, 12.59, 9.2 }, { 0.0, -8.84, 8.84, 9.2 }, { 10.72, -8.96, 8.84, 9.2 } };
		double[] distances = new double[] { 8.84, 8.84, 8.84, 8.84 };

		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		double[] expectedPosition = new double[] { 5.2, -1.5, 7.7, 5.9 };
		Optimum optimum = solver.solve();
		testResults(expectedPosition, 1.0, optimum);
	}

	private void testResults(double[] expectedPosition, final double delta, Optimum optimum) {

		double[] calculatedPosition = optimum.getPoint().toArray();

		int numberOfIterations = optimum.getIterations();
		int numberOfEvaluations = optimum.getEvaluations();

		StringBuilder output = new StringBuilder("expectedPosition: ");
		for (int i = 0; i < expectedPosition.length; i++) {
			output.append(expectedPosition[i]).append(" ");
		}
		output.append("\n");
		output.append("calculatedPosition: ");
		for (int i = 0; i < calculatedPosition.length; i++) {
			output.append(calculatedPosition[i]).append(" ");
		}
		output.append("\n");

		output.append("numberOfIterations: ").append(numberOfIterations).append("\n");
		output.append("numberOfEvaluations: ").append(numberOfEvaluations).append("\n");
		try {
			RealVector standardDeviation = optimum.getSigma(0);
			output.append("standardDeviation: ").append(standardDeviation).append("\n");
			RealMatrix covarianceMatrix = optimum.getCovariances(0);
			output.append("covarianceMatrix: ").append(covarianceMatrix).append("\n");
		} catch (SingularMatrixException e) {
			System.err.println(e.getMessage());
		}

		System.out.println(output.toString());

		// expected == calculated?
		for (int i = 0; i < calculatedPosition.length; i++) {
			assertEquals(expectedPosition[i], calculatedPosition[i], delta);
		}
	}
}
