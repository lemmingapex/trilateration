package com.lemmingapex.trilateration;

import static org.junit.Assert.assertEquals;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;

/**
 * Test class which is initialized with different predefined test cases.
 * Test was refactored from @author scott
 *
 * @author burfi
 */
public class TrilaterationTest {

	private double[][] positions;
	private double[] distances;
	private double[] expectedPosition;
	private double acceptedDelta;
	private StringBuilder output;

	private RealVector linearCalculatedPosition;
	private Optimum nonLinearOptimum;

	public TrilaterationTest(double[][] positions, double[] distances, double[] expectedPosition, double acceptedDelta) {
		this.positions = positions;
		this.distances = distances;
		this.expectedPosition = expectedPosition;
		this.acceptedDelta = acceptedDelta;
		testCase();
		outputResult();
		compareExpectedAndCalculatedResults();
	}

	private void testCase() {
		TrilaterationFunction trilaterationFunction = new TrilaterationFunction(positions, distances);
		LinearLeastSquaresSolver lSolver = new LinearLeastSquaresSolver(trilaterationFunction);
		NonLinearLeastSquaresSolver nlSolver = new NonLinearLeastSquaresSolver(trilaterationFunction, new LevenbergMarquardtOptimizer());

		linearCalculatedPosition = lSolver.solve();
		nonLinearOptimum = nlSolver.solve();
	}

	private void outputResult() {
		output = new StringBuilder();
		printDoubleArray("expectedPosition: ", expectedPosition);
		printDoubleArray("linear calculatedPosition: ", linearCalculatedPosition.toArray());
		printDoubleArray("non-linear calculatedPosition: ", nonLinearOptimum.getPoint().toArray());
		output.append("numberOfIterations: ").append(nonLinearOptimum.getIterations()).append("\n");
		output.append("numberOfEvaluations: ").append(nonLinearOptimum.getEvaluations()).append("\n");
		try {
			RealVector standardDeviation = nonLinearOptimum.getSigma(0);
			printDoubleArray("standardDeviation: ", standardDeviation.toArray());
			output.append("Norm of deviation: ").append(standardDeviation.getNorm()).append("\n");
			RealMatrix covarianceMatrix = nonLinearOptimum.getCovariances(0);
			output.append("covarianceMatrix: ").append(covarianceMatrix).append("\n");
		} catch (SingularMatrixException e) {
			System.err.println(e.getMessage());
		}

		System.out.println(output.toString());
	}

	private void compareExpectedAndCalculatedResults() {
		double[] calculatedPosition = nonLinearOptimum.getPoint().toArray();
		for (int i = 0; i < calculatedPosition.length; i++) {
			assertEquals(expectedPosition[i], calculatedPosition[i], acceptedDelta);
		}
	}

	private void printDoubleArray(String tag, double[] values) {
		output.append(tag);
		for (double p : values) {
			output.append(p).append(" ");
		}
		output.append("\n");
	}
}