package nars.experiment;


import jcog.Util;
import jcog.math.FloatRange;
import jcog.signal.wave2d.ScaledBitmap2D;
import nars.$;
import nars.NAR;
import nars.NAgentX;
import nars.concept.sensor.AbstractSensor;
import nars.gui.BagSpectrogram;
import nars.gui.sensor.VectorSensorView;
import nars.index.concept.AbstractConceptIndex;
import nars.sensor.Bitmap2DSensor;
import nars.term.atom.Atomic;
import nars.video.SwingBitmap2D;
import spacegraph.video.Draw;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static nars.Op.*;
import static spacegraph.SpaceGraph.window;

public class Arkancide extends NAgentX {

    static boolean numeric = true;
    static boolean cam = true;

    public final FloatRange ballSpeed = new FloatRange(1.75f, 0.04f, 6f);


    //final int visW = 48, visH = 32;
    //final int visW = 24, visH = 16;
    final int visW = 16, visH = 12;
    //final int visW = 8, visH = 6;


    static float paddleSpeed;


    final Arkanoid noid;

    private float prevScore;

    public static void main(String[] args) {


        runRT((NAR n) -> {

            //n.timeResolution.setAt(25); //50fps resolution


            return new Arkancide(n, cam, numeric);

        }, 20);


    }


    public Arkancide(NAR nar) {
        this(nar, cam, numeric);
    }

    public Arkancide(NAR nar, boolean cam, boolean numeric) {
        super("noid", nar);


        noid = new Arkanoid();


        paddleSpeed = 40 * noid.BALL_VELOCITY;


        //initUnipolar();
        //initBipolarDirect();
        //initBipolarRelative();
        initPushButton();

        float resX = 0.02f;
        float resY = 0.02f;

        if (cam) {

            Bitmap2DSensor<ScaledBitmap2D> cc = senseCamera(id /*$.the("cam")*/, new ScaledBitmap2D(
                    new SwingBitmap2D(noid)

                    , visW, visH


            )/*.blur()*/);
            //cc.resolution(0.05f);

            VectorSensorView vsv = new VectorSensorView(cc, nar);
//            onFrame(vsv::update);
            window(vsv.withControls(), 500, 500);


        }


        if (numeric) {
            AbstractSensor px = senseNumberBi($.inh($.the("px"), id), (() -> noid.paddle.x / noid.getWidth())).resolution(resX);
            AbstractSensor dx = senseNumberBi($.inh($.the("dx"), id), (() -> 0.5f + 0.5f * (noid.ball.x - noid.paddle.x) / noid.getWidth())).resolution(resX);
            senseNumberBi($.inh($.p("b", "x"), id), (() -> (noid.ball.x / noid.getWidth()))).resolution(resX);
            senseNumberBi($.inh($.p("b", "y"), id), (() -> 1f - (noid.ball.y / noid.getHeight()))).resolution(resY);

//            window(NARui.beliefCharts(dx.components(), nar), 500, 500);

        }

        /*action(new ActionConcept( $.func("dx", "paddleNext", "noid"), nar, (b, d) -> {
            if (d!=null) {
                paddleSpeed = Util.round(d.freq(), 0.2f);
            }
            return $.t(paddleSpeed, nar.confidenceDefault('.'));
        }));*/



        rewardNormalized("score", -1, +1, ()->{
            noid.BALL_VELOCITY = ballSpeed.floatValue();
            float nextScore = noid.next();
            float dReward = Math.max(-1f, Math.min(1f, nextScore - prevScore));
            this.prevScore = nextScore;

            //return dReward;
            return dReward != 0 ? dReward : Float.NaN;
        });

        /*actionTriState*/


//        nar.onTask(t->{
//            if (t.isGoal()) {
//                if (!t.isInput()) {
//                    if (t.isEternal())
//                        System.err.println(t);
//                    else
//                        System.out.println(t);
//                }
//            }
////           if (t.isQuest()) {
////               nar.concepts.stream().filter(x -> x.op() == IMPL && x.sub(1).equals(t.target())).forEach(i -> {
////                   //System.out.println(i);
////                   //nar.que(i.sub(0), QUEST, t.start(), t.end());
////                   nar.want(i.sub(0), Tense.Present,1f, 0.9f);
////               });
////           }
//        },GOAL);

    }

    private void initBipolarRelative() {
        actionBipolar($.the("X"), false, (dx) -> {


            if (noid.paddle.move(dx * paddleSpeed))
                return dx;
            else
                return 0;
        });
    }

    private void initBipolarDirect() {
        actionBipolar($.the("X"), true, (dx) -> {
            noid.paddle.set(dx / 2f + 0.5f);
            return dx;
        });
    }

