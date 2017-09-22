package nars.control;

import jcog.Util;
import jcog.list.FasterList;
import jcog.math.RecycledSummaryStatistics;
import jcog.pri.Prioritized;
import nars.NAR;
import nars.task.ITask;
import nars.task.NativeTask;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static jcog.Util.RouletteControl.*;

/**
 * high-level reasoner control parameters
 */
public enum MetaGoal {

    /**
     * neg: perceived as input, can be measured for partial and complete tasks
     * in their various stages of construction.
     * <p>
     * information is considered negative value by default (spam).
     * see: http://mattmahoney.net/costofai.pdf
     * <p>
     * satisfying other goals is necessary to compensate for the
     * perceptual cost.
     */
    Perceive,

    /**
     * pos: accepted as belief
     */
    Believe,

    /**
     * pos: accepted as goal
     */
    Desire,

    /**
     * pos: anwers a question
     */
    Answer,

    /**
     * pos: actuated a goal concept
     */
    Action,

    /**
     * pos: prediction confirmed a sensor input
     */
    Accurate,

    /**
     * neg: contradicted a sensor input
     */
    Inaccurate;

    /**
     * goal and goalSummary instances correspond to the possible MetaGoal's enum
     * however summary has an additional instance for the global normalization step
     */
    public static void update(FasterList<Cause> causes, float[] goal, RecycledSummaryStatistics[] goalSummary) {

        for (RecycledSummaryStatistics r : goalSummary) {
            //double m = r.getMax();
            r.clear();
            //r.setMax(m * 0.9f);
        }

        int cc = causes.size();
        for (int i = 0, causesSize = cc; i < causesSize; i++) {
            causes.get(i).commit(goalSummary);
        }

        final float epsilon = 0.01f;

        //final float LIMIT = +1f;
        final float momentum = 0.9f;

        int goals = goal.length;
        float[] goalMagnitude = new float[goals];
        for (int i = 0; i < goals; i++) {
            float m = goalSummary[i].magnitude();
            goalMagnitude[i] = Util.equals(m, 0, epsilon) ? 1 : m;
        }

        RecycledSummaryStatistics goalPreNorms = goalSummary[goals /* the extra one */];

        for (int i = 0, causesSize = cc; i < causesSize; i++) {
            Cause c = causes.get(i);
            float v = 0;
            //mix the weighted current values of each purpose, each independently normalized against the values (the reason for calculating summary statistics in previous step)
            for (int j = 0; j < goals; j++) {
                v += goal[j] * c.goalValue[j].current / goalMagnitude[j];
            }
            goalPreNorms.accept(c.valuePreNorm = v);
        }

        float max = (float) goalPreNorms.getMax();
        float min = (float) goalPreNorms.getMin();

        if (Util.equals(max, min, epsilon)) {
            causes.forEach(Cause::setValueZero); //flat
        } else {

//            boolean bipolar = !(min <= 0 ^ max < 0);
//            float mid = bipolar ? 0 : (max+min)/2f;
//            float rangePos = max - mid;
//            float rangeNeg = mid - min;

            float valueMag =
                    //Math.max(Math.abs(max), Math.abs(min)); //normalized to absolute range
                    1; //no normalization

            for (int i = 0, causesSize = cc; i < causesSize; i++) {
                Cause c = causes.get(i);

                float n = c.valuePreNorm;
//                float v = n >= 0 ?
//                        (n - mid) / rangePos :
//                        (mid - n) / rangeNeg
//                        ;
                float v = n / valueMag; //normalize to -1..+1
//
                float nextValue =
                        Util.lerp(momentum, v, c.value());

                c.setValue(nextValue);
            }
        }


//        System.out.println("WORST");
//        causes.stream().map(x -> PrimitiveTuples.pair(x, x.value())).sorted(
//                (x,y) -> Doubles.compare(x.getTwo(), y.getTwo())
//        ).limit(20).forEach(x -> {
//            System.out.println("\t" + x);
//        });
//        System.out.println();

    }

    public static void value(MetaGoal p, short[] effects, float strength, NAR nar) {
        value(p, effects, strength, nar.causes);
    }

    /**
     * learn that the given effects have a given value
     */
    public static void value(MetaGoal p, short[] effects, float strength, FasterList<Cause> causes) {

        //assert(strength >= 0);
        if (Math.abs(strength) < Prioritized.EPSILON) return; //no change

        int numCauses = effects.length;

        float vPer =
                strength / numCauses; //flat
        //strength;

        for (int i = 0; i < numCauses; i++) {
            short c = effects[i];
            Cause cc = causes.get(c);
            if (cc == null)
                continue; //ignore, maybe some edge case where the cause hasnt been registered yet?
            /*assert(cc!=null): c + " missing from: " + n.causes.size() + " causes";*/

            //float vPer = (((float) (i + 1)) / sum) * value; //linear triangle increasing to inc, warning this does not integrate to 100% here
            if (vPer != 0) {
                cc.learn(p, vPer);
            }
        }
    }

