package nars.index.concept;

import jcog.pri.PLink;
import jcog.pri.PLinkHashCached;
import jcog.pri.bag.impl.hijack.PriLinkHijackBag;
import nars.NAR;
import nars.concept.Concept;
import nars.concept.PermanentConcept;
import nars.concept.TaskConcept;
import nars.control.DurService;
import nars.term.Term;
import nars.term.Termed;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Created by me on 2/20/17.
 */
public class HijackConceptIndex extends AbstractConceptIndex {

    private final PriLinkHijackBag<Termed,PLink<Termed>> table;


    private int forgetPeriodDurs = 64;
    private float forgetTemperature = 0.1f;


    /** eliding is faster but records less accurate access statistics.
     *  but if eliding, cache statistics will not reflect the full amount of access otherwise would be necessary */
    private static final boolean ElideGets = false;

    /**
     * how many items to visit during update
     */
    private final float initialTask, initialNode;
    private final float getBoost;

    //    private long now;
//    private int dur;
    private DurService onDur;


    public HijackConceptIndex(int capacity, int reprobes) {
        super();

        initialTask = 1f/(reprobes+1);
        initialNode = initialTask / 2; //(non-task) node concepts less valuable

        getBoost = (float) (1f/Math.sqrt(capacity));

        this.table = new PriLinkHijackBag<Termed,PLink<Termed>>(capacity, reprobes) {

            {
                resize(capacity);
            }


            @Override
            public Termed key(PLink<Termed> value) {
                return value.get().term();
            }

            @Override
            protected boolean attemptRegrowForSize(int s) {
                return false;
            }


            @Override
            protected boolean replace(float incomingPri, PLink<Termed> existing) {

                boolean existingPermanent = existing.get() instanceof PermanentConcept;
                if (existingPermanent) {
                    return false;
                }

                return super.replace(incomingPri, existing);
            }

            @Override
            public void onRemove(PLink<Termed> value) {
                HijackConceptIndex.this.onRemove(value.get());
            }
        };

    }

    @Override
    public void start(NAR nar) {
        super.start(nar);
        onDur = DurService.on(nar, this::commit);
        onDur.durs(forgetPeriodDurs);
    }

    /**
     * measures accesses for eviction, so do not elide
     */
    @Override
    protected final boolean elideConceptGets() {
        return ElideGets;
    }

    private void commit() {
        table.commit(table.forget(forgetTemperature));
    }

    @Override
    public @Nullable Termed get(Term key, boolean createIfMissing) {
        @Nullable PLink<Termed> x = table.get(key);
        if (x != null) {


            boost(x, getBoost);
            return x.get();

        }

        if (createIfMissing) {
            Termed kc = nar.conceptBuilder.apply(key, null);
            if (kc != null) {
                PLink<Termed> inserted = table.put(new PLinkHashCached<>(kc, priPut(key, (Concept)kc)));
                if (inserted != null) {
                    return inserted.get();
                } else {
                    //could not insert
                    return null;

//                    ((Concept)kc).delete(nar);
//                    return kc; //return the concept although it wont exist in the index
                }
            }
        }

        return null;
    }

    private float priPut(Term key, Concept kc) {
        return kc instanceof TaskConcept ? initialTask : initialNode;
    }

    private void boost(PLink<Termed> x, float boost) {
        x.priAdd(boost);
        table.pressurize(boost);
    }


    @Override
    public void set(Term key, Termed value) {
        PLink<Termed> existing = table.get(key);
        if (existing==null || existing.get()!=value) {
            remove(key);
            PLink<Termed> inserted = table.put(new PLinkHashCached<>(value, initialTask));
            if (inserted == null && value instanceof PermanentConcept) {
                throw new RuntimeException("unresolvable hash collision between PermanentConcepts: " + inserted + ' ' + value);
            }
        }
    }

    @Override
    public void clear() {
        super.clear();
        table.clear();
    }

    @Override
    public void forEach(Consumer<? super Termed> c) {

        table.forEachKey(c);

    }

    @Override
    public int size() {
        return table.size(); /** approx since permanent not considered */
    }

    @Override
    public String summary() {
        return table.size() + " concepts";
    }

    @Override
    public Termed remove(Term entry) {
        PLink<Termed> e = table.remove(entry);
        return e != null ? e.get() : null;
    }

    @Override
    public Stream<Termed> stream() {
        return table.stream().map(Supplier::get);
    }

//    protected float score(Concept c) {
//        float beliefConf = w2c((float) c.beliefs().streamTasks().mapToDouble(t -> t.evi(now, dur)).average().orElse(0));
//        float goalConf = w2c((float) c.goals().streamTasks().mapToDouble(t -> t.evi(now, dur)).average().orElse(0));
//        float talCap = c.tasklinks().size() / (1f + c.tasklinks().capacity());
//        float telCap = c.termlinks().size() / (1f + c.termlinks().capacity());
//        return Util.or(((talCap + telCap) / 2f), (beliefConf + goalConf) / 2f) /
//                (1 + ((c.complexity() + c.volume()) / 2f) / nar.termVolumeMax.intValue());
//    }
//
//    protected void forget(PLink<Termed> x, Concept c, float amount) {
//
//        c.tasklinks().setCapacity(Math.round(c.tasklinks().capacity() * (1f - amount)));
//        c.termlinks().setCapacity(Math.round(c.termlinks().capacity() * (1f - amount)));
//
//        x.priMult(1f - amount);
//    }

}
