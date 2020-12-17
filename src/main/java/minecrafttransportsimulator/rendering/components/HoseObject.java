package minecrafttransportsimulator.rendering.components;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.baseclasses.FluidTank;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityDecor;
import minecrafttransportsimulator.jsondefs.AJSONMultiModelProvider;
import minecrafttransportsimulator.jsondefs.JSONDecor;
import minecrafttransportsimulator.jsondefs.JSONDecor.FuelSupplier;
import minecrafttransportsimulator.mcinterface.MasterLoader;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;

public final class HoseObject <JSONDefinition extends AJSONMultiModelProvider<?>>{
	private final Map<FuelSupplier, Integer> hoseDisplayListMap = new HashMap<FuelSupplier, Integer>();
	private final Map<FuelSupplier, Integer> nozzleDisplayListMap = new HashMap<FuelSupplier, Integer>();
	
	private final JSONDefinition parentDefinition;
	private final FuelSupplier definition;
	private final String subName;
	private final Point3d nozzleHoseOffset;
	private final Point3d hoseSegmentCenter;
	private final Point3d defaultSegmentRot;
	private final double defaultSegmentLength;

	private int segmentsLeftover;
	private boolean startCanBend;
	private boolean endCanBend;
	private Point3d hoseCenterPoint;
	private double hoseSegmentLength;
	
	public final FluidTank tank;
	
	public Point3d position;
	public Point3d angles;
	public EntityVehicleF_Physics connectedVehicle;
	
	//To be rendered
	private final LinkedList<HoseSegment> segmentList = new LinkedList<HoseSegment>();
	private Point3d currentNozzlePos;
	private Point3d currentNozzleAngles;

	public HoseObject(JSONDefinition parent, FuelSupplier definition, FluidTank tank, String currentSubName) {
		MasterLoader.coreInterface.logError("$$ Creating HoseObject");
		this.parentDefinition = parent;
		this.definition = definition;
		this.tank = tank;
		this.subName = currentSubName;
		
		this.defaultSegmentLength = definition.hoseStart.distanceTo(definition.hoseEnd);
		//Vector from nozzle to hose
		this.nozzleHoseOffset = definition.nozzleHosePos.copy().subtract(definition.nozzlePos);
		
		//Relative offsets of start and end of the segment
		this.defaultSegmentRot = definition.hoseEnd.copy().subtract(definition.hoseStart);
		
		//Position of the hose segment's center point
		this.hoseSegmentCenter = definition.hoseStart.copy().add(this.defaultSegmentRot.copy().multiply(0.5));
	}
	
