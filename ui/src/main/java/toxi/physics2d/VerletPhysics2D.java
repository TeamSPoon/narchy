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

import jcog.data.list.FastCoWList;
import jcog.tree.rtree.rect.RectFloat2D;
import org.jetbrains.annotations.Nullable;
import toxi.geom.Rect;
import toxi.geom.SpatialIndex;
import toxi.geom.Vec2D;
import toxi.physics2d.behaviors.GravityBehavior2D;
import toxi.physics2d.behaviors.ParticleBehavior2D;
import toxi.physics2d.constraints.ParticleConstraint2D;

import java.util.Collection;
import java.util.List;

/**
 * 3D particle physics engine using Verlet integration based on:
 * http://en.wikipedia.org/wiki/Verlet_integration
 * http://www.teknikus.dk/tj/gdc2001.htm
 */
public class VerletPhysics2D {


    /** TODO use FastIteratingConcurrentHashMap indexed by particle ID */
    public final List<VerletParticle2D> particles = new FastCoWList<>(VerletParticle2D[]::new);

    public final List<VerletSpring2D> springs = new FastCoWList<>(VerletSpring2D[]::new);

    public final Collection<ParticleBehavior2D> behaviors = new FastCoWList<>(ParticleBehavior2D[]::new);

    public final Collection<ParticleConstraint2D> constraints = new FastCoWList<>(ParticleConstraint2D[]::new);

    /**
     * Default iterations for verlet solver = 50
     */
    protected int maxIterations;

    /**
     * Optional bounding rect to constrain particles too
     */
    protected RectFloat2D worldBounds;

    protected float drag;

    protected SpatialIndex<VerletParticle2D> index;

    private final float maxTimeStep = 1f;
    private final float minTimeStep = 0.01f; //in seconds


    /**
     * Initializes an Verlet engine instance with the passed in configuration.
     *
     * @param gravity       3D gravity vector
     * @param numIterations iterations per time step for verlet solver
     * @param drag          drag value 0...1
     */
    public VerletPhysics2D(Vec2D gravity, int numIterations, float drag) {

        this.maxIterations = numIterations;
        setDrag(drag);
        if (gravity != null) {
            addBehavior(new GravityBehavior2D(gravity));
        }
    }

    public final void addBehavior(ParticleBehavior2D behavior) {
        behaviors.add(behavior);
    }

    public void addConstraint(ParticleConstraint2D constraint) {
        constraints.add(constraint);
    }

    /**
     * Adds a particle to the list
     *
     * @param p
     * @return itself
     */
    @Nullable
    public VerletPhysics2D addParticle(VerletParticle2D p) {
        if (index.index(p) && particles.add(p)) {
            return this;
        }
        return null;
    }

    /**
     * Adds a spring connector
     *
     * @param s
     * @return itself
     */
    public VerletPhysics2D addSpring(VerletSpring2D s) {
        if (getSpring(s.a, s.b) == null) {
            springs.add(s);
        }
        return this;
    }

    /**
     * Applies all global constraints and constrains all particle positions to
     * the world bounding rect set.
     */
    protected void constrain() {
        boolean hasGlobalConstraints = !constraints.isEmpty();
        for (VerletParticle2D p : particles) {
            if (hasGlobalConstraints) {
                for (ParticleConstraint2D c : constraints)
                    c.apply(p);
            }
            if (p.bounds != null) {
                p.constrain(p.bounds);
            }
            if (worldBounds != null) {
                p.constrain(worldBounds);
            }
        }
    }

    public VerletPhysics2D clear() {
        behaviors.clear();
        constraints.clear();
        particles.clear();
        springs.clear();
        return this;
    }

    public Rect getCurrentBounds() {
        Vec2D min = new Vec2D(Float.MAX_VALUE, Float.MAX_VALUE);
        Vec2D max = new Vec2D(Float.MIN_VALUE, Float.MIN_VALUE);
        for (VerletParticle2D p : particles) {
            min.minSelf(p);
            max.maxSelf(p);
        }
        return new Rect(min, max);
    }

