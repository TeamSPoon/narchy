package nars.table.dynamic;

import jcog.Util;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.concept.TaskConcept;
import nars.control.op.Remember;
import nars.link.TaskLink;
import nars.table.BeliefTable;
import nars.table.TaskTable;
import nars.table.eternal.EternalTable;
import nars.task.signal.SignalTask;
import nars.task.util.Answer;
import nars.task.util.series.AbstractTaskSeries;
import nars.term.Term;
import nars.truth.Truth;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static jcog.math.LongInterval.TIMELESS;
import static nars.Op.CONJ;
import static nars.task.Tasklike.seed;
import static nars.time.Tense.ETERNAL;

/**
 * adds a TaskSeries additional Task buffer which can be evaluated from, or not depending
 * if a stored task is available or not.
 */
abstract public class SeriesBeliefTable<T extends Task> extends DynamicTaskTable {

    public final AbstractTaskSeries<T> series;


    /**
     * permanent tasklink "generator" anchored in eternity when inseted to the concept on new tasks, but clones currently-timed tasklinks for propagation
     */
    public final TaskLink.GeneralTaskLink tasklink;

    public SeriesBeliefTable(Term c, boolean beliefOrGoal, AbstractTaskSeries<T> s) {
        super(c, beliefOrGoal);
        this.series = s;

        tasklink = new TaskLink.GeneralTaskLink(c, seed(term, punc(), ETERNAL), 0);

    }

    @Override
    public int size() {
        return series.size();
    }

    @Override
    public final void match(Answer t) {
        series.whileEach(t.time.start, t.time.end, false, t::tryAccept);
    }


    @Override
    public void clear() {
        series.clear();
    }

    @Override
    public Stream<? extends Task> streamTasks() {
        return series.stream();
    }

    @Override
    public void forEachTask(long minT, long maxT, Consumer<? super Task> x) {
        series.forEach(minT, maxT, true, x);
    }

    @Override
    public void forEachTask(Consumer<? super Task> action) {
        series.forEach(action);
    }


//    @Override
//    protected Truth truthDynamic(long start, long end, Term templateIgnored, Predicate filter, NAR nar) {
//        return (Truth) (eval(false, start, end, filter, nar));
//    }


    public void clean(List<BeliefTable> tables) {
        if (!Param.FILTER_SIGNAL_TABLE_TEMPORAL_TASKS)
            return;

        assert(beliefOrGoal);

        long sStart = series.start(), e;
        if (sStart != TIMELESS && (e = series.end()) != TIMELESS) {
            long sEnd = e;

            List<Task> deleteAfter = new LinkedList();
            for (TaskTable b : tables) {
                if (!(b instanceof DynamicTaskTable) && !(b instanceof EternalTable)) {
                    b.forEachTask(sStart, sEnd, t -> {
                        if (t.isDeleted() || absorbNonSignal(t, sStart, sEnd)) {
                            deleteAfter.add(t);
                        } else {
                            //System.out.println(t + " saved");
                        }
                    });
                }
                if (!deleteAfter.isEmpty()) {
                    deleteAfter.forEach(t -> b.removeTask(t, true));
                }
            }

        }
    }

    /**
     * used for if you can cache seriesStart,seriesEnd for a batch of calls
     */
    boolean absorbNonSignal(Task t, long seriesStart, long seriesEnd) {

        long tStart = t.start(), tEnd = t.end();
        if (tStart != ETERNAL) {
            if (seriesStart != TIMELESS && seriesEnd != TIMELESS /* allow prediction 'suffix' */) {
                if (seriesEnd >= tEnd) {
                //if (Longerval.intersectLength(tStart, tEnd, seriesStart, seriesEnd) != -1) {

                    //TODO actually absorb (transfer) the non-series task priority in proportion to the amount predicted, gradually until complete absorption
                    boolean seriesDefinedThere = !series.isEmpty(tStart, tEnd);

                    return seriesDefinedThere;

                }
            }
        }
        return false;
    }

    /**
     * correct CONJ concepts for task generation
     */
    protected static Term taskTerm(Term x) {
        if (x.op() == CONJ) {
            return x.dt(0);
        }
        return x;
    }

    public void add(T nextT) {
        series.compress();

        series.push(nextT);
    }



    /**
     * has special equality and hashcode convention allowing the end to stretch;
     * otherwise it would be seen as unique when tested after stretch
     */
    public static final class SeriesTask extends SignalTask {

        /**
         * current endpoint
         */
        long e;

        public SeriesTask(Term term, byte punc, Truth value, long start, long end, long[] stamp) {
            super(SeriesBeliefTable.taskTerm(term), punc, value, start, start, end, stamp);
            if (stamp.length != 1)
                throw new UnsupportedOperationException("requires stamp of length 1 so it can be considered an Input Task and thus have consistent hashing even while its occurrrence time is stretched");
            this.e = end;
        }

        @Override
        protected int hashCalculate() {
            //TODO also involve Term?
            return Util.hashCombine(term().hashCode(), Util.hashCombine(stamp()[0], start()));
        }

        @Override
        public boolean equals(Object x) {
            if (this == x) return true;
            if (x instanceof SeriesTask) {
                //TODO also involve Term?
                Task xx = (Task) x;
                if (stamp()[0] == xx.stamp()[0] && start() == xx.start() && term().equals(xx.term()))
                    return true;
            }
            return false; //return super.equals(x);
        }

        public void setEnd(long e) {
            this.e = e;
        }

        @Override
        public long end() {
            return e;
        }

        //        @Override
//        public ITask inputSubTask(Task ignored, NAR n) {
//            throw new UnsupportedOperationException("use input(concept) for internal storage procedure");
//        }

        /**
         * passive insertion subtask only
         */

        public SeriesRemember input(TaskConcept concept) {
            //return new TaskLinkTaskAndEmit(this, priElseZero(), concept);
            return new SeriesRemember(this, concept);
        }

    }


    public static final class SeriesRemember extends Remember {


        private SeriesRemember(SeriesTask task, TaskConcept concept) {
            super(task, concept);
            remember(task);
        }

        @Override
        protected boolean tasklink() {
            return false; //tasklink add procedure is added manually in SensorBeliefTables
        }

//        @Override
//        protected boolean taskevent() {
//            return false;
//        }

        @Override
        protected void add(NAR n) {
            //DONT. just go straight to postprocessing
        }
    }

//    private class SeriesTaskLink extends TaskLink.GeneralTaskLink {
//        public SeriesTaskLink() {
//            super(seed(SeriesBeliefTable.this.target, SeriesBeliefTable.this.punc(), Tense.ETERNAL), 0);
//        }
//
//        @Override
//        public TaskLink clone(float pri) {
//            return new GeneralTaskLink(tasklinkPtr, pri);
//        }
//    }
}
