package nars.perf;

import jcog.math.FloatSupplier;
import nars.Param;
import nars.nal.nal1.NAL1MultistepTest;

class MyNAL1MultistepTest implements FloatSupplier {
    @Override
    public float asFloat() {
        try {


            

            NAL1MultistepTest n = new NAL1MultistepTest();

            System.out.println("TTL_MUTATE=" + Param.TTL_MUTATE);
            System.out.println("TTL_MUTATE=" + Param.TTL_DERIVE_TASK_SUCCESS);

            n.multistepSim4();
            n.end();
            return 1f / (1 + n.test.time());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
