package com.siondream.libgdxjam.overlap;

import box2dLight.ConeLight;
import box2dLight.Light;
import box2dLight.PointLight;
import box2dLight.RayHandler;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Filter;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Logger;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonDataLoader.SkeletonDataLoaderParameter;
import com.siondream.libgdxjam.Env;
import com.siondream.libgdxjam.ecs.Mappers;
import com.siondream.libgdxjam.ecs.components.IDComponent;
import com.siondream.libgdxjam.ecs.components.LayerComponent;
import com.siondream.libgdxjam.ecs.components.LightComponent;
import com.siondream.libgdxjam.ecs.components.NodeComponent;
import com.siondream.libgdxjam.ecs.components.ParticleComponent;
import com.siondream.libgdxjam.ecs.components.PhysicsComponent;
import com.siondream.libgdxjam.ecs.components.RootComponent;
import com.siondream.libgdxjam.ecs.components.SizeComponent;
import com.siondream.libgdxjam.ecs.components.SpineComponent;
import com.siondream.libgdxjam.ecs.components.TextureComponent;
import com.siondream.libgdxjam.ecs.components.TransformComponent;
import com.siondream.libgdxjam.ecs.components.ZIndexComponent;
import com.siondream.libgdxjam.overlap.plugins.OverlapLoaderPlugin;
import com.siondream.libgdxjam.physics.Categories;
import com.siondream.libgdxjam.physics.Material;

public class OverlapSceneLoader extends AsynchronousAssetLoader<OverlapScene, OverlapSceneLoader.Parameters> {
	private static final String ASSETS_DIR = "overlap/assets/orig/";
	private static final String SPINE_ANIMS_DIR = ASSETS_DIR + "spine-animations/";
	private static final String PARTICLES_DIR = ASSETS_DIR + "particles/";
	
	private JsonReader reader = new JsonReader();
	private Parameters parameters;
	private TextureAtlas atlas;
	
	private Logger logger = new Logger(
		OverlapSceneLoader.class.getSimpleName(),
		Logger.INFO
	);
	
	// Final scene
	private OverlapScene map;
	
	// Plugin mapper
	private static final ObjectMap<String, OverlapLoaderPlugin > pluginMapper =
			new ObjectMap<String, OverlapLoaderPlugin >();
	
	// Cache to avoid creating a new array per physics component
	private static final BodyType[] bodyTypesCache = BodyDef.BodyType.values();
	// Cache to avoid creating new vectors in spine anims component
	private static final Vector2 v2Utils1 = new Vector2();
	private static final Vector2 v2Utils2 = new Vector2();
	
	public OverlapSceneLoader(FileHandleResolver resolver) {
		super(resolver);
		
		logger.info("initialize");
	}

	public static class Parameters extends AssetLoaderParameters<OverlapScene> {
		public float units = 1.0f;
		public String atlas = "";
		public String spineFolder = "";
		public World world;
		public Categories categories;
		public RayHandler rayHandler;
	}

	@Override
	public void loadAsync(AssetManager manager,
						  String fileName,
						  FileHandle file,
						  Parameters parameter) {
		map = loadInternal(manager, fileName, file, parameter);
	}

	@Override
	public OverlapScene loadSync(AssetManager manager,
								 String fileName,
								 FileHandle file,
								 Parameters parameter) {
		
		return map;
	}

	@Override
	public Array<AssetDescriptor> getDependencies(String fileName,
												  FileHandle file,
												  Parameters parameter) {
		
		Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();
		dependencies.add(new AssetDescriptor(parameter.atlas, TextureAtlas.class));
		findSpineAnims(reader.parse(file), dependencies);
		
		return dependencies;
	}
	
	public static void registerPlugin(String typeName, OverlapLoaderPlugin loaderClass)
	{
		pluginMapper.put(typeName, loaderClass);
	}
	
	private OverlapScene loadInternal(AssetManager manager,
						 	  		  String fileName,
						 	  		  FileHandle file,
						 	  		  Parameters parameter) {
		this.parameters = parameter;
		this.atlas = manager.get(parameters.atlas, TextureAtlas.class);
		
		logger.info("parsing scene");
		
		JsonValue root = reader.parse(file);
		
		OverlapScene scene = new OverlapScene();
		Entity rootEntity = loadRoot(scene, root.get("composite"));
		
		scene.setName(root.getString("sceneName", ""));
		scene.setRoot(rootEntity);
		
		return scene;
	}
	
