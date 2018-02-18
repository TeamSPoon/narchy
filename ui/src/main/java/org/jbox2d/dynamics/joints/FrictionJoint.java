/**
 * Copyright (c) 2013, Daniel Murphy
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * <p>
 * Created at 7:27:32 AM Jan 20, 2011
 */
/**
 * Created at 7:27:32 AM Jan 20, 2011
 */
package org.jbox2d.dynamics.joints;

import org.jbox2d.common.Mat22;
import org.jbox2d.common.MathUtils;
import org.jbox2d.common.Rot;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.SolverData;
import org.jbox2d.pooling.IWorldPool;
import spacegraph.math.Tuple2f;
import spacegraph.math.v2;

/**
 * @author Daniel Murphy
 */
public class FrictionJoint extends Joint {

    private final Tuple2f m_localAnchorA;
    private final Tuple2f m_localAnchorB;

    // Solver shared
    private final Tuple2f m_linearImpulse;
    private float m_angularImpulse;
    private float m_maxForce;
    private float m_maxTorque;

    // Solver temp
    private int m_indexA;
    private int m_indexB;
    private final Tuple2f m_rA = new v2();
    private final Tuple2f m_rB = new v2();
    private final Tuple2f m_localCenterA = new v2();
    private final Tuple2f m_localCenterB = new v2();
    private float m_invMassA;
    private float m_invMassB;
    private float m_invIA;
    private float m_invIB;
    private final Mat22 m_linearMass = new Mat22();
    private float m_angularMass;

    protected FrictionJoint(IWorldPool argWorldPool, FrictionJointDef def) {
        super(argWorldPool, def);
        m_localAnchorA = new Vec2(def.localAnchorA);
        m_localAnchorB = new Vec2(def.localAnchorB);

        m_linearImpulse = new Vec2();
        m_angularImpulse = 0.0f;

        m_maxForce = def.maxForce;
        m_maxTorque = def.maxTorque;
    }

    public Tuple2f getLocalAnchorA() {
        return m_localAnchorA;
    }

    public Tuple2f getLocalAnchorB() {
        return m_localAnchorB;
    }

    @Override
    public void getAnchorA(Tuple2f argOut) {
        A.getWorldPointToOut(m_localAnchorA, argOut);
    }

    @Override
    public void getAnchorB(Tuple2f argOut) {
        B.getWorldPointToOut(m_localAnchorB, argOut);
    }

    @Override
    public void getReactionForce(float inv_dt, Tuple2f argOut) {
        argOut.set(m_linearImpulse).scaled(inv_dt);
    }

    @Override
    public float getReactionTorque(float inv_dt) {
        return inv_dt * m_angularImpulse;
    }

    public void setMaxForce(float force) {
        assert (force >= 0.0f);
        m_maxForce = force;
    }

    public float getMaxForce() {
        return m_maxForce;
    }

    public void setMaxTorque(float torque) {
        assert (torque >= 0.0f);
        m_maxTorque = torque;
    }

    public float getMaxTorque() {
        return m_maxTorque;
    }

