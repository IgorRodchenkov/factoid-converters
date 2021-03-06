package factoid.converter;

import java.io.Reader;

/*
 * A converter class that gets a JSON object that includes sequence of BioPAX templates and enables
 * conversion to BioPAX by adding these templates to underlying Templates Model instance.
 */
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.biopax.paxtools.model.level3.ControlType;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.SmallMolecule;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import factoid.model.*;

public class FactoidToBiopax {

  private static final Map<String, ControlType> CONTROL_TYPE_MAP = createControlTypeMap();
	
	private TemplatesModel model;
	private JsonParser jsonParser;
	private Gson gson;
	
	public FactoidToBiopax() {
		model = new TemplatesModel();
		jsonParser = new JsonParser();
		gson = new Gson();
	}
	
	public void addToModel(String templatesContent) {
		JsonArray templates = jsonParser.parse(templatesContent).getAsJsonArray();
		addToModel(templates);
	}
	
	public void addToModel(Reader contentReader) {
		JsonArray templates = jsonParser.parse(contentReader).getAsJsonArray();
		addToModel(templates);
	}

  /**
   * Processes the input JSON templates,
   * creates BioPAX objects and adds to the BioPAX model.
   * @param templates
   */
	public void addToModel(JsonArray templates) {
		
		Iterator<JsonElement> it = templates.iterator();
		
		while (it.hasNext()) {
			JsonObject template = (JsonObject) it.next();
			String typeStr = template.get("type").getAsString();
			
			if (matchesTemplateType(typeStr, TemplateType.PROTEIN_CONTROLS_STATE)) {
				JsonObject controllerJson = template.get("controller").getAsJsonObject();
				
				String controlTypeStr = null;
				if (template.has("controlType")) {
					controlTypeStr = template.get("controlType").getAsString();
				}
				
				String modification = null;
				if (template.has("modification")) {
					modification = template.get("modification").getAsString();
				}
				
				JsonObject targetJson = template.get("target").getAsJsonObject();
				
				addProteinControlsState(controllerJson, targetJson, modification, controlTypeStr);
			}
			else if (matchesTemplateType(typeStr, TemplateType.EXPRESSION_REGULATION)) {
				JsonObject controllerJson = template.get("controller").getAsJsonObject();
				JsonObject targetJson = template.get("target").getAsJsonObject();
				String controlTypeStr = template.get("controlType").getAsString();
				
				addExpressionRegulation(controllerJson, targetJson, controlTypeStr);
			}
			else if(matchesTemplateType(typeStr, TemplateType.MOLECULAR_INTERACTION)) {
				JsonArray participantsJSON = template.get("participants").getAsJsonArray();
				
				addMolecularInteraction(participantsJSON);
			}
			else if(matchesTemplateType(typeStr, TemplateType.OTHER_INTERACTION)) {
				JsonArray participantsJSON = template.get("participants").getAsJsonArray();
				String controlTypeStr = null;
				
				if (template.has("controlType")) {
					controlTypeStr = template.get("controlType").getAsString();
				}
				
				addOtherInteraction(participantsJSON, controlTypeStr);
			}
		}
	}
	
	public String convertToBiopax() {
		return model.convertToOwl();
	}
	
	private static String getTemplateName(TemplateType templateType) {
		return templateType.getName();
	}
	
	private static boolean matchesTemplateType(String typeStr, TemplateType templateType) {
		return typeStr.equalsIgnoreCase(getTemplateName(templateType));
	}
	
	private ControlType getControlType(String controlTypeStr) {
		return CONTROL_TYPE_MAP.get(controlTypeStr.toUpperCase());
	}
	
	private void addOtherInteraction(JsonArray participantsJSON, String controlTypeStr) {
		List<EntityModel> participantModels = gson.fromJson(participantsJSON, new TypeToken<List<EntityModel>>(){}.getType());
		if (controlTypeStr == null) {
			model.addInteraction(participantModels);
		}
		else {
			EntityModel srcModel = participantModels.get(0);
			EntityModel tgtModel = participantModels.get(1);
			Class<? extends PhysicalEntity> controllerClass = srcModel.getEntityClass();
			Class<? extends PhysicalEntity> targetClass = tgtModel.getEntityClass();
			ControlType controlType = getControlType(controlTypeStr);
			
			if (controllerClass == Protein.class && targetClass == Protein.class) {
				model.addControlSequence(srcModel, tgtModel, controlType);
			}
			else if(controllerClass == Protein.class && targetClass == SmallMolecule.class) {
				model.addControlsConsumptionOrProduction(srcModel, tgtModel, controlType);
			}
			else if(controllerClass == SmallMolecule.class && targetClass == Protein.class) {
				model.addModulation(srcModel, tgtModel, controlType);
			}
			else if(controllerClass == SmallMolecule.class && targetClass == SmallMolecule.class) {
				model.addConversion(srcModel, tgtModel, controlType);
			}
		}
	}

	private void addMolecularInteraction(JsonArray participantsJSON) {
		List<EntityModel> participantModels = gson.fromJson(participantsJSON, new TypeToken<List<EntityModel>>(){}.getType());
		model.addMolecularInteraction(participantModels);
	}
	
	private void addProteinControlsState(JsonObject controllerProteinJson, JsonObject targetProteinJson, String modification, String controlTypeStr) {
		ControlType controlType = getControlType(controlTypeStr);
		EntityModel controllerProteinModel = gson.fromJson(controllerProteinJson, EntityModel.class);
		EntityModel targetProteinModel = gson.fromJson(targetProteinJson, EntityModel.class);
		
		model.addControlsState(controllerProteinModel, targetProteinModel, modification, controlType);
	}
	
	private void addExpressionRegulation(JsonObject controllerJson, JsonObject targetJson, String controlTypeStr) {
		ControlType controlType = getControlType(controlTypeStr);
		EntityModel controllerModel = gson.fromJson(controllerJson, EntityModel.class);
		EntityModel targetModel = gson.fromJson(targetJson, EntityModel.class);
		
		model.addExpressionRegulation(controllerModel, targetModel, controlType);
	}
	
	private static Map<String, ControlType> createControlTypeMap() {
		Map<String, ControlType> map = new HashMap<String, ControlType>();
		map.put("ACTIVATION", ControlType.ACTIVATION);
		map.put("INHIBITION", ControlType.INHIBITION);
		
		return map;
	}
	
	private enum TemplateType {
		EXPRESSION_REGULATION("Expression Regulation"),
		MOLECULAR_INTERACTION("Molecular Interaction"),
		PROTEIN_CONTROLS_STATE("Protein Controls State"),
		OTHER_INTERACTION("Other Interaction");
			
		private String name;
		
		TemplateType(String name) {
			this.name = name;
		}
		
		public String getName() {
			return name;
		}
		
		public String toString() {
			return getName();
		}
	}

}
