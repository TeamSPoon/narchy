package nars.exe;

import jcog.bag.Bag;
import jcog.bag.impl.ConcurrentCurveBag;
import nars.NAR;
import nars.control.Activate;
import nars.task.ITask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static jcog.bag.Bag.BagSample.*;

/**
 * unified executor
 * probabalistic continuation kernel
 */
public class UniExec extends Exec {

    public final int CAPACITY;

//    public static class ITask extends PLinkUntilDeleted<ITask> {
//
//        public ITask(@NotNull ITask id, float p) {
//            super(id, p);
//        }
//    }

    protected Bag<ITask, ITask> plan;

    protected int workRemaining;


    public UniExec(int capacity) {
        CAPACITY = capacity;
    }

    @Override
    protected synchronized void clear() {
        if (plan!=null)
            plan.clear();
    }

    @Override
    public void add(ITask t) {
        if (t instanceof Activate) {
            plan.putAsync(t);
        } else {
            execute(t);
        }
    }


    protected void run(int i) {
        workRemaining = i;
        plan.commit().sample(this::exeSample);
    }


    public static final Logger logger = LoggerFactory.getLogger(UniExec.class);

    public Bag.BagSample exeSample(ITask x) {
        Iterable<? extends ITask> next = null;

        try {
            next = x.run(nar);
        } catch (Exception e) {
            logger.error("{} {}", x, e);
        }


        if (next != null) {
            next.forEach(this::add);
        }

        boolean persist = x.persist();

        if (done(x))
            return persist ? Stop : RemoveAndStop;
        else
            return persist ? Next : Remove;
    }

    protected boolean done(ITask x) {
        //realtime: System.currentTimeMillis() > nextCycle

        //iterative:
        return --workRemaining <= 0;
    }

    @Override
    public synchronized void start(NAR nar) {

        plan =
                //new ConcurrentArrayBag<ITask,ITask>(this, new ConcurrentHashMap(), CAPACITY) {
                new ConcurrentCurveBag<ITask>(this, new ConcurrentHashMap(), nar.random(), CAPACITY) {

                    @Override
                    public ITask key(ITask value) {
                        return value;
                    }

                    //
                };
//            new ConcurrentCurveBag(this,
//                new ConcurrentHashMapUnsafe<>(1024), nar.random(), 1024);
        //new PriorityHijackBag<>(CAPACITY,2) {
//
//                    @Override
//                    public void setCapacity(int _newCapacity) {
//                        super.setCapacity(_newCapacity);
//                        resize(capacity());
//                    }
//          @Override
//                    protected boolean replace(float incoming, float existing) {
//                        return hijackGreedy(incoming, existing);
//                    }
//                                        @Override
//                    protected ITask merge(@NotNull ITask existing, @NotNull ITask incoming, MutableFloat overflowing) {
//                        float overflow = UniExec.this.merge(existing, incoming); //modify existing
//                        if (overflow > 0) {
//                            //pressurize(-overflow);
//                            if (overflowing != null) overflowing.add(overflow);
//                        }
//                        return existing;
//                    }
//                                     @Override
//                    public Consumer<ITask> forget(float rate) {
//                        return null;
////                        return new PriForget(rate) {
////                            @Override
////                            public void accept(@NotNull Priority b) {
////                                if (b instanceof Activate || b instanceof )
////                                super.accept(b);
////                            }
////                        };
//                    }

        super.start(nar);
    }


    public float pri(ITask key) {
        return key.pri();

//        float i = key.pri();
//        if (i!=i) return Float.NaN;
//
//        if (key instanceof Activate) {
//            return Util.lerp(i, 0f, 0.25f);
//        } else if (key instanceof Premise) {
//            return Util.lerp(i, 0.25f, 0.5f);
//        } else {
//            return Util.lerp(i, 0.5f, 1f);
//        }
    }

    @Override
    public synchronized void stop() {
        if (plan != null) {
            plan.clear();
            plan = null;
        }
    }


    @Override
    public int concurrency() {
        return 1;
    }


    @Override
    public Stream<ITask> stream() {
        return plan.stream(); //.filter(Objects::nonNull);
    }


//    final int WINDOW_SIZE = 32;
//    final int WINDOW_RATIO = 8;
//    private final TopN<ITask> top = new TopN<>(new ITask[WINDOW_SIZE], this::pri);
//    int windowTTL = WINDOW_RATIO;
//
//    private BagSample top(ITask x) {
//
//        top.add(x);
//
//        if (--windowTTL <= 0) {
//            windowTTL = WINDOW_RATIO;
//            ITask t = top.pop();
//            if (t!=null)
//                exeSample((ITask) t);
//        }
//
//        return BagSample.Next;
//    }

//    public static void main(String... args) {
//        NARS n = NARS.realtime();
//        n.exe(new Execontinue());
//
////        new Loop(1f) {
////
////            @Override
////            public boolean next() {
////                System.out.println();
////                System.out.println( Joiner.on(" ").join(exe.plan) );
////                return true;
////            }
////        };
//
//        NAR nn = n.get();
//
//        try {
//            nn.log();
//            nn.input("a:b. b:c. c:d.");
//        } catch (Narsese.NarseseException e) {
//            e.printStackTrace();
//        }
//
//    }


}
