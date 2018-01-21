package nars;

import nars.task.DerivedTask;
import nars.term.Term;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.eclipse.collections.api.block.procedure.primitive.BooleanProcedure;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NAgentTest {

    static NAR nar() {

        NAR n = NARS.tmp();
        n.termVolumeMax.set(24);
        n.freqResolution.set(0.1f);
        n.confResolution.set(0.02f);
        n.time.dur(1);
        //n.want(MetaGoal.Perceive, -0.1f);

        //n.logWhen(System.out, false, true, true);

        //MetaGoal.Action.set(n.want, 2f);
        //MetaGoal.Desire.set(n.want, 1f);
        //n.freqResolution.set(0.1f);

        //Param.DEBUG = true;
        if (Param.DEBUG) {
            n.onTask(t -> {
                if (t instanceof DerivedTask && t.isGoal()) {
                    System.out.println(t.proof());
                }
            });
        }
        return n;
    }

    @ParameterizedTest
    @ValueSource(strings={"tt", "tf", "ft", "ff"})
    public void testSame(String x) {

        boolean posOrNeg = x.charAt(0) == 't';
        boolean toggleOrPush = x.charAt(1) == 't';

        System.out.println((posOrNeg ? "positive" : " negative") + " and " + (toggleOrPush ? "toggle" : " push"));
        MiniTest a = new ToggleSame(nar(), $.the("t"),
                //$.$safe("t:y"),
                $.$safe("(t,y)"),
                posOrNeg, toggleOrPush);

        a.runSynch(1000);

        assertTrue(a.avgReward() > 0.25f);
        assertTrue(a.dex.getMean() > 0.1f);
    }


    abstract static class MiniTest extends NAgent {
        private final Runnable statPrint;
        public float rewardSum = 0;
        final DescriptiveStatistics dex = new DescriptiveStatistics();

        public MiniTest(NAR n) {
            this(null, n);
        }

        public MiniTest(Term id, NAR n) {
            super(id, n);
            statPrint = n.emotion.printer(System.out);
        }



        @Override
        public void runSynch(int frames) {
            super.runSynch(frames);
            System.out.println(this + " avgReward=" + avgReward() + " dexMean=" + dex.getMean() + " dexMax=" + dex.getMax());
            statPrint.run();
            nar.stats(System.out);
        }

        @Override
        protected float act() {
            float yy = reward();

            rewardSum += yy;
            dex.addValue(dexterity());

            return yy;
        }


        abstract float reward();

        public float avgReward() {
            return rewardSum / (((float) nar.time()) / nar.dur());
        }
    }

    static class ToggleSame extends MiniTest {

        private final boolean posOrNeg;
        private float y;

        public ToggleSame(NAR n, Term env, Term action, boolean posOrNeg, boolean toggleOrPush) {
            super(env, n);
            y = 0;
            this.posOrNeg = posOrNeg;

            BooleanProcedure pushed = (v) -> {
                //System.err.println(n.time() + ": " + v);
                this.y = v ? 1 : -1;
            };
            if (toggleOrPush)
                actionToggle(action, pushed);
            else
                actionPushButton(action, pushed);
        }

        @Override
        float reward() {
            float r = posOrNeg ? y : -y;
            y = 0; //reset
            return r;
        }

    }


//        n.onTask(t->{
//            if (t.isGoal()) {
//                if (t.term().equals(action))
//                    System.out.println(t.start() + ".." + t.end() + "\t" + t.proof());
//            }
//        });

}