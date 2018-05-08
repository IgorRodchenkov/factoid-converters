/*
 * A model class that keeps an underlying PaxTools model and enables updating it by wrapper functions.
 * This model is designed to avoid duplications of BioPAX elements in certain conditions.
 */

package model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Control;
import org.biopax.paxtools.model.level3.ControlType;
import org.biopax.paxtools.model.level3.ControlledVocabulary;
import org.biopax.paxtools.model.level3.Controller;
import org.biopax.paxtools.model.level3.Conversion;
import org.biopax.paxtools.model.level3.ConversionDirectionType;
import org.biopax.paxtools.model.level3.EntityFeature;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.ModificationFeature;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.model.level3.SequenceModificationVocabulary;
import org.biopax.paxtools.model.level3.SimplePhysicalEntity;

public class BioPAXModel {
	
	// Underlying paxtools model
	private Model model;
	// Map of term to cellular location
	private Map<String, CellularLocationVocabulary> cellularLocationMap;
	// Multiple key map of entity reference class and name to entity reference itself
	private MultiKeyMap<Object, EntityReference> entityReferenceMap;
	
	// Section: constructors
	
	public BioPAXModel() {
		BioPAXFactory factory = BioPAXLevel.L3.getDefaultFactory();
		model = factory.createModel();
		
		cellularLocationMap = new HashMap<String, CellularLocationVocabulary>();
		entityReferenceMap = new MultiKeyMap<Object, EntityReference>();
	}
	
	// Section: public methods
	
	// add a new element to model with given id
	public <T extends BioPAXElement> T addNew(Class<T> c, String id) {
		return model.addNew(c, id);
	}
	
	// add a new element to model by generating element id
	public <T extends BioPAXElement> T addNew(Class<T> c) {
		return addNew(c, generateUUID());
	}
	
	// Just get a physical entity, create it if not available yet.
	// Do not create duplicate entities if entity references, cellular locations and modifications set matches.
	public <T extends PhysicalEntity> T getOrCreatePhysicalEntity(Class<T> c, String name, CellularLocationVocabulary cellularLocation, EntityReference entityRef, Set<String> modificationTypes) {
		
		T entity = null;
		
		if (entityRef != null) {
			assertSimplePhysicalEntityOrSubclass(c);
			
			Set<T> entities = (Set<T>) entityRef.getEntityReferenceOf();
			entity = findMatchingEntity(entities, cellularLocation, modificationTypes);
		}
		
		if (entity == null) {
			entity = addNewPhysicalEntity(c, name, cellularLocation, entityRef, modificationTypes);
		}
		
		return entity;
	}
	
	public <T extends PhysicalEntity> T getOrCreatePhysicalEntity(Class<T> c, String name, CellularLocationVocabulary cellularLocation, EntityReference entityRef) {
		return getOrCreatePhysicalEntity(c, name, cellularLocation, entityRef, null);
	}
	
	public <T extends PhysicalEntity> T getOrCreatePhysicalEntity(Class<T> c, String name) {
		return getOrCreatePhysicalEntity(c, name, null, null);
	}
	
	public <T extends PhysicalEntity> T getOrCreatePhysicalEntity(Class<T> c) {
		return getOrCreatePhysicalEntity(c, null);
	}
	
	// Get cellular location mathcing the given term, create one if not available
	public CellularLocationVocabulary getOrCreateCellularLocationVocabulary(String term) {
		
		CellularLocationVocabulary clv = cellularLocationMap.get(term);
		
		// if a clv does not exists for the term create one here and put it to the map
		if(clv == null) {
			clv = addNewControlledVocabulary(CellularLocationVocabulary.class, term);
			cellularLocationMap.put(term, clv);
		}
		
		return clv;
	}
	
