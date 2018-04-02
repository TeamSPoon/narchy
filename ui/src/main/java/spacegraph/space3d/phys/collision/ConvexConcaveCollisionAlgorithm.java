/*
 * Java port of Bullet (c) 2008 Martin Dvorak <jezek2@advel.cz>
 *
 * Bullet Continuous Collision Detection and Physics Library
 * Copyright (c) 2003-2008 Erwin Coumans  http://www.bulletphysics.com/
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

package spacegraph.space3d.phys.collision;

import spacegraph.space3d.phys.Collidable;
import spacegraph.space3d.phys.collision.broad.CollisionAlgorithm;
import spacegraph.space3d.phys.collision.broad.CollisionAlgorithmConstructionInfo;
import spacegraph.space3d.phys.collision.broad.DispatcherInfo;
import spacegraph.space3d.phys.collision.narrow.ConvexCast;
import spacegraph.space3d.phys.collision.narrow.PersistentManifold;
import spacegraph.space3d.phys.collision.narrow.SubsimplexConvexCast;
import spacegraph.space3d.phys.collision.narrow.VoronoiSimplexSolver;
import spacegraph.space3d.phys.math.Transform;
import spacegraph.space3d.phys.math.VectorUtil;
import spacegraph.space3d.phys.shape.ConcaveShape;
import spacegraph.space3d.phys.shape.SphereShape;
import spacegraph.space3d.phys.shape.TriangleCallback;
import spacegraph.space3d.phys.shape.TriangleShape;
import spacegraph.space3d.phys.util.OArrayList;
import spacegraph.util.math.v3;

/**
 * ConvexConcaveCollisionAlgorithm supports collision between convex shapes
 * and (concave) trianges meshes.
 * 
 * @author jezek2
 */
public class ConvexConcaveCollisionAlgorithm extends CollisionAlgorithm {

	private boolean isSwapped;
	private ConvexTriangleCallback btConvexTriangleCallback;

	public void init(CollisionAlgorithmConstructionInfo ci, Collidable body0, Collidable body1, boolean isSwapped) {
		super.init(ci);
		this.isSwapped = isSwapped;
		this.btConvexTriangleCallback = new ConvexTriangleCallback(intersecter, body0, body1, isSwapped);
	}

	@Override
	public void destroy() {
		btConvexTriangleCallback.destroy();
	}

	@Override
	public void processCollision(Collidable body0, Collidable body1, DispatcherInfo dispatchInfo, ManifoldResult resultOut) {
		Collidable convexBody = isSwapped ? body1 : body0;
		Collidable triBody = isSwapped ? body0 : body1;

		if (triBody.shape().isConcave()) {
			Collidable triOb = triBody;
			ConcaveShape concaveShape = (ConcaveShape)triOb.shape();

			if (convexBody.shape().isConvex()) {
				float collisionMarginTriangle = concaveShape.getMargin();

				resultOut.setPersistentManifold(btConvexTriangleCallback.manifoldPtr);
				btConvexTriangleCallback.setTimeStepAndCounters(collisionMarginTriangle, dispatchInfo, resultOut);

				// Disable persistency. previously, some older algorithm calculated all contacts in one go, so you can clear it here.
				//m_dispatcher->clearManifold(m_btConvexTriangleCallback.m_manifoldPtr);

				btConvexTriangleCallback.manifoldPtr.setBodies(convexBody, triBody);

				concaveShape.processAllTriangles(
						btConvexTriangleCallback,
						btConvexTriangleCallback.getAabbMin(new v3()),
						btConvexTriangleCallback.getAabbMax(new v3()));

				resultOut.refreshContactPoints();
			}
		}
	}

