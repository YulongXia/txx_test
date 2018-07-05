package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import ai.hual.labrador.nlu.SlotValue;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MergedEntityUpdateStrategy implements SlotUpdateStrategy {

    private Set<String> classes;

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        String query = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:label ?class_label . }";
        classes = Sets.newHashSet(accessorRepository.getKnowledgeAccessor().selectOneAsList(query, "class_label"));
        String conditionEntityQuery = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/standard#Conditionclass>; rdfs:label ?class_label . }";
        classes.removeAll(accessorRepository.getKnowledgeAccessor().selectOneAsList(conditionEntityQuery, "class_label"));
        String bnQuery = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/standard#BNclass>; rdfs:label ?class_label . }";
        classes.removeAll(accessorRepository.getKnowledgeAccessor().selectOneAsList(bnQuery, "class_label"));
    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        List<QueryAct> hyps = (List<QueryAct>) context.getSlots().get("sys.hyps");
        return hyps.stream()
                .map(QueryAct::getSlots)
                .flatMap(slots -> slots.entries().stream()
                        .filter(entry -> classes.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .map(SlotValue::getMatched))
                .distinct()
                .collect(Collectors.toList());
    }
}
