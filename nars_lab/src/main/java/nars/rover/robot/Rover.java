/*
 * Here comes the text of your license
 * Each line should be prefixed with  * 
 */
package nars.rover.robot;

import nars.NAR;
import nars.java.MethodOperator;
import nars.java.NALObjects;
import nars.rover.Sim;
import nars.rover.obj.VisionRay;
import nars.task.Task;
import nars.task.in.ChangedTextInput;
import nars.truth.Truth;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Color3f;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Fixture;

import static nars.rover.Sim.f4;

/**
 * Triangular mobile vehicle
 */
public class Rover extends AbstractPolygonBot {

    private final ChangedTextInput spin;
    private final ChangedTextInput feltAngularVelocity;

    private final ChangedTextInput feltSpeed;
    private final ChangedTextInput feltSpeedAvg;
    private final ChangedTextInput mouthInput;

    //float tasteDistanceThreshold = 1.0f;
    final static int retinaPixels = 8;
    final NALObjects objs;
    int retinaRaysPerPixel = 12; //rays per vision sensor
    float aStep = (float) (Math.PI * 2f) / retinaPixels;
    float L = 25f; //vision distance
    Vec2 mouthPoint = new Vec2(2.7f, 0); //0.5f);
    @Deprecated
    double mouthArc = Math.PI / 6f; //in radians
    float biteDistanceThreshold = 0.05f;
    float linearDamping = 0.8f;
    float angularDamping = 0.6f;
    float restitution = 0.9f; //bounciness


//    final SimpleAutoRangeTruthFrequency linearVelocity;
//    final SimpleAutoRangeTruthFrequency motionAngle;
//    final SimpleAutoRangeTruthFrequency facingAngle;

    //public class DistanceInput extends ChangedTextInput
    float friction = 0.5f;
    private MotorControls motors;

    public Rover(String id, NAR nar) {
        super(id, nar);

        objs = new NALObjects(nar);

        spin = new ChangedTextInput(nar);
        mouthInput = new ChangedTextInput(nar);
        feltAngularVelocity = new ChangedTextInput(nar);
        feltSpeed = new ChangedTextInput(nar);
        feltSpeedAvg = new ChangedTextInput(nar);
//
//
//        linearVelocity = new SimpleAutoRangeTruthFrequency(nar, nar.term("<motion-->[linear]>"), new AutoRangeTruthFrequency(0.0f));
//        motionAngle = new SimpleAutoRangeTruthFrequency(nar, nar.term("<motion-->[angle]>"), new BipolarAutoRangeTruthFrequency());
//        facingAngle = new SimpleAutoRangeTruthFrequency(nar, nar.term("<motion-->[facing]>"), new BipolarAutoRangeTruthFrequency());

    }