    private void initPushButton() {
        actionPushButtonMutex($.inh(Atomic.the("L"),id), $.inh(Atomic.the("R"),id),
                (b) -> b && noid.paddle.move(-paddleSpeed),
                (b) -> b && noid.paddle.move(+paddleSpeed)
        );


    }

    private void initUnipolar() {
        actionUnipolar($.the("L"), (u) -> noid.paddle.move(-paddleSpeed * u) ? u : 0);
        actionUnipolar($.the("R"), (u) -> noid.paddle.move(+paddleSpeed * u) ? u : 0);
    }



    /**
     * https:
     */
    public static class Arkanoid extends Canvas implements KeyListener {

        int score;


        public static final int SCREEN_WIDTH = 250;
        public static final int SCREEN_HEIGHT = 250;

        public static final int BLOCK_LEFT_MARGIN = 4;
        public static final int BLOCK_TOP_MARGIN = 15;

        public static final float BALL_RADIUS = 15.0f;
        public float BALL_VELOCITY = 0.5f;

        public static final float PADDLE_WIDTH = 40.0f;
        public static final float PADDLE_HEIGHT = 20.0f;

        public static final float BLOCK_WIDTH = 40.0f;
        public static final float BLOCK_HEIGHT = 15.0f;

        public static final int COUNT_BLOCKS_X = 5;
        public static final int COUNT_BLOCKS_Y = 1; /* 3 */

        public static final float FT_STEP = 4.0f;


        /* GAME VARIABLES */

        public Arkanoid() {

            setSize(SCREEN_WIDTH, SCREEN_HEIGHT);

//            this.setUndecorated(false);
//            this.setResizable(false);
//
//
//            if (visible)
//                this.setVisible(true);

            paddle.x = SCREEN_WIDTH / 2;


//            setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
//
//            new Timer(1000 / FPS, (e) -> {
//                repaint();
//            }).start();

            reset();
        }

        @Override
        public void paint(Graphics g) {
//            super.paint(g);
            g.setColor(Color.black);
            g.fillRect(0, 0, getWidth(), getHeight());

            ball.draw(g);
            paddle.draw(g);
            for (Brick brick : bricks) {
                brick.draw(g);
            }
        }

        public final Paddle paddle = new Paddle(SCREEN_WIDTH / 2, SCREEN_HEIGHT - PADDLE_HEIGHT);
        public final Ball ball = new Ball(SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2);
        public final Collection<Brick> bricks = Collections.newSetFromMap(new ConcurrentHashMap());


        abstract static class GameObject {
            abstract float left();

            abstract float right();

            abstract float top();

            abstract float bottom();
        }

        static class Rectangle extends GameObject {

            public float x, y;
            public float sizeX;
            public float sizeY;

            @Override
            float left() {
                return x - sizeX / 2.0f;
            }

            @Override
            float right() {
                return x + sizeX / 2.0f;
            }

            @Override
            float top() {
                return y - sizeY / 2.0f;
            }

            @Override
            float bottom() {
                return y + sizeY / 2.0f;
            }

        }


        void increaseScore() {
            score++;
            if (score == (COUNT_BLOCKS_X * COUNT_BLOCKS_Y)) {
                win();
            }
        }

        protected void win() {
            reset();
        }

        protected void die() {
            reset();
        }

        class Paddle extends Rectangle {


            public Paddle(float x, float y) {
                this.x = x;
                this.y = y;
                this.sizeX = PADDLE_WIDTH;
                this.sizeY = PADDLE_HEIGHT;
            }

            /**
             * returns percent of movement accomplished
             */
            public synchronized boolean move(float dx) {
                float px = x;
                x = Util.clamp(x + dx, sizeX, SCREEN_WIDTH - sizeX);
                return !Util.equals(px, x, 1f);
            }


            void draw(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect((int) (left()), (int) (top()), (int) sizeX, (int) sizeY);
            }

            public void set(float freq) {
                x = freq * SCREEN_WIDTH;
            }

            public float moveTo(float target, float paddleSpeed) {
                target *= SCREEN_WIDTH;

                if (Math.abs(target - x) <= paddleSpeed) {
                    x = target;
                } else if (target < x) {
                    x -= paddleSpeed;
                } else {
                    x += paddleSpeed;
                }

                x = Math.min(x, SCREEN_WIDTH - 1);
                x = Math.max(x, 0);

                return x / SCREEN_WIDTH;
            }
        }


        static final AtomicInteger brickSerial = new AtomicInteger(0);

        class Brick extends Rectangle implements Comparable<Brick> {

            int id;
            boolean destroyed;

            Brick(float x, float y) {
                this.x = x;
                this.y = y;
                this.sizeX = BLOCK_WIDTH;
                this.sizeY = BLOCK_HEIGHT;
                this.id = brickSerial.incrementAndGet();
            }

