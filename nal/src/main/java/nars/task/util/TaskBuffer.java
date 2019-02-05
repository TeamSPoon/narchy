package nars.task.util;

import jcog.TODO;
import jcog.data.list.FasterList;
import jcog.math.FloatRange;
import jcog.math.IntRange;
import jcog.pri.OverflowDistributor;
import jcog.pri.PriBuffer;
import jcog.pri.Prioritizable;
import jcog.pri.ScalarValue;
import jcog.pri.bag.Bag;
import jcog.pri.bag.impl.ArrayBag;
import jcog.pri.bag.impl.BufferedBag;
import jcog.pri.bag.impl.PriArrayBag;
import jcog.pri.op.PriMerge;
import nars.Task;
import nars.control.CauseMerge;
import nars.control.channel.ConsumerX;
import nars.exe.Exec;
import nars.task.ITask;
import nars.task.NALTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static nars.Op.*;

/**
 * regulates a flow of supplied tasks to a target consumer
 */
abstract public class TaskBuffer implements Consumer<ITask> {


    /**
     * returns the input task, or the existing task if a pending duplicate was present
     */
    public abstract <T extends ITask> T add(T x);

    //public abstract void commit(long now, Consumer<ITask> target);
    public abstract void commit(long now, ConsumerX<ITask> target);

    public abstract void clear();

    /**
     * known or estimated number of tasks present
     */
    abstract public int size();

    public final IntRange capacity = new IntRange(0, 0, 4 * 1024);

    /**
     * calculate or estimate current capacity, as a value between 0 and 100% [0..1.0]
     */
    public final float volume() {
        return size() / capacity.floatValue();
    }

    @Override
    public final void accept(ITask task) {
        add(task);
    }

    //TODO
    //final AtomicLong in = new AtomicLong(), out = new AtomicLong(), drop = new AtomicLong(), merge = new AtomicLong();


    public static float merge(ITask pp, ITask tt) {
        if (pp == tt)
            return 0;

        Task ttt = (Task) tt;

        if (pp instanceof NALTask) {
            NALTask ppp = (NALTask) pp;
            ppp.priCauseMerge(ttt, CauseMerge.AppendUnique);

            long inCreation = ttt.creation();
            if (inCreation > ppp.creation)
                ppp.creation = inCreation;

        } else
            pp.priMax(tt.pri()); //just without cause update

        if (pp instanceof Task) { //HACK
            Task ppp = (Task) pp;
            if (ppp.isCyclic() && !ttt.isCyclic())
                ppp.setCyclic(false);
        }

        return 0; //TODO calculate

    }

    abstract public float priMin();

    /**
     * pass-thru, no buffering
     */
    public static class DirectTaskBuffer extends TaskBuffer {

        private final Consumer<ITask> each;

        public DirectTaskBuffer(Consumer<ITask> each) {
            this.each = each;
        }

        @Override
        public <T extends ITask> T add(T x) {
            each.accept(x);
            if (x.isDeleted())
                return null;
            else
                return x;
        }

        @Override
        public void commit(long now, ConsumerX<ITask> target) {

        }

        @Override
        public void clear() {

        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public float priMin() {
            return 0;
        }
    }

    /**
     * TODO
     * when the concept index churn rate is low, use the Map to conceptualize quickly
     * when the concept index churn rate is high, use the Bag with controlled throughput rate
     * acting as the original NoveltyBag did in OpenNARS
     * using this the system can shift energy towards exploration (more conceptualization) OR
     * towards more refined truth (more selectivity in task generation with regard to relatively
     * stable set of concepts they would add to)
     */
    public static abstract class AdaptiveTaskBuffer extends TaskBuffer {

    }

    /**
     * buffers in a Map<> for de-duplication prior to a commit that flushes them as input to NAR
     * does not obey adviced capacity
     * TODO find old implementation and re-implement this
     */
    public static class MapTaskBuffer extends TaskBuffer {
        private final Map<ITask, ITask> tasks;

        public MapTaskBuffer(int initialCapacity) {
            capacity.set(initialCapacity);
            tasks = new ConcurrentHashMap<>(initialCapacity, 0.99f);
        }

        @Override
        public void clear() {
            tasks.clear();
        }

        @Override
        public int size() {
            return tasks.size();
        }

