package sw.trilateration;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresFactory;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;

/**
 * Solves a Trilateration problem with an instance of a
 * {@link LeastSquaresOptimizer}
 * 
 * @author scott
 * 
 */
public class NonLinearLeastSquaresSolver {

    private final TrilaterationFunction function;
    private final LeastSquaresOptimizer leastSquaresOptimizer;
    
    private final static int MAXNUMBEROFITERATIONS = 1000;

	public NonLinearLeastSquaresSolver(TrilaterationFunction function, LeastSquaresOptimizer leastSquaresOptimizer) {
        this.function = function;
        this.leastSquaresOptimizer = leastSquaresOptimizer;
    }

    public Optimum solve(double[] target, double[] weights, double[] initialPoint) {
    	
    	LeastSquaresProblem leastSquaresProblem = LeastSquaresFactory.create(
                // function to be optimized
				function, 
                // target values at optimal point in least square equation
                // (x0+xi)^2 + (y0+yi)^2 + ri^2 = target[i]
				new ArrayRealVector(target, false), 
				new ArrayRealVector(initialPoint, false), 
				new DiagonalMatrix(weights),
				null, MAXNUMBEROFITERATIONS, MAXNUMBEROFITERATIONS);

        return leastSquaresOptimizer.optimize(leastSquaresProblem);
    }

	public Optimum solve() {
		int numberOfPositions = function.getPositions().length;
		int positionDimension = function.getPositions()[0].length;

		double[] initialPoint = new double[positionDimension];
		// initial point, use average of the vertices
		for (int i = 0; i < function.getPositions().length; i++) {
			double[] vertex = function.getPositions()[i];
			for (int j = 0; j < vertex.length; j++) {
				initialPoint[j] += vertex[j];
			}
		}
		for (int j = 0; j < initialPoint.length; j++) {
			initialPoint[j] /= numberOfPositions;
		}
		
		StringBuilder output = new StringBuilder("initialPoint: ");
		for (int i = 0; i < initialPoint.length; i++) {
			output.append(initialPoint[i]).append(" ");
		}
		System.out.println(output.toString());

		double[] target = new double[numberOfPositions];
		double[] distances = function.getDistances();
		double[] weights = new double[target.length];
		// Weights are inversely proportional to the the square of the distances I think
		for (int i = 0; i < target.length; i++) {
			target[i] = 0.0;
			//weights[i] = 1.0;
			weights[i] = (distances[i] * distances[i]);
		}

		return solve(target, weights, initialPoint);
	}
}