            void draw(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect((int) left(), (int) top(), (int) sizeX, (int) sizeY);
            }

            @Override
            public int compareTo(Brick o) {
                return Integer.compare(id, o.id);
            }
        }

        class Ball extends GameObject {

            public float x, y;
            float radius = BALL_RADIUS;


            public float velocityX;
            public float velocityY;

            Ball(int x, int y) {
                this.x = x;
                this.y = y;
                setVelocityRandom();
            }

            public void setVelocityRandom() {
                this.setVelocity(BALL_VELOCITY, (float) (Math.random() * -Math.PI * (2 / 3f) + -Math.PI - Math.PI / 6));
            }

            public void setVelocity(float speed, float angle) {
                this.velocityX = (float) Math.cos(angle) * speed;
                this.velocityY = (float) Math.sin(angle) * speed;
            }

            void draw(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillOval((int) left(), (int) top(), (int) radius * 2,
                        (int) radius * 2);
            }

            void update(Paddle paddle) {
                x += velocityX * FT_STEP;
                y += velocityY * FT_STEP;

                if (left() < 0)
                    velocityX = BALL_VELOCITY;
                else if (right() > SCREEN_WIDTH)
                    velocityX = -BALL_VELOCITY;
                if (top() < 0) {
                    velocityY = BALL_VELOCITY;
                } else if (bottom() > SCREEN_HEIGHT) {
                    velocityY = -BALL_VELOCITY;
                    x = paddle.x;
                    y = paddle.y - 50;
                    score--;
                    die();
                }

            }

            @Override
            float left() {
                return x - radius;
            }

            @Override
            float right() {
                return x + radius;
            }

            @Override
            float top() {
                return y - radius;
            }

            @Override
            float bottom() {
                return y + radius;
            }

        }

        boolean isIntersecting(GameObject mA, GameObject mB) {
            return mA.right() >= mB.left() && mA.left() <= mB.right()
                    && mA.bottom() >= mB.top() && mA.top() <= mB.bottom();
        }

        void testCollision(Paddle mPaddle, Ball mBall) {
            if (!isIntersecting(mPaddle, mBall))
                return;
            mBall.velocityY = -BALL_VELOCITY;
            if (mBall.x < mPaddle.x)
                mBall.velocityX = -BALL_VELOCITY;
            else
                mBall.velocityX = BALL_VELOCITY;
        }

        void testCollision(Brick mBrick, Ball mBall) {
            if (!isIntersecting(mBrick, mBall))
                return;

            mBrick.destroyed = true;

            increaseScore();

            float overlapLeft = mBall.right() - mBrick.left();
            float overlapRight = mBrick.right() - mBall.left();
            float overlapTop = mBall.bottom() - mBrick.top();
            float overlapBottom = mBrick.bottom() - mBall.top();

            boolean ballFromLeft = overlapLeft < overlapRight;
            boolean ballFromTop = overlapTop < overlapBottom;

            float minOverlapX = ballFromLeft ? overlapLeft : overlapRight;
            float minOverlapY = ballFromTop ? overlapTop : overlapBottom;

            if (minOverlapX < minOverlapY) {
                mBall.velocityX = ballFromLeft ? -BALL_VELOCITY : BALL_VELOCITY;
            } else {
                mBall.velocityY = ballFromTop ? -BALL_VELOCITY : BALL_VELOCITY;
            }
        }

        void initializeBricks(Collection<Brick> bricks) {


            bricks.clear();

            for (int iX = 0; iX < COUNT_BLOCKS_X; ++iX) {
                for (int iY = 0; iY < COUNT_BLOCKS_Y; ++iY) {
                    bricks.add(new Brick((iX + 1) * (BLOCK_WIDTH + 3) + BLOCK_LEFT_MARGIN,
                            (iY + 2) * (BLOCK_HEIGHT + 3) + BLOCK_TOP_MARGIN));
                }
            }

        }


        public void reset() {
            initializeBricks(bricks);
            ball.x = SCREEN_WIDTH / 2;
            ball.y = SCREEN_HEIGHT / 2;
            ball.setVelocityRandom();
        }


        public float next() {


            ball.update(paddle);
            testCollision(paddle, ball);


            Iterator<Brick> it = bricks.iterator();
            while (it.hasNext()) {
                Brick brick = it.next();
                testCollision(brick, ball);
                if (brick.destroyed) {
                    it.remove();
                }
            }


            return score;
        }


        @Override
        public void keyPressed(KeyEvent event) {


            switch (event.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    paddle.move(-paddleSpeed);
                    break;
                case KeyEvent.VK_RIGHT:
                    paddle.move(+paddleSpeed);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                case KeyEvent.VK_RIGHT:
                    break;
                default:
                    break;
            }
        }

        @Override
        public void keyTyped(KeyEvent arg0) {

        }


    }
}




































