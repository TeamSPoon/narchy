package nars.table;

import jcog.Util;
import jcog.list.FasterList;
import jcog.pri.Priority;
import jcog.sort.SortedArray;
import jcog.util.ArrayIterator;
import nars.NAR;
import nars.Param;
import nars.Task;
import nars.concept.TaskConcept;
import nars.control.Cause;
import nars.task.NALTask;
import nars.task.Revision;
import nars.term.Term;
import nars.truth.PreciseTruth;
import nars.truth.Stamp;
import nars.truth.Truth;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static nars.table.BeliefTable.eternalTaskValue;
import static nars.table.BeliefTable.eternalTaskValueWithOriginality;
import static nars.time.Tense.ETERNAL;


/**
 * Created by me on 5/7/16.
 */
public class EternalTable extends SortedArray<Task> implements TaskTable, FloatFunction<Task> {

    public static final EternalTable EMPTY = new EternalTable(0) {

        @Override
        public Task strongest() {
            return null;
        }

        @Override
        public Task weakest() {
            return null;
        }

        @Override
        public boolean removeTask(Task x) {
            return false;
        }

        @Override
        public boolean add(/*@NotNull*/ Task input, TaskConcept c, /*@NotNull*/ NAR nar) {
            return false;
        }


        @Override
        public void setCapacity(int c) {

        }

        @Override
        public void forEachTask(Consumer<? super Task> action) {

        }

        /*@NotNull*/
        @Override
        public Iterator<Task> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    };


    public EternalTable(int initialCapacity) {
        super();
        setCapacity(initialCapacity);
    }

    @Override
    public void forEachTask(Consumer<? super Task> x) {
        Task[] a = toArray();
        for (int i = 0, aLength = Math.min(size, a.length); i < aLength; i++) {
            Task y = a[i];
            if (y == null)
                break; 
            if (!y.isDeleted())
                x.accept(y);
        }
    }

    public Task select(@Nullable Predicate<? super Task> selector) {
        if (selector == null)
            return strongest();

        Task[] a = toArray();
        for (int i = 0, aLength = Math.min(size, a.length); i < aLength; i++) {
            Task x = a[i];
            if (x == null)
                break; 
            if (selector.test(x))
                return x;
        }
        return null;
    }

    @Override
    public Stream<Task> streamTasks() {




        Object[] list = this.list;
        int size = Math.min(list.length, this.size);
        if (size == 0)
            return Stream.empty();
        else {
            
            return ArrayIterator.stream((Task[]) list, size);
        }
    }

    @Override
    protected Task[] newArray(int s) {
        return new Task[s];
    }

    public void setCapacity(int c) {
        int wasCapacity = this.capacity();
        if (wasCapacity != c) {

            List<Task> trash = null;
            synchronized (this) {

                wasCapacity = capacity(); 

                int s = size;
                if (s > c) {

                    

                    trash = new FasterList(s - c);
                    while (c < s--) {
                        trash.add(removeLast());
                    }
                }

                if (wasCapacity != c)
                    resize(c);
            }

            
            if (trash != null) {





                trash.forEach(Task::delete);

            }

        }
    }


    @Override
    public Task[] toArray() {
        
        int s = this.size;
        if (s == 0)
            return Task.EmptyArray;
        else {
            Task[] list = this.list;
            return Arrays.copyOf(list, Math.min(s, list.length), Task[].class);
            
        }
        
    }


    @Override
    public void clear() {
        synchronized (this) {
            super.clear();
        }
    }

    public Task strongest() {
        Object[] l = this.list;
        return (l.length == 0) ? null : (Task) l[0];
    }

    public Task weakest() {
        int s = size;
        if (s == 0) return null;
        Object[] l = this.list;
        if (l.length == 0) return null;
        return (Task) l[size-1];








    }

    /**
     * for ranking purposes.  returns negative for descending order
     */
    @Override
    public final float floatValueOf(/*@NotNull*/ Task w) {
        
        return -eternalTaskValue(w);
    }




    @Deprecated
    void removeTask(/*@NotNull*/ Task t, @Nullable String reason) {






        
    }