	// Get modification feature that has the given modification type. Create one if not available.
	public ModificationFeature getOrCreateModificationFeature(String modificationType, EntityReference entityRef) {
		
		Set<EntityFeature> referenceModifications = entityRef.getEntityFeature();
		ModificationFeature modificationFeature = getFeatureByModificationType((Set)referenceModifications, modificationType);
		
		// if a modification feature does not exists for the modification type create one here and put it to the map
		if (modificationFeature == null) {
			modificationFeature = addNewModificationFeature(modificationType);
			entityRef.addEntityFeature(modificationFeature);
		}
		
		return modificationFeature;
	}
	
	// Get entity reference that has given name and class, create a new one is not available yet.
	public <T extends EntityReference> T getOrCreateEntityReference(Class<T> c, String name) {
		
		T entityRef = (T) entityReferenceMap.get(c, name);
		
		if (entityRef == null) {
			entityRef = addNewEntityReference(c, name);
			entityReferenceMap.put(c, name, entityRef);
		}
		
		return entityRef;
	}
	
	// Create a new conversion by given properties
	public <T extends Conversion> T addNewConversion(Class<T> c, PhysicalEntity left, PhysicalEntity right, ConversionDirectionType dir) {
		
		T conversion = addNew(c);
		
		if(left != null) {
			conversion.addLeft(left);
		}
		
		if(right != null) {
			conversion.addRight(right);
		}
		
		if(dir != null) {
			conversion.setConversionDirection(dir);
		}
		
		return conversion;
	}
	
	public <T extends Conversion> T addNewConversion(Class<T> c) {
		return addNewConversion(c, null, null);
	}
	
	public <T extends Conversion> T addNewConversion(Class<T> c, PhysicalEntity left, PhysicalEntity right) {
		return addNewConversion(c, left, right, ConversionDirectionType.LEFT_TO_RIGHT);
	}
	
	// Create a new control instance by given properties
	public <T extends Control> T addNewControl(Class<T> c, Controller controller, Process controlled, ControlType controlType) {
		
		T control = addNew(c);
		
		if(controller != null) {
			control.addController(controller);
		}
		
		if(controlled != null) {
			control.addControlled(controlled);
		}
		
		if(controlType != null) {
			control.setControlType(controlType);
		}
		
		return control;
	}
	
	public String convertToOwl() {
		return SimpleIOHandler.convertToOwl(model);
	}
	
	// Section: private helper methods
	
	// Generate unique id for new elements
	private static String generateUUID() {
		return UUID.randomUUID().toString();
	}
	
	// Find the physical entity that has the expected cellular location and modification types
	private static <T extends PhysicalEntity> T findMatchingEntity(Set<T> entities, CellularLocationVocabulary cellularLocation, Set<String> modificationTypes){		
		
		Optional<T> match = entities.stream().filter(t -> {
			CellularLocationVocabulary clv = t.getCellularLocation();
			return clv != null && clv.equals(cellularLocation) 
					&& isAbstractionOf(getModificationFeatureOfEntity(t), modificationTypes);
		} ).findFirst();
		
		if (match.isPresent()) {
			return match.get();
		}
		
		return null;
	}
	
	private static Set<ModificationFeature> getModificationFeatureOfEntity(PhysicalEntity entity){
		
		Set<EntityFeature> entityFeatures = entity.getFeature();
		
		// Assert that any entity feature is a ModificationFeature since other kind of entity features are 
		// not supposed to be created
		assert allAreInstanceOfModificationFeature(entityFeatures) : 
			"All members of feature set is expected to have modification feature type";
		
		// Do not filter modification features by relying on the assumption that all features are 
		// modification feature for better performance. If that is not the case the assertion above is
		// suppose to fail.
		return (Set) entityFeatures;
	}
	
	private static <T extends PhysicalEntity> void assertSimplePhysicalEntityOrSubclass(Class<T> c, String messageOpt) {
		
		String message = null;
		
		if (messageOpt == null) {
			message = "Entity reference field is valid only for instances of SimplePhysicalEntity and its subclasses";
		}
		else {
			message = messageOpt;
		}
		
		assert SimplePhysicalEntity.class.isAssignableFrom(c) : message;
	}
	
