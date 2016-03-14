package nars.nal.nal8;

import nars.NAR;
import nars.nal.AbstractNALTest;
import nars.util.signal.TestNAR;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class NAL8Test extends AbstractNALTest {

    final int cycles = 256; //150 worked for most of the initial NAL8 tests converted

    public NAL8Test(Supplier<NAR> b) { super(b); }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable configurations() {
        return AbstractNALTest.nars(8, false);
    }


    @Test
    public void subsent_1()  {
        TestNAR tester = test();

        //TODO decide correct parentheses ordering

        //tester.nar.log();
        tester.input("[opened]:{t001}. :|:");
        tester.inputAt(10,
                "(((hold:({t002}) &&+5 at:({t001})) &&+5 open({t001})) &&+5 [opened]:{t001}).");
        //on input becomes:
        //       (({t001}-->[opened]) &&-5 (open({t001}) &&-5 ((({t002})-->hold) &&+5 (({t001})-->at))))

        // hold .. at .. open
        tester.mustBelieve(cycles, "((hold:({t002}) &&+5 at:({t001})) &&+5 open({t001}))",
                1.0f, 0.42f,
                -5);


//        //the structurually inverted sequence
//        tester.mustBelieve(cycles,
//                "(hold:({t002}) &&+5 (at:({t001}) &&+5 (open({t001}) &&+5 [opened]:{t001})))",
//                1.0f, 0.90f
//                );


        //tester.inputAt(10, "(hold:({t002}) &&+5 (at:({t001}) &&+5 (open({t001}) &&+5 [opened]:{t001}))).");
////        tester.mustBelieve(cycles, "(hold:({t002}) &&+5 (at:({t001}) &&+5 open({t001})))",
////                1.0f, 0.45f,
////                -5);

//        tester.mustBelieve(cycles, "(hold:({t002}) &&+5 (at:({t001}) &&+5 open({t001})))",
//                1.0f, 0.45f,
//                -5);

    }




    @Test public void subsent_1_simpler()  {
        test()

        //.log()

        .input("hold:t2. :|:") //@ 0
        .inputAt(5, "at:t1. :|:")
        .inputAt(10, "(hold:t2 &&+5 (at:t1 &&+5 (open(t1) &&+5 [opened]:t1))).")
        .inputAt(15, "[opened]:t1. :|:")

        .mustBelieve(cycles, "open(t1)",
                //1.0f, 0.81f,
                //-5);
                1.0f, 0.34f,
                5);

    }

    @Test
    public void subsent_simultaneous()  {
        TestNAR tester = test();

        //TODO decide correct parentheses ordering

        //tester.nar.log();
        tester.input("[opened]:t1. :|:");
        tester.inputAt(10, "(hold:t2 &&+0 (at:t1 &&+0 (open(t1) &&+0 [opened]:t1))).");

        //TODO Narsese parser for this:
//        tester.mustBelieve(cycles, "( &&+0 ,(t1-->at),(t2-->hold),(t1-->[opened]),open(t1))",
//                1.0f, 0.42f,
//                0);

        tester.mustBelieve(cycles, "(&&, hold:t2, at:t1, open(t1)).",
                1.0f, 0.81f,
                0);


    }







    @Test
    public void conditional_abduction_test()  { //maybe to nal7 lets see how we will split these in the future
        TestNAR tester = test();

        tester.input("at:(SELF,{t003}). :|:");
        tester.inputAt(10, "(goto($1) ==>+5 at:(SELF,$1)).");

        tester.mustBelieve(cycles, "goto({t003})", 1.0f, 0.45f, -5);

    }

    @Test
    public void ded_with_var_temporal()  {
        TestNAR tester = test();

        tester.input("goto({t003}). :|:");
        tester.inputAt(10, "(goto($1) ==>+5 at:(SELF,$1)).");

        tester.mustBelieve(cycles, "at:(SELF,{t003})", 1.0f, 0.81f, 5);

    }

    @Test
    public void ded_with_var_temporal2()  {
        TestNAR tester = test();

        tester.input("goto({t003}). :|: ");
        tester.inputAt(10, "(goto($1) ==>+5 at:(SELF,$1)). ");

        tester.mustBelieve(cycles, "at:(SELF,{t003})", 1.0f, 0.81f,5);

    }



    @Test public void goal_deduction_tensed_conseq()  {
        TestNAR tester = test();

        tester.input("goto(x). :\\:");
        tester.inputAt(10, "(goto($1) ==>+5 at:(SELF,$1)).");

        tester.mustBelieve(cycles, "at:(SELF,x)", 1.0f, 0.81f, 0);
    }

    @Test
    public void condition_goal_deductionWithVariableElimination()  {
        test()
//        .log()
                .input("at:(SELF,{t003})!")
                .inputAt(10, "(goto($1) ==>+5 at:(SELF,$1)).")

                .mustDesire(cycles*4, "goto({t003})", 1.0f, 0.81f)
                .mustDesire(cycles, "goto({t003})", 1.0f, 0.81f, -5); //??

    }

    @Test
    public void goal_deduction()  {
        TestNAR tester = test();
        tester.input("x:y!");
        tester.input("(goto(z) ==>+5 x:y).");
        tester.mustDesire(cycles, "goto(z)", 1.0f, 0.81f);
    }
    @Test
    public void goal_deduction_alt()  {
        TestNAR tester = test();
        tester.input("x:y!");
        tester.input("(goto(x) ==>+5 x:y).");
        tester.mustDesire(cycles, "goto(x)", 1.0f, 0.81f);
    }
    @Test
    public void goal_deduction_delayed()  {
        TestNAR tester = test();

        tester.input("x:y!");
        tester.inputAt(10, "(goto(z) ==>+5 x:y).");
        tester.mustDesire(cycles, "goto(z)", 1.0f, 0.81f);
    }

    @Test public void goal_deduction_tensed_conseq_noVar()  {
        TestNAR tester = test();

        tester.input("goto(x). :\\:");
        tester.inputAt(10, "(goto(x) ==>+5 at:(SELF,x)).");

        tester.mustBelieve(cycles, "at:(SELF,x)", 1.0f, 0.81f, 0);
    }

    @Test
    public void belief_deduction_by_condition()  {
        TestNAR tester = test();

        tester.input("(open({t001}) ==>+5 [opened]:{t001}).");
        tester.inputAt(10, "open({t001}). :|:");

        tester.mustBelieve(cycles, "[opened]:{t001}", 1.0f, 0.81f, 15);

    }
    @Test
    public void condition_goal_deduction2()  {
        TestNAR tester = test();

        tester.input("a:b!");
        tester.inputAt(10, "(( c:d &&+5 e:f ) ==>+0 a:b).");

        tester.mustDesire(cycles, "( c:d &&+5 e:f)", 1.0f, 0.81f);
    }

    @Test
    public void further_detachment()  {
        TestNAR tester = test();


        tester.input("<(SELF,{t002}) --> reachable>. :|:");
        tester.inputAt(10, "(<(SELF,{t002}) --> reachable> &&+5 pick({t002}))!");

        tester.mustDesire(cycles, "pick({t002})", 1.0f, 0.81f);

    }


    @Test public void desiredFeedbackReversedIntoGoalEternal()  {
        TestNAR tester = test();
        tester.input("<y --> (/,^exe,x,_)>!");
        tester.mustDesire(5, "exe(x, y)", 1.0f, 0.9f);
    }


    @Test public void desiredFeedbackReversedIntoGoalNow()  {
        TestNAR tester = test();
        tester.input("<y --> (/,^exe,x,_)>! :|:");
        tester.mustDesire(5, "exe(x, y)", 1.0f, 0.9f, 0);
    }


    @Test
    public void condition_goal_deduction()  {
        TestNAR tester = test();

        tester.input("<(SELF,{t002}) --> reachable>! ");
        tester.inputAt(10, "((<($1,#2) --> on> &&+0 <(SELF,#2) --> at>) ==>+0 <(SELF,$1) --> reachable>).");

        tester.mustDesire(cycles, "(<(SELF,#1) --> at> &&+0 <({t002},#1) --> on>)", 1.0f, 0.81f);

    }

    @Test public void testExecutionResult()  {
        TestNAR tester = test();

        tester.input("<#y --> (/,^exe,x,_)>! :|:");
        tester.mustDesire(4, "exe(x, #1)", 1.0f, 0.9f, 0);

        //if (!(tester.nar instanceof SingleStepNAR)) {
        //tester.nar.log();
        //tester.mustBelieve(250, "exe(x, a)", 1.0f, 0.99f, 10);
        //        tester.mustBelieve(26, "<a --> (/, ^exe, x, _)>",
        //                exeFunc.getResultFrequency(),
        //                exeFunc.getResultConfidence(),
        //                exeFunc.getResultFrequency(),
        //                exeFunc.getResultConfidence(),
        //                6);
//            tester.nar.onEachFrame(n -> {
//                if (n.time() > 8)
//                    assertEquals(1, exeCount);
//            });
        //}

    }

    @Test
    public void detaching_single_premise()  {
        TestNAR tester = test();
        tester
                .input("(reachable:(SELF,{t002}) &&+5 pick({t002}))!")
                .mustDesire(cycles, "reachable:(SELF,{t002})", 1.0f, 0.81f)
                .mustDesire(cycles, "pick({t002})", 1.0f, 0.81f)
        ;
    }
    @Test
    public void detaching_single_premise_temporal()  {
        TestNAR tester = test();
        tester
                .input("(reachable:(SELF,{t002}) &&+5 pick({t002}))! :|:")
                .mustDesire(cycles, "reachable:(SELF,{t002})", 1.0f, 0.81f, 0)
                .mustDesire(cycles, "pick({t002})", 1.0f, 0.81f, 5)
        ;
    }
    @Test
    public void detaching_condition_2()  {
        TestNAR tester = test();

        tester.input("at:(SELF,{t001}). :|: ");
        tester.inputAt(10, "((at:(SELF,{t001}) &&+5 open({t001})) ==>+5 [opened]:{t001}). :|:");

        tester.mustBelieve(cycles, "(open({t001}) ==>+5 [opened]:{t001})", 1.0f, 0.81f, 0);

    }



    @Test
    public void goal_ded_2()  {
        TestNAR tester = test();

        tester.input("at:(SELF,{t001}). :|:");
        tester.inputAt(10, "(at:(SELF,{t001}) &&+5 open({t001}))!");

        tester.mustDesire(cycles, "open({t001})", 1.0f, 0.81f);

    }

    @Test
    public void condition_goal_deduction_3simplerReverse()  {
        test()
//                .log()
                .inputAt(1, "at:t003!")
                .inputAt(1, "(at:$1 ==>+5 goto:$1).")

                .mustDesire(cycles*2, "goto:t003", 1.0f, 0.45f);

    }
    public void condition_goal_deduction_3simpler()  {
        test()
                .log()
                .inputAt(1, "at:t003!")
                .inputAt(1, "(goto:$1 ==>+5 at:$1).")

                .mustDesire(cycles*2, "goto:t003", 1.0f, 0.81f);
    }






    @Test
    public void temporal_deduction_1()  {
        TestNAR tester = test();

        //tester.nar.log();
//        tester.input("pick({t002}). :\\:");
//        tester.inputAt(10, "(pick({t002}) ==>+5 hold:({t002})). :\\:");
//
//        tester.mustBelieve(cycles, "hold:({t002})", 1.0f, 0.81f, 0);

        tester.input("pick:t2. :\\:");
        tester.inputAt(10, "(pick:t2 ==>+5 hold:t2).");

        tester.mustBelieve(cycles, "hold:t2", 1.0f, 0.81f, 0); //-5 +5 = 0

    }

    @Test
    public void subgoal_2_small()  {
        TestNAR tester = test();

        tester.input("(hold:(SELF,y) &&+5 at:(SELF,x))!");

        tester.mustDesire(cycles, "hold:(SELF,y)", 1.0f, 0.81f);

    }


    @Test
    public void subgoal_2()  {
        TestNAR tester = test();

        tester.input("(<(SELF,{t002}) --> hold> &&+5 (at:(SELF,{t001}) &&+5 open({t001})))!");

        tester.mustDesire(cycles, "<(SELF,{t002}) --> hold>",
                1.0f, 0.81f);

    }


    @Test
    public void further_detachment_2()  {
        TestNAR tester = test();

        tester.input("reachable:(SELF,{t002}). :|:");
        tester.inputAt(3, "((reachable:(SELF,{t002}) &&+5 pick({t002})) ==>+7 hold:(SELF,{t002})).");

        tester.mustBelieve(cycles, "(pick({t002}) ==>+7 hold:(SELF, {t002}))", 1.0f, 0.81f,
                0);
                //5); <- ?? isnt this more correct?

    }


    @Test
    public void goal_deduction_2()  {
        TestNAR tester = test();

        tester.input("goto({t001}). :\\: "); //-5
        tester.inputAt(7, "(goto($1) ==>+2 at:(SELF,$1)). ");

        tester.mustBelieve(cycles, "at:(SELF,{t001})", 1.0f, 0.81f, -3);

    }

    @Test
    public void condition_goal_deduction_2()  {
        TestNAR tester = test();

        tester.input("<({t002},{t003}) --> on>. :|:");
        tester.inputAt(10, "(<({t002},#1) --> on> &&+0 <(SELF,#1) --> at>)!");

        tester.mustDesire(cycles, "<(SELF,{t003}) --> at>", 1.0f, 0.81f, 0);

    }

    @Test
    public void temporal_goal_detachment_1()  {
        TestNAR tester = test();


        tester.input("<(SELF,{t002}) --> hold>.");
        tester.inputAt(10, "(<(SELF,{t002}) --> hold> &&+5 (at:(SELF,{t001}) &&+5 open({t001}) ))!");

        tester.mustDesire(cycles, "( at:(SELF,{t001}) &&+5 open({t001}))", 1.0f, 0.81f);

    }


    @Test
    public void detaching_single_premise2()  {
        TestNAR tester = test();

        tester.input("(at:(SELF,{t001}) &&+5 open({t001}) )!");


        tester.mustDesire(cycles, "at:(SELF,{t001})", 1.0f, 0.81f);

    }

    @Test
    public void detaching_condition()  {
        TestNAR tester = test();

        tester.input("( ( hold:(SELF,{t002}) &&+5 (at:(SELF,{t001}) &&+5 open({t001}))) ==>+5 [opened]:{t001}).");
        tester.inputAt(10, "hold:(SELF,{t002}). :|:");

        tester.mustBelieve(cycles, "((at:(SELF,{t001}) &&+5 open({t001})) ==>+5 [opened]:{t001})", 1.0f, 0.81f, 10);

    }

    @Test
    public void subgoal_1_abd()  {
        TestNAR tester = test();

        tester.input("[opened]:{t001}. :|:");
        tester.inputAt(10, "((hold:(SELF,{t002}) &&+5 ( at:(SELF,{t001}) &&+5 open({t001}))) ==>+5 [opened]:{t001}).");

        tester.mustBelieve(cycles, "( hold:(SELF,{t002}) &&+5 ( at:(SELF,{t001}) &&+5 open({t001})))",
                1.0f, 0.45f,
                -5);

    }

    @Test
    public void temporal_deduction_2()  {
        TestNAR tester = test();

        tester.input("((hold:(SELF,{t002}) &&+5 (at:(SELF,{t001}) &&+5 open({t001}))) ==>+5 [opened]:{t001}).");
        tester.inputAt(10, "hold:(SELF,{t002}). :|: ");

        tester.mustBelieve(cycles, "((at:(SELF,{t001}) &&+5 open({t001})) ==>+5 [opened]:{t001})", 1.0f, 0.81f, 10);

    }

    @Test public void arbitraryConjunction() {
        TestNAR tester = test();
        tester.believe("believe(x)."); //psuedo operators since operation is > nal6
        tester.believe("want(x)."); //psuedo operators since operation is > nal6
        tester.believe("((believe($1) && want($1)) ==> grateful($1))");
        tester.mustBelieve(cycles*3, "(believe(x) && want(x)).", 1.00f, 0.81f); //en("there is a lock which is opened by key1");
        tester.mustBelieve(cycles*3, "grateful(x).", 1.00f, 0.42f); //en("there is a lock which is opened by key1");


    }

    @Test
    public void goalInferredFromSimilarity()  {
        TestNAR tester = test();

        tester.input("(a:b<->c:d).");
        tester.input("c:d!");
        tester.mustDesire(cycles, "a:b", 1.0f, 0.81f);
    }
}
