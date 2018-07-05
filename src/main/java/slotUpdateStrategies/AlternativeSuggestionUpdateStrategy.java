package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.stream.Stream;

public class AlternativeSuggestionUpdateStrategy implements SlotUpdateStrategy {

    private static final Set<String> OBJECT_PROPERTIES = Sets.newHashSet("YshapeProperty", "DiffusionProperty", "ConditionProperty", "HualObjectProperty");

    private static final Set<String> DATATYPE_PROPERTIES = Sets.newHashSet("datatype", "HualDatatypeProperty");

    private Set<String> classes;

    private ContextedString total;

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        String query = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:label ?class_label . }";
        classes = Sets.newHashSet(accessorRepository.getKnowledgeAccessor().selectOneAsList(query, "class_label"));
        String conditionEntityQuery = "SELECT DISTINCT ?class_label WHERE { ?class a owl:Class; rdfs:subClassOf* <http://hual.ai/standard#Conditionclass>; rdfs:label ?class_label . }";
        classes.removeAll(accessorRepository.getKnowledgeAccessor().selectOneAsList(conditionEntityQuery, "class_label"));
        total = Optional.ofNullable(map.get("total")).orElse(new ContextedString("3"));
    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        if (Stream.of("sys.yes", "sys.no").anyMatch(x -> x.equals(queryAct.getIntent()))) {
            return o;
        }

        List<QueryAct> hyps = (List<QueryAct>) context.getSlots().get("sys.hyps");
        int intTotal = Integer.parseInt(total.render(context));

        List<List<String>> alternatives = new ArrayList<>();
        int i = 1;
        while (alternatives.size() < intTotal && i < hyps.size()) {
            String entity = null;
            String object = null;
            String datatype = null;
            for (String key : hyps.get(i).getSlots().keySet()) {
                if (classes.contains(key)) {
                    entity = hyps.get(i).getSlots().get(key).get(0).getMatched().toString();
                } else if (OBJECT_PROPERTIES.contains(key)) {
                    object = hyps.get(i).getSlots().get(key).get(0).getMatched().toString();
                } else if (DATATYPE_PROPERTIES.contains(key)) {
                    datatype = hyps.get(i).getSlots().get(key).get(0).getMatched().toString();
                }
            }
            if (entity == null && object == null && datatype == null) {
                i++;
                continue;
            }
            boolean duplicated = false;
            for (List<String> alternative : alternatives) {
                if (Objects.equals(alternative.get(0), entity) && Objects.equals(alternative.get(1), object) && Objects.equals(alternative.get(2), datatype)) {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated) {
                alternatives.add(Arrays.asList(entity, object, datatype, String.valueOf(hyps.get(i).getScore())));
            }
            i++;
        }
        return alternatives;
    }
}
