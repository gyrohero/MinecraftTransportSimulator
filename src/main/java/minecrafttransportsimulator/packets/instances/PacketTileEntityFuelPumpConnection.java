package minecrafttransportsimulator.packets.instances;

import io.netty.buffer.ByteBuf;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityFuelPump;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.IWrapperWorld;
import minecrafttransportsimulator.packets.components.APacketTileEntity;
import minecrafttransportsimulator.vehicles.main.AEntityBase;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;

/**Packet sent to pumps on clients to change what vehicle they are connected to.
 * 
 * @author don_bruce
 */
public class PacketTileEntityFuelPumpConnection extends APacketTileEntity<TileEntityFuelPump>{
	private final int vehicleID;
	private final boolean connect;
	
	public PacketTileEntityFuelPumpConnection(TileEntityFuelPump pump, boolean connect){
		super(pump);
		this.vehicleID = pump.connectedVehicle.lookupID;
		this.connect = connect;
	}
	
	public PacketTileEntityFuelPumpConnection(ByteBuf buf){
		super(buf);
		this.vehicleID = buf.readInt();
		this.connect = buf.readBoolean();
	}
	
	@Override
	public void writeToBuffer(ByteBuf buf){
		super.writeToBuffer(buf);
		buf.writeInt(vehicleID);
		buf.writeBoolean(connect);
	}
	
	@Override
	protected boolean handle(IWrapperWorld world, IWrapperPlayer player, TileEntityFuelPump pump){
		for(AEntityBase entity : AEntityBase.createdClientEntities){
			if(entity.lookupID == vehicleID){
				EntityVehicleF_Physics vehicle = (EntityVehicleF_Physics) entity;
				if(connect){
					pump.connectedVehicle = vehicle;
					vehicle.beingFueled = true;
					pump.getTank().resetAmountDispensed();
					vehicle.activateFuelPoint(new Point3d(pump.position));
				}else{
					vehicle.beingFueled = false;
					pump.connectedVehicle = null;
				}
				if (pump.hose != null) {
					System.out.println("$$ Packet calling generate");
					pump.hose.generate();					
				}
				return true;
			}
		}
		return true;
	}
}
