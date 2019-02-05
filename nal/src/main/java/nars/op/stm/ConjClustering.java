package nars.op.stm;

import jcog.Util;
import jcog.WTF;
import jcog.data.set.MetalLongSet;
import jcog.math.FloatRange;
import jcog.math.LongInterval;
import jcog.pri.Prioritizable;
import jcog.pri.VLink;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.bag.BagClustering;
import nars.control.CauseMerge;
import nars.control.DurService;
import nars.control.channel.CauseChannel;
import nars.exe.Causable;
import nars.task.NALTask;
import nars.task.util.TaskException;
import nars.term.Term;
import nars.term.util.conj.Conj;
import nars.time.Tense;
import nars.truth.Stamp;
import nars.truth.Truth;
import org.eclipse.collections.api.tuple.primitive.LongObjectPair;
import org.eclipse.collections.api.tuple.primitive.ObjectBooleanPair;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static nars.truth.func.TruthFunctions.c2wSafe;
import static nars.truth.func.TruthFunctions.confCompose;
import static org.eclipse.collections.impl.tuple.primitive.PrimitiveTuples.pair;

public class ConjClustering extends Causable {

    public final BagClustering<Task> data;
    final CentroidConjoiner conjoiner = new CentroidConjoiner();
    private final BagClustering.Dimensionalize<Task> model;
    private final CauseChannel in;
    private final byte punc;

    public final FloatRange termVolumeMaxFactor = new FloatRange(0.75f, 0, 1f);

    private final Predicate<Task> filter;
    private long now;
    private int dur;
    private float confMin;
    private int volMax, volMaxSafe;
    private int ditherTime;
    private final boolean popConjoinedTasks = false;
    static final boolean priCopyOrTransfer = false;

    final AtomicBoolean learn = new AtomicBoolean(true);
    private int maxStampLen;

    public ConjClustering(NAR nar, byte punc, int centroids, int capacity) {
        this(nar, punc, (t) -> true, centroids, capacity);
    }

    public ConjClustering(NAR nar, byte punc, Predicate<Task> filter, int centroids, int capacity) {
        super();

        this.in = nar.newChannel(this);//.buffered();

        this.model = new BagClustering.Dimensionalize<Task>(5) {

            /** # durs (in-)sensitivity factor */
            static final double TIME_SENSITIVITY = 8;

            @Override
            public void coord(Task t, double[] c) {
                Truth tt = t.truth();
                c[0] = t.mid();
                c[1] = t.priElseZero();

                c[2] = tt.polarity();
                c[3] = tt.conf();

                c[4] = t.range();
            }

            @Override
            public double distanceSq(double[] a, double[] b) {
                double dMid = Math.abs(a[0] - b[0])/dur;
                double dRange = Math.abs(a[4] - b[4]);
                double dPri = Math.abs(a[1] - b[1]);
                double dPolarity = Math.abs(a[2] - b[2]);
                double dConf = Math.abs(a[3] - b[3]);
                return dMid / (TIME_SENSITIVITY) +
                        dPri +
                        dPolarity +
                        dConf +
                        dRange/(1 + dMid) * 0.5;

//                return (1 + (Math.abs(a[0] - b[0]) / Math.min(a[4], b[4])) + (Math.abs(a[4] - b[4]) / dur))
//                        *
//                        (
//                                Math.abs(a[1] - b[1])
//                                        + Math.abs(a[2] - b[2])
//                                        + Math.abs(a[3] - b[3]) * 0.1f
//                        );
            }
        };

        this.data = new BagClustering<>(model, centroids, capacity);

        this.punc = punc;
        this.filter = filter;

        this.now = Long.MIN_VALUE;
        update(nar);

        nar.on(this);
    }

    @Override
    protected void starting(NAR nar) {

        DurService.on(nar, ()-> learn.set(true));

        on(nar.onTask(t -> {
            if (!t.isEternal()
                    && !t.hasVars() //<-- TODO requires multi-normalization (shifting offsets) //TODO allow ImDep's
                    && t.stamp().length <= maxStampLen
                    && filter.test(t)) {

                data.put(t, pri(t));

            }
        }, punc));

    }

    protected float pri(Task t) {
        return t.priElseZero()
                //* TruthIntegration.evi(t);
                 * t.originality();

    }



    @Override
    protected /*synchronized*/ void next(NAR nar, BooleanSupplier kontinue /* max tasks generated per centroid, >=1 */) {

        update(nar);

        if (data == null || data.bag.isEmpty()) return;

        if (learn.compareAndSet(true,false)) {
            //learn once per duration
            data.learn(forgetRate(), 1);
        }

        conjoiner.kontinue = kontinue;
        data.forEachCentroid(nar, nar.random(), conjoiner::conjoinCentroid);

        //in.commit();
    }

    private void update(NAR nar) {
        long now = nar.time();
        long lastNow = this.now;
        if (lastNow!=now) {
            //parameters must be set even if data is empty due to continued use in the filter
            //but at most once per cycle or duration
            this.now = now;
            this.dur = nar.dur();
            this.ditherTime = nar.dtDither();
            this.maxStampLen =
                    //Param.STAMP_CAPACITY / 2; //for minimum of 2 tasks in each conjunction
                    //Param.STAMP_CAPACITY - 1;
                    Integer.MAX_VALUE;
            this.confMin = nar.confMin.floatValue();
            this.volMaxSafe = Math.round((this.volMax = nar.termVolumeMax.intValue()) * termVolumeMaxFactor.floatValue());
        }
    }

