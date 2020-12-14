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
	private final Point3d defaultSegmentRot;
	private final double defaultSegmentLength;
	private final LinkedList<HoseSegment> segmentList = new LinkedList<HoseSegment>();

	private int segmentsLeftover;
	private boolean startCanBend;
	private boolean endCanBend;
	
	public final FluidTank tank;
	
	public Point3d position;
	public Point3d angles;
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
		this.defaultSegmentRot = definition.hoseEnd.copy().subtract(definition.hoseStart);
		
		//Position of the hose segment's center point
		this.hoseSegmentCenter = definition.hoseStart.copy().add(this.defaultSegmentRot.multiply(0.5));
	}
	
	private class HoseSegment{
		private final Point3d pos;
		private final Point3d angles;
		
		private HoseSegment(Point3d pos, Point3d angles) {
			this.pos = pos;
			this.angles = angles;
		}
	}

	//Takes an angle and clamps it to a max or min value of +/- the max bend angle
	private float clampAngle(float rawAngle) {
		if (rawAngle >= 0) {
			return min(rawAngle, definition.maxBend);
		}
		else {
			return max(rawAngle, -definition.maxBend);
		}
	}
	
	/*
	 * Returns true if able to connect the points with the current segment length.
	 * False otherwise. Note that the rotations passed in here are vectors representing
	 * the direction that the segment is facing, as opposed to the 'angles' member of
	 * the HoseSegment class, which represents the angular rotation around each axis.
	 */
	private boolean attemptHoseConnection(Point3d startPoint, Point3d startRot, Point3d endPoint, Point3d endRot, int numSegments) {
		final float errorAllowed = 0.01f;
		//Recursively add segments, attempting to meet at the center point.
		//TODO: 0 segments shouldn't be possible anymore. Make it throw some error.
		if (numSegments == 0) {
			//The segments should meet at the middle.
			//A little fudge factor is added for floating point errors.
			if (startPoint.distanceTo(endPoint) <= hoseSegmentLength + errorAllowed) {
				segmentList.clear();
				return true;
			else {
				return false;
			}
		}
		else if (numSegments == 1) {
			if (startPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed && endPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed) {
				//Average the start and end rotations and add this to the list
				segmentList.clear();
				segmentList.add(new HoseSegment(this.hoseCenterPoint, startRot.copy().add(endRot.multiply(-1)).anglesFrom(this.defaultSegmentRot)));
				return true;
			}
			else {
				return false;
			}
		}
		else {
			//2 or more segments left.
			//Determine which side to work from by which is further from the center point.
			boolean workFromEnd = startPoint.distanceTo(this.hoseCenterPoint) < endPoint.distanceTo(this.hoseCenterPoint);
			Point3d workingPoint = workFromEnd ? endPoint : startPoint;
			Point3d workingRot = workFromEnd ? endRot : startRot;

			if (workingPoint.distanceTo(this.hoseCenterPoint) > hoseSegmentLength + errorAllowed) {
				//Not at center yet. Keep building segments.
				//Get new rotation to turn toward the center point.
				Point3d pointToCenter = this.hoseCenterPoint.copy().subtract(workingPoint);
				//TODO: Add anglesFrom function to Point3d. Will need to pass desired axis that the
				// angles are referenced from.
				Point3d pointToCenterAngles = pointToCenter.anglesFrom(this.defaultSegmentRot);

				//Clamp all angles to have an absolute value <= max bend angle.
				Point3d newAngles = new Point3d(clampAngle((float)pointToCenterAngles.x),
					clampAngle((float)pointToCenterAngles.y),
					clampAngle((float)pointToCenterAngles.z));

				//Use the clamped angles to determine new rotation vectors.
				Point3d newRot = this.defaultSegmentRot.copy().rotateFine(newAngles);
				//Note that newRot should be of length hoseSegmentLength
				Point3d newCenter = workingPoint.copy().add(newRot.copy().multiply(0.5));

				//Only allow 1 bend each side if isRigid is true.
				if (!workFromEnd && this.startCanBend) {
					if (definition.isRigid) {
						this.startCanBend = false;
					}
				}
				if (workFromEnd && this.endCanBend) {
					if (definition.isRigid) {
						this.endCanBend = false;
					}
				}

				//Recurse, using the new segment start and end points and rotation vectors.
				boolean success = workFromEnd ? 
					attemptHoseConnection(startPoint, startRot, newCenter.copy().add(newRot.copy().multiply(0.5)), newRot, numSegments-1) : 
					attemptHoseConnection(newCenter.copy().add(newRot.copy().multiply(0.5)), newRot, endPoint, endRot, numSegments-1);
				
				if (success) {
					//Add the center points and angles to the segment list
					if (!workFromEnd) {
						segmentList.addFirst(new HoseSegment(newCenter, newAngles));
					}
					else {
						segmentList.addLast(new HoseSegment(newCenter, newAngles));
					}
				}
			}
			else {
				//Additional segments aren't needed.
				//Keep track of how many extra segments for optimization purposes.
				this.segmentsLeftover = numSegments;
				return true;
			}
		}
	}
	
	public void render() {
		Point3d currentNozzlePos;
		Point3d currentNozzleAngles;
		
		// No valid vehicle, keep the nozzle in its stowed position
		if (connectedVehicle == null || !connectedVehicle.isValid) {
			currentNozzlePos = position.copy().add(definition.nozzlePos);
			currentNozzleAngles = this.angles;
			/* RENDER NOZZLE */
		}
		else {
			currentNozzlePos = connectedVehicle.getConnectedFuelPointPosition();
			currentNozzleAngles = connectedVehicle.angles.copy().rotateFine(definition.attachRot);
		}
		
		Point3d hoseEndPoint = currentNozzlePos.add(nozzleHoseOffset.copy().rotateFine(currentNozzleAngles));
		Point3d hoseStartPoint = position.copy().add(definition.hoseStart);
		//Center point will average the start and end points.
		//May add gravity sag later.
		this.hoseCenterPoint = hoseEndPoint.copy().add(hoseStartPoint).multiply(0.5);

		// Hose connecting algorithm
		this.hoseSegmentLength = defaultSegmentLength;
		this.segmentsLeftover = 0;
		this.startCanBend = true;
		this.endCanBend = true;

		final int MAX_ATTEMPTS = definition.maxExtend == 0 ? 1 : 3; //Should be at least 2 to allow extension.
		//If it can't extend, only attempt once.
		int attemptNum = 0;
		boolean success = false;

		while(attemptNum < MAX_ATTEMPTS) {
			success = attemptHoseConnection(hoseStartPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(this.angles),
				hoseEndPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(currentNozzleAngles), definition.numSegments);
			if (success) {
				if (attemptNum == 0) {
					//Succeeded on first attempt, so segments are as short as possible.
					//No need to try again.
					break;
				}
				else {
					//Binary search decrement.
					this.hoseSegmentLength -= definition.maxExtend * Math.pow(2, -attemptNum);
				}
			}
			else {
				if (attemptNum == 1) {
					//Already fully extended. No way to connect.
					break;
				}
				//Binary search increment.
				this.hoseSegmentLength += definition.maxExtend * Math.pow(2, -attemptNum);
			}
			++attemptNum;
		}

		if (!success) {
			//Couldn't connect, so break the connection.
			this.connectedVehicle = null;
		}
		else {
			//Render the hose using the last successful segmentList.
			for (HoseSegment segment : this.segmentList) {
				/* RENDER HOSE SEGMENT */
			}
		}
	}
}
