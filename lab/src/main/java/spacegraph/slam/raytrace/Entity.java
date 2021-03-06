package spacegraph.slam.raytrace;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public abstract class Entity {
    public Surface surface;
    public Vector3 position;
    public BufferedImage texture;
    public abstract Ray3 collide(Ray3 ray);

    public static final class Cube extends Entity {
        public double sideLength;

        private final Ray3[] faces;

        public Cube(Vector3 position, double sideLength, Surface surface, String texture) {
            this.position = position;
            this.sideLength = sideLength;
            this.surface = surface;
            double hs = sideLength / 2;
            try {
                this.texture = ImageIO.read(new File(texture));
            } catch (IOException e) {
            }
            faces = new Ray3[]{
                new Ray3(
                    position.plus(new Vector3(-hs, 0, 0)),
                    new Vector3(-1, 0, 0)
                ),
                new Ray3(
                    position.plus(new Vector3(hs, 0, 0)),
                    new Vector3(1, 0, 0)
                ),
                new Ray3(
                    position.plus(new Vector3(0, -hs, 0)),
                    new Vector3(0, -1, 0)
                ),
                new Ray3(
                    position.plus(new Vector3(0, hs, 0)),
                    new Vector3(0, 1, 0)
                ),
                new Ray3(
                    position.plus(new Vector3(0, 0, -hs)),
                    new Vector3(0, 0, -1)
                ),
                new Ray3(
                    position.plus(new Vector3(0, 0, hs)),
                    new Vector3(0, 0, 1)
                )
            };
        }

        @Override
        public Ray3 collide(Ray3 ray) {
            Ray3 closestNormal = null;
            double distanceSquared = 0;
            for (Ray3 face : faces) {
                Vector3 faceNormal = face.direction;
                double distance = ray.position.minus(face.position).dot(faceNormal);
                if (distance < 0) {
                    faceNormal = faceNormal.scale(-1);
                    distance = -distance;
                }
                Ray3 normal = new Ray3(
                    ray.position.minus(
                        ray.direction.scale(distance / ray.direction.dot(faceNormal))
                    ),
                    faceNormal
                );
                if (normal.position.minus(ray.position).dot(ray.direction) < Sphere.epsilon) {
                    continue;
                }
                Vector3 fp = normal.position.minus(face.position);
                double hs = sideLength / 2;
                if (Math.abs(fp.x()) > hs || Math.abs(fp.y()) > hs || Math.abs(fp.z()) > hs) {
                    continue;
                }
                if (closestNormal == null ||
                        normal.position.minus(ray.position).lengthSquared() < distanceSquared) {
                    closestNormal = normal;
                    distanceSquared = normal.position.minus(ray.position).lengthSquared();
                }
            }
            return closestNormal;
        }
    }

    public static final class Sphere extends Entity {
        static final double epsilon = 0.000001;

        public double radius;

        public Sphere(Vector3 position, double radius, Surface surface, String texture) {
            this.position = position;
            this.radius = radius;
            this.surface = surface;
            try {
                this.texture = ImageIO.read(new File(texture));
            } catch (IOException e) {
            }
        }

        @Override
        public Ray3 collide(Ray3 ray) {
            Vector3 closestPoint = ray.direction.scale(
                position.minus(ray.position).dot(ray.direction)
            ).plus(ray.position);
            Vector3 perpendicular = closestPoint.minus(position);
            if (perpendicular.lengthSquared() >= radius * radius) {
                return null;
            }
            Vector3 opposite = ray.direction.scale(
                Math.sqrt(radius*radius - perpendicular.lengthSquared())
            );
            Vector3 posPerp = position.plus(perpendicular);
            Vector3 intersection1 = posPerp.minus(opposite);
            Vector3 intersection2 = posPerp.plus(opposite);
            double distance1 = intersection1.minus(ray.position).dot(ray.direction);
            double distance2 = intersection2.minus(ray.position).dot(ray.direction);

            if (distance1 <= epsilon && distance2 <= epsilon) {
                return null;
            }
            Vector3 intersection;
            if (distance1 > 0 && distance2 <= epsilon) {
                intersection = intersection1;
            } else if (distance2 > 0 && distance1 <= epsilon) {
                intersection = intersection2;
            } else if (distance1 < distance2) {
                intersection = intersection1;
            } else {
                intersection = intersection2;
            }
            Ray3 normal = new Ray3(intersection, intersection.minus(position));
            if (ray.position.minus(position).lengthSquared() < radius * radius) {
                normal.direction.invert();
            }
            normal.direction = normal.direction.normalize();
            return normal;
        }
    }

    public enum Surface {
        Specular, Diffuse, Transparent
    }
}
