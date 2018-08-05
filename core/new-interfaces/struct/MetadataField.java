package nl.inl.blacklab.interfaces.struct;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.ValueListComplete;

/** A metadata field. */
public interface MetadataField extends Field {
	
    String uiType();

	FieldType type();

	List<String> displayOrder();

	String analyzerName();

	String unknownValue();

	UnknownCondition unknownCondition();

	Map<String, Integer> valueDistribution();

	ValueListComplete isValueListComplete();

    Map<String, String> displayValues();

	String group();

}