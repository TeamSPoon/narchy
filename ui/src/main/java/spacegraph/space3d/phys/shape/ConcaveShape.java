/*
 * Java port of Bullet (c) 2008 Martin Dvorak <jezek2@advel.cz>
 *
 * Bullet Continuous Collision Detection and Physics Library
 * Copyright (c) 2003-2008 Erwin Coumans  http:
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from
 * the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose, 
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package spacegraph.space3d.phys.shape;

import spacegraph.util.math.v3;

/**
 * ConcaveShape class provides an interface for non-moving (static) concave shapes.
 * 
 * @author jezek2
 */
public abstract class ConcaveShape extends CollisionShape {

	protected float collisionMargin;

	public abstract void processAllTriangles(TriangleCallback callback, v3 aabbMin, v3 aabbMax);

	@Override
	public float getMargin() {
		return collisionMargin;
	}

	@Override
	public CollisionShape setMargin(float margin) {
		this.collisionMargin = margin;
		return this;
	}
	
}
