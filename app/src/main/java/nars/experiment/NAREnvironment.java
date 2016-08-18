package nars.experiment;

import nars.*;
import nars.budget.UnitBudget;
import nars.budget.merge.BudgetMerge;
import nars.concept.Concept;
import nars.nar.Default;
import nars.task.GeneratedTask;
import nars.task.MutableTask;
import nars.term.Term;
import nars.util.data.list.FasterList;
import nars.util.math.FirstOrderDifferenceFloat;
import nars.util.math.PolarRangeNormalizedFloat;
import nars.util.math.RangeNormalizedFloat;
import nars.util.signal.Emotion;
import nars.util.signal.MotorConcept;
import nars.util.signal.SensorConcept;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static nars.$.t;
import static nars.agent.NAgent.varPct;
import static nars.nal.Tense.ETERNAL;
import static nars.util.Texts.n4;

/**
 * explicit management of sensor concepts and motor functions
 */
abstract public class NAREnvironment {


    static final Logger logger = LoggerFactory.getLogger(NAREnvironment.class);

    public final SensorConcept happy;
    private final float reinforcementAttention;
    public final SensorConcept joy;
    public final RangeNormalizedFloat rewardNormalized;
    public NAR nar;

    public final List<SensorConcept> sensors = $.newArrayList();
    public final List<MotorConcept> actions = $.newArrayList();

    public float alpha, gamma, epsilonProbability = 0.02f;

    @Deprecated public float gammaEpsilonFactor = 0.5f;

    public float rewardValue;
    private final FasterList<Task> predictors = $.newArrayList();
    private final boolean trace = true;

    int ticksBeforeObserve, ticksBeforeDecide;
    protected long now;
    private long stopTime;
    private NARLoop loop;


    public NAREnvironment(NAR nar) {
        this.nar = nar;
        alpha = this.nar.confidenceDefault(Symbols.BELIEF);
        gamma = this.nar.confidenceDefault(Symbols.GOAL);

        float rewardGamma =
                1.0f
                //gamma
        ;

        this.reinforcementAttention = gamma;

        float rewardConf = alpha;

        rewardNormalized = new RangeNormalizedFloat(() -> rewardValue);

        happy = new SensorConcept("(happy)", nar,
                rewardNormalized,
                (x) -> t(x, rewardConf)
        );
        predictors.add(happy.desire($.t(1f, rewardGamma)));


        joy = new SensorConcept("(joy)", nar,
                new PolarRangeNormalizedFloat(
                    new FirstOrderDifferenceFloat(
                        ()->nar.time(), () -> rewardValue
                    )
                ),
                (x) -> t(x, rewardConf)
        );

    }

    /**
     * install motors and sensors in the NAR
     */
    abstract protected void init(NAR n);


    /**
     * interpret motor states into env actions
     */
    protected abstract float act();

    protected void next() {

        for (int i = 0; i < ticksBeforeObserve; i++)
            nar.clock.tick();

        reinforce();

        for (int i = 0; i < ticksBeforeDecide; i++)
            nar.clock.tick();



        now = nar.time();

        rewardValue = act();

        if (trace)
            System.out.println(summary());


        if (now >= stopTime) {
            if (loop!=null) {
                loop.stop();
                this.loop = null;
            }
        }
    }

    public String summary() {

        @NotNull Emotion emotion = nar.emotion;

        //long now = nar.time();


        return
//                 + "rwrd=[" +
//                     n4( sad.beliefs().truth(now).motivation() )
//                             + "," +
//                     n4( happy.beliefs().truth(now).motivation() )
//                 + "] "
                  "rwrd=" + n4(rewardValue) + "\t" +
                  "hapy=" + n4(emotion.happy()) + " "
                + "busy=" + n4(emotion.busy.getSum()) + " "
                + "lern=" + n4(emotion.learning()) + " "
                + "strs=" + n4(emotion.stress.getSum()) + " "
                + "alrt=" + n4(emotion.alert.getSum()) + " "
                + " var=" + n4( varPct(nar) ) + " "
                   + "\t" + nar.index.summary()

//                + "," + dRewardPos.belief(nar.time()) +
//                "," + dRewardNeg.belief(nar.time());
                ;

    }


