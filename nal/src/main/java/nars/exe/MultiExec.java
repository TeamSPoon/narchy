package nars.exe;

import com.conversantmedia.util.concurrent.ConcurrentQueue;
import com.google.common.util.concurrent.AtomicDouble;
import jcog.Util;
import jcog.exe.AffinityExecutor;
import jcog.exe.Can;
import nars.NAR;
import nars.Task;
import nars.control.Activate;
import nars.control.Cause;
import nars.control.Premise;
import nars.derive.Conclude;
import nars.task.ITask;
import nars.task.NativeTask;
import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static nars.time.Tense.ETERNAL;

public class MultiExec extends Exec {

    public static final Logger logger = LoggerFactory.getLogger(MultiExec.class);

    private final BlockingQueue<ITask> q;
    private AffinityExecutor exe;

    final Sub[] sub;
    private final int num;

    final static int SUB_CAPACITY = 1024;


    @Deprecated
    final SharedCan deriver = new SharedCan();

    public MultiExec(int threads) {
        this(threads, threads * 16);
    }

    public MultiExec(int threads, int qSize) {
        num = threads;
        sub = new Sub[num];
        q = Util.blockingQueue(qSize);
    }

    @Override
    public synchronized void start(NAR nar) {
        super.start(nar);

        //exe = Executors.newFixedThreadPool(num);
        exe = new AffinityExecutor();

        for (int i = 0; i < num; i++) {
            exe.execute(sub[i] = new Sub(SUB_CAPACITY, deriver));
        }

        nar.can.add(deriver);
    }

    class Sub extends UniExec implements Runnable {


        private final SharedCan can;

        public Sub(int capacity, SharedCan deriver) {
            super(capacity);
            this.can = deriver;
            start(MultiExec.this.nar);
        }

        int premiseRemaining, premiseDone;

        @Override
        public void add(ITask t) {
            throw new UnsupportedOperationException("called?");
        }

        @Override
        public void run() {

            Activate.BatchActivate.enable();

            int conc = MultiExec.this.concurrency();
            int idle = 0;
            while (true) {
                try {

                    int s = work(conc);

                    int p = think(conc);

                    if ((s == 0) && (p== 0)) {
                        Util.pauseNext(idle++);
                    } else {
                        Activate.BatchActivate.get().commit(nar);
                        idle = 0;
                    }

                } catch (Throwable t) {
                    logger.error("{} {}", this, t);
                }
            }
        }

        public int think(int conc) {
            int p;
            if ((p = premiseRemaining = can.share(1f / conc)) > 0) {
                premiseDone = 0;

                int loops = 0;


                //spread the sampling over N batches for fairness
                int batches = 8;
                int batchSize = plan.capacity() / batches;

                long start = System.nanoTime();

                while (premiseRemaining > 0) {

                    int premiseDoneAtStart = premiseDone;

                    workRemaining = batchSize;
                    plan.commit().sample(super::exeSample);

                    loops++;

                    if (premiseDone == premiseDoneAtStart)
                        break; //encountered no premises in this batch that could have been processed
                }

                long end = System.nanoTime();

                //System.err.println(premiseDone + "/" + work + " in " + loops + " loops\tvalue=" + can.value() + " " + n4((end - start) / 1.0E9) + "sec");

                can.update(premiseDone, (end - start) / 1.0E9);
            }
            return p;
        }

        public int work(int conc) {
            int s;
            float qs = ((ConcurrentQueue) q).size();
            if (qs > 0) {
                s = (int) Math.ceil(qs / Math.max(1, (conc - 1)));
                for (int i = 0; i < s; i++) {
                    ITask k = q.poll();
                    if (k != null)
                        execute(k);
                    else
                        break;
                }
            } else {
                s = 0;
            }
            return s;
        }

        @Override
        protected boolean done(ITask x) {
            if (x instanceof Premise) {
                premiseDone++;
                if (--premiseRemaining <= 0)
                    return true;
            }

            return super.done(x);
        }

        protected void execute(ITask x) {
            Iterable<? extends ITask> y;
            try {
                y = x.run(nar);
                if (y != null)
                    y.forEach(MultiExec.this::add);
            } catch (Throwable t) {
                logger.error("{} {}", x, t);
                return;
            }

        }
    }

    @Override
    public void add(ITask t) {
        if (t instanceof Task) {

            Iterable<? extends ITask> y = t.run(nar);
            if (y != null)
                y.forEach(q::add);

        } else if (t instanceof NativeTask) {
            q.add(t);
        } else {
            sub[which(t)].plan.putAsync((t));
        }
    }


    protected int which(ITask t) {
        return Math.abs(t.hashCode() % sub.length);
    }

    @Override
    public int concurrency() {
        return sub.length;
    }

    @Override
    public Stream<ITask> stream() {
        return Stream.of(sub).flatMap(UniExec::stream);
    }

    private class SharedCan extends Can {

        final AtomicInteger workDone = new AtomicInteger(0);
        final AtomicInteger workRemain = new AtomicInteger(0);
        final AtomicDouble time = new AtomicDouble(0);

        final AtomicLong valueCachedAt = new AtomicLong(ETERNAL);
        float valueCached = 0;

        @Override
        public void commit() {
            workRemain.set(iterations());
        }

        public int share(float prop) {
            return (int) Math.ceil(workRemain.get() * prop);
        }

        @Override
        public float value() {
            long now = nar.time();
            if (valueCachedAt.getAndSet(now) != now) {

                //HACK
                float valueSum = 0;
                for (Cause c : nar.causes) {
                    if (c instanceof Conclude.RuleCause) {
                        valueSum += c.value();
                    }
                }

                this.valueCached = valueSum;

                int w = workDone.getAndSet(0);
                double t = time.getAndSet(0);

                this.update(w, valueCached, t);
            }

            return valueCached;
        }

        public void update(int work, double timeSec) {
            this.workDone.addAndGet(work);
            this.time.addAndGet(timeSec);
        }

    }
}
