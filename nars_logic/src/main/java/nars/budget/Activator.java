package nars.budget;

import nars.task.Task;

/**
 * Defines an event-driven activation policy for reacting to
 * NAR budgeting events and their outcomes
 */
@FunctionalInterface
public interface Activator  {


    void accept(Task[] t, float activation);

    /** after a task has been processed.
     * if it was rejected or had no effect then activation==0 */
    //void activate(Task t, Concept c, float activation);


}