	private static <T extends PhysicalEntity> void assertSimplePhysicalEntityOrSubclass(Class<T> c) {
		assertSimplePhysicalEntityOrSubclass(c, null);
	}
	
	// TODO try converting this to allAreInstanceOf(Class? c, Collection collection)
	private static <T extends EntityFeature> boolean allAreInstanceOfModificationFeature(Collection<T> collection) {
		Iterator<T> it = collection.iterator();
		while(it.hasNext()) {
			Object o = it.next();
			if (!(o instanceof ModificationFeature)) {
				return false;
			}
		}
		return true;
	}
	
	// Check if modificationTypes set is an abstraction of modifications set
	private static boolean isAbstractionOf(Set<ModificationFeature> modifications, Set<String> modificationTypes) {
		
		// return false if only one side is null 
		if ( (modifications == null) == !(modificationTypes == null)  ) {
			return false;
		}
		
		// return true if both sides are null
		// note that we made sure that either both or none is null
		if (modifications == null) {
			return true;
		}
		
		if (modifications.isEmpty() && modificationTypes.isEmpty()) {
			return true;
		}
		
		if (modifications.size() != modificationTypes.size()) {
			return false;
		}
		
		return modifications.stream().map(t -> getOnlyElement(t.getModificationType().getTerm())).collect(Collectors.toSet()).equals(modificationTypes);
	}
	
	// get only element of collection
	// TODO this method would be moved to a utility file
	private static <T extends Object> T getOnlyElement(Collection<T> collection) {
		return collection.iterator().next();
	}
	
	// Create a new physical entity with given properties
	private <T extends PhysicalEntity> T addNewPhysicalEntity(Class<T> c, String name, CellularLocationVocabulary cellularLocation, 
			EntityReference entityRef, Set<String> modificationTypes) {
		
		T entity = addNew(c);
		
		if (name != null) {
			entity.setDisplayName(name);
		}
		
		if (entityRef != null) {
			assertSimplePhysicalEntityOrSubclass(c);
			
			((SimplePhysicalEntity) entity).setEntityReference(entityRef);
		}
		
		if (cellularLocation != null) {
			entity.setCellularLocation(cellularLocation);
		}
		
		if (modificationTypes != null) {
			for(String modificationType : modificationTypes) {
				ModificationFeature modificationFeature = getOrCreateModificationFeature(modificationType, entityRef);
				entity.addFeature(modificationFeature);
			}
		}
		
		return entity;
	}
	
	// create a new controlled vocabulary initialized with the given term
	private <T extends ControlledVocabulary> T addNewControlledVocabulary(Class<T> c, String term) {
		T vocab = addNew(c);
		
		if (term != null) {
			vocab.addTerm(term);
		}
		
		return vocab;
	}
	
	// Create a new modification feature that has the given modification type
	private ModificationFeature addNewModificationFeature(String modificationType) {
		SequenceModificationVocabulary seqModVocab = addNewControlledVocabulary(SequenceModificationVocabulary.class, modificationType);
		
		ModificationFeature modificationFeature = addNew(ModificationFeature.class);
		modificationFeature.setModificationType(seqModVocab);
		
		return modificationFeature;
	}
	
	// Get modification feature with the given modification type from a set of modification features
	private ModificationFeature getFeatureByModificationType(Set<ModificationFeature> modificationFeatures,
			String modificationType) {
		
		Iterator<ModificationFeature> it = modificationFeatures.iterator();
		
		while (it.hasNext()) {
			ModificationFeature modificationFeature = it.next();
			Set<String> terms = modificationFeature.getModificationType().getTerm();
			if ( getOnlyElement(terms).equals(modificationType) ) {
				return modificationFeature;
			}
		}
		
		return null;
	}
	
	// Create a new entity reference by given properties
	private <T extends EntityReference> T addNewEntityReference(Class<T> c, String name) {
		
		T entityRef = addNew(c);
		
		if(name != null) {
			entityRef.setDisplayName(name);
		}
		
		return entityRef;
	}
}