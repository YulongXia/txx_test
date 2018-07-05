package utils;

import ai.hual.labrador.kg.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pojo.ObjectProperty.*;

public class KnowledgeQueryUtils {

    private static final Logger logger = LoggerFactory.getLogger(ai.hual.labrador.kg.utils.KnowledgeQueryUtils.class);

    private KnowledgeAccessor knowledge;
    private KnowledgeStatusAccessor knowledgeStatusAccessor;

    public KnowledgeQueryUtils(KnowledgeAccessor knowledge, KnowledgeStatusAccessor knowledgeStatusAccessor) {
        this.knowledge = knowledge;
        this.knowledgeStatusAccessor = knowledgeStatusAccessor;
    }

    private interface StatusChecker {

        boolean check(Binding binding);

    }

    private static class InstanceStatusChecker implements StatusChecker {

        private KnowledgeStatusAccessor knowledgeStatusAccessor;
        private Function<Binding, String> mapper;

        private InstanceStatusChecker(KnowledgeStatusAccessor knowledgeStatusAccessor,
                                      Function<Binding, String> mapper) {
            this.knowledgeStatusAccessor = knowledgeStatusAccessor;
            this.mapper = mapper;
        }

        @Override
        public boolean check(Binding binding) {
            return knowledgeStatusAccessor.instanceStatus(mapper.apply(binding)) != KnowledgeStatus.DISABLED;
        }
    }

    private static class PropertyStatusChecker implements StatusChecker {

        private KnowledgeStatusAccessor knowledgeStatusAccessor;
        private Function<Binding, String> instanceMapper;
        private Function<Binding, String> propertyMapper;

        private PropertyStatusChecker(KnowledgeStatusAccessor knowledgeStatusAccessor,
                                      Function<Binding, String> instanceMapper, Function<Binding, String> propertyMapper) {
            this.knowledgeStatusAccessor = knowledgeStatusAccessor;
            this.instanceMapper = instanceMapper;
            this.propertyMapper = propertyMapper;
        }

        @Override
        public boolean check(Binding binding) {
            return knowledgeStatusAccessor.propertyStatus(
                    instanceMapper.apply(binding), propertyMapper.apply(binding)) != KnowledgeStatus.DISABLED;
        }
    }

    private <T> List<T> queryAndCheckStatus(String queryString, Function<Binding, T> resultMapper,
                                            StatusChecker... statusCheckers) {
        logger.debug("SPARQL {}", queryString);
        SelectResult selectResult = knowledge.select(queryString);

        List<T> result = new ArrayList<>();
        for (Binding binding : selectResult.getBindings()) {
            if (statusCheckers == null || statusCheckers.length == 0) {
                result.add(resultMapper.apply(binding));
            } else {
                boolean enabled = true;
                for (StatusChecker statusChecker : statusCheckers) {
                    if (!statusChecker.check(binding)) {
                        enabled = false;
                        break;
                    }
                }
                if (enabled) {
                    result.add(resultMapper.apply(binding));
                }
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}",
                selectResult.getBindings().size(), result.size(), queryString);
        return result;
    }

    private StatusChecker instanceEnabled(Function<Binding, String> mapper) {
        return new InstanceStatusChecker(knowledgeStatusAccessor, mapper);
    }

    private StatusChecker instanceEnabled(String key) {
        return instanceEnabled(binding -> binding.value(key));
    }

    private StatusChecker propertyEnabled(Function<Binding, String> instanceMapper, Function<Binding, String> propertyMapper) {
        return new PropertyStatusChecker(knowledgeStatusAccessor, instanceMapper, propertyMapper);
    }

