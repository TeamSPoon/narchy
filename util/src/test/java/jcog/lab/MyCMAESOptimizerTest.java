package jcog.lab;

import jcog.lab.util.MyCMAESOptimizer;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomAdaptor;
import org.apache.commons.math3.util.FastMath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class MyCMAESOptimizerTest {

    private static final int DIM = 13;
    private static final int LAMBDA = 4 + (int)(3.* FastMath.log(DIM));









































































    @Test
    void testRosen() {
        double[] startPoint = point(DIM,0.1);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,1.0),0.0);
        doTest(new Rosen(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new Rosen(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testMaximize() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),1.0);
        doTest(new MinusElli(), startPoint, insigma, boundaries,
                GoalType.MAXIMIZE, LAMBDA, true, 0, 1.0-1e-13,
                2e-10, 5e-6, 100000, expected);
        doTest(new MinusElli(), startPoint, insigma, boundaries,
                GoalType.MAXIMIZE, LAMBDA, false, 0, 1.0-1e-13,
                2e-10, 5e-6, 100000, expected);
        boundaries = boundaries(DIM,-0.3,0.3);
        startPoint = point(DIM,0.1);
        doTest(new MinusElli(), startPoint, insigma, boundaries,
                GoalType.MAXIMIZE, LAMBDA, true, 0, 1.0-1e-13,
                2e-10, 5e-6, 200000, expected);
    }

    @Test
    void testEllipse() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Elli(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new Elli(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testElliRotated() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new ElliRotated(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new ElliRotated(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testCigar() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Cigar(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 200000, expected);
        doTest(new Cigar(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testCigarWithBoundaries() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = boundaries(DIM, -1e100, Double.POSITIVE_INFINITY);
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Cigar(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 200000, expected);
        doTest(new Cigar(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testTwoAxes() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new TwoAxes(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 200000, expected);
        doTest(new TwoAxes(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, false, 0, 1e-13,
                1e-8, 1e-3, 200000, expected);
    }

    @Test
    void testCigTab() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.3);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new CigTab(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 5e-5, 100000, expected);
        doTest(new CigTab(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 5e-5, 100000, expected);
    }

    @Test
    void testSphere() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Sphere(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new Sphere(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testTablet() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Tablet(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new Tablet(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testDiffPow() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new DiffPow(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 10, true, 0, 1e-13,
                1e-8, 1e-1, 100000, expected);
        doTest(new DiffPow(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 10, false, 0, 1e-13,
                1e-8, 2e-1, 100000, expected);
    }

    @Test
    void testSsDiffPow() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new SsDiffPow(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 10, true, 0, 1e-13,
                1e-4, 1e-1, 200000, expected);
        doTest(new SsDiffPow(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 10, false, 0, 1e-13,
                1e-4, 1e-1, 200000, expected);
    }

    @Test
    void testAckley() {
        double[] startPoint = point(DIM,1.0);
        double[] insigma = point(DIM,1.0);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Ackley(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, true, 0, 1e-13,
                1e-9, 1e-5, 100000, expected);
        doTest(new Ackley(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, false, 0, 1e-13,
                1e-9, 1e-5, 100000, expected);
    }

    @Test
    void testRastrigin() {
        double[] startPoint = point(DIM,0.1);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,0.0),0.0);
        doTest(new Rastrigin(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, (int)(200*FastMath.sqrt(DIM)), true, 0, 1e-13,
                1e-13, 1e-6, 200000, expected);
        doTest(new Rastrigin(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, (int)(200*FastMath.sqrt(DIM)), false, 0, 1e-13,
                1e-13, 1e-6, 200000, expected);
    }

    @Test
    void testConstrainedRosen() {
        double[] startPoint = point(DIM, 0.1);
        double[] insigma = point(DIM, 0.1);
        double[][] boundaries = boundaries(DIM, -1, 2);
        PointValuePair expected =
                new PointValuePair(point(DIM,1.0),0.0);
        doTest(new Rosen(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, true, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
        doTest(new Rosen(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, 2*LAMBDA, false, 0, 1e-13,
                1e-13, 1e-6, 100000, expected);
    }

    @Test
    void testDiagonalRosen() {
        double[] startPoint = point(DIM,0.1);
        double[] insigma = point(DIM,0.1);
        double[][] boundaries = null;
        PointValuePair expected =
                new PointValuePair(point(DIM,1.0),0.0);
        doTest(new Rosen(), startPoint, insigma, boundaries,
                GoalType.MINIMIZE, LAMBDA, false, 1, 1e-13,
                1e-10, 1e-4, 1000000, expected);
    }

    @Test
    void testMath864() {
        final double[] sigma = { 1e-1 };
        final MyCMAESOptimizer optimizer
                = new MyCMAESOptimizer(30000, 0, true, 10,
                0, rng(), false, null, 5, sigma);
        final MultivariateFunction fitnessFunction = new MultivariateFunction() {
            @Override
            public double value(double[] parameters) {
                final double target = 1;
                final double error = target - parameters[0];
                return error * error;
            }
        };

        final double[] start = { 0 };
        final double[] lower = { -1e6 };
        final double[] upper = { 1.5 };
        final double[] result = optimizer.optimize(new MaxEval(10000),
                new ObjectiveFunction(fitnessFunction),
                GoalType.MINIMIZE,
                new InitialGuess(start),
                new SimpleBounds(lower, upper)).getPoint();
        assertTrue(
                result[0] <= upper[0], ()->"Out of bounds (" + result[0] + " > " + upper[0] + ")");
    }

    /**
     * Cf. MATH-867
     */
    @Test
    void testFitAccuracyDependsOnBoundary() {

        double[] sigma1 = {1e-1};
        MyCMAESOptimizer optimizer
                = new MyCMAESOptimizer(30000, 0, true, 10,
                0, rng(), false, null, 5, sigma1);
        final MultivariateFunction fitnessFunction = new MultivariateFunction() {
            @Override
            public double value(double[] parameters) {
                final double target = 11.1;
                final double error = target - parameters[0];
                return error * error;
            }
        };

        final double[] start = { 1 };

        
        PointValuePair result = optimizer.optimize(new MaxEval(100000),
                new ObjectiveFunction(fitnessFunction),
                GoalType.MINIMIZE,
                SimpleBounds.unbounded(1),
                new InitialGuess(start));
        final double resNoBound = result.getPoint()[0];

        
        final double[] lower = { -20 };
        final double[] upper = { 5e16 };
        final double[] sigma2 = { 10 };
        optimizer
                = new MyCMAESOptimizer(30000, 0, true, 10,
                0, rng(), false, null, 5, sigma2);

        result = optimizer.optimize(new MaxEval(100000),
                new ObjectiveFunction(fitnessFunction),
                GoalType.MINIMIZE,
                new InitialGuess(start),
                new SimpleBounds(lower, upper));
        final double resNearLo = result.getPoint()[0];

        
        lower[0] = -5e16;
        upper[0] = 20;
        result = optimizer.optimize(new MaxEval(100000),
                new ObjectiveFunction(fitnessFunction),
                GoalType.MINIMIZE,
                new InitialGuess(start),
                new SimpleBounds(lower, upper));
        final double resNearHi = result.getPoint()[0];

        
        
        

        
        
        assertEquals(resNoBound, resNearLo, 1e-3);
        assertEquals(resNoBound, resNearHi, 1e-3);
    }

    /**
     * @param func Function to optimize.
     * @param startPoint Starting point.
     * @param inSigma Individual input sigma.
     * @param boundaries Upper / lower point limit.
     * @param goal Minimization or maximization.
     * @param lambda Population size used for offspring.
     * @param isActive Covariance update mechanism.
     * @param diagonalOnly Simplified covariance update.
     * @param stopValue Termination criteria for optimization.
     * @param fTol Tolerance relative error on the objective function.
     * @param pointTol Tolerance for checking that the optimum is correct.
     * @param maxEvaluations Maximum number of evaluations.
     * @param expected Expected point / value.
     */
    private void doTest(MultivariateFunction func,
                        double[] startPoint,
                        double[] inSigma,
                        double[][] boundaries,
                        GoalType goal,
                        int lambda,
                        boolean isActive,
                        int diagonalOnly,
                        double stopValue,
                        double fTol,
                        double pointTol,
                        int maxEvaluations,
                        PointValuePair expected) {
        int dim = startPoint.length;
        
        MyCMAESOptimizer optim = new MyCMAESOptimizer(30000, stopValue, isActive, diagonalOnly,
                0, rng(), false, null, lambda, inSigma);
        PointValuePair result = boundaries == null ?
                optim.optimize(new MaxEval(maxEvaluations),
                        new ObjectiveFunction(func),
                        goal,
                        new InitialGuess(startPoint),
                        SimpleBounds.unbounded(dim)) :
                optim.optimize(new MaxEval(maxEvaluations),
                        new ObjectiveFunction(func),
                        goal,
                        new SimpleBounds(boundaries[0],
                                boundaries[1]),
                        new InitialGuess(startPoint));

        
        assertEquals(expected.getValue(), result.getValue(), fTol);
        for (int i = 0; i < dim; i++) {
            assertEquals(expected.getPoint()[i], result.getPoint()[i], pointTol);
        }

        assertTrue(optim.getIterations() > 0);
    }

    private static RandomAdaptor rng() {
        return new RandomAdaptor(new MersenneTwister(1));
    }

    private static double[] point(int n, double value) {
        double[] ds = new double[n];
        Arrays.fill(ds, value);
        return ds;
    }

    private static double[][] boundaries(int dim,
                                         double lower, double upper) {
        double[][] boundaries = new double[2][dim];
        for (int i = 0; i < dim; i++) {
            boundaries[0][i] = lower;
        }
        for (int i = 0; i < dim; i++) {
            boundaries[1][i] = upper;
        }
        return boundaries;
    }

    private static class Sphere implements MultivariateFunction {

        @Override
        public double value(double[] x) {
            double f = 0;
            for (int i = 0; i < x.length; ++i) {
                f += x[i] * x[i];
            }
            return f;
        }
    }

    private static class Cigar implements MultivariateFunction {
        private double factor;

        Cigar() {
            this(1e3);
        }

        Cigar(double axisratio) {
            factor = axisratio * axisratio;
        }

        @Override
        public double value(double[] x) {
            double f = x[0] * x[0];
            for (int i = 1; i < x.length; ++i) {
                f += factor * x[i] * x[i];
            }
            return f;
        }
    }

    private static class Tablet implements MultivariateFunction {
        private double factor;

        Tablet() {
            this(1e3);
        }

        Tablet(double axisratio) {
            factor = axisratio * axisratio;
        }

        @Override
        public double value(double[] x) {
            double f = factor * x[0] * x[0];
            for (int i = 1; i < x.length; ++i) {
                f += x[i] * x[i];
            }
            return f;
        }
    }

    private static class CigTab implements MultivariateFunction {
        private double factor;

        CigTab() {
            this(1e4);
        }

        CigTab(double axisratio) {
            factor = axisratio;
        }

        @Override
        public double value(double[] x) {
            int end = x.length - 1;
            double f = x[0] * x[0] / factor + factor * x[end] * x[end];
            for (int i = 1; i < end; ++i) {
                f += x[i] * x[i];
            }
            return f;
        }
    }

    private static class TwoAxes implements MultivariateFunction {

        private double factor;

        TwoAxes() {
            this(1e6);
        }

        TwoAxes(double axisratio) {
            factor = axisratio * axisratio;
        }

        @Override
        public double value(double[] x) {
            double f = 0;
            for (int i = 0; i < x.length; ++i) {
                f += (i < x.length / 2 ? factor : 1) * x[i] * x[i];
            }
            return f;
        }
    }

    private static class ElliRotated implements MultivariateFunction {
        private Basis B = new Basis();
        private double factor;

        ElliRotated() {
            this(1e3);
        }

        ElliRotated(double axisratio) {
            factor = axisratio * axisratio;
        }

        @Override
        public double value(double[] x) {
            double f = 0;
            x = B.Rotate(x);
            for (int i = 0; i < x.length; ++i) {
                f += FastMath.pow(factor, i / (x.length - 1.)) * x[i] * x[i];
            }
            return f;
        }
    }

    private static class Elli implements MultivariateFunction {

        private double factor;

        Elli() {
            this(1e3);
        }

        Elli(double axisratio) {
            factor = axisratio * axisratio;
        }

        @Override
        public double value(double[] x) {
            double f = 0;
            for (int i = 0; i < x.length; ++i) {
                f += FastMath.pow(factor, i / (x.length - 1.)) * x[i] * x[i];
            }
            return f;
        }
    }

    private static class MinusElli implements MultivariateFunction {

        @Override
        public double value(double[] x) {
            return 1.0-(new Elli().value(x));
        }
    }

    private static class DiffPow implements MultivariateFunction {

        @Override
        public double value(double[] x) {
            double f = 0;
            for (int i = 0; i < x.length; ++i) {
                f += FastMath.pow(FastMath.abs(x[i]), 2. + 10 * (double) i
                        / (x.length - 1.));
            }
            return f;
        }
    }

    private static class SsDiffPow implements MultivariateFunction {

        @Override
        public double value(double[] x) {
            double f = FastMath.pow(new DiffPow().value(x), 0.25);
            return f;
        }
    }

    private static class Rosen implements MultivariateFunction {

        @Override
        public double value(double[] x) {
            double f = 0;
            for (int i = 0; i < x.length - 1; ++i) {
                f += 1e2 * (x[i] * x[i] - x[i + 1]) * (x[i] * x[i] - x[i + 1])
                        + (x[i] - 1.) * (x[i] - 1.);
            }
            return f;
        }
    }

    private static class Ackley implements MultivariateFunction {
        private double axisratio;

        Ackley(double axra) {
            axisratio = axra;
        }

        Ackley() {
            this(1);
        }

        @Override
        public double value(double[] x) {
            double f = 0;
            double res2 = 0;
            double fac = 0;
            for (int i = 0; i < x.length; ++i) {
                fac = FastMath.pow(axisratio, (i - 1.) / (x.length - 1.));
                f += fac * fac * x[i] * x[i];
                res2 += FastMath.cos(2. * FastMath.PI * fac * x[i]);
            }
            f = (20. - 20. * FastMath.exp(-0.2 * FastMath.sqrt(f / x.length))
                    + FastMath.exp(1.) - FastMath.exp(res2 / x.length));
            return f;
        }
    }

    private static class Rastrigin implements MultivariateFunction {

        private double axisratio;
        private double amplitude;

        Rastrigin() {
            this(1, 10);
        }

        Rastrigin(double axisratio, double amplitude) {
            this.axisratio = axisratio;
            this.amplitude = amplitude;
        }

        @Override
        public double value(double[] x) {
            double f = 0;
            double fac;
            for (int i = 0; i < x.length; ++i) {
                fac = FastMath.pow(axisratio, (i - 1.) / (x.length - 1.));
                if (i == 0 && x[i] < 0) {
                    fac *= 1.;
                }
                f += fac * fac * x[i] * x[i] + amplitude
                        * (1. - FastMath.cos(2. * FastMath.PI * fac * x[i]));
            }
            return f;
        }
    }

    private static class Basis {
        double[][] basis;
        Random rand = new Random(2); 

        double[] Rotate(double[] x) {
            GenBasis(x.length);
            double[] y = new double[x.length];
            for (int i = 0; i < x.length; ++i) {
                y[i] = 0;
                for (int j = 0; j < x.length; ++j) {
                    y[i] += basis[i][j] * x[j];
                }
            }
            return y;
        }

        void GenBasis(int DIM) {
            if (basis != null ? basis.length == DIM : false) {
                return;
            }

            double sp;
            int i, j, k;

            /* generate orthogonal basis */
            basis = new double[DIM][DIM];
            for (i = 0; i < DIM; ++i) {
                /* sample components gaussian */
                for (j = 0; j < DIM; ++j) {
                    basis[i][j] = rand.nextGaussian();
                }
                /* substract projection of previous vectors */
                for (j = i - 1; j >= 0; --j) {
                    for (sp = 0., k = 0; k < DIM; ++k) {
                        sp += basis[i][k] * basis[j][k]; /* scalar product */
                    }
                    for (k = 0; k < DIM; ++k) {
                        basis[i][k] -= sp * basis[j][k]; /* substract */
                    }
                }
                /* normalize */
                for (sp = 0., k = 0; k < DIM; ++k) {
                    sp += basis[i][k] * basis[i][k]; /* squared norm */
                }
                for (k = 0; k < DIM; ++k) {
                    basis[i][k] /= FastMath.sqrt(sp);
                }
            }
        }
    }
}