    @Override
    public void init(Sim p) {
        super.init(p);

        try {
            addMotorController();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public RoboticMaterial getMaterial() {
        return new NARRoverMaterial(this, nar);
    }

    @Override
    protected Body newTorso() {
        PolygonShape shape = new PolygonShape();

        Vec2[] vertices = {new Vec2(3.0f, 0.0f), new Vec2(-1.0f, +2.0f), new Vec2(-1.0f, -2.0f)};
        shape.set(vertices, vertices.length);
        //shape.m_centroid.set(bodyDef.position);
        BodyDef bd = new BodyDef();
        bd.linearDamping = (linearDamping);
        bd.angularDamping = (angularDamping);
        bd.type = BodyType.DYNAMIC;
        bd.position.set(0, 0);

        Body torso = getWorld().createBody(bd);
        Fixture f = torso.createFixture(shape, mass);
        f.setRestitution(restitution);
        f.setFriction(friction);


        //for (int i = -pixels / 2; i <= pixels / 2; i++) {
        for (int i = 0; i < retinaPixels; i++) {
            final float angle = /*MathUtils.PI / 2f*/ aStep * i;
            final boolean eats = ((angle < mouthArc / 2f) || (angle > (Math.PI * 2f) - mouthArc / 2f));

            //System.out.println(i + " " + angle + " " + eats);

            VisionRay v = new VisionRay(this, torso,
                    /*eats ?*/ mouthPoint /*: new Vec2(0,0)*/,
                    angle, aStep, retinaRaysPerPixel, L) {


                @Override
                public void onTouch(Body touched, float di) {
                    if (touched == null) return;

                    if (touched.getUserData() instanceof Sim.Edible) {

                        if (eats) {

                            if (di <= biteDistanceThreshold)
                                eat(touched);
                            /*} else if (di <= tasteDistanceThreshold) {
                                //taste(touched, di );
                            }*/
                        }
                    }
                }
            };
            v.sparkColor = new Color3f(0.5f, 0.4f, 0.4f);
            v.normalColor = new Color3f(0.4f, 0.4f, 0.4f);


            draw.addLayer(v);

            senses.add(v);
        }
        return torso;
    }

    protected void addMotorController() throws Exception {

        motors = objs.the("motor", MotorControls.class, this);


    }

    //public static final ConceptDesire strongestTask = (c ->  c.getGoals().topEternal().getExpectation() );

    @Override
    protected void feelMotion() {
        //radians per frame to angVelocity discretized value
        float xa = torso.getAngularVelocity();
        float angleScale = 1.50f;
        String angDir = xa > 0 ? "r" : "l";
        float angSpeed = (float) (Math.log(Math.abs(xa * angleScale) + 1f)) / 2f;
        float maxAngleVelocityFelt = 0.8f;
        if (angSpeed > maxAngleVelocityFelt) {
            angSpeed = maxAngleVelocityFelt;
        }
//        if (angVelocity < 0.1) {
//            feltAngularVelocity.set("rotated(" + f(0) + "). :|: %0.95;0.90%");
//            //feltAngularVelocity.set("feltAngularMotion. :|: %0.00;0.90%");
//        } else {
//            String direction;
//            if (xa < 0) {
//                direction = sim.angleTerm(-MathUtils.PI);
//            } else /*if (xa > 0)*/ {
//                direction = sim.angleTerm(+MathUtils.PI);
//            }
//            feltAngularVelocity.set("rotated(" + f(angVelocity) + "," + direction + "). :|:");
//            // //feltAngularVelocity.set("<" + direction + " --> feltAngularMotion>. :|: %" + da + ";0.90%");
//        }


        float linSpeed = torso.getLinearVelocity().length();
        //linearVelocity.observe(linVelocity);


        Vec2 currentPosition = torso.getWorldCenter();
        //if (!positions.isEmpty()) {
            //Vec2 movement = currentPosition.sub(positions.poll());
            //double theta = Math.atan2(movement.y, movement.x);
            //motionAngle.observe((float)theta);
        //}
        positions.addLast(currentPosition.clone());


        String torsoAngle = sim.angleTerm(torso.getAngle());

        if (linSpeed == 0)
            feltSpeed.set("(--, motion:#anything). :|:");
        else
            feltSpeed.set("motion:(" + f4(linSpeed) + ',' + torsoAngle + ',' + f4(angSpeed) + "). :|:");



        //facingAngle.observe( angVelocity ); // );
        //nar.inputDirect(nar.task("<facing-->[" +  + "]>. :|:"));
        spin.set("spin:(" + torsoAngle + "," + angDir + f4(angSpeed) + "). :|:");

        //System.out.println("  " + motion);


        //feltSpeed.set("feltSpeed. :|: %" + sp + ";0.90%");
        //int positionWindow1 = 16;

        /*if (positions.size() >= positionWindow1) {
            Vec2 prevPosition = positions.removeFirst();
            float dist = prevPosition.sub(currentPosition).length();
            float scale = 1.5f;
            dist /= positionWindow1;
            dist *= scale;
            if (dist > 1.0f) {
                dist = 1.0f;
            }
            feltSpeedAvg.set("<(*,linVelocity," + Rover2.f(dist) + ") --> feel" + positionWindow1 + ">. :\\:");
        }*/

    }

    public static class MotorControls {

        public final Rover rover;

        public MotorControls(Rover rover) {
            this.rover = rover;
        }

        public void stop() {
            rover.thrustRelative(0);
            rover.rotateRelative(0);
        }

        public Truth forward(boolean forward) {
            Task c = MethodOperator.invokingTask();
            float thrust = c!=null ? c.expectation() : 1;
            if (!forward) thrust = -thrust;
            return rover.thrustRelative(thrust);
        }

        public Truth rotate(boolean left) {
            Task c = MethodOperator.invokingTask();
            float thrust = c!=null ? c.expectation() : 1;
            if (left) thrust = -thrust;
            return rover.rotateRelative(thrust);
        }

        public void random() {
            switch ((int)(5 * Math.random())) {
                case 0: forward(true);  break;
                case 1: forward(false);  break;
                case 2: rotate(true);  break;
                case 3: rotate(false);  break;
                case 4: stop();  break;
            }

        }
    }

}

//        new CycleDesire("motor(random)", strongestTask, nar) {
//            @Override float onFrame(float desire) {
//                //variable causes random movement
//                double v = Math.random();
//                if (v > (desire - 0.5f)*2f) {
//                    return Float.NaN;
//                }
//
//                //System.out.println(v + " " + (desire - 0.5f)*2f);
//
//                float strength = 0.65f;
//                float negStrength = 1f - strength;
//                String tPos = "%" + strength + "|" + desire + "%";
//                String tNeg = "%" + negStrength + "|" + desire + "%";
//
//                v = Math.random();
//                if (v < 0.25f) {
//                    nar.input(nar.task("motor(left)! " + tPos));
//                    nar.input(nar.task("motor(right)! " + tNeg));
//                } else if (v < 0.5f) {
//                    nar.input(nar.task("motor(left)! " + tNeg));
//                    nar.input(nar.task("motor(right)! " + tPos));
//                } else if (v < 0.75f) {
//                    nar.input(nar.task("motor(forward)! " + tPos));
//                    nar.input(nar.task("motor(reverse)! " + tNeg));
//                } else {
//                    nar.input(nar.task("motor(forward)! " + tNeg));
//                    nar.input(nar.task("motor(reverse)! " + tPos));
//                }
//                return desire;
//            }
//        };
//        /*new CycleDesire("motor(forward)", strongestTask, nar) {
//            @Override
//            float onFrame(float desire) {
//                thrustRelative(desire * linearThrustPerCycle);
//                return desire;
//            }
//
//        };*/
//        /*new CycleDesire("motor(reverse)", strongestTask, nar) {
//            @Override float onCycle(float desire) {
//                thrustRelative(desire * -linearThrustPerCycle);
//                return desire;
//            }
//        };*/
//
//        new BiCycleDesire("motor(forward)", "motor(reverse)", strongestTask,nar) {
//
//            @Override
//            float onFrame(float desire, boolean positive) {
//                if (positive) {
//                    thrustRelative(desire * linearThrustPerCycle);
//                }
//                else {
//                    thrustRelative(desire * -linearThrustPerCycle);
//                }
//                return desire;
//            }
//        };
//
//        new BiCycleDesire("motor(left)", "motor(right)", strongestTask,nar) {
//
//            @Override
//            float onFrame(float desire, boolean positive) {
//
//                if (positive) {
//                    rotateRelative(+80*desire);
//                }
//                else {
//                    rotateRelative(-80*desire);
//                }
//                return desire;
//            }
//        };
//
//        new CycleDesire("motor(left)", strongestTask, nar) {
//            @Override float onCycle(float desire) {
//
//                return desire;
//            }
//        };
//        new CycleDesire("motor(right)", strongestTask, nar) {
//            @Override float onCycle(float desire) {
//
//                return desire;
//            }
//        };
