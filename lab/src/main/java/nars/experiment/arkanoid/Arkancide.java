package nars.experiment.arkanoid;


import nars.$;
import nars.NAR;
import nars.Param;
import nars.concept.ActionConcept;
import nars.remote.NAgents;

public class Arkancide extends NAgents {


//    final int visW = 48;
//    final int visH = 24;
    final int visW = 32;
    final int visH = 16;

    final int afterlife = 60;

    float paddleSpeed = 40f;


    final Arkanoid noid;

    private float prevScore;


    public Arkancide(NAR nar) {
        super(nar);

        noid = new Arkanoid() {
            @Override
            protected void die() {
                nar.time.tick(afterlife);
                super.die();
            }
        };

        senseNumberBi("noid(paddle,x,p)", ()->noid.paddle.x);
        senseNumberBi("noid(ball,x,p)", ()->noid.ball.x);
        senseNumberBi("noid(ball,y,p)", ()->noid.ball.y);
        senseNumberBi("noid(ball,x,v)", ()->noid.ball.velocityX);
        senseNumberBi("noid(ball,y,v)", ()->noid.ball.velocityY);

        addCamera("noid", noid, visW, visH);
        //addCameraRetina("noid", noid, visW/2, visH/2, (v) -> t(v, alpha));

        action(new ActionConcept(
                //"happy:noid(paddle,x)"
                "noid(leftright)"
                , nar, (b,d)->{
            if (d!=null) {
                float pct = noid.paddle.moveTo(d.freq(), paddleSpeed ); //* d.conf());
                return $.t(pct, gamma);
            }
            return null; //$.t(0.5f, alpha);
        }));
//        action(new ActionConcept(
//                //"happy:noid(paddle,x)"
//                "(leftright)"
//                , nar, (b,d)->{
//            if (d!=null) {
//                //TODO add limits for feedback, dont just return the value
//                //do this with a re-usable feedback interface because this kind of acton -> limitation detection will be common
//                float pct = noid.paddle.move((d.freq() - 0.5f) * paddleSpeed);
////                if (pct > 0)
////                    return $.t(d.freq(), gamma*pct);
//                    //return $.t(Util.lerp(d.freq(), 0.5f, pct), alpha);
//
//                return $.t(d.freq(), gamma);
//
//            }
//            return null; //$.t(0.5f, alpha);
//        }));


    }

    @Override
    protected float act() {
        float nextScore = noid.next();
        float reward = nextScore - prevScore;
        this.prevScore = nextScore;
        if (reward == 0)
            return Float.NaN;
        return reward;
    }

    public static void main(String[] args) {
        Param.DEBUG = true;

        //runRT(Arkancide::new);
        //nRT(Arkancide::new, 25, 5);
        runRT(Arkancide::new, 30, 7);
    }


}