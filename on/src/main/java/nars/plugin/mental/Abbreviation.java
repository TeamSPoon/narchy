package nars.plugin.mental;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AtomicDouble;
import nars.NAR;
import nars.config.Parameters;
import nars.entity.*;
import nars.inference.BudgetFunctions;
import nars.io.Symbols;
import nars.language.Similarity;
import nars.language.Term;
import nars.operator.Operation;
import nars.operator.Operator;
import nars.storage.Memory;
import nars.util.EventEmitter.EventObserver;
import nars.util.Events.TaskDerive;
import nars.util.Plugin;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static nars.language.CompoundTerm.termArray;

/**
 * 1-step abbreviation, which calls ^abbreviate directly and not through an added Task.
 * Experimental alternative to Abbreviation plugin.
 */
public class Abbreviation implements Plugin {

    private final double abbreviationProbability = InternalExperience.INTERNAL_EXPERIENCE_PROBABILITY;
    
    /**
    * Operator that give a CompoundTerm an atomic name
    */
    public static class Abbreviate extends Operator {

        public Abbreviate() {
            super("^abbreviate");
        }

        private static final AtomicInteger currentTermSerial = new AtomicInteger(1);

        public static Term newSerialTerm(char prefix) {
            return new Term(prefix + String.valueOf(currentTermSerial.incrementAndGet()));
        }


        /**
         * To create a judgment with a given statement
         * @param args Arguments, a Statement followed by an optional tense
         * @param memory The memory in which the operation is executed
         * @return Immediate results as Tasks
         */
        @Override
        protected ArrayList<Task> execute(Operation operation, Term[] args, Memory memory) {
            
            Term compound = args[0];
            
            Term atomic = newSerialTerm(Symbols.TERM_PREFIX);
                        
            Sentence sentence = new Sentence(
                    Similarity.make(compound, atomic), 
                    Symbols.JUDGMENT_MARK, 
                    new TruthValue(1, Parameters.DEFAULT_JUDGMENT_CONFIDENCE),  // a naming convension
                    new Stamp(memory));
            
            float quality = BudgetFunctions.truthToQuality(sentence.truth);
            
            BudgetValue budget = new BudgetValue(
                    Parameters.DEFAULT_JUDGMENT_PRIORITY, 
                    Parameters.DEFAULT_JUDGMENT_DURABILITY, 
                    quality);

            return Lists.newArrayList( new Task(sentence, budget) );        

        }

    }
    
    public AtomicInteger abbreviationComplexityMin = new AtomicInteger(20);
    public AtomicDouble abbreviationQualityMin = new AtomicDouble(0.95f);
    public EventObserver obs;
    
    //TODO different parameters for priorities and budgets of both the abbreviation process and the resulting abbreviation judgment
    //public AtomicDouble priorityFactor = new AtomicDouble(1.0);
    
    public boolean canAbbreviate(Task task) {
        return !(task.sentence.term instanceof Operation) && 
                (task.sentence.term.getComplexity() > abbreviationComplexityMin.get()) &&
                (task.budget.getQuality() > abbreviationQualityMin.get());
    }
    
    @Override
    public boolean setEnabled(final NAR n, final boolean enabled) {
        final Memory memory = n.memory;
        
        Operator _abbreviate = memory.getOperator("^abbreviate");
        if (_abbreviate == null) {
            _abbreviate = memory.addOperator(new Abbreviate());
        }
        final Operator abbreviate = _abbreviate;
        
        if(obs==null) {
            obs= (event, a) -> {
                if (event != TaskDerive.class)
                    return;

                if ((abbreviationProbability < 1.0) && (Memory.randomNumber.nextDouble() > abbreviationProbability))
                    return;

                Task task = (Task)a[0];

                //is it complex and also important? then give it a name:
                if (canAbbreviate(task)) {

                    Operation operation = Operation.make(
                            abbreviate, termArray(task.sentence.term ),
                            false);

                    operation.setTask(task);

                    abbreviate.call(operation, memory);
                }

            };
        }
        
        memory.event.set(obs, enabled, TaskDerive.class);
        
        return true;
    }
    
}