	private Entity loadRoot(OverlapScene scene, JsonValue value) {
		logger.info("loading root");
		
		Entity entity = new Entity();
		
		RootComponent root = new RootComponent();
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		
		entity.add(root);
		entity.add(node);
		entity.add(transform);
		
		loadLayers(entity, value.get("layers"));
		loadImages(scene, entity, value.get("sImages"));
		loadSpineAnimations(scene, entity, value.get("sSpineAnimations"));
		loadComposites(scene, entity, value.get("sComposites"));
		loadParticles(scene, entity, value.get("sParticleEffects"));
		loadLights(scene, entity, value.get("sLights"));
			
		return entity;
	}
	
	private void loadImages(OverlapScene scene, Entity parent, JsonValue value) {
		if (value == null || value.size == 0) { return; }
		
		NodeComponent node = Mappers.node.get(parent);
		
		for (int i = 0; i < value.size; ++i) {
			Entity child = loadImage(scene, value.get(i));
			NodeComponent childNode = Mappers.node.get(child);
			
			node.children.add(child);
			childNode.parent = parent;
		}
	}
	
	private void loadSpineAnimations(OverlapScene scene, Entity parent, JsonValue value)
	{
		if (value == null || value.size == 0) { return; }
		
		NodeComponent node = Mappers.node.get(parent);
		
		for (int i = 0; i < value.size; ++i) {
			Entity child = loadSpineAnimation(value.get(i));
			NodeComponent childNode = Mappers.node.get(child);
			
			node.children.add(child);
			childNode.parent = parent;
		}
	}
	
	private void loadComposites(OverlapScene scene, Entity parent, JsonValue value) {
		if (value == null || value.size == 0) { return; }
		
		NodeComponent node = Mappers.node.get(parent);
		
		for (int i = 0; i < value.size; ++i) {
			Entity child = loadComposite(scene, value.get(i));
			NodeComponent childNode = Mappers.node.get(child);
			
			node.children.add(child);
			childNode.parent = parent;
		}
	}
	
	private void loadParticles(OverlapScene scene, Entity parent, JsonValue value) {
		if (value == null || value.size == 0) { return; }
		
		NodeComponent node = Mappers.node.get(parent);
		
		for (int i = 0; i < value.size; ++i) {
			Entity child = loadParticle(scene, value.get(i));
			NodeComponent childNode = Mappers.node.get(child);
			
			node.children.add(child);
			childNode.parent = parent;
		}
	}
	
	private void loadLights(OverlapScene scene, Entity parent, JsonValue value)
	{
		if (value == null || value.size == 0) { return; }
		
		NodeComponent node = Mappers.node.get(parent);
		
		for (int i = 0; i < value.size; ++i) {
			Entity child = loadLight(scene, value.get(i));
			NodeComponent childNode = Mappers.node.get(child);
			
			node.children.add(child);
			childNode.parent = parent;
		}
	}
	
	private Entity loadComposite(OverlapScene scene, JsonValue value) {
		Entity entity = new Entity();
		
		logger.info("loading composite: " + value.getString("itemIdentifier", value.getString("uniqueId", "")));
		
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		ZIndexComponent index = new ZIndexComponent();
		IDComponent id = new IDComponent();
		
		entity.add(node);
		entity.add(transform);
		entity.add(index);
		entity.add(id);
		
		id.value = value.getInt("uniqueId");
		index.layer = value.getString("layerName");
		
		loadTransform(transform, value);
		loadLayers(entity, value.get("layers"));
		loadPolygon(entity, transform, value);
		
		JsonValue composite = value.get("composite");
		loadImages(scene, entity, composite.get("sImages"));
		loadComposites(scene, entity, composite.get("sComposites"));
		loadParticles(scene, entity, composite.get("sParticleEffects"));
		loadLights(scene, entity, composite.get("sLights"));
		loadSpineAnimations(scene, entity, composite.get("sSpineAnimations"));

		if(value.has("customVars"))
		{
			loadTypeProperties(scene, entity, getExtraInfo(value.getString("customVars")));
		}
		
		return entity;
	}
	
	
	
