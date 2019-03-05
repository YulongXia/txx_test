package pojo;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class EntitiesAndTheirProperties {

    private ListMultimap<String, String> entitiesAndTheirDatatypes = ArrayListMultimap.create();
    private ListMultimap<String, ObjectProperty> entitiesAndTheirObjects = ArrayListMultimap.create();
    private ListMultimap<String, String> entitiesAndTheirHualObjects = ArrayListMultimap.create();

    /**
     * A list multimap with entity label as key and datatype labels as value
     */
    public ListMultimap<String, String> getEntitiesAndTheirDatatypes() {
        return entitiesAndTheirDatatypes;
    }

    /**
     * A list multimap with entity label as key and objects as value
     */
    public ListMultimap<String, ObjectProperty> getEntitiesAndTheirObjects() {
        return entitiesAndTheirObjects;
    }

    /**
     * A list multimap with entity label as key and HualObject iris as value
     */
    public ListMultimap<String, String> getEntitiesAndTheirHualObjects() {
        return entitiesAndTheirHualObjects;
    }

}
