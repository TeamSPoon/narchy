/*
 *   __               .__       .__  ._____.
 * _/  |_  _______  __|__| ____ |  | |__\_ |__   ______
 * \   __\/  _ \  \/  /  |/ ___\|  | |  || __ \ /  ___/
 *  |  | (  <_> >    <|  \  \___|  |_|  || \_\ \\___ \
 *  |__|  \____/__/\_ \__|\___  >____/__||___  /____  >
 *                   \/       \/             \/     \/
 *
 * Copyright (c) 2006-2011 Karsten Schmidt
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * http://creativecommons.org/licenses/LGPL/2.1/
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
 */

package toxi.physics2d;

import jcog.data.list.FasterList;
import jcog.pri.ScalarValue;
import jcog.tree.rtree.rect.RectFloat2D;
import org.jetbrains.annotations.Nullable;
import toxi.geom.Polygon2D;
import toxi.geom.ReadonlyVec2D;
import toxi.geom.Rect;
import toxi.geom.Vec2D;
import toxi.physics2d.behaviors.ParticleBehavior2D;
import toxi.physics2d.constraints.ParticleConstraint2D;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An individual 3D particle for use by the VerletPhysics and VerletSpring
 * classes. A particle has weight, can be locked in space and its position
 * constrained inside an (optional) axis-aligned bounding box.
 */
public class VerletParticle2D extends Vec2D {

    protected final Vec2D next;
    protected final Vec2D prev;
    protected boolean isLocked;

    /**
     * Bounding box, by default set to null to disable
     */
    @Nullable
    public Rect bounds;

    /**
     * An optional particle constraints, called immediately after a particle is
     * updated (and only used if particle is unlocked (default)
     */
    public List<ParticleConstraint2D> constraints;

    public List<ParticleBehavior2D> behaviors;
    /**
     * Particle weight, default = 1
     */
    protected float mass, invMass;

    protected final Vec2D force = new Vec2D();

    final AtomicInteger serial = new AtomicInteger(0);
    private final int id = serial.getAndIncrement();

    /**
     * Creates particle at position xyz
     *
     * @param x
     * @param y
     */
    public VerletParticle2D(float x, float y) {
        this(x, y, 1);
    }

    /**
     * Creates particle at position xyz with weight w
     *
     * @param x
     * @param y
     * @param w
     */
    public VerletParticle2D(float x, float y, float w) {
        super(x, y);
        next = new Vec2D(x,y); prev = new Vec2D(x,y);
        mass(w);
    }

    /**
     * Creates particle at the position of the passed in vector
     *
     * @param v position
     */
    public VerletParticle2D(ReadonlyVec2D v) {
        this(v.x(), v.y(), 1);
    }

    /**
     * Creates particle with weight w at the position of the passed in vector
     *
     * @param v position
     * @param w weight
     */
    public VerletParticle2D(ReadonlyVec2D v, float w) {
        this(v.x(), v.y(), w);
    }

//    /**
//     * Creates a copy of the passed in particle
//     *
//     * @param p
//     */
//    public VerletParticle2D(VerletParticle2D p) {
//        this(p.x, p.y, p.weight);
//        isLocked = p.isLocked;
//    }

    public VerletParticle2D addBehavior(ParticleBehavior2D behavior) {
        return addBehavior(behavior, 1);
    }

    public VerletParticle2D addBehavior(ParticleBehavior2D behavior,
                                        float timeStep) {
        if (behaviors == null)
            behaviors = new FasterList<>(1);
        behavior.configure(timeStep);
        behaviors.add(behavior);
        return this;
    }

    public VerletParticle2D addBehaviors(Collection<ParticleBehavior2D> behaviors) {
        return addBehaviors(behaviors, 1);
    }

    public VerletParticle2D addBehaviors(Iterable<ParticleBehavior2D> behaviors, float timeStemp) {
        for (ParticleBehavior2D b : behaviors) {
            addBehavior(b, timeStemp);
        }
        return this;
    }

    /**
     * Adds the given constraint implementation to the list of constraints
     * applied to this particle at each time step.
     *
     * @param c constraint instance
     * @return itself
     */
    public VerletParticle2D addConstraint(ParticleConstraint2D c) {
        if (constraints == null) {
            constraints = new FasterList<>(1);
        }
        constraints.add(c);
        return this;
    }

    public VerletParticle2D addConstraints(Iterable<ParticleConstraint2D> constraints) {
        for (ParticleConstraint2D c : constraints) {
            addConstraint(c);
        }
        return this;
    }