	private Entity loadImage(OverlapScene scene, JsonValue value) {
		Entity entity = new Entity();
		
		logger.info("loading image: " + value.getString("imageName"));
		
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		TextureComponent texture = new TextureComponent();
		ZIndexComponent index = new ZIndexComponent();
		SizeComponent size = new SizeComponent();
		IDComponent id = new IDComponent();
		
		id.value = value.getInt("uniqueId");
		loadTransform(transform, value);
		index.layer = value.getString("layerName");
		texture.region = atlas.findRegion(value.getString("imageName"));
		size.width = texture.region.getRegionWidth() * parameters.units;
		size.height = texture.region.getRegionHeight() * parameters.units;
		
		loadPolygon(entity, transform, value);
		
		entity.add(node);
		entity.add(size);
		entity.add(transform);
		entity.add(texture);
		entity.add(index);
		entity.add(id);
		
		if(value.has("customVars"))
		{
			loadTypeProperties(scene, entity, getExtraInfo(value.getString("customVars")));
		}
		
		return entity;
	}
	
	private Entity loadParticle(OverlapScene scene, JsonValue value) {
		Entity entity = new Entity();
		
		logger.info("loading particle: " + value.getString("particleName") + " " + value.getString("itemIdentifier", ""));
		
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		ParticleComponent particle = new ParticleComponent();
		ZIndexComponent index = new ZIndexComponent();
		SizeComponent size = new SizeComponent();
		IDComponent id = new IDComponent();
		
		id.value = value.getInt("uniqueId");
		
		loadTransform(transform, value);
		index.layer = value.getString("layerName");
		
		String particleName = value.getString("particleName");
		
		ParticleEffect effect = new ParticleEffect();
		effect.load(
			Gdx.files.internal(PARTICLES_DIR + particleName),
			atlas
		);
		
		particle.effect = effect;
		BoundingBox box = particle.effect.getBoundingBox(); 
		size.width = (box.max.x - box.min.x) * parameters.units;
		size.height = (box.max.y - box.min.y) * parameters.units;
		
		entity.add(node);
		entity.add(size);
		entity.add(transform);
		entity.add(particle);
		entity.add(index);
		entity.add(id);
		
		return entity;
	}
	
	private void loadPolygon(Entity entity, TransformComponent transform, JsonValue value)
	{
		if (this.parameters.world == null) { return; }
		if (this.parameters.categories == null) { return; }
		
		logger.info("loading physic body: " + value.getString("layerName"));
		
		// Other possible required properties
		ObjectMap<String, String> extraInfo = 
				getExtraInfo(value.has("customVars") ? value.getString("customVars") : null);
		
		// Polygon shape
		JsonValue polygonInfo = value.get("shape");
		if (polygonInfo == null || polygonInfo.size == 0) { return; }
		
		// Parse vertices
		JsonValue shapeInfo = polygonInfo.get("polygons");
		JsonValue physicsInfo = value.get("physics");
				
		Body body;
		BodyDef bodyDef = new BodyDef();
		
		if (physicsInfo == null || physicsInfo.size == 0)
		{
			// Default body properties
			bodyDef.type = BodyType.StaticBody;
			bodyDef.allowSleep = true;
			bodyDef.awake = true;
		}
		else
		{
			// Body properties
			bodyDef.type = bodyTypesCache[physicsInfo.has("bodyType") ? physicsInfo.getInt("bodyType") : 0];
			bodyDef.allowSleep = physicsInfo.has("allowSleep") ? physicsInfo.getBoolean("allowSleep") : true;
			bodyDef.awake = physicsInfo.has("awake") ? physicsInfo.getBoolean("awake") : true;
		}
		
		// Create the body
		body = this.parameters.world.createBody(bodyDef);
		body.setUserData(entity);
		
		for (JsonValue vertices = shapeInfo.child; vertices != null; vertices = vertices.next) {
			Array<Vector2> points = Array.of(Vector2.class);
			for (JsonValue vertex = vertices.child; vertex != null; vertex = vertex.next) {
				points.add(new Vector2(
					vertex.has("x") ? vertex.getFloat("x") * parameters.units : 0.0f,
					vertex.has("y") ? vertex.getFloat("y") * parameters.units : 0.0f
				));
			}
			
			FixtureDef fixtureDef = new FixtureDef();
			
			// If it has not physics component, search for material and if not, set to default material
			Material material;
			
			if (physicsInfo == null || physicsInfo.size == 0)
			{
				if(extraInfo.containsKey("material"))
				{
					material = Material.getMaterial(extraInfo.get("material"));
				}
				else
				{
					material = Material.DEFAULT;
				}

				fixtureDef.density = material.getDensity();
				fixtureDef.friction = material.getFriction();
				fixtureDef.restitution = material.getRestitution();
			}
			else
			{
				// Material properties, if not present, set the physics component properties
				if(extraInfo.containsKey("material"))
				{
					material = Material.getMaterial(extraInfo.get("material"));
					fixtureDef.density = material.getDensity();
					fixtureDef.friction = material.getFriction();
					fixtureDef.restitution = material.getRestitution();
				}
				else
				{
					fixtureDef.density = physicsInfo.has("density") ? physicsInfo.getFloat("density") : 0f;
					fixtureDef.friction = physicsInfo.has("friction") ? physicsInfo.getFloat("friction") : 0f;
					fixtureDef.restitution = physicsInfo.has("restitution") ? physicsInfo.getFloat("restitution") : 0f;
				}
			}
			
			float[] polyVertices = new float[points.size * 2];
			int vertexIndex = 0;
			
			for (Vector2 point : points) {
				polyVertices[vertexIndex++] = point.x;
				polyVertices[vertexIndex++] = point.y;
			}
			
			PolygonShape polygon = new PolygonShape();
			polygon.set(polyVertices);
			fixtureDef.shape = polygon;
			
			Filter filter = new Filter();
			filter.categoryBits = parameters.categories.getBits("level");
			
			Fixture fixture = body.createFixture(fixtureDef);
			fixture.setFilterData(filter);
			

			polygon.dispose();
		}

		body.setTransform(transform.position, transform.angle);
		
		// Create the physics component
		PhysicsComponent physicsComponent = new PhysicsComponent();
		physicsComponent.body = body; 
		
		entity.add(physicsComponent);
		
	}
	
