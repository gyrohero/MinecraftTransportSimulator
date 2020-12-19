package minecrafttransportsimulator.rendering.components;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityFuelPump;
import minecrafttransportsimulator.jsondefs.AJSONMultiModelProvider;
import minecrafttransportsimulator.jsondefs.JSONDecor.FuelSupplier;
import minecrafttransportsimulator.mcinterface.MasterLoader;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;
import minecrafttransportsimulator.vehicles.parts.PartInteractable;

public final class HoseObject {
	private final Map<FuelSupplier, Integer> hoseDisplayListMap = new HashMap<FuelSupplier, Integer>();
	private final Map<FuelSupplier, Integer> nozzleDisplayListMap = new HashMap<FuelSupplier, Integer>();
	
	private final String subName;
	private final Point3d nozzleHoseOffset;
	private final Point3d hoseSegmentCenter;
	private final Point3d defaultSegmentRot;
	private final double defaultSegmentLength;

	private AJSONMultiModelProvider<?> parentDefinition;
	private FuelSupplier definition;
	private int segmentsLeftover;
	private boolean startCanBend;
	private boolean endCanBend;
	private Point3d hoseCenterPoint;
	private double hoseSegmentLength;
	private TileEntityFuelPump parentPump;
	private PartInteractable parentBarrel;
	
	public Point3d position;
	public Point3d angles;
	
	//To be rendered
	private final LinkedList<HoseSegment> segmentList = new LinkedList<HoseSegment>();
	private Point3d currentNozzlePos;
	private Point3d currentNozzleAngles;
	
	
	//Constructor for pump-based hoses.
	public HoseObject(TileEntityFuelPump pump, String currentSubName) {
		this(currentSubName, pump.definition.fuelSupplier);
		this.parentPump = pump;
		this.parentDefinition = pump.definition;
    	this.position = new Point3d(pump.position);
    	this.angles = new Point3d(0, pump.rotation, 0);
		
		this.generate();
	}

	//Constructor for interactable-based hoses.
	public HoseObject(PartInteractable barrel, String currentSubName) {
		this(currentSubName, barrel.definition.interactable.fuelSupplier);
		this.parentBarrel = barrel;
		this.parentDefinition = barrel.definition;
		
		this.generate();
	}

