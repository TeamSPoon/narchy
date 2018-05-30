package nars.derive.budget;

import jcog.Util;
import jcog.math.FloatRange;
import nars.Task;
import nars.derive.Derivation;
import nars.derive.DeriverBudgeting;
import nars.truth.Truth;

import static nars.truth.TruthFunctions.w2cSafe;

/** TODO parameterize, modularize, refactor etc */
public class DefaultDeriverBudgeting implements DeriverBudgeting {

    /** global multiplier */
    public final FloatRange scale = new FloatRange(1f, 0f, 1f);

    /** how important is it to retain evidence.
     * leniency towards uncertain derivations */
    public final FloatRange evidenceImportance = new FloatRange(1f, 0f, 1f);

    @Override
    public float pri(Task t, Derivation d) {
        float factor = this.scale.floatValue();

        


            
            float pCompl = d.parentComplexitySum;
            float dCompl = t.voluplexity();
            float relGrowthCost =
                    (pCompl / (pCompl + dCompl));

            
            relGrowthCost = Util.sqr(relGrowthCost);

            factor *= relGrowthCost;


        {
            
            
        }

        

        Truth derivedTruth = t.truth();























































        if (/*BELIEF OR GOAL*/derivedTruth != null) {

            
            boolean single = d.single;
            float pEvi = single ? d.premiseEviSingle : d.premiseEviDouble;
            if (pEvi > 0) {
                float pConf = w2cSafe(pEvi);

                
                float dConf = derivedTruth.conf();
                if (single)
                    dConf /=2; 

                float eviFactor = dConf / pConf; 
                
                factor *= Util.lerp(evidenceImportance.floatValue(), 1, eviFactor);
            }

            


        } else {
            
            
            
            factor *= Util.lerp(evidenceImportance.floatValue(), 1, relGrowthCost); 
        }

        return factor * d.pri;

        

    }
}
