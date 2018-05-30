/*******************************************************************************
 * Copyright (c) 2013, Daniel Murphy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 	* Redistributions of source code must retain the above copyright notice,
 * 	  this list of conditions and the following disclaimer.
 * 	* Redistributions in binary form must reproduce the above copyright notice,
 * 	  this list of conditions and the following disclaimer in the documentation
 * 	  and/or other materials provided with the distribution.
 *
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
 ******************************************************************************/
package spacegraph.space2d.phys.collision.shapes;

import spacegraph.space2d.phys.collision.AABB;
import spacegraph.space2d.phys.collision.RayCastInput;
import spacegraph.space2d.phys.collision.RayCastOutput;
import spacegraph.space2d.phys.common.Rot;
import spacegraph.space2d.phys.common.Settings;
import spacegraph.space2d.phys.common.Transform;
import spacegraph.space2d.phys.common.Vec2;
import spacegraph.util.math.Tuple2f;
import spacegraph.util.math.v2;

/**
 * A line segment (edge) shape. These can be connected in chains or loops to other edge shapes. The
 * connectivity information is used to ensure correct contact normals.
 *
 * @author Daniel
 */
public class EdgeShape extends Shape {

    /**
     * edge vertex 1
     */
    public final Tuple2f m_vertex1 = new Vec2();
    /**
     * edge vertex 2
     */
    public final Tuple2f m_vertex2 = new Vec2();

    /**
     * optional adjacent vertex 1. Used for smooth collision
     */
    public final Tuple2f m_vertex0 = new Vec2();
    /**
     * optional adjacent vertex 2. Used for smooth collision
     */
    public final Tuple2f m_vertex3 = new Vec2();
    public boolean m_hasVertex0 = false, m_hasVertex3 = false;


    public EdgeShape() {
        super(ShapeType.EDGE);
        radius = Settings.polygonRadius;
    }

    @Override
    public int getChildCount() {
        return 1;
    }

    public void set(Tuple2f v1, Tuple2f v2) {
        m_vertex1.set(v1);
        m_vertex2.set(v2);
        m_hasVertex0 = m_hasVertex3 = false;
    }

    @Override
    public boolean testPoint(Transform xf, Tuple2f p) {
        return false;
    }

    
    private final v2 normal = new Vec2();

    @Override
    public float computeDistanceToOut(Transform xf, Tuple2f p, int childIndex, v2 normalOut) {
        float xfqc = xf.c;
        float xfqs = xf.s;
        float xfpx = xf.pos.x;
        float xfpy = xf.pos.y;
        float v1x = (xfqc * m_vertex1.x - xfqs * m_vertex1.y) + xfpx;
        float v1y = (xfqs * m_vertex1.x + xfqc * m_vertex1.y) + xfpy;
        float v2x = (xfqc * m_vertex2.x - xfqs * m_vertex2.y) + xfpx;
        float v2y = (xfqs * m_vertex2.x + xfqc * m_vertex2.y) + xfpy;

        float dx = p.x - v1x;
        float dy = p.y - v1y;
        float sx = v2x - v1x;
        float sy = v2y - v1y;
        float ds = dx * sx + dy * sy;
        if (ds > 0) {
            float s2 = sx * sx + sy * sy;
            if (ds > s2) {
                dx = p.x - v2x;
                dy = p.y - v2y;
            } else {
                dx -= ds / s2 * sx;
                dy -= ds / s2 * sy;
            }
        }

        float d1 = (float) Math.sqrt(dx * dx + dy * dy);
        if (d1 > 0) {
            normalOut.x = 1 / d1 * dx;
            normalOut.y = 1 / d1 * dy;
        } else {
            normalOut.x = 0;
            normalOut.y = 0;
        }
        return d1;
    }

    
    
    
    