    protected void mission() {
        int dt = 1 + ticksBeforeObserve + ticksBeforeDecide;


        @NotNull Term what = $.$("?w"); //#w

        predictors.add(
                //what will soon imply reward R
                nar.ask($.impl(what, dt, happy.term()), '?', ETERNAL)
        );

        //what co-occurs with reward R
        predictors.add(
                nar.ask($.conj(what, dt, happy.term()), '?', ETERNAL)
        );
        predictors.add(
                nar.ask($.conj(what, dt, happy.term()), '?', now)
        );
        predictors.add( //+2 cycles ahead
                nar.ask($.conj(what, dt*2, happy.term()), '?', now)
        );


        for (Concept x : actions) {

            //quest for each action
            predictors.add(nar.ask(x, '@', ETERNAL));
            predictors.add(nar.ask(x, '@', now));

            //does action A co-occur with reward R?
            predictors.add(
                    nar.ask($.conj(x.term(), dt, happy.term()), '?', ETERNAL)
            );
            predictors.add(
                    nar.ask($.conj(x.term(), dt, happy.term()), '?', now)
            );



        }

        System.out.println(predictors);

    }

    public NARLoop run(final int cycles, int frameDelayMS) {

        ticksBeforeDecide = 0;
        ticksBeforeObserve = 0;

        this.stopTime = nar.time() + cycles;

        System.gc();

        init(nar);

        mission();

        nar.onFrame(nn -> next());

        this.loop = new NARLoop(nar, frameDelayMS);

        return loop;

//        nar.next(); //step one for any mission() results to process first
//
//        for (int t = 0; t < cycles; t++) {
//            next();
//
//            if (frameDelayMS > 0)
//                Util.pause(frameDelayMS);
//        }
    }

    protected void reinforce() {
        long now = nar.time();

        //System.out.println(nar.conceptPriority(reward) + " " + nar.conceptPriority(dRewardSensor));
        if (reinforcementAttention > 0) {

            //boost(happy);
            //boost(happy); //boosted by the (happy)! task that is boosted below
            //boost(sad);


            for (MotorConcept c : actions) {
                if (nar.random.nextFloat() < epsilonProbability) {
                    nar.inputLater(new GeneratedTask(c, '!',
                            $.t(nar.random.nextFloat()
                            //Math.random() > 0.5f ? 1f : 0f
                            , gamma * gammaEpsilonFactor))

                    //in order to auto-destruct corectly, the task needs to remove itself from the taskindex too
                    /* {
                        @Override
                        public boolean onConcept(@NotNull Concept c) {
                            if (super.onConcept(c)) {
                                //self-destruct later
                                nar.runLater(()->{
                                    delete();
                                });
                                return true;
                            }
                            return false;
                        }
                    }*/.
                            present(now).log("Curiosity"));
                }
                boost(c);
            }

            for (Task x : predictors)
                boost(x);


        }


    }

    @Nullable
    protected Concept boost(Concept c) {

        new NAR.Activation(UnitBudget.One, c, nar, alpha) {

            @Override
            public void activate(@NotNull NAR nar, float activation) {
                linkTermLinks(c, alpha, nar);
                super.activate(nar, activation);
            }
        };

        return c;
        //return c;
        //return nar.activate(c, null);
    }


    private void boost(@NotNull Task t) {
        BudgetMerge.max.apply(t.budget(), UnitBudget.One, reinforcementAttention);
        if (t.isDeleted())
            throw new RuntimeException();

        float REINFORCEMENT_DURABILITY = 0.9f;
        if (t.occurrence() != ETERNAL) {
            nar.inputLater(new GeneratedTask(t.term(), t.punc(), t.truth()).time(now, now)
                    .budget(reinforcementAttention, REINFORCEMENT_DURABILITY, 0.9f).log("Predictor"));
        } else {
            //re-use existing eternal task
            NAR.Activation a = new NAR.Activation(t, nar, 1f) {
                @Override
                public void linkTerms(Concept src, Term[] tgt, float scale, float minScale, @NotNull NAR nar) {
                    super.linkTerms(src, tgt, scale, minScale, nar);

                    linkTermLinks(src, scale, nar);
                }
            };
        }

    }
}