	@Override
	public float calculateTimeOfImpact(Collidable body0, Collidable body1, DispatcherInfo dispatchInfo, ManifoldResult resultOut) {
		v3 tmp = new v3();

		Collidable convexbody = isSwapped ? body1 : body0;
		Collidable triBody = isSwapped ? body0 : body1;

		// quick approximation using raycast, todo: hook up to the continuous collision detection (one of the btConvexCast)

		// only perform CCD above a certain threshold, this prevents blocking on the long run
		// because object in a blocked ccd state (hitfraction<1) get their linear velocity halved each frame...
		tmp.sub(convexbody.getInterpolationWorldTransform(new Transform()), convexbody.getWorldTransform(new Transform()));
		float squareMot0 = tmp.lengthSquared();
		if (squareMot0 < convexbody.getCcdSquareMotionThreshold()) {
			return 1f;
		}

		Transform tmpTrans = new Transform();

		//const btVector3& from = convexbody->m_worldTransform.getOrigin();
		//btVector3 to = convexbody->m_interpolationWorldTransform.getOrigin();
		//todo: only do if the motion exceeds the 'radius'

		Transform triInv = triBody.getWorldTransform(new Transform());
		triInv.inverse();

		Transform convexFromLocal = new Transform();
		convexFromLocal.mul(triInv, convexbody.getWorldTransform(tmpTrans));

		Transform convexToLocal = new Transform();
		convexToLocal.mul(triInv, convexbody.getInterpolationWorldTransform(tmpTrans));

		if (triBody.shape().isConcave()) {
			v3 rayAabbMin = new v3(convexFromLocal);
			VectorUtil.setMin(rayAabbMin, convexToLocal);

			v3 rayAabbMax = new v3(convexFromLocal);
			VectorUtil.setMax(rayAabbMax, convexToLocal);

			float ccdRadius0 = convexbody.getCcdSweptSphereRadius();

			tmp.set(ccdRadius0, ccdRadius0, ccdRadius0);
			rayAabbMin.sub(tmp);
			rayAabbMax.add(tmp);

			float curHitFraction = 1f; // is this available?
			LocalTriangleSphereCastCallback raycastCallback = new LocalTriangleSphereCastCallback(convexFromLocal, convexToLocal, convexbody.getCcdSweptSphereRadius(), curHitFraction);

			raycastCallback.hitFraction = convexbody.getHitFraction();

			Collidable concavebody = triBody;

			ConcaveShape triangleMesh = (ConcaveShape)concavebody.shape();

			if (triangleMesh != null) {
				triangleMesh.processAllTriangles(raycastCallback, rayAabbMin, rayAabbMax);
			}

			if (raycastCallback.hitFraction < convexbody.getHitFraction()) {
				convexbody.setHitFraction(raycastCallback.hitFraction);
				return raycastCallback.hitFraction;
			}
		}

		return 1f;
	}

	@Override
	public void getAllContactManifolds(OArrayList<PersistentManifold> manifoldArray) {
		if (btConvexTriangleCallback.manifoldPtr != null) {
			manifoldArray.add(btConvexTriangleCallback.manifoldPtr);
		}
	}

	public void clearCache() {
		btConvexTriangleCallback.clearCache();
	}

	////////////////////////////////////////////////////////////////////////////

	private static class LocalTriangleSphereCastCallback extends TriangleCallback {
		public final Transform ccdSphereFromTrans = new Transform();
		public final Transform ccdSphereToTrans = new Transform();
		public final Transform meshTransform = new Transform();

		public final float ccdSphereRadius;
		public float hitFraction;

		private final Transform ident = new Transform();

		public LocalTriangleSphereCastCallback(Transform from, Transform to, float ccdSphereRadius, float hitFraction) {
			this.ccdSphereFromTrans.set(from);
			this.ccdSphereToTrans.set(to);
			this.ccdSphereRadius = ccdSphereRadius;
			this.hitFraction = hitFraction;

			// JAVA NOTE: moved here from processTriangle
			ident.setIdentity();
		}

		@Override
        public void processTriangle(v3[] triangle, int partId, int triangleIndex) {
			// do a swept sphere for now

			//btTransform ident;
			//ident.setIdentity();

			ConvexCast.CastResult castResult = new ConvexCast.CastResult();
			castResult.fraction = hitFraction;
			SphereShape pointShape = new SphereShape(ccdSphereRadius);
			TriangleShape triShape = new TriangleShape(triangle[0], triangle[1], triangle[2]);
			VoronoiSimplexSolver simplexSolver = new VoronoiSimplexSolver();
			SubsimplexConvexCast convexCaster = new SubsimplexConvexCast(pointShape, triShape, simplexSolver);
			//GjkConvexCast	convexCaster(&pointShape,convexShape,&simplexSolver);
			//ContinuousConvexCollision convexCaster(&pointShape,convexShape,&simplexSolver,0);
			//local space?

			if (convexCaster.calcTimeOfImpact(ccdSphereFromTrans, ccdSphereToTrans, ident, ident, castResult)) {
				if (hitFraction > castResult.fraction) {
					hitFraction = castResult.fraction;
				}
			}
		}
	}

	////////////////////////////////////////////////////////////////////////////

	public static class CreateFunc extends CollisionAlgorithmCreateFunc {

		@Override
		public CollisionAlgorithm createCollisionAlgorithm(CollisionAlgorithmConstructionInfo ci, Collidable body0, Collidable body1) {
			ConvexConcaveCollisionAlgorithm algo = new ConvexConcaveCollisionAlgorithm();
			algo.init(ci, body0, body1, false);
			return algo;
		}

    }

	public static class SwappedCreateFunc extends CollisionAlgorithmCreateFunc {

		@Override
		public CollisionAlgorithm createCollisionAlgorithm(CollisionAlgorithmConstructionInfo ci, Collidable body0, Collidable body1) {
			ConvexConcaveCollisionAlgorithm algo = new ConvexConcaveCollisionAlgorithm();
			algo.init(ci, body0, body1, true);
			return algo;
		}

    }
	
}