    @Override
    public boolean raycast(RayCastOutput output, RayCastInput input, Transform xf, int childIndex) {

        float tempx, tempy;
        final Tuple2f v1 = m_vertex1;
        final Tuple2f v2 = m_vertex2;
        final Rot xfq = xf;
        final Tuple2f xfp = xf.pos;

        
        
        
        tempx = input.p1.x - xfp.x;
        tempy = input.p1.y - xfp.y;
        final float p1x = xfq.c * tempx + xfq.s * tempy;
        final float p1y = -xfq.s * tempx + xfq.c * tempy;

        tempx = input.p2.x - xfp.x;
        tempy = input.p2.y - xfp.y;
        final float p2x = xfq.c * tempx + xfq.s * tempy;
        final float p2y = -xfq.s * tempx + xfq.c * tempy;

        final float dx = p2x - p1x;
        final float dy = p2y - p1y;

        
        
        normal.x = v2.y - v1.y;
        normal.y = v1.x - v2.x;
        normal.normalize();
        final float normalx = normal.x;
        final float normaly = normal.y;

        
        
        
        tempx = v1.x - p1x;
        tempy = v1.y - p1y;
        float numerator = normalx * tempx + normaly * tempy;
        float denominator = normalx * dx + normaly * dy;

        if (denominator == 0.0f) {
            return false;
        }

        float t = numerator / denominator;
        if (t < 0.0f || 1.0f < t) {
            return false;
        }

        
        final float qx = p1x + t * dx;
        final float qy = p1y + t * dy;

        
        
        
        final float rx = v2.x - v1.x;
        final float ry = v2.y - v1.y;
        final float rr = rx * rx + ry * ry;
        if (rr == 0.0f) {
            return false;
        }
        tempx = qx - v1.x;
        tempy = qy - v1.y;
        
        float s = (tempx * rx + tempy * ry) / rr;
        if (s < 0.0f || 1.0f < s) {
            return false;
        }

        output.fraction = t;
        if (numerator > 0.0f) {
            
            output.normal.x = -xfq.c * normal.x + xfq.s * normal.y;
            output.normal.y = -xfq.s * normal.x - xfq.c * normal.y;
        } else {
            
            output.normal.x = xfq.c * normal.x - xfq.s * normal.y;
            output.normal.y = xfq.s * normal.x + xfq.c * normal.y;
        }
        return true;
    }

    @Override
    public void computeAABB(AABB aabb, Transform xf, int childIndex) {
        final Tuple2f lowerBound = aabb.lowerBound;
        final Tuple2f upperBound = aabb.upperBound;
        final Rot xfq = xf;

        final float v1x = (xfq.c * m_vertex1.x - xfq.s * m_vertex1.y) + xf.pos.x;
        final float v1y = (xfq.s * m_vertex1.x + xfq.c * m_vertex1.y) + xf.pos.y;
        final float v2x = (xfq.c * m_vertex2.x - xfq.s * m_vertex2.y) + xf.pos.x;
        final float v2y = (xfq.s * m_vertex2.x + xfq.c * m_vertex2.y) + xf.pos.y;

        lowerBound.x = v1x < v2x ? v1x : v2x;
        lowerBound.y = v1y < v2y ? v1y : v2y;
        upperBound.x = v1x > v2x ? v1x : v2x;
        upperBound.y = v1y > v2y ? v1y : v2y;

        lowerBound.x -= radius;
        lowerBound.y -= radius;
        upperBound.x += radius;
        upperBound.y += radius;
    }

    @Override
    public void computeMass(MassData massData, float density) {
        massData.mass = 0.0f;
        massData.center.set(m_vertex1).added(m_vertex2).scaled(0.5f);
        massData.I = 0.0f;
    }

    @Override
    public Shape clone() {
        EdgeShape edge = new EdgeShape();
        edge.radius = this.radius;
        edge.m_hasVertex0 = this.m_hasVertex0;
        edge.m_hasVertex3 = this.m_hasVertex3;
        edge.m_vertex0.set(this.m_vertex0);
        edge.m_vertex1.set(this.m_vertex1);
        edge.m_vertex2.set(this.m_vertex2);
        edge.m_vertex3.set(this.m_vertex3);
        return edge;
    }
}