    /**
     * @return null: no revision could be applied
     * ==newBelief: existing duplicate found
     * non-null: revised task
     */
    @Nullable
    private /*Revision*/Task tryRevision(/*@NotNull*/ Task y /* input */,
                                                      @Nullable NAR nar) {

        Object[] list = this.list;
        int bsize = list.length;
        if (bsize == 0)
            return null; 


        
        Task oldBelief = null;
        Truth conclusion = null;

        Truth newBeliefTruth = y.truth();

        for (int i = 0; i < bsize; i++) {
            Task x = (Task) list[i];

            if (x == null) 
                break;

            if (x.equals(y)) {
                /*if (x!=y && x.isInput())
                    throw new RuntimeException("different input task instances with same stamp");*/
                return x;
            }


            
            float xconf = x.conf();
            if ((!x.isCyclic() && !y.isCyclic()) &&
                 Arrays.equals(x.stamp(), y.stamp()) &&
                 Util.equals(xconf, y.conf(), nar.confResolution.floatValue())) {

                conclusion = new PreciseTruth(0.5f * (x.freq() + y.freq()), xconf);

            } else if (Stamp.overlapsAny(y, x)) {








                continue; 

            } else {


                
                
                
                
                
                

                
                
                
                

                Truth xt = x.truth();

                

                Truth yt = Revision.revise(newBeliefTruth, xt, 1f, conclusion == null ? 0 : conclusion.evi());
                if (yt == null)
                    continue;

                yt = yt.dither(nar);
                if (yt == null || yt.equalsIn(xt, nar) || yt.equalsIn(newBeliefTruth, nar)) 
                    continue;

                conclusion = yt;
            }

            oldBelief = x;

        }

        if (oldBelief == null)
            return null;

        final float newBeliefWeight = y.evi();

        

        float aProp = newBeliefWeight / (newBeliefWeight + oldBelief.evi());
        Term t =
                Revision.intermpolate(
                        y.term(), oldBelief.term(),
                        aProp,
                        nar
                );


        Task prevBelief = oldBelief;
        Task x = Task.tryTask(t, y.punc(), conclusion, (term, revisionTruth) ->
                new NALTask(term,
                        y.punc(),
                        revisionTruth,
                        nar.time() /* creation time */,
                        ETERNAL, ETERNAL,
                        Stamp.zip(y.stamp(),prevBelief.stamp(), aProp)
                )
        );
        if (x != null) {
            x.priSet(Priority.fund(Math.max(prevBelief.priElseZero(), y.priElseZero()), false, prevBelief, y));
            ((NALTask) x).cause = Cause.sample(Param.causeCapacity.intValue(), y, prevBelief);

            if (Param.DEBUG)
                x.log("Insertion Revision");




        }

        return x;
    }

    @Nullable
    private Task put(final Task incoming) {
        Task displaced = null;

        synchronized (this) {
            if (size == capacity()) {
                Task weakestPresent = weakest();
                if (weakestPresent != null) {
                    if (eternalTaskValueWithOriginality(weakestPresent)
                            <=
                        eternalTaskValueWithOriginality(incoming)) {
                        displaced = removeLast();
                    } else {
                        return incoming; 
                    }
                }
            }

            add(incoming, this);
        }

        return displaced;
    }

    public final Truth truth() {
        Task s = strongest();
        return s != null ? s.truth() : null;
    }











    @Override
    public boolean removeTask(Task x) {


        synchronized (this) {
            x.delete();

            int index = indexOf(x, this);
            if (index == -1)
                return false;

            int findAgainToBeSure = indexOf(x, this);
            return (findAgainToBeSure != -1) && remove(findAgainToBeSure) != null;
        }


    }

    @Override
    public boolean add(/*@NotNull*/ Task input, TaskConcept c, /*@NotNull*/ NAR nar) {

        int cap = capacity();
        if (cap == 0) {
            
            /*if (input.isInput())
                throw new RuntimeException("input task rejected (0 capacity): " + input + " "+ this + " " + this.capacity());*/
            return false;
        }









            Task revised = tryRevision(input, nar);
            if (revised == null) {
                


                return insert(input);
            } else {

                if (revised.equals(input)) {
                    


                    return true;
                } else {

                    if (insert(revised)) {
                        
                        

                        if (input.equals(revised)) {
                            System.out.println("input=revised");
                        }

                        if (insert(input)) {
                            
                        } /*else {
                            input.delete(); 
                        }*/

                    }

                    nar.eventTask.emit(revised);

                    return true; 
                }





























            }


    }


    /**
     * try to insert but dont delete the input task if it wasn't inserted (but delete a displaced if it was)
     * returns true if it was inserted, false if not
     */
    private boolean insert(/*@NotNull*/ Task input) {

        Task displaced = put(input);

        if (displaced == input) {
            
            return false;
        } else if (displaced != null) {
            removeTask(displaced,
                    "Displaced"
                    
            );
        }
        return true;
    }






















    @Nullable public Truth strongestTruth() {
        Task e = strongest();
        return (e != null) ? e.truth() : null;
    }

}