    /**
     * find datatype or object property x that
     * entity -> x -> value
     * or
     * entity -> x -> bn
     * where entity -> x is enabled and bn is enabled when x is a object property
     *
     * @param entity the label of the entity
     * @return an instance of {@link EntitiesAndTheirProperties} with given entities
     */
    public EntitiesAndTheirProperties queryProperties(String entity) {
        String queryString = "SELECT DISTINCT ?entity ?datatype ?datatypeLabel ?object ?objectLabel ?objectType ?bn ?bnlabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "{ ?entity ?datatype ?value . ?datatype rdf:type owl:DatatypeProperty . ?datatype rdfs:label ?datatypeLabel .} UNION " +
                "{\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> }\n" +
                "?entity ?object ?bn. ?object rdfs:subPropertyOf* ?objectType.\n" +
                "OPTIONAL { ?object rdfs:label ?objectLabel. }\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}\n" +
                "}";

        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        EntitiesAndTheirProperties properties = new EntitiesAndTheirProperties();
        for (Binding binding : result.getBindings()) {
            String entityIRI = binding.value("entity");
            if (binding.value("datatype") != null) {
                // datatype
                if (knowledgeStatusAccessor.propertyStatus(entityIRI, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                    properties.getEntitiesAndTheirDatatypes().put(entity, binding.value("datatypeLabel"));
                }
            } else if (binding.value("object") != null) {
                // object
                String bnIRI = binding.value("bn");
                if (knowledgeStatusAccessor.instanceStatus(bnIRI) != KnowledgeStatus.DISABLED) {
                    properties.getEntitiesAndTheirObjects().put(entity, new ObjectProperty(
                            binding.value("object"), binding.value("objectLabel"), binding.value("objectType"),
                            new BlankNode(binding.value("bn"), binding.value("bnlabel"))));
                }
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(),
                properties.getEntitiesAndTheirObjects().size() + properties.getEntitiesAndTheirDatatypes().size(),
                queryString);

        return properties;
    }

    /**
     * find datatype or object property x that
     * entity -> x -> value
     * or
     * entity -> x -> bn
     * where entity -> x is enabled and bn is enabled when x is a object property
     *
     * @param entities the label of the entity
     * @return an instance of {@link EntitiesAndTheirProperties} with given entities
     * TODO unicode ??? problem in virtuoso sparql
     */
    public EntitiesAndTheirProperties queryProperties(List<String> entities) {
        String queryString = "SELECT DISTINCT ?entity ?entityLabel ?datatype ?datatypeLabel ?object ?objectLabel ?objectType ?bn ?bnlabel WHERE {\n" +
                "VALUES ?entityLabel { '" + String.join("' '", entities) + "' }\n" +
                "?entity rdfs:label ?entityLabel .\n" +
                "{ ?entity ?datatype ?value . ?datatype rdf:type owl:DatatypeProperty . ?datatype rdfs:label ?datatypeLabel .} UNION " +
                "{\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> }\n" +
                "?entity ?object ?bn. FILTER EXISTS { ?object rdfs:subPropertyOf* ?objectType. }\n" +
                "OPTIONAL { ?object rdfs:label ?objectLabel. }\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}\n" +
                "}";

        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        EntitiesAndTheirProperties properties = new EntitiesAndTheirProperties();
        for (Binding binding : result.getBindings()) {
            String entity = binding.value("entityLabel");
            String entityIRI = binding.value("entity");
            if (binding.value("datatype") != null) {
                // datatype
                if (knowledgeStatusAccessor.propertyStatus(entityIRI, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                    properties.getEntitiesAndTheirDatatypes().put(entity, binding.value("datatypeLabel"));
                }
            } else if (binding.value("object") != null) {
                // object
                String bnIRI = binding.value("bn");
                if (knowledgeStatusAccessor.instanceStatus(bnIRI) != KnowledgeStatus.DISABLED) {
                    properties.getEntitiesAndTheirObjects().put(entity, new ObjectProperty(
                            binding.value("object"), binding.value("objectLabel"), binding.value("objectType"),
                            new BlankNode(binding.value("bn"), binding.value("bnlabel"))));
                }
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(),
                properties.getEntitiesAndTheirObjects().size() + properties.getEntitiesAndTheirDatatypes().size(),
                queryString);

        return properties;
    }

    /**
     * find enabled datatypes that
     * entity -> object -> bn -> datatype -> value
     *
     * @param entity the label of the entity
     * @param object the iri of the object property
     * @return a list of datatype labels where bn and bn -> datatype are both enabled
     */
    public List<BNAndDatatypeAndValue> queryDatatypeOfObject(String entity, String object) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?datatypeLabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity <%s> ?bn .\n", object) +
                "?datatype rdfs:label ?datatypeLabel ; a owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(b.value("datatypeLabel"), b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }

    /**
     * find enabled datatypes that
     * bn -> datatype -> value
     *
     * @param bnlabel the label of the bn
     * @return a list of datatype labels where bn and bn -> datatype are both enabled
     */
    public List<String> queryDatatypesWithBNLabel(String bnlabel) {
        String queryString = "SELECT DISTINCT ?bn ?datatype ?datatypeLabel WHERE {\n" +
                String.format("?bn <http://hual.ai/special#bnlabel> '%s' .\n", bnlabel) +
                "?datatype rdfs:label ?datatypeLabel ; a owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("datatypeLabel"),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }

    /**
     * find yshape property of two entities where bn and datatype is enabled
     *
     * @param entities a list of entity labels
     * @return a list of yshape bn and dp
     */
    public List<YshapeBNAndDP> queryYshapeBNLabelsAndDatatypes(List<String> entities) {
        StringBuilder entityPairValues = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            String s1Label = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                String s2Label = entities.get(j);
                entityPairValues.append("('").append(s1Label).append("' '").append(s2Label).append("')\n");
            }
        }
        return queryYshapeBNLabelsAndDatatypes(entityPairValues.toString());
    }

    /**
     * find yshape property of two entities where bn and datatype is enabled
     *
     * @param e1 a list of entity labels of entity 1
     * @param e2 a list of entity labels of entity 2
     * @return a list of yshape bn and dp
     */
    public List<YshapeBNAndDP> queryYshapeBNLabelsAndDatatypes(List<String> e1, List<String> e2) {
        StringBuilder entityPairValues = new StringBuilder();
        for (String s1Label : e1) {
            for (String s2Label : e2) {
                entityPairValues.append("('").append(s1Label).append("' '").append(s2Label).append("')\n");
            }
        }
        return queryYshapeBNLabelsAndDatatypes(entityPairValues.toString());
    }

    private List<YshapeBNAndDP> queryYshapeBNLabelsAndDatatypes(String entityPairValues) {
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label ?yp ?bn ?bnlabel ?dp ?dplabel WHERE{\n" +
                "VALUES (?s1Label ?s2Label) {\n" +
                entityPairValues +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                "?bn <http://hual.ai/special#bnlabel> ?bnlabel .\n" +
                "?bn ?dp ?value . ?dp rdfs:label ?dplabel .\n" +
                "FILTER EXISTS {\n" +
                "?yp rdfs:subPropertyOf <http://hual.ai/standard#YshapeProperty> .\n" +
                "?dp rdfs:subPropertyOf <http://hual.ai/standard#HualDataTypeProperty> . \n" +
                "}\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDP(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), b.value("bnlabel")), b.value("dplabel")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }


    /**
     * find yshape property of two entities and datatype where bn and bn -> datatype is enabled
     *
     * @param entities a list of entity labels
     * @param datatype the label of the datatype
     * @return a list of yshape bns
     */
    public List<YshapeBNAndDP> queryYshapeBNsWithDatatypes(List<String> entities, String datatype) {
        StringBuilder entityPairValues = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            String s1Label = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                String s2Label = entities.get(j);
                entityPairValues.append("('").append(s1Label).append("' '").append(s2Label).append("')\n");
            }
        }
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label ?yp ?bn ?bnlabel ?dp WHERE{\n" +
                "VALUES (?s1Label ?s2Label) {\n" +
                entityPairValues.toString() +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                "?bn <http://hual.ai/special#bnlabel> ?bnlabel .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n", datatype) +
                "FILTER EXISTS {\n" +
                "?yp rdfs:subPropertyOf <http://hual.ai/standard#YshapeProperty> .\n" +
                "?dp rdfs:subPropertyOf <http://hual.ai/standard#HualDataTypeProperty> . \n" +
                "}\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDP(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), b.value("bnlabel")), datatype),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    /**
     * find entity x and BN y with datatype that
     * x -> datatype -> value
     * or
     * x -> object -> y -> datatype -> value
     * where x -> datatype is enabled in the first case or y and y -> datatype is enabled in the second case.
     *
     * @param datatype The datatype property label
     * @param cls      restrict entity in the specified class. if cls == null, no class restriction is considered
     * @return A multimap with entity label x as key and bn label y as value.
     * If the datatype is directly connected to the entity, the value will be a null.
     * e.g.:
     * In knowledge graph:
     * a -> datatype -> v1
     * a -> object -> b -> datatype -> v2
     * c -> datatype -> v3
     * d -> object -> e -> datatype -> v4
     * <p>
     * Result of queryEntitiesAndBNsWithDatatype(datatype, knowledge):
     * {
     * a -> [null, b],
     * c -> [null],
     * d -> [e]
     * }
     */
    public ListMultimap<String, String> queryEntitiesAndBNsWithDatatype(String datatype, String cls) {
        String queryString = "SELECT DISTINCT ?entity ?entityLabel ?bn ?bnlabel WHERE {\n" +
                String.format("?datatype rdfs:label '%s' .\n", datatype) +
                "{ ?entity ?datatype ?value . } UNION " +
                "{ ?entity ?object ?bn . ?bn ?datatype ?value . OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel . } }\n" +
                "?entity rdfs:label ?entityLabel .\n" +
                (cls == null ? "" : String.format("?entity a/rdfs:subClassOf*/rdfs:label '%s' .\n", cls)) +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        ListMultimap<String, String> entitiesAndBNs = ArrayListMultimap.create();
        if (result.getBindings().size() > 0) {
            String datatypeIRI = queryDatatypeIRI(datatype);
            for (Binding binding : result.getBindings()) {
                if (binding.value("bn") == null) {
                    // only entity
                    String entityIRI = binding.value("entity");
                    if (knowledgeStatusAccessor.propertyStatus(entityIRI, datatypeIRI) != KnowledgeStatus.DISABLED) {
                        entitiesAndBNs.put(binding.value("entityLabel"), binding.value("bnlabel"));
                    }
                } else {
                    // entity -> bn -> datatype
                    String bnIRI = binding.value("bn");
                    if (knowledgeStatusAccessor.instanceStatus(bnIRI) != KnowledgeStatus.DISABLED &&
                            knowledgeStatusAccessor.propertyStatus(bnIRI, datatypeIRI) != KnowledgeStatus.DISABLED) {
                        entitiesAndBNs.put(binding.value("entityLabel"), binding.value("bnlabel"));
                    }
                }
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(), entitiesAndBNs.size(), queryString);

        return entitiesAndBNs;
    }

    /**
     * find object properties with given entity and datatype
     * entity -> object -> bn -> datatype -> value
     * where bn and bn -> datatype is enabled
     *
     * @param entity   the label of the entity
     * @param datatype the label of the datatype
     * @return a list of object properties
     */
    public List<ObjectProperty> queryObjectsOfDatatype(String entity, String datatype) {
        String queryString = "SELECT DISTINCT ?o ?oLabel ?objectType ?bn ?bnlabel WHERE {\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> }\n" +
                String.format("?entity rdfs:label '%s'. ?entity ?o ?bn.\n", entity) +
                "?o rdfs:subPropertyOf* ?objectType.\n" +
                "OPTIONAL { ?o rdfs:label ?oLabel. }\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                String.format("?bn ?dp ?value. ?dp rdfs:label '%s' .\n", datatype) +
                "}";
        String datatypeIRI = queryDatatypeIRI(datatype);
        return queryAndCheckStatus(queryString, b -> new ObjectProperty(
                        b.value("o"), b.value("oLabel"), b.value("objectType"),
                        new BlankNode(b.value("bn"), b.value("bnlabel"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> datatypeIRI));
    }

    /**
     * find entity x that
     * x -> object -> y
     *
     * @param object the iri of the object property
     * @return a list of enabled entities.
     */
    public List<String> queryEntityWithObject(String object) {
        String queryString = "SELECT DISTINCT ?entity ?entityLabel WHERE {\n" +
                String.format("?entity <%s> ?o .\n", object) +
                "?entity rdfs:label ?entityLabel .\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("entityLabel"),
                instanceEnabled("entity"));
    }

    /**
     * find value
     * entity -> datatype -> value
     *
     * @param entity   the label of the entity
     * @param datatype the label of the datatype property
     * @return a list of values
     */
    public List<String> query(String entity, String datatype) {
        String queryString = "SELECT DISTINCT ?o WHERE {\n" +
                String.format("?s rdfs:label '%s' .\n", entity) +
                String.format("?p rdfs:subPropertyOf*/rdfs:label '%s' .\n", datatype) +
                "?s ?p ?o .\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("o"));
    }


    /**
     * find subProperties
     * datatype -> subProperties
     *
     * @param datatype the label of the datatype property
     * @return a list of values
     */
    public List<String> querySubProperties(String datatype) {
        String queryString = "SELECT DISTINCT ?subPropertyLabel WHERE {\n" +
                String.format("?p rdfs:label '%s' .\n", datatype) +
                "?subProperty rdfs:subPropertyOf ?p.\n"+
                "?subProperty rdfs:label ?subPropertyLabel.\n"+
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("subPropertyLabel"));
    }

    /**
     * find bns
     * entity -> object -> bn
     * where bn is enabled
     *
     * @param entity the label of the  entity
     * @param object the iri of the object
     * @return a list of blank node
     */
    public List<BlankNode> queryBNs(String entity, String object) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel WHERE {\n" +
                String.format("?s rdfs:label '%s' .\n", entity) +
                String.format("?s <%s> ?bn .\n", object) +
                "?bn <http://hual.ai/special#bnlabel> ?bnlabel . \n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BlankNode(b.value("bn"), b.value("bnlabel")),
                instanceEnabled("bn"));
    }

    /**
     * find bn and value by entity, object and datatype where
     * entity -> object -> bn -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param entity   the label of the entity
     * @param object   the iri of the object property
     * @param datatype the label of the datatype property
     * @return a list of bn and values
     */
    public List<BNAndValue> query(String entity, String object, String datatype) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("{ ?has_object rdfs:subPropertyOf* <%s> . ?entity ?has_object ?bn . } UNION { ?entity <%s> ?bn . }\n", object, object) +
                String.format("?datatype rdfs:subPropertyOf*/rdfs:label '%s' .\n", datatype) +
                "?bn ?datatype ?value .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        String datatypeIRI = queryDatatypeIRI(datatype);
        return queryAndCheckStatus(queryString, b -> new BNAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")), b.value("value")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> datatypeIRI));
    }

    /**
     * query value of given bn and datatype
     * bn -> datatype -> value
     * where bn -> datatype is enabled
     *
     * @param bn       the blank node
     * @param datatype the label of the datatype
     * @return The value
     */
    public String queryBNDatatype(BlankNode bn, String datatype) {
        String queryString = "SELECT DISTINCT ?value WHERE{\n" +
                String.format("<%s> ?dp ?value . ?dp rdfs:label '%s' .\n", bn.getIri(), datatype) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "value");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * query value of given bn and datatype
     * bn -> datatype -> value
     * where bn -> datatype is enabled
     *
     * @param bnlabel         the label of the blank node
     * @param conditionEntity the label of the condition entity
     * @param datatype        the label of the datatype
     * @return The value
     */
    public BNAndValue queryWithBNLabelAndConditionEntityAndDatatype(String bnlabel, String conditionEntity, String datatype) {
        String queryString = "SELECT DISTINCT ?bn ?value WHERE {\n" +
                String.format("?conditionEntity rdfs:label '%s' .\n", conditionEntity) +
                String.format("?dp rdfs:label '%s'.\n", datatype) +
                String.format("?bn <http://hual.ai/special#bnlabel> '%s'.\n", bnlabel) +
                "?bn ?undercondition ?conditionEntity. \n" +
                "?bn ?dp ?value .\n" +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);
        logger.debug("SPARQL size: {}, query: {}", result.getBindings().size(), queryString);

        if (result.getBindings().isEmpty()) {
            return null;
        }
        Binding binding = result.getBindings().get(0);
        return new BNAndValue(new BlankNode(binding.value("bn"), bnlabel), binding.value("value"));
    }

    /**
     * find value
     * entity -> datatype -> value
     * where entity -> datatype is enabled
     *
     * @param entities a list of entity labels
     * @param datatype the label of the datatype property
     * @return a map with entity label as key and value as value
     */
    public Map<String, String> queryValidEntitiesAndValuesWithDatatype(List<String> entities, String datatype) {
        String queryString = "SELECT DISTINCT ?entity ?entityLabel ?datatype ?value WHERE {\n" +
                "VALUES ?entityLabel { '" + String.join("' '", entities) + "' }\n" +
                "?entity rdfs:label ?entityLabel .\n" +
                String.format("?datatype rdfs:label '%s' .\n", datatype) +
                "?entity ?datatype ?value . " +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        ListMultimap<String, String> entitiesAndValues = ArrayListMultimap.create();
        for (Binding binding : result.getBindings()) {
            String entity = binding.value("entityLabel");
            String entityIRI = binding.value("entity");
            if (knowledgeStatusAccessor.propertyStatus(entityIRI, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                entitiesAndValues.put(entity, binding.value("value"));
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(), entitiesAndValues.size(), queryString);

        return entitiesAndValues.keySet().stream().collect(Collectors.toMap(entity -> entity, entity -> {
            List<String> values = entitiesAndValues.get(entity);
            return values.size() == 1 ? values.get(0) : values.toString();
        }));
    }

    /**
     * find bn and value by entity, object and datatype where
     * entity -> object -> bn -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param entities a list of entity labels
     * @param object   the iri of the object property
     * @param datatype the label of the datatype property
     * @return a multimap with entity label as key and bn and value as value
     */
    public ListMultimap<String, BNAndValue> queryValidEntitiesAndBNAndValuesWithObjectAndDatatype(List<String> entities, String object, String datatype) {
        String queryString = "SELECT DISTINCT ?entity ?entityLabel ?bn ?bnlabel ?value WHERE {\n" +
                "VALUES ?entityLabel { '" + String.join("' '", entities) + "' }\n" +
                "?entity rdfs:label ?entityLabel .\n" +
                String.format("?entity ?object ?bn . FILTER EXISTS { ?object rdfs:subPropertyOf* <%s> } .\n", object) +
                String.format("?bn ?datatype ?value . FILTER EXISTS { ?datatype rdfs:subPropertyOf*/rdfs:label '%s' } .\n", datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        ListMultimap<String, BNAndValue> entitiesAndBNAndValues = ArrayListMultimap.create();
        for (Binding binding : result.getBindings()) {
            String bnIRI = binding.value("bn");
            if (knowledgeStatusAccessor.instanceStatus(bnIRI) != KnowledgeStatus.DISABLED &&
                    knowledgeStatusAccessor.propertyStatus(bnIRI, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                entitiesAndBNAndValues.put(binding.value("entityLabel"), new BNAndValue(
                        new BlankNode(bnIRI, binding.value("bnlabel")), binding.value("value")));
            }
        }

        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(), entitiesAndBNAndValues.size(), queryString);

        return entitiesAndBNAndValues;
    }

    /**
     * find value
     * bn -> datatype -> value
     * where bn and bn -> datatype is enabled
     *
     * @param bnLabels a list of bn labels
     * @return a multimap with bn label as key and bn as value
     */
    public ListMultimap<String, BlankNode> queryValidBNsWithBNLabels(List<String> bnLabels) {
        String queryString = "SELECT DISTINCT ?entity ?bn ?bnlabel ?datatype WHERE {\n" +
                "VALUES ?bnlabel { '" + String.join("' '", bnLabels) + "' }\n" +
                "?bn <http://hual.ai/special#bnlabel> ?bnlabel .\n" +
                "?entity ?object ?bn. ?bn ?datatype ?value . " +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        ListMultimap<String, BlankNode> bns = ArrayListMultimap.create();
        for (Binding binding : result.getBindings()) {
            String bnlabel = binding.value("bnlabel");
            String bn = binding.value("bn");
            if (knowledgeStatusAccessor.instanceStatus(binding.value("entity")) != KnowledgeStatus.DISABLED &&
                    knowledgeStatusAccessor.instanceStatus(bn) != KnowledgeStatus.DISABLED &&
                    knowledgeStatusAccessor.propertyStatus(bn, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                bns.put(bnlabel, new BlankNode(bn, bnlabel));
            }
        }
        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(), bns.size(), queryString);

        return bns;
    }

    /**
     * find value
     * bn -> datatype -> value
     * where entity and bn and bn -> datatype is enabled
     *
     * @param bnLabels a list of bn labels
     * @param datatype the label of the datatype property
     * @return a multimap with bn label as key and bn as value
     */
    public ListMultimap<String, BlankNode> queryValidBNsWithBNLabelsAndDP(List<String> bnLabels, String datatype) {
        String queryString = "SELECT DISTINCT ?entity ?bn ?bnlabel ?datatype WHERE {\n" +
                "VALUES ?bnlabel { '" + String.join("' '", bnLabels) + "' }\n" +
                "?bn <http://hual.ai/special#bnlabel> ?bnlabel .\n" +
                String.format("?datatype rdfs:label '%s' .\n", datatype) +
                "?entity ?object ?bn. ?bn ?datatype ?value . " +
                "}";
        logger.debug("SPARQL {}", queryString);
        SelectResult result = knowledge.select(queryString);

        ListMultimap<String, BlankNode> bnsAndValues = ArrayListMultimap.create();
        for (Binding binding : result.getBindings()) {
            String bnlabel = binding.value("bnlabel");
            String bn = binding.value("bn");
            if (knowledgeStatusAccessor.instanceStatus(binding.value("entity")) != KnowledgeStatus.DISABLED &&
                    knowledgeStatusAccessor.instanceStatus(bn) != KnowledgeStatus.DISABLED &&
                    knowledgeStatusAccessor.propertyStatus(bn, binding.value("datatype")) != KnowledgeStatus.DISABLED) {
                bnsAndValues.put(bnlabel, new BlankNode(bn, bnlabel));
            }
        }
        logger.debug("SPARQL size: {}, enabled: {}, query: {}", result.getBindings().size(), bnsAndValues.size(), queryString);

        return bnsAndValues;
    }

    /**
     * find another entity in yshape
     * e1 -> yshape -> bn
     * e2 -> yshape -> bn
     * where bn and e2 is enabled
     *
     * @param entity the label of the entity
     * @param yshape yshape uri
     * @return a list of labels of the other entities
     */
    public List<String> queryAnotherYshapeEntities(String entity, String yshape) {
        String queryString = "SELECT DISTINCT ?e2 ?e2Label ?bn WHERE {\n" +
                String.format("?e1 rdfs:label '%s' . ?e2 rdfs:label ?e2Label .\n", entity) +
                String.format("?e1 <%s> ?bn. ?e2 <%s> ?bn .\n", yshape, yshape) +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("e2Label"),
                instanceEnabled("bn"),
                instanceEnabled("e2"));
    }

    /**
     * find condition entities
     * entity -> object -> bn (with condition entity)
     * where bn is enabled
     *
     * @param entity the label of the entity
     * @param object the iri of the object  property
     * @return a list of condition entity and bns
     */
    public List<ConditionEntityAndBN> queryConditionEntities(String entity, String object) {
        String queryString = "SELECT DISTINCT ?cel ?cec ?bn ?bnlabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity <%s> ?bn.\n", object) +
                "?bn ?undercondition ?conditionEntity. ?conditionEntity rdfs:label ?cel.\n" +
                "?undercondition rdfs:subPropertyOf <http://hual.ai/standard#Undercondition>.\n" +
                "?conditionEntity a/rdfs:label ?cec .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new ConditionEntityAndBN(new ConditionEntity(b.value("cel"), b.value("cec")),
                        new BlankNode(b.value("bn"), b.value("bnlabel"))),
                instanceEnabled("bn"));
    }

    /**
     * find condition entities and values
     * entity -> object -> bn (with condition entity) -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param entity   the label of the entity
     * @param object   the iri of the object
     * @param datatype the label of the datatype
     * @return a list of condition entity and bn and values
     */
    public List<ConditionEntityAndBNAndValue> queryConditionEntitiesAndValuesWithDatatype(String entity, String object, String datatype) {
        String queryString = "SELECT DISTINCT ?cel ?cec ?value ?bn ?bnlabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity <%s> ?bn.\n", object) +
                "?bn ?undercondition ?conditionEntity . ?undercondition rdfs:subPropertyOf <http://hual.ai/standard#Undercondition> .\n" +
                "?conditionEntity rdfs:label ?cel.\n" +
                "?conditionEntity a/rdfs:label ?cec .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s'.\n", datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        String datatypeIRI = queryDatatypeIRI(datatype);
        return queryAndCheckStatus(queryString, b -> new ConditionEntityAndBNAndValue(new ConditionEntity(b.value("cel"), b.value("cec")),
                        new BNAndValue(new BlankNode(b.value("bn"), b.value("bnlabel")), b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> datatypeIRI));
    }

    /**
     * find condition entities and values
     * bn (with condition entity) -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param bnlabel  the label of the bn
     * @param datatype the label of the datatype
     * @return a list of condition entity and bn and values
     */
    public List<ConditionEntityAndBNAndValue> queryConditionEntitiesAndValuesWithBNLabelAndDatatype(String bnlabel, String datatype) {
        String queryString = "SELECT DISTINCT ?cel ?cec ?value ?bn WHERE {\n" +
                String.format("?dp rdfs:label '%s' .\n", datatype) +
                String.format("?bn <http://hual.ai/special#bnlabel> '%s' .\n", bnlabel) +
                "OPTIONAL { ?bn ?undercondition ?conditionEntity . ?conditionEntity rdfs:label ?cel .\n" +
                "?undercondition rdfs:subPropertyOf <http://hual.ai/standard#Undercondition>. }\n" +
                "?conditionEntity a/rdfs:label ?cec .\n" +
                "?bn ?dp ?value .\n" +
                "}";
        String datatypeIRI = queryDatatypeIRI(datatype);
        return queryAndCheckStatus(queryString, b -> new ConditionEntityAndBNAndValue(new ConditionEntity(b.value("cel"), b.value("cec")),
                        new BNAndValue(new BlankNode(b.value("bn"), bnlabel), b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> datatypeIRI));
    }

    /**
     * find datatype and values
     * entity -> object -> bn (with condition entity) -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param entity          the label of the entity
     * @param object          the iri of the object property
     * @param conditionEntity the label of the condition entity
     * @return a list of datatype and values
     */
    public List<DatatypeAndValue> queryConditionDatatypesAndValues(String entity, String object, String conditionEntity) {
        String queryString = "SELECT DISTINCT ?bn ?dp ?dplabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity <%s> ?bn.\n", object) +
                String.format("?bn ?undercondition ?conditionEntity. ?conditionEntity rdfs:label '%s'.\n", conditionEntity) +
                "?undercondition rdfs:subPropertyOf <http://hual.ai/standard#Undercondition>.\n" +
                "?bn ?dp ?value . ?dp rdfs:label ?dplabel. FILTER NOT EXISTS { ?dp rdfs:subPropertyOf* <http://hual.ai/standard#Undercondition>. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new DatatypeAndValue(b.value("dplabel"), b.value("value")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    /**
     * find datatype and values
     * bn (with condition entity) -> datatype -> value
     * where bn and bn -> datatype are both enabled
     *
     * @param bnlabel         the label of the bn
     * @param conditionEntity the label of the condition entity
     * @return a list of datatype and values
     */
    public List<BNAndDatatypeAndValue> queryConditionDatatypesAndValues(String bnlabel, String conditionEntity) {
        String queryString = "SELECT DISTINCT ?bn ?dp ?dplabel ?value WHERE {\n" +
                String.format("?bn <http://hual.ai/special#bnlabel> '%s' .\n", bnlabel) +
                String.format("?conditionEntity rdfs:label '%s' .\n", conditionEntity) +
                "?undercondition rdfs:subPropertyOf* <http://hual.ai/standard#Undercondition>.\n" +
                "?bn ?undercondition ?conditionEntity .\n" +
                "?bn ?dp ?value . ?dp rdfs:label ?dplabel. FILTER NOT EXISTS { ?dp rdfs:subPropertyOf* <http://hual.ai/standard#Undercondition>. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), bnlabel),
                        new DatatypeAndValue(b.value("dplabel"), b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    /**
     * find valid classes
     * entity (of class) -> datatype
     *
     * @param entity   the label of the entity
     * @param datatype the label of the datatype
     * @return a list of iri of valid classes who can be the domain of the datatype property
     */
    public List<String> queryValidClassesWithEntityAndDatatype(String entity, String datatype) {
        String queryString = "SELECT DISTINCT ?class WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?entity a ?class.\n" +
                String.format("?dp rdfs:domain ?clazz. ?dp rdfs:label '%s'.\n", datatype) +
                "?class rdfs:subClassOf* ?clazz.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("class"));
    }

    public String queryObjectLabel(String o) {
        String queryString = "SELECT DISTINCT ?label WHERE {\n" +
                String.format("<%s> rdfs:label ?label.\n", o) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "label");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    public String queryEntityIRI(String entity) {
        String queryString = "SELECT DISTINCT ?entity WHERE {\n" +
                String.format("?entity rdfs:label '%s'.\n", entity) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "entity");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    public String queryDatatypeIRI(String datatype) {
        String queryString = "SELECT DISTINCT ?datatype WHERE {\n" +
                String.format("?datatype rdfs:label '%s'.\n", datatype) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "datatype");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    public String queryObjectType(String objectIRI) {
        String queryString = "SELECT DISTINCT ?objectType WHERE {\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> }\n" +
                String.format("<%s> rdfs:subPropertyOf ?objectType .\n", objectIRI) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "objectType");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    public String queryConditionClass(String conditionEntityLabel) {
        String queryString = "SELECT DISTINCT ?cec WHERE {\n" +
                String.format("?conditionEntity rdfs:label '%s' .\n", conditionEntityLabel) +
                "?conditionEntity a/rdfs:label ?cec .\n" +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "cec");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<String> queryMainClazz(String mainClass) {
        String queryString = "SELECT DISTINCT ?classLabel WHERE {\n" +
                String.format("?class rdfs:subClassOf <http://hual.ai/taikang#%s> .\n",mainClass )+
                "?class rdfs:label ?classLabel.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("classLabel"));
    }

}