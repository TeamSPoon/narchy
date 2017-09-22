//package jcog;
//
//import net.openhft.affinity.AffinityLock;
//import org.jetbrains.annotations.NotNull;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.lang.management.ManagementFactory;
//import java.lang.management.ThreadInfo;
//import java.lang.management.ThreadMXBean;
//import java.util.Collection;
//import java.util.concurrent.CopyOnWriteArraySet;
//import java.util.concurrent.Executor;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * uses affinity locking to pin new threads to their own unique, stable CPU core/hyperthread etc
// */
//public class AffinityExecutor implements Executor {
//
//    private static final Logger logger = LoggerFactory.getLogger(AffinityExecutor.class);
//
//    public final Collection<Thread> threads = new CopyOnWriteArraySet<>();
//    public final String id;
//
//    public AffinityExecutor(String id) {
//        this.id = id;
//    }
//
//    @Override
//    public final void execute(@NotNull Runnable command) {
//        execute(command, 1);
//    }
//
//    public final void shutdownNow() {
//        stop();
//    }
//
//    final static class AffinityThread extends Thread {
//
//        Runnable cmd;
//
//        public AffinityThread(@NotNull String name, Runnable cmd) {
//            super(name);
//
//            this.cmd = cmd;
//        }
//
//        @Override
//        public void run() {
//
//
//            try (AffinityLock lock = AffinityLock.acquireCore()) {
//                cmd.run(); //avoid virtual call to super etc
//            } catch (Exception e) {
//                logger.error("Could not acquire affinity lock; executing normally: {} ", e);
//                AffinityLock.dumpLocks();
//                //e.printStackTrace();
//
//                cmd.run();
//            }
//
//            threads.remove(this);
//
//        }
//    }
//
//
//    static final AtomicInteger serial = new AtomicInteger(0);
//
//    public void stop() {
//        synchronized (threads) {
//            threads.removeIf(t -> {
//                t.stop();
//                return true;
//            });
//            assert (threads.isEmpty());
//        }
//    }
//
//
//    public final void execute(Runnable worker, int count) {
//        synchronized (threads) {
//            //assert (threads.isEmpty());
//
//            for (int i = 0; i < count; i++) {
//                AffinityThread at = new AffinityThread(
//                    id + "_" + serial.getAndIncrement(),
//                        worker);
//                threads.add(at);
//                at.start();
//            }
//
//        }
//    }
//
//
////        @Override
////        public String toString()
////        {
////            return "BasicExecutor{" +
////                    "threads=" + dumpThreadInfo() +
////                    '}';
////        }
//
//    private String dumpThreadInfo() {
//        final StringBuilder sb = new StringBuilder();
//
//        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
//
//        for (Thread t : threads) {
//            ThreadInfo threadInfo = threadMXBean.getThreadInfo(t.getId());
//            sb.append("{");
//            sb.append("name=").append(t.getName()).append(",");
//            sb.append("id=").append(t.getId()).append(",");
//            sb.append("state=").append(threadInfo.getThreadState()).append(",");
//            sb.append("lockInfo=").append(threadInfo.getLockInfo());
//            sb.append("}");
//        }
//
//        return sb.toString();
//    }
//
//    public long[] threadIDs() {
//        return threads.stream().mapToLong(t -> t.getId()).toArray();
//    }
//}