	private Entity loadLight(OverlapScene scene, JsonValue value) 
	{
		Entity entity = new Entity();
		
		logger.info("loading light: " + (value.has("itemIdentifier") ? value.getString("itemIdentifier") : "default") );
		
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		ZIndexComponent index = new ZIndexComponent();
		LightComponent light = new LightComponent();
		IDComponent id = new IDComponent();
		
		id.value = value.getInt("uniqueId");
		
		loadTransform(transform, value);
		
		index.layer = value.getString("layerName");
				
		// Create a new light
		light.light = createLight(value.getString("type"), transform, value);
		
		entity.add(node);
		entity.add(transform);
		entity.add(index);
		entity.add(light);
		entity.add(id);
		
		return entity;
	}
	
	private Light createLight(String type, TransformComponent transform, JsonValue params)
	{
		Light light = null;
		
		// Common attributes for the constructor
		Color color = (params.has("tint") ? getColor(params.get("tint").asFloatArray()) : Color.WHITE);
		int rays = params.has("rays") ? params.getInt("rays") : 12; // Default is 12
		float distance = params.has("distance") ? params.getFloat("distance") : 300f;
		
		// Light direction
		transform.angle = params.has("directionDegree") ? params.getFloat("directionDegree") : 0f;
		
		if (type.equals("CONE")) {
			light = new ConeLight(
				parameters.rayHandler, 
				rays,
				color, 
				distance, 
				transform.position.x, 
				transform.position.y, 
				transform.angle, 
				params.has("coneDegree") ? params.getFloat("coneDegree") : 45f
			);
		}
		else if (type.equals("POINT")) {
			light = new PointLight(
				parameters.rayHandler, 
				rays, 
				color,
				distance, 
				transform.position.x, 
				transform.position.y
			);
		}

		light.setStaticLight( params.has("isStatic") ? false : true );
		light.setXray( params.has("isXRay") ? false : true );
		light.setSoftnessLength( params.has("softnessLength") ? params.getFloat("softnessLength") : 1.5f );
		
		return light;
	}
	
	private Color getColor(float[] colorArray)
	{
		if(colorArray.length < 4)
		{
			logger.error("Light color couldn't be parsed");
			return Color.WHITE;
		}

		Color color = new Color();
		color.r = colorArray[0];
		color.g = colorArray[1];
		color.b = colorArray[2];
		color.a = colorArray[3];
		
		return color;
	}
	
