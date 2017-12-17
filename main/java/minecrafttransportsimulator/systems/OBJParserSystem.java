package minecrafttransportsimulator.systems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import minecrafttransportsimulator.MTS;

import org.lwjgl.util.vector.Vector3f;

/**Class responsible for parsing OBJ models into arrays that can be fed to the GPU.
 * Much more versatile than the Forge system.
 * 
 * @author don_bruce
 */
public final class OBJParserSystem{
	public static Map<String, Float[][]> parseOBJModel(String modelName){
		try{
			Map<String, Float[][]> partMap = new HashMap<String, Float[][]>();
			BufferedReader reader = new BufferedReader(new FileReader(MTS.assetDir + File.separatorChar + "objmodels" + File.separatorChar + modelName));
			
			String partName = null;
			final List<Float[]> vertexList = new ArrayList<Float[]>();
			final List<Float[]> textureList = new ArrayList<Float[]>();
			final List<String> faceList = new ArrayList<String>();
			while(reader.ready()){
				String line = reader.readLine();
				if(line.startsWith("o")){
					//Declaration of an object.
					//Save current part we are parsing (if any) and start new part.
					if(partName != null){
						partMap.put(partName, compileVertexArray(vertexList, textureList, faceList));
						vertexList.clear();
						textureList.clear();
						faceList.clear();
					}
					partName = line.trim().substring(2, line.length());
				}
				if(partName != null){
					if(line.startsWith("v ")){
						Float[] coords = new Float[3];
						line = line.trim().substring(2, line.length());
						coords[0] = Float.valueOf(line.substring(0, line.indexOf(' ')));
						coords[1] = Float.valueOf(line.substring(line.indexOf(' ') + 1, line.lastIndexOf(' ')));
						coords[2] = Float.valueOf(line.substring(line.lastIndexOf(' ') + 1, line.length()));
						vertexList.add(coords);
					}else if(line.startsWith("vt ")){
						Float[] coords = new Float[2];
						line = line.trim().substring(3, line.length());
						int space = line.indexOf(' ');
						int vertexEnd = line.lastIndexOf(' ') == space ? line.length() : line.lastIndexOf(' ');
						coords[0] = Float.valueOf(line.substring(0, space));
						coords[1] = Float.valueOf(line.substring(space + 1, vertexEnd));
						textureList.add(coords);
					}else if(line.startsWith("f ")){
						faceList.add(line.trim().substring(2, line.trim().length()));
					}
				}
			}
			//End of file.  Save the last part in process and close the file.
			partMap.put(partName, compileVertexArray(vertexList, textureList, faceList));
			reader.close();
			return partMap;
		}catch (IOException e){
			e.printStackTrace();
			return null;
		}
	}
	
	private static Float[][] compileVertexArray(List<Float[]> vertexList, List<Float[]> textureList, List<String> faceList){
		List<Integer[]> faceValues = new ArrayList<Integer[]>();
		for(String faceString : faceList){
			for(byte i=0; i<3; ++i){
				int defEnd = faceString.indexOf(' ');
				String faceDef;
				if(defEnd != -1){
					faceDef = faceString.substring(0, defEnd);
					faceString = faceString.substring(defEnd + 1);
				}else{
					faceDef = faceString;
					faceString = "";
				}
				int slash = faceDef.indexOf('/');
				int faceEnd = faceDef.lastIndexOf('/') == slash ? faceDef.length() : faceDef.lastIndexOf('/');
				int vertexNumber = Integer.valueOf(faceDef.substring(0, slash)) - 1;
				//Make sure texture is defined for this shape before trying to load it in.
				if(faceDef.substring(slash + 1, faceEnd).equals("")){
					faceString = "";
					break;
				}else{
					int textureNumber = Integer.valueOf(faceDef.substring(slash + 1, faceEnd)) - 1;
					faceValues.add(new Integer[]{vertexNumber, textureNumber});
				}
			}
			
			if(!faceString.isEmpty()){
				//This only happens when there's quads in an obj.  Make a second face.
				//Duplicate point 3, add point 4, and duplicate point 1.
				int defEnd = faceString.indexOf(' ');
				if(defEnd != -1){
					faceString = faceString.substring(0, defEnd);
				}
				faceValues.add(faceValues.get(faceValues.size() - 1));
				int slash = faceString.indexOf('/');
				int faceEnd = faceString.lastIndexOf('/') == slash ? faceString.length() : faceString.lastIndexOf('/');
				int vertexNumber = Integer.valueOf(faceString.substring(0, slash)) - 1;
				int textureNumber = Integer.valueOf(faceString.substring(slash + 1, faceEnd)) - 1;
				faceValues.add(new Integer[]{vertexNumber, textureNumber});
				faceValues.add(faceValues.get(faceValues.size() - 5));
			}
		}
		
		//Get the correct offset for face values in the lists.
		//Find the smallest face number and use that as the offset.
		int vertexOffset = Integer.MAX_VALUE;
		int textureOffset = Integer.MAX_VALUE;
		for(Integer[] face : faceValues){
			vertexOffset = Math.min(vertexOffset, face[0]);
			textureOffset = Math.min(textureOffset, face[1]);
		}
		
		//Now populate vertex and texture arrays.
		List<Float[]> vertexArray = new ArrayList<Float[]>();
		List<Float[]> textureArray = new ArrayList<Float[]>();
		for(Integer[] face : faceValues){
			vertexArray.add(vertexList.get(face[0] - vertexOffset));
			textureArray.add(textureList.get(face[1] - textureOffset));
		}
		
		//Finally, create a normal array from the vertex array.
		List<Float[]> normalArray = new ArrayList<Float[]>();
		for(int i=0; i<=faceValues.size() - 3; i += 3){
			Float[] faceVertex1 = vertexArray.get(i);
			Float[] faceVertex2 = vertexArray.get(i + 1);
			Float[] faceVertex3 = vertexArray.get(i + 2);
			Vector3f v1 = new Vector3f(faceVertex1[0], faceVertex1[1], faceVertex1[2]);
			Vector3f v2 = new Vector3f(faceVertex2[0], faceVertex2[1], faceVertex2[2]);
			Vector3f v3 = new Vector3f(faceVertex3[0], faceVertex3[1], faceVertex3[2]);
			Vector3f norm = Vector3f.cross(Vector3f.sub(v2, v1, null), Vector3f.sub(v3, v1, null), null).normalise(null);
			
			//Add once for each vertex that was parsed.
			normalArray.add(new Float[]{norm.x, norm.y, norm.z});
			normalArray.add(new Float[]{norm.x, norm.y, norm.z});
			normalArray.add(new Float[]{norm.x, norm.y, norm.z});
		}
		
		//Compile arrays and return.
		List<Float[]> compiledArray = new ArrayList<Float[]>();
		for(int i=0; i<vertexArray.size(); ++i){
			compiledArray.add(new Float[]{
				vertexArray.get(i)[0],
				vertexArray.get(i)[1],
				vertexArray.get(i)[2],
				textureArray.get(i)[0],
				textureArray.get(i)[1],
				normalArray.get(i)[0],
				normalArray.get(i)[1],
				normalArray.get(i)[2]
			});
		}
		
		return compiledArray.toArray(new Float[compiledArray.size()][8]);
	}
}
