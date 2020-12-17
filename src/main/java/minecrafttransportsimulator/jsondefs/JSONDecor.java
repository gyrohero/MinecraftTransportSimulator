package minecrafttransportsimulator.jsondefs;

import java.util.List;

import minecrafttransportsimulator.baseclasses.Point3d;

public class JSONDecor extends AJSONMultiModelProvider<JSONDecor.DecorGeneral>{
	public FuelSupplier fuelSupplier;

    public class DecorGeneral extends AJSONMultiModelProvider<JSONDecor.DecorGeneral>.General{
    	public String type;
    	public float width;
    	public float height;
    	public float depth;
    	public TextLine[] textLines;
    	public List<JSONText> textObjects;
    	public List<String> itemTypes;
    	public List<String> partTypes;
    	public List<String> items;
    }
    
    @Deprecated
    public class TextLine{
    	public float xPos;
    	public float yPos;
    	public float zPos;
    	public float scale;
    	public String color;
    }
    
    public class FuelSupplier{
    	public String hoseObjectName;
    	public int numSegments;
    	public Point3d hoseStart;
    	public Point3d hoseEnd;
    	
    	public String nozzleObjectName;
    	public Point3d nozzlePos;
    	public Point3d nozzleHosePos;
    	public Point3d attachRot;
    	
    	public float maxExtend;
    	public boolean isRigid;
    	public float maxBend;
    }
}