	//Generic constructor
	private HoseObject(String currentSubName, FuelSupplier definition) {
		System.out.println("$$ Creating HoseObject");
		this.subName = currentSubName;
		this.definition = definition;
		
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
	
	//Returns the currently connected vehicle, if valid.
	private EntityVehicleF_Physics getConnectedVehicle() {
		if (this.parentPump != null) {
			return parentPump.connectedVehicle;
		}
		else if (this.parentBarrel != null) {
			//return parentBarrel.connectedVehicle;
		}
		return null;
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
	private boolean attemptHoseConnection(Point3d startPoint, Point3d startAngles, Point3d endPoint, Point3d endAngles, int numSegments) {
		System.out.println("$$ Attempting hose connection. Segments left: " + numSegments);
		final float errorAllowed = 0.1f;
		//Recursively add segments, attempting to meet at the center point.
		if (numSegments == 1) {
			System.out.println("$$ 1 segment left. Distances are: " + 
					(startPoint.distanceTo(this.hoseCenterPoint)) + " and " +
					(startPoint.distanceTo(this.hoseCenterPoint)));
			if (startPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed && endPoint.distanceTo(this.hoseCenterPoint) <= errorAllowed) {
				//Average the start and end rotations and add this to the list
				System.out.println("$$ Clearing out the segment list");
				segmentList.clear();
				System.out.println("$$ Adding a segment at position: " + this.hoseCenterPoint);
				segmentList.add(new HoseSegment(this.hoseCenterPoint, startAngles.copy().add(endAngles)));
				return true;
			}
			else {
				System.out.println("$$ Failed with 1 segment left");
				return false;
			}
		}
		else {
			//2 or more segments left.
			//Determine which side to work from by which is further from the center point.
			boolean workFromEnd = startPoint.distanceTo(this.hoseCenterPoint) < endPoint.distanceTo(this.hoseCenterPoint);
			Point3d workingPoint = workFromEnd ? endPoint : startPoint;
			Point3d workingAngles = workFromEnd ? endAngles : startAngles;

			if (workingPoint.distanceTo(this.hoseCenterPoint) > hoseSegmentLength + errorAllowed) {
				//Not at center yet. Keep building segments.
				//Get new rotation to turn toward the center point.
				Point3d pointToCenter = this.hoseCenterPoint.copy().subtract(workingPoint);
				Point3d deltaAngles = pointToCenter.anglesFrom(this.defaultSegmentRot).subtract(workingAngles);

				//Clamp all angles to have an absolute value <= max bend angle.
				Point3d newAngles = new Point3d(clampAngle((float)deltaAngles.x),
					clampAngle((float)deltaAngles.y),
					clampAngle((float)deltaAngles.z)).add(workingAngles);

				//Note that newRot should be of length hoseSegmentLength
				Point3d newCenter = workingPoint.copy().add(this.defaultSegmentRot.copy().multiply(0.5).rotateFine(newAngles));

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

				System.out.println("$$ Current segment centered at: " + newCenter);
				//Recurse, using the new segment start and end points and rotation vectors.
				boolean success = workFromEnd ? 
					attemptHoseConnection(startPoint, startAngles, newCenter.copy().add(this.defaultSegmentRot.copy().multiply(0.5).rotateFine(newAngles)), newAngles, numSegments-1) : 
					attemptHoseConnection(newCenter.copy().add(this.defaultSegmentRot.copy().multiply(0.5).rotateFine(newAngles)), newAngles, endPoint, endAngles, numSegments-1);
				
				if (success) {
					//Add the center points and angles to the segment list
					if (!workFromEnd) {
						System.out.println("$$ Adding a start segment at position: " + newCenter);
						segmentList.addFirst(new HoseSegment(newCenter, newAngles));
					}
					else {
						System.out.println("$$ Adding an end segment at position: " + newCenter);
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
		System.out.println("$$ Generating Hose");
		// No valid vehicle, keep the nozzle in its stowed position
		
		System.out.println("$$ Current parent position: " + this.position + ", angles: " + this.angles);
		
		EntityVehicleF_Physics connectedVehicle = this.getConnectedVehicle();
		if (connectedVehicle == null || !connectedVehicle.isValid) {
			this.currentNozzlePos = definition.nozzlePos;
			this.currentNozzleAngles = new Point3d(0, 0, 0);//this.angles;
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
			System.out.println("$$ Attempt number: " + attemptNum);
			success = attemptHoseConnection(hoseStartPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(this.angles),
				hoseEndPoint, this.defaultSegmentRot.copy().normalize().multiply(this.hoseSegmentLength).rotateFine(this.currentNozzleAngles), definition.numSegments);
			if (success) {
				if (attemptNum == 0) {
					//Succeeded on first attempt, so segments are as short as possible.
					//No need to try again.
					System.out.println("$$ Succeeded, breaking loop");
					break;
				}
				else {
					//Binary search decrement.
					System.out.println("$$ BS decrement");
					this.hoseSegmentLength -= definition.maxExtend * Math.pow(2, -attemptNum);
				}
			}
			else {
				if (attemptNum == 1) {
					//Already fully extended. No way to connect.
					System.out.println("$$ Failed at full extension");
					break;
				}
				//Binary search increment.
				System.out.println("$$ BS increment");
				this.hoseSegmentLength += definition.maxExtend * Math.pow(2, -attemptNum);
			}
			++attemptNum;
		}

		System.out.println("$$ Hose generation result: " + success);

		if (!success) {
			//Couldn't connect, so break the connection.
			//TODO: Send connection breaking packed here
		}
		else {
			System.out.println("$$ Number of segments to render: " + this.segmentList.size());
			//this.render();
		}
	}	

	
	public void render(float partialTicks){
		//System.out.println("$$ Rendering HoseObject");
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
			//System.out.println("$$ In the solid rendering pass...");
			GL11.glPushMatrix();
			
			//Bind the texture. This will be the same for all hose segments and the nozzle.
			MasterLoader.renderInterface.bindTexture(parentDefinition.getTextureLocation(this.subName));
			
			//Render the hose segments.
			for (HoseSegment segment : this.segmentList) {
				//System.out.println("$$ Segment position: " + segment.pos);
				GL11.glPushMatrix();
				GL11.glRotated(segment.angles.y, 0, 1, 0);
				GL11.glRotated(segment.angles.x, 1, 0, 0);
				GL11.glRotated(segment.angles.z, 0, 0, 1);
				GL11.glTranslated(segment.pos.x, segment.pos.y, segment.pos.z);
				GL11.glCallList(hoseDisplayListMap.get(definition));
				GL11.glPopMatrix();
			}
			
			//Render the nozzle, if present.
			if (nozzleObjectPresent) {
				//System.out.println("$$ Nozzle position: " + currentNozzlePos);
				GL11.glPushMatrix();
				GL11.glRotated(currentNozzleAngles.y, 0, 1, 0);
				GL11.glRotated(currentNozzleAngles.x, 1, 0, 0);
				GL11.glRotated(currentNozzleAngles.z, 0, 0, 1);
				GL11.glTranslated(currentNozzlePos.x, currentNozzlePos.y, currentNozzlePos.z);
				GL11.glCallList(nozzleDisplayListMap.get(definition));
				GL11.glPopMatrix();
			}
			
			GL11.glPopMatrix();
		}
	}
}
