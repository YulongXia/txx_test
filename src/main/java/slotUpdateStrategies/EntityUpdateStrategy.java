package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EntityUpdateStrategy implements SlotUpdateStrategy {

    private Set<String> classes;

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        String query = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:label ?class_label . }";
        classes = Sets.newHashSet(accessorRepository.getKnowledgeAccessor().selectOneAsList(query, "class_label"));
        String conditionEntityQuery = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/taikang/taikang_rs#ConditionClass>; rdfs:label ?class_label . }";
        classes.removeAll(accessorRepository.getKnowledgeAccessor().selectOneAsList(conditionEntityQuery, "class_label"));
        String bnQuery = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/standard#BNclass>; rdfs:label ?class_label . }";
        classes.removeAll(accessorRepository.getKnowledgeAccessor().selectOneAsList(bnQuery, "class_label"));
    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        List<?> lastWhichEntity = (List<?>) context.getSlots().get("lastWhichEntity");
        if (lastWhichEntity == null || lastWhichEntity.isEmpty()) {
            return findEntities(queryAct);
        }

        List<?> originalEntities = findOriginalEntitiesLastReferred(queryAct, lastWhichEntity);
        if (!originalEntities.isEmpty()) {
            return originalEntities;
        }

        return findEntities(queryAct);
    }

    private List<?> findEntities(QueryAct queryAct) {
        return queryAct.getSlots().entries().stream()
                .filter(slot -> classes.contains(slot.getKey()))
                .map(slot -> slot.getValue().getMatched())
                .distinct()
                .collect(Collectors.toList());
    }

    // find entities with original names which are referred to in the last turn with a whichEntity response.
    private List<?> findOriginalEntitiesLastReferred(QueryAct queryAct, List<?> lastWhichEntity) {
        return queryAct.getSlots().entries().stream()
                .filter(slot -> classes.contains(slot.getKey()))
                .map(slot -> queryAct.getQuery().substring(slot.getValue().getRealStart(), slot.getValue().getRealEnd()))
                .filter(lastWhichEntity::contains)
                .distinct()
                .collect(Collectors.toList());
    }
}