    public float getDrag() {
        return 1f - drag;
    }

    /**
     * @return the index
     */
    public SpatialIndex<VerletParticle2D> getIndex() {
        return index;
    }


    /**
     * Attempts to find the spring element between the 2 particles supplied
     *
     * @param a particle 1
     * @param b particle 2
     * @return spring instance, or null if not found
     */
    public VerletSpring2D getSpring(Vec2D a, Vec2D b) {
        for (VerletSpring2D s : springs) {
            if ((s.a == a && s.b == b) || (s.a == b && s.b == a)) {
                return s;
            }
        }
        return null;
    }



    public boolean removeBehavior(ParticleBehavior2D c) {
        return behaviors.remove(c);
    }

    public boolean removeConstraint(ParticleConstraint2D c) {
        return constraints.remove(c);
    }

    /**
     * Removes a particle from the simulation.
     *
     * @param p particle to remove
     * @return true, if removed successfully
     */
    public boolean removeParticle(VerletParticle2D p) {
        index.unindex(p);
        //TODO remove associated springs
        return particles.remove(p);
    }

    /**
     * Removes a spring connector from the simulation instance.
     *
     * @param s spring to remove
     * @return true, if the spring has been removed
     */
    public boolean removeSpring(VerletSpring2D s) {
        return springs.remove(s);
    }

    /**
     * Removes a spring connector and its both end point particles from the
     * simulation
     *
     * @param s spring to remove
     * @return true, only if spring AND particles have been removed successfully
     */
    public boolean removeSpringElements(VerletSpring2D s) {
        if (removeSpring(s)) {
            return (removeParticle(s.a) && removeParticle(s.b));
        }
        return false;
    }

    public final void setDrag(float drag) {
        this.drag = drag;
    }

    /**
     * @param index the index to set
     */
    public void setIndex(SpatialIndex<VerletParticle2D> index) {
        this.index = index;
    }

    /**
     * @param maxIterations the numIterations to set
     */
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }


    /**
     * Sets bounding box
     *
     * @param world
     * @return itself
     */
    public VerletPhysics2D setWorldBounds(RectFloat2D world) {
        worldBounds = world;
        return this;
    }

    /**
     * Progresses the physics simulation by 1 time step and updates all forces
     * and particle positions accordingly
     *
     * @param dt
     * @return itself
     */
    public VerletPhysics2D update(float dt) {


        dt = Math.min(dt, maxTimeStep); //hard uppper limit on timestep

        int ii = 0;
        while (++ii < maxIterations) {
            if (dt / ii < minTimeStep)
                break;
            //else: further subdivide
        }

        float subDT = dt / ii;

        for (ParticleBehavior2D b : behaviors)
            b.configure(subDT);

        for (int i = ii - 1; i >= 0; i--) {
            behave(subDT);
            integrate();
            spring(subDT);
            constrain();
            index();
        }
        return this;
    }

    private void index() {
        if (index != null) {
            for (VerletParticle2D p : particles)
                if (p.changed())
                    index.reindex(p, VerletParticle2D::commit);
        } else {
            for (VerletParticle2D p : particles)
                p.commit();
        }
    }

    /**
     * Updates all particle positions
     *
     * @param dt
     */
    protected void behave(float dt) {


        for (ParticleBehavior2D b : behaviors) {
            if (index != null && b.supportsSpatialIndex()) {
                b.applyWithIndex(index);
            } else {
                for (VerletParticle2D p : particles)
                    b.apply(p);
            }
        }


    }

    protected void integrate() {
        for (VerletParticle2D p : particles) {
            p.update(drag);
        }
    }

    /**
     * Updates all spring connections based on new particle positions
     *
     * @param subDT
     */
    protected void spring(float subDT) {
        if (!springs.isEmpty()) {
            for (VerletSpring2D s : springs) {
                s.update(false);
            }
        }
    }
}