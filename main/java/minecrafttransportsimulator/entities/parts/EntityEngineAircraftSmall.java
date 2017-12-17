package minecrafttransportsimulator.entities.parts;

import minecrafttransportsimulator.dataclasses.MTSRegistry;
import minecrafttransportsimulator.entities.core.EntityMultipartParent;
import minecrafttransportsimulator.entities.main.EntityPlane;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class EntityEngineAircraftSmall extends EntityEngineAircraft{
	public EntityEngineAircraftSmall(World world){
		super(world);
	}
	
	public EntityEngineAircraftSmall(World world, EntityMultipartParent parent, String parentUUID, float offsetX, float offsetY, float offsetZ, int propertyCode){
		super(world, (EntityPlane) parent, parentUUID, offsetX, offsetY, offsetZ);
	}

	@Override
	protected float getSize(){
		return 1.0F;
	}

	@Override
	protected byte getStarterPower(){
		return 50;
	}

	@Override
	protected byte getStarterIncrement(){
		return 4;
	}

	@Override
	protected String getCrankingSoundName(){
		return "small_engine_cranking";
	}

	@Override
	protected String getStartingSoundName(){
		return "small_engine_starting";
	}

	@Override
	protected String getRunningSoundName(){
		return "small_engine_running";
	}

	@Override
	protected Item getEngineItem(){
		return MTSRegistry.engineAircraftSmall;
	}

	@Override
	protected boolean isSmall(){
		return true;
	}
}