        @Override
        public float priMin() {
            throw new TODO();
        }

        @Override
        public <T extends ITask> T add(T n) {
            ITask p = tasks.putIfAbsent(n, n);
            if (p != null) {
                merge(p, n);
                return (T) p;
            }
            return n;
        }

        /**
         * TODO time-sensitive
         */
        @Override
        public void commit(long now, ConsumerX<ITask> target) {
            Iterator<ITask> ii = tasks.values().iterator();
            while (ii.hasNext()) {
                target.accept(ii.next());
                ii.remove();
            }
        }
    }


    /**
     * buffers and deduplicates in a Bag<Task,Task> allowing higher priority inputs to evict
     * lower priority inputs before they are flushed.  commits involve a multi-thread shareable drainer task
     * allowing multiple inputting threads to fill the bag, potentially deduplicating each's results,
     * while other thread(s) drain it in prioritized order as input to NAR.
     */
    public static class BagTaskBuffer extends TaskBuffer {


        /**
         * temporary buffer before input so they can be merged in case of duplicates
         */
        public final Bag<ITask, ITask> tasks = new BufferedBag.SimplestBufferedBag<>(
                new PriArrayBag<ITask>(PriMerge.max,
                        new HashMap()
                        //new UnifiedMap()
                ) {
                    @Override
                    protected float merge(ITask existing, ITask incoming) {
                        return TaskBuffer.merge(existing, incoming);
                    }

                    @Override
                    protected int histogramBins() {
                        return 0;
                    }
                },
                new PriBuffer<ITask>(PriMerge.max) {
                    @Override
                    protected void merge(Prioritizable existing, ITask incoming, float pri, OverflowDistributor<ITask> overflow) {
                        TaskBuffer.merge((ITask)existing, incoming);
                    }
                }
        );
//                new PriArrayBag<ITask>(PriMerge.max, new HashMap()
//                        //new UnifiedMap()
//                ) {
//                    @Override
//                    protected float merge(ITask existing, ITask incoming) {
//                        return TaskBuffer.merge(existing, incoming);
//                    }
//                };

        //new HijackBag...


        @Override
        public float priMin() {
            return tasks.isFull() ? tasks.priMin() : 0;
        }

        @Override
        public void clear() {
            tasks.clear();
        }

        @Override
        public int size() {
            return tasks.size();
        }

        //            @Override
//            protected boolean fastMergeMaxReject() {
//                return true;
//            }

        //            @Override
//            public Task put(Task incoming, NumberX overflow) {
//                //fast merge intercept: avoids synchronization in normal insert procedure
//                Task existing = map.get(incoming);
//                if (existing!=null) {
//                    DERIVATION_MERGE.apply(existing, incoming);
//                    //TODO sort if order changed
//                    return existing;
//                }
//
//
//                return super.put(incoming, overflow);
//            }
//
//            /** returning null elides lookup which was already performed on the intercept */
//            @Override protected Task getExisting(Task key) {
//                return null;
//            }
//            @Override
//            protected float merge(Task existing, Task incoming) {
//                throw new UnsupportedOperationException("should not reach here");
//            }


        /**
         * perceptual valve
         * dilation factor
         * input rate
         * proportional to # of capacities/durations of time
         */
        public final FloatRange valve = new FloatRange(0.25f, 0, 1f);

        final AtomicBoolean busy = new AtomicBoolean(false);

        static final ThreadLocal<FasterList<ITask>> batch = ThreadLocal.withInitial(FasterList::new);

        private transient long prev = Long.MIN_VALUE;



        /**
         * @capacity size of buffer for tasks that have been input (and are being de-duplicated) but not yet input.
         * input may happen concurrently (draining the bag) while inputs are inserted from another thread.
         */
        public BagTaskBuffer(int capacity, float rate) {
            this.capacity.set(capacity);
            this.valve.set(rate);
            this.tasks.setCapacity(capacity);
        }

        @Override
        public <T extends ITask> T add(T x) {
            return (T) tasks.put(x);
        }

