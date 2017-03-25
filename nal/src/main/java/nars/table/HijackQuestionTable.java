package nars.table;

import nars.NAR;
import nars.bag.impl.TaskHijackBag;
import nars.budget.BudgetMerge;

import java.util.Random;

/**
 * Created by me on 2/16/17.
 */
public class HijackQuestionTable extends TaskHijackBag implements QuestionTable {


    public HijackQuestionTable(int cap, int reprobes, BudgetMerge merge, Random random) {
        super(reprobes, merge, random);

        capacity(cap);
    }


//    @Override
//    public float pri(@NotNull Task key) {
//        return (1f + key.priSafe(0)) * (1f * key.qua());
//    }



    @Override
    public void capacity(int newCapacity, NAR nar) {
        setCapacity(newCapacity); //hijackbag
        capacity(newCapacity); //question table
    }


}
