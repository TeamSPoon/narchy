package nars.experiment.arkanoid;


import jcog.math.FloatNormalized;
import nars.$;
import nars.NAR;
import nars.Param;
import nars.concept.ActionConcept;
import nars.remote.NAgents;

public class Arkancide extends NAgents {

    public static void main(String[] args) {
        Param.DEBUG = false;

        //runRT(Arkancide::new);
        //nRT(Arkancide::new, 25, 5);

        NAR nar = runRT(Arkancide::new, 40, 10);

        //nar.beliefConfidence(0.75f);
        //nar.goalConfidence(0.75f);
    }


    final int visW = 32;
    final int visH = 16;

    final int afterlife = 60;

    float paddleSpeed = 40f;


    final Arkanoid noid;

    private float prevScore;


    public Arkancide(NAR nar) {
        super("noid", nar);

        noid = new Arkanoid() {
            @Override
            protected void die() {
                nar.time.tick(afterlife);
                super.die();
            }
        };

        float resX = Math.max(0.01f, 1f/visW); //dont need more resolution than 1/pixel_width
        float resY = Math.max(0.01f, 1f/visH); //dont need more resolution than 1/pixel_width

        senseNumberBi( "x(paddle)", new FloatNormalized(()->noid.paddle.x)).resolution(resX);
        senseNumberBi( "x(ball)", new FloatNormalized(()->noid.ball.x)).resolution(resX);
        senseNumberBi( "y(ball)", new FloatNormalized(()->noid.ball.y)).resolution(resY);
        senseNumberBi("vx(ball)", new FloatNormalized(()->noid.ball.velocityX));
        senseNumberBi("vy(ball)", new FloatNormalized(()->noid.ball.velocityY));

        addCamera("cam", noid, visW, visH);
        //addCameraRetina("zoom(cam(noid))", noid, visW/2, visH/2, (v) -> $.t(v, alpha));


        action(new ActionConcept( "dx(paddle)" , nar, (b, d) -> {

            float pct;
            if (d != null) {
                pct = noid.paddle.moveTo(d.freq(), paddleSpeed); //* d.conf());
            } else {
                pct = noid.paddle.x / noid.SCREEN_WIDTH; //unchanged
            }
            return $.t(pct, nar.confidenceDefault('.'));
            //return null; //$.t(0.5f, alpha);
        }).feedbackResolution(resX));

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


}