    protected float forgetRate() {
        //nar.forgetRate.floatValue()
        return 1f;
    }

    @Override
    public float value() {
        return in.value();
    }

    public static class STMClusterTask extends NALTask {

        STMClusterTask(@Nullable ObjectBooleanPair<Term> cp, Truth t, long start, long end, long[] evidence, byte punc, long now) throws TaskException {
            super(cp.getOne(), punc, t.negIf(cp.getTwo()), now, start, end, evidence);
        }

        @Override
        public boolean isInput() {
            return false;
        }
    }

    class CentroidConjoiner {

        final Map<LongObjectPair<Term>, Task> vv = new HashMap<>(16);

        final MetalLongSet actualStamp = new MetalLongSet(Param.STAMP_CAPACITY * 2);

        /**
         * HACK
         */
        transient volatile public BooleanSupplier kontinue;


        private boolean conjoinCentroid(Stream<VLink<Task>> group, NAR nar) {

            Iterator<VLink<Task>> gg =
                    group.filter(x -> x != null && !x.isDeleted()).iterator();

            main:
            while (gg.hasNext()) {

                vv.clear();
                actualStamp.clear();


                long end = Long.MIN_VALUE;
                long start = Long.MAX_VALUE;

                float freq = 1f, conf = 1f;
                float priMax = Float.NEGATIVE_INFINITY, priMin = Float.POSITIVE_INFINITY;
                float confMax = Float.NEGATIVE_INFINITY;//, confMin = Float.POSITIVE_INFINITY;
                int vol = 0;


                while (gg.hasNext()) {

                    Task t = gg.next().get();

                    Truth tx = t.truth();
                    float tc = tx.conf();
                    if (confCompose(conf, tc) < confMin)
                        continue; //try next

                    Term taskTerm = t.term();

                    if (tx.isNegative())
                        taskTerm = taskTerm.neg();

                    int xtv = taskTerm.volume();
                    if (vol + xtv + 1 > ConjClustering.this.volMaxSafe)
                        continue; //try next


                    long[] tStamp = t.stamp();

                    if (!Stamp.overlapsAny(actualStamp, tStamp)) {

                        long taskStart = Tense.dither(t.start(), ditherTime);

                        if (vv.isEmpty() || !vv.containsKey(pair(taskStart, taskTerm.neg()))) {

                            LongObjectPair<Term> ps = pair(taskStart, taskTerm);

                            if (null == vv.putIfAbsent(ps, t)) {
                                vol += xtv;

                                actualStamp.addAll(tStamp);

                                if (start > taskStart) start = taskStart;
                                if (end < taskStart) end = taskStart;


                                if (tc > confMax) confMax = tc;

                                conf = confCompose(conf, tc);

                                float tf = tx.freq();
                                freq *= tx.isNegative() ? (1f - tf) : tf;

                                float p = t.priElseZero();
                                if (p < priMin) priMin = p;
                                if (p > priMax) priMax = p;

                                if (vv.size() >= Param.STAMP_CAPACITY)
                                    break;
                            }
                        }
                    }
                }

                int vs = vv.size();
                if (vs < 2)
                    continue;

                Task[] uu = vv.values().toArray(Task.EmptyArray);


                float e = c2wSafe(conf);
                if (e!=e)
                    throw new WTF();
                if (e > 0) {
                    final Truth t = Truth.theDithered(freq, e, nar);
                    if (t != null) {

                        Term cj = Conj.conj(vv.keySet());
                        if (cj.volume() > volMax)
                            return false;

                        if (cj != null) {

                            Term tt = Task.forceNormalizeForBelief(cj);

                            ObjectBooleanPair<Term> cp = Task.tryContent(tt, punc, true);
                            if (cp != null) {

                                long range = Util.min(LongInterval::range, uu) - 1;
                                long tEnd = start + range;
                                NALTask m = new STMClusterTask(cp, t,
                                        Tense.dither(start, ditherTime), Tense.dither(tEnd,ditherTime),
                                        Stamp.sample(Param.STAMP_CAPACITY, actualStamp, nar.random()), punc, now);
                                m.cause(CauseMerge.AppendUnique.merge(Param.causeCapacity.intValue(), uu));


                                int v = cp.getOne().volume();
                                float cmplFactor =
                                        1f - Util.unitize(((float) v) / volMax);

//                                float freqFactor =
//                                        t.freq();
//                                float confFactor =
//                                        (conf / (conf + confMax));


                                m.pri(Prioritizable.fund(Util.unitize((priMin /* * uu.length*/ ) * cmplFactor /* * freqFactor  * confFactor*/ ), priCopyOrTransfer, uu));

                                if (popConjoinedTasks) {
                                    for (Task aa : uu)
                                        data.remove(aa);
                                }

                                in.input(m);

                                if (!kontinue.getAsBoolean())
                                    return false;

                            } else {
                                return false;
                            }
                        }
                    }


                }
            }

            return true;
        }

    }



}
