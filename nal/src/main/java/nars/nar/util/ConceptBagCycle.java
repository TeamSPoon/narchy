package nars.nar.util;

import nars.$;
import nars.NAR;
import nars.bag.Bag;
import nars.bag.impl.CurveBag;
import nars.budget.merge.BudgetMerge;
import nars.concept.Concept;
import nars.concept.util.ConceptBuilder;
import nars.link.BLink;
import nars.util.data.MutableInteger;
import nars.util.data.Range;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMapUnsafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * The default deterministic memory cycle implementation that is currently used as a standard
 * for development and testing.
 *
 * multithreading granularity at the concept (outermost loop)
 */
public class ConceptBagCycle implements Consumer<NAR> {
    /**
     * How many concepts to fire each cycle; measures degree of parallelism in each cycle
     */
    @Range(min = 0, max = 64, unit = "Concept")
    public final @NotNull MutableInteger conceptsFiredPerCycle;


    private static final Logger logger = LoggerFactory.getLogger(ConceptBagCycle.class);



    /**
     * concepts active in this cycle
     */
    @NotNull
    public final Bag<Concept> concepts;

    @Deprecated
    public final transient @NotNull NAR nar;


    @Range(min = 0, max = 16, unit = "TaskLink") //TODO use float percentage
    public final MutableInteger tasklinksFiredPerFiredConcept = new MutableInteger(1);

    @Range(min = 0, max = 16, unit = "TermLink")
    public final MutableInteger termlinksFiredPerFiredConcept = new MutableInteger(1);



    private final ConceptBuilder conceptBuilder;

    //cached value for use in the next firing
    private int taskLinks, termLinks;


//    private Comparator<? super BLink<Concept>> sortConceptLinks = (a, b) -> {
//        Concept A = a.get();
//        Concept B = b.get();
//        String as = A!=null ? A.term
//    };

//    private static final Logger logger = LoggerFactory.getLogger(AbstractCore.class);

    //private final CapacityLinkedHashMap<Premise,Premise> recent = new CapacityLinkedHashMap<>(256);
    //long novel=0, total=0;

    public ConceptBagCycle(@NotNull NAR nar, int initialCapacity) {

        this.nar = nar;

        this.conceptsFiredPerCycle = new MutableInteger(1);
        this.conceptBuilder = nar.concepts.conceptBuilder();

        this.concepts = new MonitoredCurveBag(nar, initialCapacity, ((DefaultConceptBuilder) conceptBuilder).defaultCurveSampler);

        nar.onFrame(this);
        nar.eventReset.on(this::reset);

    }

    /** called when a concept is displaced from the concept bag */
    protected void sleep(@NotNull Concept c) {
        NAR n = this.nar;

        n.policy(c, conceptBuilder.sleep(), n.time());

        n.emotion.alert(1f / concepts.size());
    }

    /** called when a concept enters the concept bag
     * @return whether to accept the item into the bag
     * */
    protected boolean awake(@NotNull Concept c) {

        NAR n = this.nar;
        n.policy(c, conceptBuilder.awake(), n.time());

        return true;
    }


    public void reset(NAR m) {
        concepts.clear();
    }



    /** called each frame */
    @Override public void accept(NAR nar) {

        int cpf = conceptsFiredPerCycle.intValue();

        taskLinks = tasklinksFiredPerFiredConcept.intValue();
        termLinks = termlinksFiredPerFiredConcept.intValue();

        List<BLink<Concept>> toFire = $.newArrayList(cpf);

        concepts.commit();
        concepts.sample(cpf, toFire::add);

        //toFire.sort(sortConceptLinks);

        this.nar.runLater(toFire, bc -> {
            Concept c = bc.get();
            if (c != null) {
                new FireConceptSquared(c, this.nar,
                        taskLinks, termLinks,
                        this.nar::input //input them within the current thread here
                );
            }
        }, 4f);

    }

    /** extends CurveBag to invoke entrance/exit event handler lambda */
    public final class MonitoredCurveBag extends CurveBag<Concept> {

        final NAR nar;

        public MonitoredCurveBag(NAR nar, int capacity, @NotNull CurveSampler sampler) {
            super(capacity, sampler, BudgetMerge.plusBlend,
                    //new ConcurrentHashMap<>(capacity)
                    nar.exe.concurrent() ?  new ConcurrentHashMapUnsafe<>(capacity) : new HashMap(capacity)
                    //new NonBlockingHashMap<>(capacity)
            );
            this.nar = nar;
        }

        @Override
        public void clear() {
            forEach((BLink<Concept> v) -> { if (v!=null) sleep(v.get()); }); //HACK allow opportunity to process removals
            super.clear();
        }

        @Override
        protected final void onActive(@NotNull Concept c) {
            awake(c);
        }

        @Override
        protected final void onRemoved(@NotNull Concept c, @Nullable BLink<Concept> value) {
            if (value!=null)
                sleep(c);
        }

        @Override
        public @Nullable BLink<Concept> remove(@NotNull Concept x) {
            BLink<Concept> r = super.remove(x);
            if (r!=null) {
                sleep(x);
            }
            return r;
        }


    }


//    public void conceptualize(@NotNull Concept c, @NotNull Budgeted b, float conceptActivation, float linkActivation, NAR.Activation activation) {
//
//        concepts.put(c, b, conceptActivation, activation.overflow);
//        //if (b.isDeleted())
//            //return;
//            //throw new RuntimeException("Concept rejected: " + b);
//        if (linkActivation > 0)
//            c.link(b, linkActivation, nar, activation);
//    }


    //try to implement some other way, this is here because of serializability

}
