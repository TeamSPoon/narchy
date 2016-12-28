package nars.experiment.misc;

import jcog.data.random.XorShift128PlusRandom;
import nars.$;
import nars.NAR;
import nars.NAgent;
import nars.Param;
import nars.concept.ActionConcept;
import nars.concept.SensorConcept;
import nars.index.term.map.CaffeineIndex;
import nars.nar.Default;
import nars.util.exe.Executioner;
import nars.util.exe.SynchronousExecutor;
import nars.conceptualize.DefaultConceptBuilder;
import nars.remote.NAgents;
import nars.task.DerivedTask;
import nars.time.FrameTime;

import java.util.Arrays;

import static java.lang.System.out;


/**
 * Created by me on 5/4/16.
 */
public class Line1DContinuous extends NAgent {

    static {
        Param.DEBUG = false;
    }

    public interface IntToFloatFunction {
        float valueOf(int i);
    }

    private final IntToFloatFunction targetFunc;
    int size;
    boolean print;
    private float yHidden;
    private float yEst;
    float speed = 5f;
    final float[] ins;

    public Line1DContinuous(NAR n, int size, IntToFloatFunction target) {
        super("x", n, 1);
        this.size = size;
        ins = new float[size*2];
        this.targetFunc = target;

        yEst = size/2; //NAR estimate of Y
        yHidden = size/2; //actual best Y used by loss function


        for (int i = 0; i < size; i++) {
            int ii = i;
            //hidden
            sensors.add(new SensorConcept(
                    $.func("h", $.the("x"), $.the( i)),
                    //$.p($.the("h"), $.the(i)),
                    n, ()->{
                return ins[ii];
            }, (v) -> $.t(v, alpha)));

            //estimated
            sensors.add(new SensorConcept(
                    $.func("e", $.the("x"), $.the( i)),
                    //$.func("e", $.the(i)),
                    //$.p($.the("e"), $.the(i)),
                    n, ()->{
                return ins[size + ii];
            }, (v) -> $.t(v, alpha)));
        }

        ActionConcept a;

        actions.add(a = new ActionConcept("move(x)", n, (b, d) -> {
            if (d!=null) {
                float v =
                        //d.expectation();
                        d.freq();
                float yBefore = yEst;
                yEst += (v - 0.5f)*speed;

                float f;
                if (yBefore==0 || yBefore == 1) {
                    //wall
                    f = 0.5f;
                } else {
                    f = v;
                }

                return $.t(f, alpha);
                //return d;
            }
            return null;
        }));

//        actions.add(a1 = new ActionConcept("e(left)", n, (b, d) -> {
//            if (d!=null) {
//                float v =
//                        //d.expectation();
//                        d.freq();
//                yEst += v*speed;
//                return $.t(d.freq(), gamma);
//            }
//            return null;
//        }));
//        actions.add(a2 = new ActionConcept("e(right)", n, (b, d) -> {
//            if (d!=null) {
//                float v =
//                        //d.expectation();
//                        d.freq();
//                yEst += v*(-speed);
//                return $.t(d.freq(), gamma);
//            }
//            return null;
//        }));



//        n.onTask(t -> {
//           if (t instanceof DerivedTask
//                   && t.punc() == GOAL
//                   && (t.term().equals(a1.term()) || t.term().equals(a2.term()))
//                   //&& t.term().containsTermRecursively(a.term())
//            ) {
//                System.out.println(t.proof());
//                System.out.println();
////                n.runLater(()->a1.print());
////               n.runLater(()->a2.print());
//           }
//        });

        trace = false;

    }