    /**
     * contributes the value to a particular goal in a cause's goal vector
     */
    public void learn(Traffic[] goalValue, float v) {
        goalValue[ordinal()].addAndGet(v);
    }

    /**
     * sets the desired level for a particular MetaGoal.
     * the value may be positive or negative indicating
     * its desirability or undesirability.
     * the absolute value is considered relative to the the absolute values
     * of the other MetaGoal's
     */
    public void want(float[] wants, float v) {
        wants[ordinal()] = v;
    }

    /**
     * creates a new goal vector
     */
    public static float[] newWants() {
        return new float[MetaGoal.values().length];
    }


    public static final Logger logger = LoggerFactory.getLogger(MetaGoal.class);

    /**
     * estimate the priority factor determined by the current value of priority-affecting causes
     */
    public static float privaluate(FasterList<Cause> values, short[] effect) {

        int effects = effect.length;
        if (effects == 0) return 0;

        float value = 0;
        for (short c : effect) {
            Cause cause = values.getSafe(c);
            if (cause == null) {
                logger.error("cause id={} missing", c);
                continue;
            }

            if (cause.valuePrioritizes)
                value += cause.value();
        }


        return value / effects;
    }

    public static void cause(FasterList<Causable> causables, NAR nar) {
        int cc = causables.size();
        if (cc == 0)
            return;

        long dt = nar.time.sinceLast();


        /** factor to multiply the mean iteration to determine demand for the next cycle.
         *  this allows the # of iterations to continually increase.
         either:
         1) a cause will not be able to meet this demand and this value
         will remain relatively constant
         2) a cause will hit a point of diminishing returns where increasing
         the demand does not yield any more value, and the system should
         continue to tolerate this dynamic balance
         3) a limit to the # of iterations that a cause is able to supply in a cycle
         has not been determined (but is limited by the hard limit factor for safety).
         */
        float ITERATION_DEMAND_GROWTH = 1.75f;

        final int ITERATION_DEMAND_MAX = 64 * 1024;


        final long targetCycleTimeNS = 25 * 1000000; //x ms
        final float targetCycleTimeNSperEach = targetCycleTimeNS / cc;

        //Benefit to cost ratio (estimate)
        //https://en.wikipedia.org/wiki/Benefit%E2%80%93cost_ratio
        //BCR = Discounted value of incremental benefits ÷ Discounted value of incremental costs
        float[] bcr = new float[cc];
        float[] granular = new float[cc];
        int[] iterLimit = new int[cc];
        for (int i = 0, causablesSize = cc; i < causablesSize; i++) {
            Causable c = causables.get(i);
            float time = (float) Math.max(1, c.exeTimeNS());
            float iters = (float) Math.max(1, c.iterationsMean());
            iterLimit[i] = Math.min(ITERATION_DEMAND_MAX, Math.round(iters * ITERATION_DEMAND_GROWTH));

            bcr[i] = (float) (c.value() / time);
            granular[i] = iters / (time / (targetCycleTimeNSperEach));
        }


        int[] iter = new int[cc];
        Arrays.fill(iter, 1);

        int SAMPLING_FACTOR = 8;
        final int[] throttle = {
            cc * SAMPLING_FACTOR
        };

        Util.decideRoulette(cc, (c) -> bcr[c], nar.random(), (j) -> {

            float a = Math.max(1, granular[j] * bcr[j]);
            iter[j] += a;
            boolean changedWeights = false;
            int li = iterLimit[j];
            if (iter[j] > li) {
                iter[j] = li;
                bcr[j] = 0;
                changedWeights = true;
            }

            if (throttle[0]-- <= 0) return STOP;
            else {
                return changedWeights ? WEIGHTS_CHANGED : CONTINUE;
            }
        });

        for (int i = 0, causablesSize = cc; i < causablesSize; i++)
            if (iter[i] > 0)
                nar.input(new InvokeCause(causables.get(i), iter[i]));
    }

    final private static class InvokeCause extends NativeTask {

        public final Causable cause;
        public final int iterations;

        private InvokeCause(Causable cause, int iterations) {
            assert (iterations > 0);
            this.cause = cause;
            this.iterations = iterations;
        }
        //TODO deadline? etc

        @Override
        public String toString() {
            return cause + ":" + iterations + "x";
        }

        @Override
        public @Nullable Iterable<? extends ITask> run(NAR n) {
            cause.run(n, iterations);
            return null;
        }
    }


}