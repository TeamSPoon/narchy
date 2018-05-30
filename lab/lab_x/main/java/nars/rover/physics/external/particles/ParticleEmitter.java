package nars.rover.obj.util.external.particles;


import nars.rover.obj.util.external.Entity;
import spacegraph.space2d.phys.collision.shapes.PolygonShape;
import spacegraph.space2d.phys.common.Vec2;
import spacegraph.space2d.phys.dynamics.BodyDef;
import spacegraph.space2d.phys.dynamics.BodyType;
import spacegraph.space2d.phys.dynamics.FixtureDef;

/**
 * Poops out particles
 * 
 * @author TranquilMarmot
 */
public class ParticleEmitter extends Entity {
	/** Settings being used by this emitter */
	public EmitterSettings settings;
	
	/** Counter to know when to emit particles */
	private float timeSinceLastEmission;
	
	/** Whether or not this emitter is currently releasing particles */
	private boolean active;
	
	/** Number of particles that this emitter has created */
	private int numParticlesOut;
	
	public ParticleEmitter(int layer, EmitterSettings settings){
		super(null, layer, new Vec2(settings.offset.x + settings.attached.getLocation().x, settings.offset.y + settings.attached.getLocation().y));
		this.settings = settings;
		
		this.timeSinceLastEmission = 0.0f;
		this.active = true;
		numParticlesOut = 0;
	}
	
	/** @return Body def for particle, based on current settings */
	private BodyDef getParticleBodyDef(){
		BodyDef def = new BodyDef();
		
		def.active = true;
		def.allowSleep = false;
		def.awake = true;
		def.type = BodyType.DYNAMIC;
		def.linearVelocity.set(settings.particleForce.x, settings.particleForce.y);
		def.position.set(settings.offset.x + settings.attached.getLocation().x, settings.offset.y + settings.attached.getLocation().y);
		
		return def;
	}
	
	/** @return Fixture def for particle, based on current settings */
	private FixtureDef getParticleFixtureDef(){
		FixtureDef def = new FixtureDef();
		
		def.density = settings.particleDensity;
		
		
		
		def.friction = settings.particleFriction;
		def.restitution = settings.particleRestitution;
		PolygonShape particleBox = new PolygonShape();
		particleBox.setAsBox(settings.particleWidth, settings.particleHeight);
		def.shape = particleBox;
		
		return def;
	}
	
	@Override
	public void update(float timeStep){
		super.update(timeStep);
		
		
		this.setLocation(new Vec2(settings.offset.x + settings.attached.getLocation().x, settings.offset.y + settings.attached.getLocation().y));
		
		if(this.active){
			timeSinceLastEmission += timeStep;
			
			
			if(timeSinceLastEmission >= settings.particleEmissionRate && numParticlesOut < settings.maxParticles){
				settings.onCreateParticle();
				
				for(int i = 0; i < settings.particlesPerEmission; i++){
					this.create(new Particle(
							(float)Math.random() * settings.particleLifetime,
							this,
							settings.particleRenderer,
							this.getLayer(),
							getParticleBodyDef(),
							settings.particleWidth, settings.particleHeight,
							getParticleFixtureDef()
							),false);
					
					numParticlesOut++;
					if(numParticlesOut >= settings.maxParticles)
						break;
				}
				
				
				timeSinceLastEmission = 0.0f;
			}
		}
	}

	protected void create(Particle particle, boolean b) {
		
	}


	protected void destroy(Particle p){
		/**
		 * Called by particles when they die so the emitter knows how
		 * many particles it has out
		 */
		numParticlesOut--;

		p.delete();
	}

	/** Deactivate this emitter so it no longer releases particles */
	public void deactivate(){ this.active = false; }
	
	/** Activate this emitter */
	public void activate(){ this.active = true; }	
	
}