	private Entity loadSpineAnimation(JsonValue value)
	{
		Entity entity = new Entity();
		
		logger.info("loading spine anim: " + value.getString("animationName"));
		
		NodeComponent node = new NodeComponent();
		TransformComponent transform = new TransformComponent();
		ZIndexComponent index = new ZIndexComponent();
		SizeComponent size = new SizeComponent();
		SpineComponent spine = new SpineComponent();
		IDComponent id = new IDComponent();
		
		id.value = value.getInt("uniqueId");
		
		loadTransform(transform, value);
		index.layer = value.getString("layerName");
		
		// Load custom info
		ObjectMap<String, String> extraInfo = 
				getExtraInfo(value.has("customVars") ? value.getString("customVars") : null);

		// Get animation asset path
		String animationName = value.getString("animationName");
		String animationPathWithoutExtension = 
				parameters.spineFolder + animationName + "/" + animationName;
		
		// Load spine atlas
		SkeletonData skeletonData = Env.getGame().getAssetManager().get(
				animationPathWithoutExtension + ".json", 
				SkeletonData.class);
		
		// Load spine skeleton
		spine.skeleton = new Skeleton(skeletonData);
		
		// Load animation state data
		AnimationStateData stateData = new AnimationStateData(skeletonData);
		spine.state = new AnimationState(stateData);
		spine.skeleton.setSkin(skeletonData.getSkins().first());
		spine.state.setAnimation(
			0, 
			value.getString("currentAnimationName"), 
			true
		);

		// Update bounds and origin
		spine.skeleton.updateWorldTransform();
		spine.skeleton.getBounds(v2Utils1, v2Utils2);
		size.width = v2Utils2.x;
		size.height = v2Utils2.y;
		transform.origin.set(v2Utils1);
		// Fix to position spine anim in the right coords... TODO: Is there a good solution?
		transform.position.add(size.width * .5f, size.height * .8f);

		entity.add(node);
		entity.add(size);
		entity.add(transform);
		entity.add(index);
		entity.add(spine);
		entity.add(id);
		
		return entity;
	}
	
	private ObjectMap<String, String> getExtraInfo(String extraInfo)
	{
		ObjectMap<String, String> extraInfoTable = new ObjectMap<String, String>();
		
		if(extraInfo == null)
		{
			return extraInfoTable;
		}
		
		String[] extraInfoData = extraInfo.split(";");
		for(String entry : extraInfoData)
		{
			String[] keyValue = entry.split(":");
			extraInfoTable.put(keyValue[0], keyValue[1]);
		}
		
		return extraInfoTable;
	}
	
	private void loadTransform(TransformComponent transform, JsonValue value) {
		transform.position.x = value.getFloat("x", 0.0f) * parameters.units;
		transform.position.y = value.getFloat("y", 0.0f) * parameters.units;
		transform.origin.x = value.getFloat("originX", 0.0f) * parameters.units;
		transform.origin.y = value.getFloat("originY", 0.0f) * parameters.units;
		transform.scale.x = value.getFloat("scaleX", 1.0f);
		transform.scale.y = value.getFloat("scaleY", 1.0f);
		transform.angle = value.getFloat("rotation", 0.0f);
	}
	
	private void loadLayers(Entity entity, JsonValue value) {
		if (value == null || value.size == 0) { return; }
		
		LayerComponent layer = new LayerComponent();
		
		for (int i = 0; i < value.size; ++i) {
			layer.names.add(value.get(i).getString("layerName"));
		}
		
		entity.add(layer);
	}
	
	private void loadTypeProperties(OverlapScene scene, Entity entity, ObjectMap<String, String> value)
	{
		if (value == null || value.size == 0) { return; }
		
		if (!value.containsKey("type")) { return; }
		
		OverlapLoaderPlugin loader = pluginMapper.get(value.get("type"));
		loader.load(scene, entity, value);
	}
	
	private void findSpineAnims(JsonValue value, Array<AssetDescriptor> dependencies)
	{
		if (value.has("composite"))
		{
			findSpineAnims(value.get("composite"), dependencies);
		}
		if (value.has("sComposites"))
		{
			JsonValue composites = value.get("sComposites");

			for (int i = 0; i < composites.size; ++i)
			{
				findSpineAnims(composites.get(i), dependencies);
			}
		}
		if (value.has("sSpineAnimations"))
		{
			JsonValue animations = value.get("sSpineAnimations");

			for (int i = 0; i < animations.size; ++i)
			{
				String animationName = animations.get(i).getString("animationName");

				logger.info("-- Found spine animation: " + animationName);

				String fileWithoutExtension = SPINE_ANIMS_DIR + animationName + "/" + animationName;
				
				SkeletonDataLoaderParameter skeletonParams = new SkeletonDataLoaderParameter();
				skeletonParams.atlasName = fileWithoutExtension + ".atlas";
				
				dependencies.add(new AssetDescriptor(
						fileWithoutExtension + ".json",
						SkeletonData.class,
						skeletonParams
						));
			}
		}
	}
}
