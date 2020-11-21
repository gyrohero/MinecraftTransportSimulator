package minecrafttransportsimulator.items.components;

import java.util.List;

import minecrafttransportsimulator.jsondefs.AJSONMultiModelProvider;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;

public abstract class AItemSubTyped<JSONDefinition extends AJSONMultiModelProvider<?>> extends AItemPack<JSONDefinition>{
	public final String subName;
	
	public AItemSubTyped(JSONDefinition definition, String subName){
		super(definition);
		this.subName = subName;
	}
	
	@Override
	public String getRegistrationName(){
		return super.getRegistrationName() + subName;
	}
	
	@Override
	public String getItemName(){
		for(AJSONMultiModelProvider<?>.SubDefinition subDefinition : definition.definitions){
			if(subDefinition.subName.equals(subName)){
				return subDefinition.name;
			}
		}
		return "";
	}
	
	@Override
	public void addTooltipLines(List<String> tooltipLines, IWrapperNBT data){
		super.addTooltipLines(tooltipLines, data);
		for(AJSONMultiModelProvider<?>.SubDefinition subDefinition : definition.definitions){
			if(subDefinition.subName.equals(subName)){
				for(String tooltipLine : subDefinition.description.split("\n")){
					tooltipLines.add(tooltipLine);
				}
			}
		}
	}
	
	public List<String> getExtraMaterials(){
		for(AJSONMultiModelProvider<?>.SubDefinition subDefinition : definition.definitions){
			if(subDefinition.subName.equals(subName)){
				return subDefinition.extraMaterials;
			}
		}
		return null;
	}
}