    @Override
    protected float act() {


        yHidden = Math.round(targetFunc.valueOf((int) now) * (size-1));

        yHidden = Math.min(size-1, Math.max(0, yHidden));
        yEst    = Math.min(size-1, Math.max(0, yEst));



        //update perceived state:
        //1*size
        //        Arrays.fill(ins, 0.5f);
        //        ins[yHidden] += 0.5f;
        //        ins[yEst] -= 0.5f;
        //2*size
        Arrays.fill(ins, 0f);
        float smoothing = 1/2f;
        for (int i = 0; i < size; i++) {
            ins[i] = Math.abs(yHidden - i)/(size*smoothing);
            ins[i + this.size] = Math.abs(yEst - i)/(size*smoothing);
        }
//        ins[Math.round(yHidden)] = 1f;
//        ins[Math.round(this.size + yEst)] = 1f;


        float dist =  Math.abs(yHidden - yEst) / this.size;

        //float closeness = 1f - dist;
        //float reward = ((closeness*closeness*closeness) -0.5f)*2f;
        //float reward = dist < speed ? (0.5f/(1f+dist)) : -dist;
        float reward =
                -dist * 2f + 1f;
                //(1f-dist)*(1f-dist);
                //1f / (1+dist*dist);



//        float de;
//        switch (aa) {
//            case 1: //right
//                de = 1f*speed;
//                break;
//            case 0: //left
//                de = -1f*speed;
//                break;
////                case 3:
////                    de = 1f * speed/4f;
////                    break;
////                case 4:
////                    de = -1f * speed/4f;
////                    break;
//            case 2:
//            default:
//                de = 0f; //nothing
//                break;
//        }




        if (yEst > this.size -1) yEst = this.size -1;
        if (yEst < 0) yEst = 0;


        if (print) {


            int colActual = Math.round(yHidden);
            int colEst = Math.round(yEst);
            for (int i = 0; i < this.size; i++) {

                char c;
                if (i == colActual && i == colEst) {
                    c = '@';
                }else if (i == colActual)
                    c = 'X';
                else if (i == colEst)
                    c = '+';
                else
                    c = '.';

                out.print(c);
            }

            //out.print(Texts.n2(ins));

            //out.print(' ');
            //out.print(reward);
            out.print(' ');
            out.print(summary());
            out.println();
        }

        return reward;
    }


    public static IntToFloatFunction sine(float targetPeriod) {
        return (t) -> 0.5f + 0.5f * (float) Math.sin(t / (targetPeriod));
        //+ 0.05f * (a * (float)Math.cos(t / (targetPeriod/3f))-1)
        //return 0.5f + 0.5f * (float)Math.tan(t / (targetPeriod)) + (float)Math.random()*0.1f;
    }
    public static IntToFloatFunction random(float targetPeriod) {
        return (t) -> (((((int)(t/targetPeriod)) * 31) ^ 37) % 256)/256.0f;

        //+ 0.05f * (a * (float)Math.cos(t / (targetPeriod/3f))-1)
        //return 0.5f + 0.5f * (float)Math.tan(t / (targetPeriod)) + (float)Math.random()*0.1f;
    }

    public static void main(String[] args) {

        XorShift128PlusRandom rng = new XorShift128PlusRandom((int)(Math.random()*1000));
        int conceptsPerCycle = 4;

        final Executioner exe =
                //new MultiThreadExecutioner(2, 2048);
                new SynchronousExecutor();

        Default nar = new Default(1024,
                conceptsPerCycle, 2, 8, rng,
                new CaffeineIndex(new DefaultConceptBuilder(), 1024*16, false, exe),
                new FrameTime(1f), exe
        );
        nar.termVolumeMax.set(19);


        nar.beliefConfidence(0.9f);
        nar.goalConfidence(0.9f);

        //nar.truthResolution.setValue(0.02f);


        Line1DContinuous l = new Line1DContinuous(nar, 6,
                //sine(50)
                random(16)
        );


        NAgents.chart(l);

        //nar.logSummaryGT(System.out, 0.5f);
        nar.onTask(t -> {
            if (t instanceof DerivedTask && t.isGoal())
                System.out.println(t.proof());
        });

        l.print = true;
        l.runRT(25, 15000).join();
        //l.run(2000);


        NAR.printActiveTasks(nar, true);
        NAR.printActiveTasks(nar, false);

        l.predictors.forEach(p->{
           nar.concept(p).print();
        });
        System.out.println("AVG SCORE=" + l.rewardSum()/ nar.time());

    }

}