	class HoseSegment{
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
			return Math.min(rawAngle, definition.maxBend);
		}
		else {
			return Math.max(rawAngle, -definition.maxBend);
		}
	}
	
	/**
	 * Returns true if able to connect the points with the current segment length.
	 * False otherwise. Note that the rotations passed in here are vectors representing
	 * the direction that the segment is facing, as opposed to the 'angles' member of
	 * the HoseSegment class, which represents the angular rotation around each axis.
	 */
	private boolean attemptHoseConnection(Point3d startPoint, Point3d startRot, Point3d endPoint, Point3d endRot, int numSegments) {
		MasterLoader.coreInterface.logError("$$ Attempting hose connection. Segments left: " + numSegments);
		final float errorAllowed = 0.1f;
		//Recursively add segments, attempting to meet at the center point.
		if (numSegments == 1) {
			MasterLoader.coreInterface.logError("$$ 1 segment left. Distances are: " + 
					(startPoint.distanceTo(this.hoseCenterPoint)) + " and " +
					(startPoint.distanceTo(this.hoseCenterPoint)));
			if (startPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed && endPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed) {
				//Average the start and end rotations and add this to the list
				MasterLoader.coreInterface.logError("$$ Clearing out the segment list");
				segmentList.clear();
				MasterLoader.coreInterface.logError("$$ Adding a segment at position: " + this.hoseCenterPoint);
				segmentList.add(new HoseSegment(this.hoseCenterPoint, startRot.copy().add(endRot.multiply(-1)).anglesFrom(this.defaultSegmentRot)));
				return true;
			}
			else {
				MasterLoader.coreInterface.logError("$$ Failed with 1 segment left");
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

				MasterLoader.coreInterface.logError("$$ Current segment centered at: " + newCenter);
				//Recurse, using the new segment start and end points and rotation vectors.
				boolean success = workFromEnd ? 
					attemptHoseConnection(startPoint, startRot, newCenter.copy().add(newRot.copy().multiply(0.5)), newRot, numSegments-1) : 
					attemptHoseConnection(newCenter.copy().add(newRot.copy().multiply(0.5)), newRot, endPoint, endRot, numSegments-1);
				
				if (success) {
					//Add the center points and angles to the segment list
					if (!workFromEnd) {
						MasterLoader.coreInterface.logError("$$ Adding a start segment at position: " + newCenter);
						segmentList.addFirst(new HoseSegment(newCenter, newAngles));
					}
					else {
						MasterLoader.coreInterface.logError("$$ Adding an end segment at position: " + newCenter);
						segmentList.addLast(new HoseSegment(newCenter, newAngles));
					}
				}
				return success;
			}
			else {
				//Additional segments aren't needed.
				//Keep track of how many extra segments for optimization purposes.
				this.segmentsLeftover = numSegments;
				return true;
			}
		}
	}
	
	public void generate() {
		if (definition.numSegments < 1) {
			MasterLoader.coreInterface.logError("Tried to generate hose with 0 segments. Aborting.");
			return;
		}
		MasterLoader.coreInterface.logError("$$ Generating Hose");
		// No valid vehicle, keep the nozzle in its stowed position
		
		MasterLoader.coreInterface.logError("$$ Current parent position: " + this.position + ", angles: " + this.angles);
		
		if (connectedVehicle == null || !connectedVehicle.isValid) {
			this.currentNozzlePos = definition.nozzlePos;
			this.currentNozzleAngles = this.angles;
		}
		else {
			this.currentNozzlePos = connectedVehicle.currentFuelPoint.pos.copy().subtract(this.position);
			this.currentNozzleAngles = connectedVehicle.angles.copy().rotateFine(definition.attachRot);
		}
		
		Point3d hoseEndPoint = this.currentNozzlePos.copy().add(nozzleHoseOffset.copy().rotateFine(this.currentNozzleAngles));
		Point3d hoseStartPoint = definition.hoseStart;
		//Center point will average the start and end points.
		this.hoseCenterPoint = hoseEndPoint.copy().add(hoseStartPoint).multiply(0.5);

		//On non-rigid hoses, factor in gravity droop.
		if (!definition.isRigid) {
			//More droop when not connected because there's no tension.
			this.hoseCenterPoint.y -= connectedVehicle == null ? 0.75 : 0.5;
		}

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
			MasterLoader.coreInterface.logError("$$ Attempt number: " + attemptNum);
			success = attemptHoseConnection(hoseStartPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(this.angles),
				hoseEndPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(this.currentNozzleAngles), definition.numSegments);
			if (success) {
				if (attemptNum == 0) {
					//Succeeded on first attempt, so segments are as short as possible.
					//No need to try again.
					MasterLoader.coreInterface.logError("$$ Succeeded, breaking loop");
					break;
				}
				else {
					//Binary search decrement.
					MasterLoader.coreInterface.logError("$$ BS decrement");
					this.hoseSegmentLength -= definition.maxExtend * Math.pow(2, -attemptNum);
				}
			}
			else {
				if (attemptNum == 1) {
					//Already fully extended. No way to connect.
					MasterLoader.coreInterface.logError("$$ Failed at full extension");
					break;
				}
				//Binary search increment.
				MasterLoader.coreInterface.logError("$$ BS increment");
				this.hoseSegmentLength += definition.maxExtend * Math.pow(2, -attemptNum);
			}
			++attemptNum;
		}

		MasterLoader.coreInterface.logError("$$ Hose generation result: " + success);

		if (!success) {
			//Couldn't connect, so break the connection.
			this.connectedVehicle = null;
		}
		else {
			MasterLoader.coreInterface.logError("$$ Number of segments to render: " + this.segmentList.size());
			this.render();
		}
	}	

	
	public void render(){
		MasterLoader.coreInterface.logError("$$ Rendering HoseObject");
		//Check that the nozzle object isn't null, in case this model doesn't use a nozzle.
		final boolean nozzleObjectPresent = definition.nozzleObjectName != null;
		
		//If we don't have the displaylist and texture cached, do it now.
		if(!hoseDisplayListMap.containsKey(definition) || !nozzleDisplayListMap.containsKey(definition)){
			Map<String, Float[][]> parsedModel = OBJParser.parseOBJModel(parentDefinition.getModelLocation());
			Float[][] nozzleModel = nozzleObjectPresent ? parsedModel.get(definition.nozzleObjectName) : null;
			Float[][] hoseModel = parsedModel.get(definition.hoseObjectName);
			
			//First, for the nozzle model.
			if (nozzleModel != null) {
				int displayListIndex = GL11.glGenLists(1);
				
				GL11.glNewList(displayListIndex, GL11.GL_COMPILE);
				GL11.glBegin(GL11.GL_TRIANGLES);
				for(Float[] vertex : nozzleModel){
					GL11.glTexCoord2f(vertex[3], vertex[4]);
					GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
					GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
				}
				GL11.glEnd();
				GL11.glEndList();
				nozzleDisplayListMap.put(definition, displayListIndex);
			}
			
			//Now for the hose model. We won't check this for not null, since a null hose object would defeat the whole point.
			int displayListIndex = GL11.glGenLists(1);
			
			GL11.glNewList(displayListIndex, GL11.GL_COMPILE);
			GL11.glBegin(GL11.GL_TRIANGLES);
			for(Float[] vertex : hoseModel){
				GL11.glTexCoord2f(vertex[3], vertex[4]);
				GL11.glNormal3f(vertex[5], vertex[6], vertex[7]);
				GL11.glVertex3f(vertex[0], vertex[1], vertex[2]);
			}
			GL11.glEnd();
			GL11.glEndList();
			hoseDisplayListMap.put(definition, displayListIndex);
		}
		
		//Now retrieve the models and render them.
		//Don't do solid model rendering on the blend pass.
		if(MasterLoader.renderInterface.getRenderPass() != 1){
			MasterLoader.coreInterface.logError("$$ In the solid rendering pass...");
			//Bind the texture. This will be the same for all hose segments and the nozzle.
			GL11.glPushMatrix();
			MasterLoader.renderInterface.bindTexture(parentDefinition.getTextureLocation(this.subName));
			
			//Move to the hose's parent location.
			GL11.glTranslated(position.x, position.y, position.z);
			
			//Render the hose segments.
			for (HoseSegment segment : this.segmentList) {
				MasterLoader.coreInterface.logError("$$ Segment position: " + segment.pos);
				GL11.glPushMatrix();
				GL11.glTranslated(segment.pos.x, segment.pos.y, segment.pos.z);
//				GL11.glRotated(segment.angles.y, 0, 1, 0);
//				GL11.glRotated(segment.angles.x, 1, 0, 0);
//				GL11.glRotated(segment.angles.z, 0, 0, 1);
				GL11.glCallList(hoseDisplayListMap.get(definition));
				GL11.glPopMatrix();
			}
			
			//Render the nozzle, if present.
			if (nozzleObjectPresent) {
				MasterLoader.coreInterface.logError("$$ Nozzle position: " + currentNozzlePos);
				GL11.glPushMatrix();
				GL11.glTranslated(currentNozzlePos.x, currentNozzlePos.y, currentNozzlePos.z);
//				GL11.glRotated(currentNozzleAngles.y, 0, 1, 0);
//				GL11.glRotated(currentNozzleAngles.x, 1, 0, 0);
//				GL11.glRotated(currentNozzleAngles.z, 0, 0, 1);
				GL11.glCallList(hoseDisplayListMap.get(definition));
				GL11.glPopMatrix();
			}
			
			GL11.glPopMatrix();
		}
	}
}
