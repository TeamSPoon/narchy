package nars.nar;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.BasicExecutor;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import nars.NAR;
import nars.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Created by me on 8/16/16.
 */
public class MultiThreadExecutioner extends Executioner {


    private final int threads;
    private final RingBuffer<TaskEvent> ring;
    private final Executor exec;
    private SequenceBarrier barrier;


    static final class TaskEvent {
        @Nullable
        public Task[] tasks;
        @Nullable
        public Runnable r;
    }

    @NotNull
    final Disruptor<TaskEvent> disruptor;

    public MultiThreadExecutioner(int threads, int ringSize) {
        this(threads, ringSize, new BasicExecutor(Executors.defaultThreadFactory()));
    }

    public MultiThreadExecutioner(int threads, int ringSize, Executor exe) {

        this.threads = threads;
        this.exec = exe;


        this.disruptor = new Disruptor<>(
                TaskEvent::new,
                ringSize /* ringbuffer size */,
                exe,
                ProducerType.MULTI,
                new SleepingWaitStrategy()
                //new BlockingWaitStrategy()
                //new LiteTimeoutBlockingWaitStrategy(10, TimeUnit.MILLISECONDS)
                //new LiteBlockingWaitStrategy()
        );
        this.ring = disruptor.getRingBuffer();


    }

    @Override
    public void stop() {
        synchronized (disruptor) {
            disruptor.shutdown();
            disruptor.halt();
        }
    }

    @Override
    public void next(@NotNull NAR nar) {
        //nar.eventFrameStart.emitAsync(nar, runWorker);

        Consumer[] vv = nar.eventFrameStart.getCachedNullTerminatedArray();
        if (vv != null) {
            for (int i = 0; ; ) {
                Consumer c = vv[i++];
                if (c != null) {
                    execute(() -> { //TODO these Runnables can be cached in an array parallel to 'vv'
                        c.accept(nar);
                    });
                } else
                    break; //null terminator hit
            }
        }

    }

    @Override
    public void start(@NotNull NAR nar) {

        WorkHandler[] taskWorker = new WorkHandler[threads];
        for (int i = 0; i < threads; i++)
            taskWorker[i] = new TaskEventWorkHandler(nar);
        EventHandlerGroup workers = disruptor.handleEventsWithWorkerPool(taskWorker);

        //barrier = workers.asSequenceBarrier();
        barrier = workers.asSequenceBarrier();

        disruptor.start();
    }

    public void synchronize() {
        //if (!runWorker.isQuiescent()) {
//                while (!runWorker.awaitQuiescence(QUIESENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
//                    //logger.warn("runWorker lag: {}", runWorker);
//                    Thread.yield();
//                }
        //}


        //long cap = ring.remainingCapacity();
        //int size = ring.getBufferSize();
        //if (cap!= size) {
        /*while ((cap = ring.remainingCapacity()) < ring.getBufferSize())*/

        //System.out.println("waiting for: " + e + " free=" + cap);
            try {
                barrier.waitFor(ring.getCursor());
            } catch (AlertException e1) {
                e1.printStackTrace();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } catch (TimeoutException e1) {
                e1.printStackTrace();
            }
        //}

        //long used = size - ring.remainingCapacity();
//
//        RingBuffer<TaskEvent> ring = this.ring;
//        while (ring.remainingCapacity() < ring.getBufferSize()) {
//            //while ((cap = ring.remainingCapacity()) < ring.getBufferSize()) {
//
//            //logger.info( "<-- seq=" + ring.getCursor() + " remain=" + cap);// + " last=" + last[0]);
//
//            //Thread.yield();
//            //Util.pause(1);
//            try {
//                Thread.sleep(0);
//            } catch (InterruptedException e) {
//
//            }
//        }


    }


    final static EventTranslatorOneArg<TaskEvent, Task[]> taskPublisher = (TaskEvent x, long seq, Task[] b) -> x.tasks = b;

    @Override
    public void inputLater(@NotNull Task[] t) {
        disruptor.publishEvent(taskPublisher, t);
    }

    final static EventTranslatorOneArg<TaskEvent, Runnable> runPublisher = (TaskEvent x, long seq, Runnable b) -> {
        x.r = b;
    };

    @Override
    public void execute(@NotNull Runnable r) {
        disruptor.publishEvent(runPublisher, r);
    }

    @Override
    public boolean executeMaybe(Runnable r) {
        return ring.tryPublishEvent(runPublisher, r);
    }

    private final static class TaskEventWorkHandler implements WorkHandler<TaskEvent> {
        @NotNull
        private final NAR nar;

        public TaskEventWorkHandler(@NotNull NAR nar) {
            this.nar = nar;
        }

        @Override
        public void onEvent(@NotNull TaskEvent te) throws Exception {
            Task[] tt = te.tasks;
            if (tt != null) {
                te.tasks = null;
                try {
                    nar.input(tt);
                } catch (Throwable e) {
                    NAR.logger.error("task: {}", e);
                }
            }
            Runnable rr = te.r;
            if (rr != null) {
                te.r = null;
                try {
                    rr.run();
                } catch (Throwable e) {
                    NAR.logger.error("run: {}", e);
                }
            }
        }
    }

}