    /**
     * @see org.jbox2d.dynamics.joints.Joint#initVelocityConstraints(org.jbox2d.dynamics.TimeStep)
     */
    @Override
    public void initVelocityConstraints(final SolverData data) {
        m_indexA = A.island;
        m_indexB = B.island;
        m_localCenterA.set(A.sweep.localCenter);
        m_localCenterB.set(B.sweep.localCenter);
        m_invMassA = A.m_invMass;
        m_invMassB = B.m_invMass;
        m_invIA = A.m_invI;
        m_invIB = B.m_invI;

        float aA = data.positions[m_indexA].a;
        Vec2 vA = data.velocities[m_indexA].v;
        float wA = data.velocities[m_indexA].w;

        float aB = data.positions[m_indexB].a;
        Vec2 vB = data.velocities[m_indexB].v;
        float wB = data.velocities[m_indexB].w;


        final Tuple2f temp = pool.popVec2();
        final Rot qA = pool.popRot();
        final Rot qB = pool.popRot();

        qA.set(aA);
        qB.set(aB);

        // Compute the effective mass matrix.
        Rot.mulToOutUnsafe(qA, temp.set(m_localAnchorA).subbed(m_localCenterA), m_rA);
        Rot.mulToOutUnsafe(qB, temp.set(m_localAnchorB).subbed(m_localCenterB), m_rB);

        // J = [-I -r1_skew I r2_skew]
        // [ 0 -1 0 1]
        // r_skew = [-ry; rx]

        // Matlab
        // K = [ mA+r1y^2*iA+mB+r2y^2*iB, -r1y*iA*r1x-r2y*iB*r2x, -r1y*iA-r2y*iB]
        // [ -r1y*iA*r1x-r2y*iB*r2x, mA+r1x^2*iA+mB+r2x^2*iB, r1x*iA+r2x*iB]
        // [ -r1y*iA-r2y*iB, r1x*iA+r2x*iB, iA+iB]

        float mA = m_invMassA, mB = m_invMassB;
        float iA = m_invIA, iB = m_invIB;

        final Mat22 K = pool.popMat22();
        K.ex.x = mA + mB + iA * m_rA.y * m_rA.y + iB * m_rB.y * m_rB.y;
        K.ex.y = -iA * m_rA.x * m_rA.y - iB * m_rB.x * m_rB.y;
        K.ey.x = K.ex.y;
        K.ey.y = mA + mB + iA * m_rA.x * m_rA.x + iB * m_rB.x * m_rB.x;

        K.invertToOut(m_linearMass);

        m_angularMass = iA + iB;
        if (m_angularMass > 0.0f) {
            m_angularMass = 1.0f / m_angularMass;
        }

        if (data.step.warmStarting) {
            // Scale impulses to support a variable time step.
            m_linearImpulse.scaled(data.step.dtRatio);
            m_angularImpulse *= data.step.dtRatio;

            final Tuple2f P = pool.popVec2();
            P.set(m_linearImpulse);

            temp.set(P).scaled(mA);
            vA.subLocal(temp);
            wA -= iA * (Tuple2f.cross(m_rA, P) + m_angularImpulse);

            temp.set(P).scaled(mB);
            vB.addLocal(temp);
            wB += iB * (Tuple2f.cross(m_rB, P) + m_angularImpulse);

            pool.pushVec2(1);
        } else {
            m_linearImpulse.setZero();
            m_angularImpulse = 0.0f;
        }
//    data.velocities[m_indexA].v.set(vA);
        assert !(data.velocities[m_indexA].w != wA) || (data.velocities[m_indexA].w != wA);
        data.velocities[m_indexA].w = wA;
//    data.velocities[m_indexB].v.set(vB);
        data.velocities[m_indexB].w = wB;

        pool.pushRot(2);
        pool.pushVec2(1);
        pool.pushMat22(1);
    }

    @Override
    public void solveVelocityConstraints(final SolverData data) {
        Vec2 vA = data.velocities[m_indexA].v;
        float wA = data.velocities[m_indexA].w;
        Vec2 vB = data.velocities[m_indexB].v;
        float wB = data.velocities[m_indexB].w;

        float mA = m_invMassA, mB = m_invMassB;
        float iA = m_invIA, iB = m_invIB;

        float h = data.step.dt;

        // Solve angular friction
        {
            float Cdot = wB - wA;
            float impulse = -m_angularMass * Cdot;

            float oldImpulse = m_angularImpulse;
            float maxImpulse = h * m_maxTorque;
            m_angularImpulse = MathUtils.clamp(m_angularImpulse + impulse, -maxImpulse, maxImpulse);
            impulse = m_angularImpulse - oldImpulse;

            wA -= iA * impulse;
            wB += iB * impulse;
        }

        // Solve linear friction
        {
            final Tuple2f Cdot = pool.popVec2();
            final Tuple2f temp = pool.popVec2();

            Tuple2f.crossToOutUnsafe(wA, m_rA, temp);
            Tuple2f.crossToOutUnsafe(wB, m_rB, Cdot);
            Cdot.added(vB).subbed(vA).subbed(temp);

            final Tuple2f impulse = pool.popVec2();
            Mat22.mulToOutUnsafe(m_linearMass, Cdot, impulse);
            impulse.negated();


            final Tuple2f oldImpulse = pool.popVec2();
            oldImpulse.set(m_linearImpulse);
            m_linearImpulse.added(impulse);

            float maxImpulse = h * m_maxForce;

            if (m_linearImpulse.lengthSquared() > maxImpulse * maxImpulse) {
                m_linearImpulse.normalize();
                m_linearImpulse.scaled(maxImpulse);
            }

            impulse.set(m_linearImpulse).subbed(oldImpulse);

            temp.set(impulse).scaled(mA);
            vA.subLocal(temp);
            wA -= iA * Tuple2f.cross(m_rA, impulse);

            temp.set(impulse).scaled(mB);
            vB.addLocal(temp);
            wB += iB * Tuple2f.cross(m_rB, impulse);

        }

//    data.velocities[m_indexA].v.set(vA);
        assert !(data.velocities[m_indexA].w != wA) || (data.velocities[m_indexA].w != wA);
        data.velocities[m_indexA].w = wA;

//    data.velocities[m_indexB].v.set(vB);
        data.velocities[m_indexB].w = wB;

        pool.pushVec2(4);
    }

    @Override
    public boolean solvePositionConstraints(final SolverData data) {
        return true;
    }
}
