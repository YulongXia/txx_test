package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import ai.hual.labrador.nlu.SlotValue;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConditionEntityUpdateStrategy implements SlotUpdateStrategy {

    private Set<String> classes;

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        //String query = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/standard#Conditionclass>; rdfs:label ?class_label . }";
        String query = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/taikang/taikang_rs#ConditionClass>; rdfs:label ?class_label . }";
        classes = Sets.newHashSet(accessorRepository.getKnowledgeAccessor().selectOneAsList(query, "class_label"));
    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        ListMultimap<String, SlotValue> slots = queryAct.getSlots();
        return slots.entries().stream()
                .filter(entry -> classes.contains(entry.getKey()))
                .findFirst()
                .map(entry -> entry.getValue().getMatched())
                .orElse(Optional.ofNullable(context.getSlots().get("contextConditionEntity")).orElse(null));
                //.orElse(Optional.ofNullable((String) context.getSlots().get("contextConditionEntity")).orElse(null));
    }
}
