package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import ai.hual.labrador.nlu.SlotValue;
import com.google.common.collect.*;

import java.util.*;
import java.util.stream.Collectors;

public class CpContextConditionEntitiesUpdateStrategy implements SlotUpdateStrategy {
    private Set<String> classes;
    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        String queryString = "SELECT DISTINCT ?vtLabel ?vLabel WHERE {\n" +
                "      ?s ?cp ?bn.\n" +
                "      ?cp rdf:type <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "      ?bn ?op ?value.\n" +
                "      ?op rdf:type/rdfs:subClassOf owl:ObjectProperty.\n" +
                "      ?op rdfs:label ?opLabel.\n" +
                "      ?value rdf:type/rdfs:label ?vtLabel.\n" +
                "      ?value rdfs:label ?vLabel.\n" +
                "}";
        classes = Sets.newHashSet(accessorRepository.getKnowledgeAccessor().selectOneAsList(queryString, "vtLabel"));

    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        ListMultimap<String, SlotValue> slots = queryAct.getSlots();
        Object a = slots.entries().stream()
                .filter(entry -> classes.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getValue().getMatched(),entry -> entry.getKey()));
        Map<String,String> result = (Map<String,String>) a;
        if(result.size() == 0)
            return o;
        if(o != null) {
            for (Map.Entry<String, String> entry : result.entrySet()) {
                ((Map<String,String>) o).put(entry.getKey(), entry.getValue());
            }
            return o;
        } else
            return result;
    }
}
