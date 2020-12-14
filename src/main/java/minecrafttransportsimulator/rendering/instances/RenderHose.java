package minecrafttransportsimulator.rendering.instances;

import java.util.LinkedList;

import minecrafttransportsimulator.baseclasses.FluidTank;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.jsondefs.JSONDecor.FuelSupplier;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;

public class RenderHose {
	
	private final FuelSupplier definition;
	private final Point3d nozzleHoseOffset;
	private final Point3d hoseSegmentCenter;
	private final Point3d offsetSegmentEnd;
	private final Point3d offsetSegmentStart;
	private final double defaultSegmentLength;
	private final LinkedList<HoseSegment> segmentList = new LinkedList<HoseSegment>();
	
	public final FluidTank tank;
	
	public Point3d position;
	public Point3d rotation;
	public EntityVehicleF_Physics connectedVehicle;
	public double hoseSegmentLength;
	public Point3d hoseCenterPoint;

	public RenderHose(FuelSupplier definition, FluidTank tank) {
		this.definition = definition;
		this.tank = tank;
		
		this.defaultSegmentLength = definition.hoseStart.distanceTo(definition.hoseEnd);
		//Vector from nozzle to hose
		this.nozzleHoseOffset = definition.hoseEnd.copy().subtract(definition.nozzlePos);
		
		//Relative offsets of start and end of the segment
		this.offsetSegmentEnd = definition.hoseEnd.copy().subtract(definition.hoseStart).multiply(0.5);
		this.offsetSegmentStart = this.offsetSegmentEnd.copy().multiply(-1);
		
		//Position of the hose segment's center point
		this.hoseSegmentCenter = definition.hoseStart.copy().add(this.offsetSegmentEnd);
	}
	
	private class HoseSegment{
		private final Point3d pos;
		private final Point3d rot;
		
		private HoseSegment(Point3d pos, Point3d rot) {
			this.pos = pos;
			this.rot = rot;
		}
	}
	
	/*
	 * Returns true if able to connect the points with the current segment length.
	 * False otherwise.
	 */
	private boolean attemptHoseConnection(Point3d startPoint, Point3d startRot, Point3d endPoint, Point3d endRot, int numSegments) {
		final float errorAllowed = 0.01f;
		//Recursively add segments, attempting to meet at the center point.
		if (numSegments == 0) {
			//The segments should meet at the middle.
			//A little fudge factor is added for floating point errors.
			return startPoint.distanceTo(endPoint) <= hoseSegmentLength + errorAllowed;
		}
		else if (numSegments == 1) {
			if (startPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed && endPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed) {
				//Average the start and end rotations and add this to the list
				segmentList.add(new HoseSegment(this.hoseCenterPoint, startRot.multiply(0.5).add(endRot.multiply(0.5))));
				return true;
			}
			else {
				return false;
			}
		}
		else {
			//2 or more segments left
			if (startPoint.distanceTo(this.hoseCenterPoint) > hoseSegmentLength + errorAllowed || startPoint.distanceTo(this.hoseCenterPoint) > hoseSegmentLength + errorAllowed) {
				//Not at center yet. Keep building segments.
				/* Stuff to rotate segments toward center */
				
				boolean success = attemptHoseConnection();
				if (success) {
					segmentList.addFirst(new HoseSegment());
					segmentList.addLast(new HoseSegment());
				}
			}
			else {
				//Additional segments aren't needed.
				return true;
			}
		}
	}
	
	public void render() {
		Point3d currentNozzlePos;
		Point3d currentNozzleRot;
		
		// No valid vehicle, keep the nozzle in its stowed position
		if (connectedVehicle == null || !connectedVehicle.isValid) {
			currentNozzlePos = position.copy().add(definition.nozzlePos);
			currentNozzleRot = this.rotation;
			/* RENDER NOZZLE */
		}
		else {
			currentNozzlePos = connectedVehicle.getConnectedFuelPointPosition();
			currentNozzleRot = connectedVehicle.angles.copy().rotateFine(definition.attachRot);
		}
		
		Point3d hoseEndPoint = currentNozzlePos.add(nozzleHoseOffset.copy().rotateFine(currentNozzleRot));
		Point3d hoseStartPoint = position.copy().add(definition.hoseStart);

		// Hose connecting algorithm
		this.hoseSegmentLength = defaultSegmentLength;

		/*
		 *  Returns true if able to connect with this hose length,
		 *  false if unable.
		 */
		final boolean attemptHoseConnection(Point3d startPoint, Point3d endPoint) {
			
		}
	}
}
