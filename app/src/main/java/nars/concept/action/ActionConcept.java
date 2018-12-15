package nars.concept.action;

import jcog.math.FloatRange;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.attention.AttVectorNode;
import nars.concept.PermanentConcept;
import nars.concept.TaskConcept;
import nars.concept.action.curiosity.CuriosityTask;
import nars.concept.sensor.Sensor;
import nars.control.MetaGoal;
import nars.link.TermLinker;
import nars.table.BeliefTable;
import nars.table.dynamic.SensorBeliefTables;
import nars.table.temporal.RTreeBeliefTable;
import nars.term.Term;
import nars.term.Termed;
import nars.truth.Truth;
import nars.truth.polation.TruthIntegration;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

import static nars.Op.GOAL;
import static nars.truth.TruthFunctions.w2cSafe;


public abstract class ActionConcept extends TaskConcept implements Sensor, PermanentConcept {

    public final AttVectorNode attn;

    protected ActionConcept(Term term, TermLinker linker, NAR n) {
        this(term,
                new SensorBeliefTables(term, true, n.conceptBuilder),

                //n.conceptBuilder.newTable(term, false),
                new RTreeBeliefTable(),

                linker,
                n);
    }

    protected ActionConcept(Term term, BeliefTable beliefs, BeliefTable goals, NAR n) {
        this(term, beliefs, goals, n.conceptBuilder.termlinker(term), n);
    }

    protected ActionConcept(Term term, BeliefTable beliefs, BeliefTable goals, TermLinker l, NAR n) {
        super(term, beliefs, goals, l, n.conceptBuilder);

        this.attn = new AttVectorNode(term, List.of(term)) {
            @Override
            public float elementPri(NAR nar) {
                return nar.priDefault(GOAL);
            }
        };
        ((SensorBeliefTables) beliefs()).resolution(FloatRange.unit(n.freqResolution));
    }


    @Override
    public Iterable<Termed> components() {
        return List.of(this);
    }

//    /** estimates the organic (derived, excluding curiosity) goal confidence for the given time interval
//     * TODO exclude input tasks from the calculation */
//    abstract public float dexterity(long start, long end, NAR n);
    abstract public float dexterity();

    @Override
    public FloatRange resolution() {
        return ((SensorBeliefTables) beliefs()).resolution();
    }

    @Override
    public void value(Task t, NAR n) {

        if (!(t instanceof CuriosityTask)) {
            super.value(t, n);

            if (t.isGoal()) {
                long now = n.time();
                long dt = t.minTimeTo(now);
                int dur = n.dur();

                MetaGoal.Action.learn(t.cause(), w2cSafe(Param.evi(1, dt, dur) * (TruthIntegration.evi(t))), n.causes);

            }
        }
    }

    public ActionConcept resolution(float v) {
        resolution().set(v);
        return this;
    }



    /**
     * determines the feedback belief when desire or belief has changed in a MotorConcept
     * implementations may be used to trigger procedures based on these changes.
     * normally the result of the feedback will be equal to the input desired value
     * although this may be reduced to indicate that the motion has hit a limit or
     * experienced resistence
     *
     * @param desired  current desire - null if no desire Truth can be determined
     * @param believed current belief - null if no belief Truth can be determined
     * @return truth of a new feedback belief, or null to disable the creation of any feedback this iteration
     */
    @FunctionalInterface
    public interface MotorFunction extends BiFunction<Truth, Truth, Truth> {

        @Nullable Truth apply(@Nullable Truth believed, @Nullable Truth desired);

    }

}



