        @Override
        public void commit(long now, ConsumerX<ITask> target) {

            if (!busy.compareAndSet(false, true))
                return; //an operation is in-progress

            try {

                if (prev == Long.MIN_VALUE)
                    prev = now - 1;

                long dt = now - prev;
                if (dt == 0)
                    return;

                prev = now;

                tasks.setCapacity(capacity.intValue());


                tasks.commit(null);

                int s = tasks.size();
                if (s == 0)
                    return;

                int n = Math.min(s, batchSize(dt));
                if (n > 0) {


                    int c = target.concurrency();
                    if (c <= 2) {
                        tasks.pop(null, n, target::input);
                    } else {
                        //concurrency > 2
                        int nEach = (int) Math.ceil(((float) n) / (c - 1));
                        for (int i = 0; i < c && n > 0; i++) {

                            ((Exec) target)/*HACK*/.input((nn) -> {

                                FasterList batch = BagTaskBuffer.batch.get();
                                Bag<ITask, ITask> t = tasks;

                                if  (t instanceof BufferedBag)
                                    t = ((BufferedBag)t).bag;

                                if (t instanceof ArrayBag) {
                                    ((ArrayBag) t).popBatch(nEach, batch::add);
                                } else {
                                    t.pop(null, nEach, batch::add); //per item.. may be slow
                                }

                                if (!batch.isEmpty()) {
                                    if (batch.size() > 2)
                                        batch.sortThis(sloppySorter);
                                    ITask.run(batch, nn);
                                    batch.clear();
                                }
                            });
                            n -= nEach;
                        }


//                            int m = batch.size();
//                            switch (m) {
//                                case 0:
//                                    break;
//                                case 1:
//                                    target.accept(batch.get(0));
//                                    break;
//                                default:
//                                    if (m > 2)
//                                        batch.sortThis(sloppySorter);
//                                    break;
//                            }

//                            batch.forEach(target::input);
                    }
                }

            } finally {
                busy.set(false);
            }

        }

        /** fast, imprecise sort.  for cache locality and concurrency purposes */
        static final Comparator<Task> sloppySorter = Comparator
            .comparingInt((Task x) -> x.term().concept().hashCode())
            .thenComparing((Task x) -> -x.priElseZero());


        /**
         * TODO abstract
         */
        protected int batchSize(float dt) {
            //rateControl.apply(tasks.size(), tasks.capacity());
            float v = valve.floatValue();
            if (v < ScalarValue.EPSILON)
                return 0;
            return Math.max(1, Math.round(
                    //dt * v * tasks.capacity()
                    v * tasks.capacity()
            ));
        }
    }


    public static class BagPuncTasksBuffer extends TaskBuffer {

        public final TaskBuffer belief, goal, question, quest;
        private final TaskBuffer[] ALL;

        public BagPuncTasksBuffer(int capacity, float rate) {
            belief = new BagTaskBuffer(capacity, rate);
            goal = new BagTaskBuffer(capacity, rate);
            question = new BagTaskBuffer(capacity, rate);
            quest = new BagTaskBuffer(capacity, rate);

            ALL = new TaskBuffer[]{belief, goal, question, quest};

            this.capacity.set(capacity);
        }

        @Override
        public float priMin() {
            float q = question.priMin();
            if (q < ScalarValue.EPSILON) return 0;
            float qq = quest.priMin();
            if (qq < ScalarValue.EPSILON) return 0;
            float b = belief.priMin();
            if (b < ScalarValue.EPSILON) return 0;
            float g = goal.priMin();
            if (g < ScalarValue.EPSILON) return 0;
            return Math.min(Math.min(Math.min(b, g), q), qq);
        }

        private TaskBuffer buffer(byte punc) {
            switch (punc) {
                case BELIEF:
                    return belief;
                case GOAL:
                    return goal;
                case QUESTION:
                    return question;
                case QUEST:
                    return quest;
                default:
                    return null;
            }
        }

        @Override
        public void clear() {
            for (TaskBuffer x : ALL)
                x.clear();
        }

        @Override
        public <T extends ITask> T add(T x) {
            return buffer(x.punc()).add(x);
        }

        @Override
        public void commit(long now, ConsumerX<ITask> target) {
            //TODO parallelize

            int c = Math.max(1, capacity.intValue() / ALL.length);
            for (TaskBuffer x : ALL) {
                x.capacity.set(c);
                x.commit(now, target);
            }
        }


        @Override
        public int size() {
            int s = 0;
            for (TaskBuffer x : ALL)
                s += x.size();
            return s;
        }
    }
}
