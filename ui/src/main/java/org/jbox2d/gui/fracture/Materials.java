package org.jbox2d.gui.fracture;

import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.dynamics.*;
import org.jbox2d.fracture.Material;
import org.jbox2d.gui.ICase;
import spacegraph.math.v2;

/**
 * Testovaci scenar, ktory umoznuje testovanie materialov. Podla parametra sa
 * stene v scenari nadefinuje dany material, vystreli sa do nej projektyl a skuma
 * chovanie daneho materialu.
 *
 * @author Marek Benovic
 */
public class Materials implements ICase {
    private final Material material;

    /**
     * Inicializuje testovaci scenar a definuje telesu material z parametra.
     *
     * @param material
     */
    public Materials(Material material) {
        this.material = material;
    }

    @Override
    public void init(Dynamics2D w) {
        {
            BodyDef bodyDef2 = new BodyDef();
            bodyDef2.type = BodyType.DYNAMIC;
            bodyDef2.position.set(0.0f, 10.0f); //pozicia
            Body newBody = w.addBody(bodyDef2);
            PolygonShape shape3 = new PolygonShape();
            shape3.setAsBox(5.0f, 10.0f);
            Fixture f = newBody.addFixture(shape3, 1.0f);
            f.m_friction = 0.2f; // trenie
            f.m_restitution = 0.0f; //odrazivost

            f.m_material = material;

            f.m_material.m_rigidity = 20.0f;
        }

        {
            BodyDef bodyDefBullet = new BodyDef();
            bodyDefBullet.type = BodyType.DYNAMIC;
            bodyDefBullet.position.set(-30.0f, 12.0f); //pozicia
            bodyDefBullet.linearVelocity = new v2(100.0f, 0.0f); // smer pohybu
            Body bodyBullet = w.addBody(bodyDefBullet);
            CircleShape circleShape = new CircleShape();
            circleShape.m_radius = 1.0f;
            Fixture fixtureBullet = bodyBullet.addFixture(circleShape, 5.0f);
            fixtureBullet.m_friction = 0.4f; // trenie
            fixtureBullet.m_restitution = 0.1f; //odrazivost
        }
    }

    @Override
    public String toString() {
        return "Material: " + material.toString();
    }
}