    public VerletParticle2D addForce(Vec2D f) {
        force.addSelf(f);
        return this;
    }

//    public VerletParticle2D addVelocity(Vec2D v) {
//        prev.subSelf(v);
//        return this;
//    }

    public void applyBehaviors() {
        if (behaviors != null) {
            for (ParticleBehavior2D b : behaviors) {
                b.apply(this);
            }
        }
    }

    public void applyConstraints() {
        if (constraints != null) {
            for (ParticleConstraint2D pc : constraints) {
                pc.apply(this);
            }
        }
    }


    protected void applyForce(float drag) {
        //Pos(next) = Pos(now) + (Pos(now) - Pos(prev)) + Accel * dt * dt
        //F = ma, a = F/m

        Vec2D d = (this.sub(prev).scale(1f - drag)).addSelf(force.scale(
                //invWeight * dt * dt
                //invWeight * dt
                invMass
        ));

        next.addSelf(d);

        clearForce();
    }

    @Override
    public Vec2D constrain(Rect r) {
        return next.constrain(r);
    }

    @Override
    public Vec2D constrain(RectFloat2D r) {
        return next.constrain(r);
    }

    @Override
    public Vec2D constrain(Polygon2D poly) {
        return next.constrain(poly);
    }

    @Override
    public Vec2D constrain(Vec2D min, Vec2D max) {
        return next.constrain(min, max);
    }

    public void commit() {
        prev.set(this);
        set(next);
    }

    public VerletParticle2D clearForce() {
        force.clear();
        return this;
    }

    public VerletParticle2D clearVelocity() {
        next.set(this);
        return this;
    }

    public Vec2D getForce() {
        return force;
    }

    /**
     * @return the inverse weight (1/weight)
     */
    public final float getInvMass() {
        return invMass;
    }

//    /**
//     * Returns the particle's position at the most recent time step.
//     *
//     * @return previous position
//     */
//    public Vec2D getPreviousPosition() {
//        return prev;
//    }

    public Vec2D getVelocity() {
        return sub(prev);
    }

    public float getSpeed() {
        return (float) Math.sqrt(getSpeedSq());
    }

    public float getSpeedSq() {
        return getVelocity().magSquared();
    }

    @Override
    public final int hashCode() {
        return id;
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * @return the weight
     */
    public final float getMass() {
        return mass;
    }

    /**
     * @return true, if particle is locked
     */
    public final boolean isLocked() {
        return isLocked;
    }

    /**
     * Locks/immobilizes particle in space
     *
     * @return itself
     */
    public VerletParticle2D lock() {
        isLocked = true;
        return this;
    }

    public VerletParticle2D removeAllBehaviors() {
        behaviors.clear();
        return this;
    }

    /**
     * Removes any currently applied constraints from this particle.
     *
     * @return itself
     */
    public VerletParticle2D removeAllConstraints() {
        constraints.clear();
        return this;
    }

    public boolean removeBehavior(ParticleBehavior2D b) {
        return behaviors.remove(b);
    }

    public boolean removeBehaviors(Collection<ParticleBehavior2D> behaviors) {
        return this.behaviors.removeAll(behaviors);
    }

    /**
     * Attempts to remove the given constraint instance from the list of active
     * constraints.
     *
     * @param c constraint to remove
     * @return true, if successfully removed
     */
    public boolean removeConstraint(ParticleConstraint2D c) {
        return constraints.remove(c);
    }

    public boolean removeConstraints(
            Collection<ParticleConstraint2D> constraints) {
        return this.constraints.removeAll(constraints);
    }

//    public VerletParticle2D scaleVelocity(float scl) {
//        //prev.interpolateToSelf(this, 1f - scl);
//        next.interpolateToSelf(this, 1f-scl);
//        return this;
//    }
//    public VerletParticle2D scaleForce(float scl) {
//        //prev.interpolateToSelf(this, 1f - scl);
//        force.scale(scl);
//        return this;
//    }


//    public VerletParticle2D setPreviousPosition(Vec2D p) {
//        prev.set(p);
//        return this;
//    }

    public final void mass(float w) {
        mass = w;
        invMass = 1f / w;
    }

    /**
     * Unlocks particle again
     *
     * @return itself
     */
    public VerletParticle2D unlock() {
        clearVelocity();
        isLocked = false;
        return this;
    }

    public void update(float drag) {
        if (!isLocked) {
            applyBehaviors();
            applyForce(drag);
            applyConstraints();
        }
    }

    public boolean changed() {
        return !equalsWithTolerance(next, ScalarValue.EPSILON);
    }
}