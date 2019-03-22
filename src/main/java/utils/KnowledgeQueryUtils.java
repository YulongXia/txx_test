package utils;

import ai.hual.labrador.kg.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.sun.org.apache.bcel.internal.generic.Select;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;

import java.util.*;
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
                "{ ?entity ?datatype ?value . ?datatype rdf:type/rdfs:subClassOf* owl:DatatypeProperty . ?datatype rdfs:label ?datatypeLabel .} UNION " +
                "{\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> <" + COMPLEX_PROPERTY + "> }\n" +
                "?entity ?object ?bn. ?object a/rdfs:subPropertyOf* ?objectType.\n" +
                "OPTIONAL { ?object rdfs:label ?objectLabel. }\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}\n" +
                "}";

//        String queryString = "SELECT DISTINCT ?entity ?datatype ?datatypeLabel ?object ?objectLabel ?objectType ?bn ?bnlabel WHERE {\n" +
//                String.format("?entity rdfs:label '%s' .\n", entity) +
//                "{ ?entity ?datatype ?value . ?datatype rdf:type owl:DatatypeProperty . ?datatype rdfs:label ?datatypeLabel .} UNION " +
//                "{\n" +
//                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> <" + COMPLEX_PROPERTY + "> }\n" +
//                "?entity ?object ?bn. ?object a/rdfs:subPropertyOf* ?objectType.\n" +
//                "OPTIONAL { ?object rdfs:label ?objectLabel. }\n" +
//                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
//                "}\n" +
//                "}";

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
                "?bn ?dp ?value . ?dp rdfs:label ?dplabel .\n" +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>." +
                "?dp a/rdfs:subClassOf owl:DatatypeProperty." +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDP(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), String.format("%s,%s",b.value("s1Label"),b.value("s2Label"))), b.value("dplabel")),
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
                "optional {?bn <http://hual.ai/special#bnlabel> ?bnlabel .}\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n", datatype) +
                "FILTER EXISTS {\n" +
                "{?yp rdfs:subPropertyOf <http://hual.ai/standard#YshapeProperty>  .} UNION {?yp rdfs:label 'with bn'.}\n" +
                "{?dp rdfs:subPropertyOf <http://hual.ai/standard#HualDataTypeProperty> . } UNION {?dp rdf:type ?dptype. ?dptype rdfs:subClassOf owl:DatatypeProperty.}\n" +
                "}}";
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
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> <" + COMPLEX_PROPERTY + "> }\n" +
                String.format("?entity rdfs:label '%s'. ?entity ?o ?bn.\n", entity) +
                "?o rdf:type/rdfs:subPropertyOf* ?objectType.\n" +
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
                String.format("?entity <%s> ?o .\n", queryCpIRI(object)) +
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

    public String queryCpIRI(String cplabel) {
        String queryString = "SELECT DISTINCT ?cp WHERE {\n" +
                String.format("?cp rdfs:label '%s'.\n", cplabel) +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<String> result = knowledge.selectOneAsList(queryString, "cp");
        logger.debug("SPARQL size: {}, query: {}", result.size(), queryString);
        return result.isEmpty() ? cplabel : result.get(0);
    }

    public String queryObjectType(String objectIRI) {
        String queryString = "SELECT DISTINCT ?objectType WHERE {\n" +
                "VALUES ?objectType { <" + YSHAPE_PROPERTY + "> <" + DIFFUSION_PROPERTY + "> <" + CONDITION_PROPERTY + "> <" + COMPLEX_PROPERTY +"> }\n" +
                String.format("{<%s> rdfs:subPropertyOf ?objectType .} UNION {<%s> a ?objectType.}\n", objectIRI,objectIRI) +
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

    /**
     * find value
     * entity -> to_bn(ComplexProperty) -> bn -> datatype -> value
     *
     * @param entity   the label of the entity
     * @param datatype the label of the datatype property
     * @return conditions and dps
     */
    public BNAndPropertyAndValue queryComplexPropertyAndBN(String entity, String bn , String datatype) {

        String queryString1 = "select distinct ?o\n" +
                "where {\n" +
                String.format("<%s> ?p ?o.\n", bn) +
                String.format("?p rdfs:label '%s'.\n", datatype) +
                "}";

        SelectResult result1 = knowledge.select(queryString1);
        List<Binding> bindings1 = result1.getBindings();
        String value = bindings1.get(0).value("o");
        BNAndPropertyAndValue dpAndvalue = new BNAndPropertyAndValue(bn,datatype,value);

        return dpAndvalue;
    }


    public List<String> queryBNswithEntityAndComplexPropertyAndDatatype(String entity,String complexproperty,String datatype){
        String queryString = "select distinct ?bn\n" +
                "where {\n" +
                String.format("?s rdfs:label '%s'.\n",entity) +
                String.format("?s <%s> ?bn .\n",complexproperty) +
                String.format("<%s> a <%s> .\n",complexproperty,COMPLEX_PROPERTY) +
                "?bn ?dp ?value.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"bn");
        return result;
    }

    public List<String> queryBNswithEntityAndComplexPropertyAndConditionEntitiesAndDatatype(String entity,String complexproperty,String datatype,List<String> conditionEntities){
        StringBuilder conditionEntitiesClause = new StringBuilder();
        Integer i = 0;
        for(String ce : conditionEntities){
            conditionEntitiesClause.append(String.format("?bn ?op ?ce_%s.\n",i.toString()));
            conditionEntitiesClause.append(String.format("?ce_%s rdfs:label '%s'.\n",i.toString(),ce));
            ++ i;
        }
        String queryString = "select distinct ?bn\n" +
                "where {\n" +
                String.format("?s rdfs:label '%s'.\n",entity) +
                String.format("?s <%s> ?bn .\n",complexproperty) +
                "?bn ?dp ?value.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                conditionEntitiesClause.toString() +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"bn");
        return result;
    }



    public String queryValuewithBNAndConditionsAndDatatype(String bn, HashMap<String,String> conditions,String datatype){
        StringBuilder conditionsclause = new StringBuilder();
        Integer i = 0;
        for(Map.Entry<String,String> entry:conditions.entrySet()){
            conditionsclause.append(String.format("op_%s rdfs:label %s.\n%s ?op_%s %s.\n",i.toString(),entry.getKey(),bn,i.toString(),entry.getValue()));
            ++i;
        }
        String queryString = "select distinct ?value\n"+
                "where {\n" +
                String.format("'%s' '%s' ?value.\n ",bn,datatype)+
                conditionsclause.toString() +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"value");
        if(result.size() != 0){
            return result.get(0);
        }
        return null;
    }

    public List<String> queryConditionEntityClassWithBNs(List<String> bns){
        StringBuilder bnsclause = new StringBuilder();
        for(String bn:bns){
            bnsclause.append(String.format("<%s>",bn));
        }
        String queryString = "select distinct ?cLabel \n" +
                             "where { \n" +
                             String.format("values ?bn {%s}\n",bnsclause.toString()) +
                             "?s ?cp ?bn.\n" +
                             "?cp rdf:type <http://hual.ai/new_standard#ComplexProperty>.\n" +
                             "?bn ?op ?value.\n" +
                             "?op rdf:type/rdfs:subClassOf*  <http://hual.ai/new_standard#ObjectProperty>.\n" +
                             "?bn ?op ?value.\n" +
                             "?value a ?vClass.\n" +
                             "?vClass rdfs:label ?cLabel.\n" +
                             "}";
        List<String> result = knowledge.selectOneAsList(queryString,"cLabel");
        return result;
    }


    public ConditionClassesAndValues queryConditionClassesAndValueWithBNs(List<String> bns){

        ConditionClassesAndValues result = new ConditionClassesAndValues();

        List<String> classes = this.queryConditionEntityClassWithBNs(bns);
        StringBuilder bnsclause = new StringBuilder();
        for(String bn:bns){
            bnsclause.append(String.format("<%s>",bn));
        }
        String queryString_tpl = "select distinct ?vLabel \n" +
                "where { \n" +
                String.format("values ?bn {%s}\n",bnsclause.toString()) +
                "?s ?cp ?bn.\n" +
                "?cp rdf:type <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "?bn ?op ?value.\n" +
                "?op rdf:type/rdfs:subClassOf*  <http://hual.ai/new_standard#ObjectProperty>.\n" +
                "?value a ?vClass.\n" +
                "?value rdfs:label ?vLabel.\n" +
                "?vClass rdfs:label '%s'.\n" +
                "}";
        for(String clazz:classes){
            String queryString = String.format(queryString_tpl,clazz);
            List<String> tmp = knowledge.selectOneAsList(queryString,"vLabel");
            if(tmp.size() > 1)
                result.add(clazz,tmp);
        }
        return result;
    }

    public List<BNAndDatatypeAndValue> queryDatatypeOfComplexProperty(String entity, String complex) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?datatypeLabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity ?cp ?bn . ?cp rdfs:subPropertyOf*/rdfs:label '%s'.\n", complex) +
                "?datatype rdfs:label ?datatypeLabel ; rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
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

    public List<BNAndDatatypeAndValue> queryDatatypeOfComplexPropertyAndDatatype(String entity, String complex,String datatype) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity ?cp ?bn . ?cp rdfs:subPropertyOf*/rdfs:label '%s'.\n", complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }
    public List<BNAndDatatypeAndValue> queryDatatypeOfComplexPropertyAndDatatype(String entity, String datatype) {
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?entity ?cp ?bn . " +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }

    public List<Pair<String,String>> queryDatatypesWithOneTypeBN(List<String> bns){
        Set<Pair<String,String>> result = new HashSet<>();
        for(String bn:bns){
            String queryString = "select distinct ?cpLabel ?dpLabel \n" +
                    "where { \n" +
                    String.format("?s ?cp <%s>.\n",bn) +
                    "?cp rdfs:label ?cpLabel.\n " +
                    "?cp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                    String.format("<%s> ?dp ?value.\n",bn) +
                    "?dp rdf:type/rdfs:subClassOf  owl:DatatypeProperty.\n" +
                    "?dp rdfs:label ?dpLabel.\n" +
                    "}";
            SelectResult res = knowledge.select(queryString);
            for(Binding b:res.getBindings()){
                result.add(new Pair<>(b.value("cpLabel"),b.value("dpLabel")));
            }
        }
        return result.stream().collect(Collectors.toList());
    }

    public List<Pair<String,String>> queryDatatypesWithOneTypeBN(List<String> bns,Map<String,String> cpces){
        StringBuilder cesclause = new StringBuilder();
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }
        Set<Pair<String,String>> result = new HashSet<>();
        StringBuilder bnsclause = new StringBuilder();
        for(String bn:bns){
            String queryString = "select distinct ?cpLabel ?dpLabel \n" +
                    "where { \n" +
                    String.format("values ?bn {%s}\n",bnsclause.toString()) +
                    "?s ?cp ?bn.\n" +
                    "?cp rdfs:label ?cpLabel." +
                    "?cp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                    "?bn ?dp ?value.\n" +
                    "?dp rdf:type/rdfs:subClassOf  owl:DatatypeProperty.\n" +
                    "?dp rdfs:label ?dpLabel." +
                    cesclause.toString() +
                    "}";
            SelectResult res = knowledge.select(queryString);
            for(Binding b:res.getBindings()){
                result.add(new Pair<>(b.value("cpLabel"),b.value("dpLabel")));
            }
        }

        return result.stream().collect(Collectors.toList());
    }

//    public String queryAnotherEntityLabelOfwithbnByOneEntityAndBn(String entity,String bn){
//        String queryString = "select distinct ?dpLabel \n" +
//                "where { \n" +
//                String.format("values ?bn {%s}\n",bnsclause.toString()) +
//                "?s ?cp ?bn.\n" +
//                "?cp rdf:type/rdfs:subPropertyOf* <http://hual.ai/new_standard#ComplexProperty>.\n" +
//                "?bn ?dp ?value.\n" +
//                "?dp rdf:type/rdfs:subClassOf  owl:DatatypeProperty.\n" +
//                "?dp rdfs:label ?dpLabel" +
//                "}";
//        List<String> result = knowledge.selectOneAsList(queryString,"dpLabel");
//        return result;
//    }


    public List<String> checkValidationOfEntityAndObject(String entity,String object){
        String queryString = "select distinct ?clazz where {\n" +
                String.format("    ?object rdfs:label '%s'.\n",object) +
                "    ?object rdfs:domain ?clazz.\n" +
                String.format("    ?entity rdfs:label '%s'.\n",entity) +
                "    {?entity rdf:type ?clazz.} UNION {?entity rdf:type ?Class. ?Class rdfs:subClassOf* ?clazz.}\n" +
                "}";

        List<String> result = knowledge.selectOneAsList(queryString,"clazz");
        return result;
    }

    public List<String> checkValidationOfEntityAndObjectAndCES(String entity,String object,List<String> ces){
        StringBuilder ceLabels = new StringBuilder();
        for(Integer i=0;i<ces.size();++i){
            ceLabels.append(String.format("'%s' ",ces.get(i)));
        }

        String queryString = "SELECT DISTINCT ?class ?ceLabel WHERE {\n" +
                "VALUES ?ceLabel {" + ceLabels.toString() + "}\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?entity a/rdfs:subClassOf* ?cpdomain.\n" +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                "?class rdfs:subClassOf* ?cprange.\n" +
                "?bn a ?class.\n" +
                String.format("?cp rdfs:label '%s'.\n",object) +
                "?ce rdfs:label ?ceLabel.\n" +
                "?ce a ?cClass.\n" +
                "?underCondition rdfs:domain ?ceClass.\n" +
                "?class rdfs:subClassOf* ?ceClass.\n" +
                "}";

        List<String> result = knowledge.selectOneAsList(queryString,"class");
        return result;
    }

    public List<String> checkValidationOfEntityAndObjectAndDatatypeAndCES(String entity,String object,String datatype,List<String> ces){
        StringBuilder ceLabels = new StringBuilder();
        for(Integer i=0;i<ces.size();++i){
            ceLabels.append(String.format("'%s' ",ces.get(i)));
        }

        String queryString = "SELECT DISTINCT ?class ?ceLabel WHERE {\n" +
                "VALUES ?ceLabel {" + ceLabels.toString() + "}\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                String.format("?cp rdfs:label '%s'.\n",object) +
                "?cp rdfs:domain ?cpdomainclass.\n" +
                "?entity a ?eclass.?eclass rdfs:subClassOf* ?cpdomainclass.\n" +
                "?cp rdfs:range ?cprangeclass.\n" +
                "?bn a ?class.?class rdfs:subClassOf* ?cprangeclass.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "?dp rdfs:domain ?dpdomainclass.\n" +
                "?class rdfs:subClassOf* ?dpdomainclass.\n" +
                "?ce rdfs:label ?ceLabel.\n" +
                "?ce a ?cClass.\n" +
                "?underCondition rdfs:domain ?bnClass.\n" +
                "?class rdfs:subClassOf* ?bnClass.\n" +
                "?underCondition rdfs:range ?cClass.\n" +
                "}";

        List<String> result = knowledge.selectOneAsList(queryString,"class");
        return result;



//        StringBuilder cevliadClause = new StringBuilder();
//        for(Integer i=0;i<ces.size();++i){
//            cevliadClause.append(String.format("?op_%s rdfs:domain ?domain_op_%s.\n",i.toString(),i.toString()))
//                    .append(String.format("  {?bn rdf:type ?domain_op_%s.} UNION {?bn rdf:type ?bnClass. ?bnClass rdfs:subClassOf* ?domain_op_%s.}\n",i.toString(),i.toString()))
//                    .append(String.format("?op_%s rdfs:range ?range_op_%s.\n",i.toString(),i.toString()))
//                    .append(String.format("?ce_%s rdfs:label %s.\n",i.toString(),ces.get(i)))
//                    .append(String.format("{?ce_%s rdf:type ?range_op_%s.} UNION {?ce_%s rdf:type ?Class_ce_%s. ?Class_ce_%s rdfs:subClassOf* ?range_op_%s.}\n",i.toString(),i.toString(),i.toString(),i.toString(),i.toString(),i.toString()));
//        }
//        String queryString = "select distinct ?clazz where {\n" +
//                String.format("    ?object rdfs:label ?clazz.\n",object) +
//                "    ?object rdfs:domain ?clazz.\n" +
//                String.format("    ?entity rdfs:label '%s'.\n",entity) +
//                "    {?entity rdf:type ?clazz.} UNION {?entity rdf:type ?eClass. ?eClass rdfs:subClassOf* ?clazz.}\n" +
//                "?object rdfs:range ?clazz1. \n" +
//                "  {?bn rdf:type ?clazz1.} UNION {?bn rdf:type ?class1. ?class1 rdfs:subClassOf* ?clazz1.}\n" +
//                String.format("?dp rdfs:label '%s'.\n",datatype) +
//                "?dp rdfs:domain ?clazz2.\n" +
//                "{?bn rdf:type ?clazz2.} UNION {?bn rdf:type ?class2. ?class2 rdfs:subClassOf* ?Clazz2.}\n" +
//                cevliadClause.toString() +
//                "}";
//
//        List<String> result = knowledge.selectOneAsList(queryString,"clazz");
//        return result;
    }



    public List<BNAndDatatypeAndValue> queryDatatypeOfComplexPropertyUnderConditions(String entity, String complex,List<String> ces) {
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'.\n",i.toString(),i.toString(),i.toString(),ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?datatypeLabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity ?cp ?bn . ?cp rdfs:label '%s'.\n", complex) +
                "?datatype rdfs:label ?datatypeLabel ; rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(b.value("datatypeLabel"), b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }


    public List<BNAndDatatypeAndValue> queryDatatypeOfComplexPropertyAndDataypeUnderConditions(String entity, String complex,String datatype,List<String> ces) {
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label %s.\n",i.toString(),i.toString(),i.toString(),ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                String.format("?entity ?cp ?bn . ?cp rdfs:label '%s'.\n", complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype)+
                cesclause.toString() +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValue(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }


    public List<EntityAndBNAndDatatypeAndValue> queryDatatypeOfComplexPropertyAndDataypeUnderConditions(String complex,String datatype,List<String> ces) {
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'.?op_%s a <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }
        String queryString = "SELECT DISTINCT ?entity ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity ?cp ?bn . ?cp rdfs:label '%s'.\n", complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype)+
                cesclause.toString()+
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new EntityAndBNAndDatatypeAndValue(b.value("entity"),
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }

    public List<BNAndDatatypeAndValueAndConditions> queryBNAndDatatypewithEntityAndDatatypeUnderConditions(String entity,String datatype,Map<String,String> cpces){
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }
        String queryString1 = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                    String.format("?entity rdfs:label '%s' .\n", entity) +
                    "?entity ?cp ?bn .\n" +
                    "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                    "?bn ?datatype ?value .\n" +
                    String.format("?datatype rdfs:label '%s'.\n",datatype) +
                    "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                    cesclause.toString() +
                    "}\n";
            Map<String,String> conds = new HashMap<>();
            for(Map.Entry<String,String> e: cpces.entrySet()) {
                conds.put(e.getKey(), e.getValue());
            }
            List<BNAndDatatypeAndValueAndConditions> result =  queryAndCheckStatus(queryString1, b -> new BNAndDatatypeAndValueAndConditions(
                            new BlankNode(b.value("bn"), b.value("bnlabel")),
                            new DatatypeAndValue(datatype, b.value("value")),
                            conds),
                    instanceEnabled("bn"),
                    propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                    .stream().distinct().collect(Collectors.toList());
            return result;

    }


    public List<BNAndDatatypeAndValueAndConditions> queryBNAndDatatypewithEntityAndDatatypeUnderConditions(String entity,String complex,String datatype,Map<String,String> cpces){
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }
        String queryString1 = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?entity ?cp ?bn .\n" +
                String.format("?cp rdfs:label '%s.\n'",complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "}\n";
        Map<String,String> conds = new HashMap<>();
        for(Map.Entry<String,String> e: cpces.entrySet()) {
            conds.put(e.getKey(), e.getValue());
        }
        List<BNAndDatatypeAndValueAndConditions> result =  queryAndCheckStatus(queryString1, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value")),
                        conds),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        return result;

    }


    public List<BNAndDatatypeAndValueAndConditions> queryRestCondsWithEntityAndDatatypeUnderConditions(String entity,String datatype,Map<String, String> cpces){
        List<String> ces = new ArrayList<>();
        if(cpces != null)
            ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        List<String> ceswithquotationmark = new ArrayList<>();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
            ceswithquotationmark.add(String.format("'%s'",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value ?condition_label ?conditionClassLabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?entity ?cp ?bn .\n" +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "?bn ?op ?condition." +
                "?condition rdfs:label ?condition_label." +
                "?condition a ?conditionClass. " +
                "?conditionClass rdfs:label ?conditionClassLabel." +
                "?op a <http://hual.ai/new_standard#ObjectProperty>." +
                String.format("FILTER (?condition_label NOT IN (%s))",String.join(",",ceswithquotationmark)) +
                "}\n";

        List<BNAndDatatypeAndValueAndConditions> tmp = queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value")),
                        new HashMap<String,String>(){{put(b.value("condition_label"),b.value("conditionClassLabel"));}}),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        Map<String,List<BNAndDatatypeAndValueAndConditions>> map = tmp.stream().collect(Collectors.groupingBy(x -> x.getBn().getIri(),Collectors.toList()));
        List<BNAndDatatypeAndValueAndConditions> result = new ArrayList<>();
        for(Map.Entry<String,List<BNAndDatatypeAndValueAndConditions>> entry: map.entrySet()){
            BNAndDatatypeAndValueAndConditions ele = null;
            if(entry.getValue().size() > 0){
                ele = new BNAndDatatypeAndValueAndConditions(entry.getValue().get(0).getBn(),
                        entry.getValue().get(0).getDatatypeAndValue(),
                        null);
                Map<String,String> conditions = new HashMap<>();
                for(BNAndDatatypeAndValueAndConditions b : entry.getValue()){
                    for(Map.Entry<String,String> e:b.getConditions().entrySet()){
                        if( e != null && e.getKey() != null  && e.getKey().trim().length() != 0 && ! conditions.containsKey(e.getKey())){
                            conditions.put(e.getKey(),e.getValue());
                        }
                    }
                }

                ele.setConditions(conditions);
            }
            if(ele != null)
                result.add(ele);
        }
        return result;
    }


    public List<BNAndDatatypeAndValueAndConditions> queryBNAndDatatypewithEntityAndDatatype(String entity,String datatype){
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value ?condition_label ?conditionClassLabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?entity ?cp ?bn .\n" +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "optional {?bn ?op ?condition.?condition rdfs:label ?condition_label.?condition a ?conditionClass. ?conditionClass rdfs:label ?conditionClassLabel.?op a <http://hual.ai/new_standard#ObjectProperty>.}\n" +
                "}";
        logger.debug("SPARQL {}", queryString);
        List<BNAndDatatypeAndValueAndConditions> tmp = queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value")),
                        new HashMap<String,String>(){{put(b.value("condition_label"),b.value("conditionClassLabel"));}}),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        Map<String,List<BNAndDatatypeAndValueAndConditions>> map = tmp.stream().collect(Collectors.groupingBy(x -> x.getBn().getIri(),Collectors.toList()));
        List<BNAndDatatypeAndValueAndConditions> result = new ArrayList<>();
        for(Map.Entry<String,List<BNAndDatatypeAndValueAndConditions>> entry: map.entrySet()){
            BNAndDatatypeAndValueAndConditions ele = null;
            if(entry.getValue().size() > 0){
                ele = new BNAndDatatypeAndValueAndConditions(entry.getValue().get(0).getBn(),
                        entry.getValue().get(0).getDatatypeAndValue(),
                        null);
                Map<String,String> conditions = new HashMap<>();
                for(BNAndDatatypeAndValueAndConditions b : entry.getValue()){
                    for(Map.Entry<String,String> e:b.getConditions().entrySet()){
                        if( e != null && e.getKey() != null  && e.getKey().trim().length() != 0 && ! conditions.containsKey(e.getKey())){
                            conditions.put(e.getKey(),e.getValue());
                        }
                    }
                }

                ele.setConditions(conditions);
            }
            if(ele != null)
                result.add(ele);
        }
        return result;

    }

    public List<BNAndDatatypeAndValueAndConditions> queryBNAndDatatypewithEntityAndComplexAndDatatypeUnderConditions(String entity,String complex,String datatype,Map<String, String> cpces){
        List<String> ces = new ArrayList<>();
        if(cpces != null)
            ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }

            String queryString1 = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value  WHERE {\n" +
                    String.format("?entity rdfs:label '%s' .\n", entity) +
                    "?entity ?cp ?bn .\n" +
                    String.format("?cp rdfs:label '%s'.\n",complex) +
                    "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                    "?bn ?datatype ?value .\n" +
                    String.format("?datatype rdfs:label '%s'.\n",datatype) +
                    "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                    cesclause.toString() +
                    "}\n";
            Map<String,String> conds = new HashMap<>();
            for(Map.Entry<String,String> e: cpces.entrySet()){
                conds.put(e.getKey(),e.getValue());
            }
            List<BNAndDatatypeAndValueAndConditions> result =  queryAndCheckStatus(queryString1, b -> new BNAndDatatypeAndValueAndConditions(
                            new BlankNode(b.value("bn"), b.value("bnlabel")),
                            new DatatypeAndValue(datatype, b.value("value")),
                            conds),
                    instanceEnabled("bn"),
                    propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                    .stream().distinct().collect(Collectors.toList());
            return result;
    }


    public List<BNAndDatatypeAndValueAndConditions> queryRestCondsWithEntityAndComplexAndDatatypeUnderConditions(String entity,String complex,String datatype,Map<String, String> cpces){
        List<String> ces = new ArrayList<>();
        if(cpces != null)
            ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        List<String> ceswithquotationmark = new ArrayList<>();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
            ceswithquotationmark.add(String.format("'%s'",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?value ?condition_label ?conditionClassLabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?entity ?cp ?bn .\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype) +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "optional {?bn ?op ?condition.?condition rdfs:label ?condition_label.?condition a ?conditionClass. ?conditionClass rdfs:label ?conditionClassLabel.?op a <http://hual.ai/new_standard#ObjectProperty>.}\n" +
                String.format("FILTER (?condition_label NOT IN (%s))",String.join(",",ceswithquotationmark)) +
                "}";

        List<BNAndDatatypeAndValueAndConditions> tmp = queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value")),
                        new HashMap<String,String>(){{put(b.value("condition_label"),b.value("conditionClassLabel"));}}),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        Map<String,List<BNAndDatatypeAndValueAndConditions>> map = tmp.stream().collect(Collectors.groupingBy(x -> x.getBn().getIri(),Collectors.toList()));
        List<BNAndDatatypeAndValueAndConditions> result = new ArrayList<>();
        for(Map.Entry<String,List<BNAndDatatypeAndValueAndConditions>> entry: map.entrySet()){
            BNAndDatatypeAndValueAndConditions ele = null;
            if(entry.getValue().size() > 0){
                ele = new BNAndDatatypeAndValueAndConditions(entry.getValue().get(0).getBn(),
                        entry.getValue().get(0).getDatatypeAndValue(),
                        null);
                Map<String,String> conditions = new HashMap<>();
                for(BNAndDatatypeAndValueAndConditions b : entry.getValue()){
                    for(Map.Entry<String,String> e:b.getConditions().entrySet()){
                        if(e != null && e.getKey() != null  && e.getKey().trim().length() != 0 && ! conditions.containsKey(e.getKey()) ){
                            conditions.put(e.getKey(),e.getValue());
                        }
                    }
                }

                ele.setConditions(conditions);
            }
            if(ele != null)
                result.add(ele);
        }
        return result;
    }


    public List<BNAndDatatypeAndValueAndConditions> queryBNAndDatatypewithEntityAndComplexUnderConditions(String entity,String complex,Map<String, String> cpces){
        List<String> ces = new ArrayList<>();
        if(cpces != null)
            ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
        }

        String queryString1 = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?datatypeLabel ?value  WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?entity ?cp ?bn .\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?datatype rdfs:label ?datatypeLabel ; rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "}\n";
        Map<String,String> conds = new HashMap<>();
        for(Map.Entry<String,String> e: cpces.entrySet()){
            conds.put(e.getKey(),e.getValue());
        }
        List<BNAndDatatypeAndValueAndConditions> result =  queryAndCheckStatus(queryString1, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(b.value("datatypeLabel"), b.value("value")),
                        conds),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        return result;
    }

    public List<BNAndDatatypeAndValueAndConditions> queryRestCondsWithEntityAndComplexUnderConditions(String entity,String complex,Map<String, String> cpces){
        List<String> ces = new ArrayList<>();
        if(cpces != null)
            ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder cesclause = new StringBuilder();
        List<String> ceswithquotationmark = new ArrayList<>();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'. ?op_%s rdf:type  <http://hual.ai/new_standard#ObjectProperty>.\n",i.toString(),i.toString(),i.toString(),ces.get(i),i.toString()));
            ceswithquotationmark.add(String.format("'%s'",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?bn ?bnlabel ?datatype ?datatypeLabel ?value ?condition_label ?conditionClassLabel WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?entity ?cp ?bn .\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?datatype rdfs:label ?datatypeLabel ; rdf:type ?type. ?type rdfs:subClassOf owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                cesclause.toString() +
                "optional { ?bn ?op ?condition.?condition rdfs:label ?condition_label.?condition a ?conditionClass. ?conditionClass rdfs:label ?conditionClassLabel.?op a <http://hual.ai/new_standard#ObjectProperty>.}\n" +
                String.format("FILTER (?condition_label NOT IN (%s))",String.join(",",ceswithquotationmark)) +
                "}";

        List<BNAndDatatypeAndValueAndConditions> tmp = queryAndCheckStatus(queryString, b -> new BNAndDatatypeAndValueAndConditions(
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(b.value("datatypeLabel"), b.value("value")),
                        new HashMap<String,String>(){{put(b.value("condition_label"),b.value("conditionClassLabel"));}}),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
        Map<String,List<BNAndDatatypeAndValueAndConditions>> map = tmp.stream().collect(Collectors.groupingBy(x -> x.getBn().getIri(),Collectors.toList()));
        List<BNAndDatatypeAndValueAndConditions> result = new ArrayList<>();
        for(Map.Entry<String,List<BNAndDatatypeAndValueAndConditions>> entry: map.entrySet()){
            BNAndDatatypeAndValueAndConditions ele = null;
            if(entry.getValue().size() > 0){
                ele = new BNAndDatatypeAndValueAndConditions(entry.getValue().get(0).getBn(),
                        entry.getValue().get(0).getDatatypeAndValue(),
                        null);
                Map<String,String> conditions = new HashMap<>();
                for(BNAndDatatypeAndValueAndConditions b : entry.getValue()){
                    for(Map.Entry<String,String> e:b.getConditions().entrySet()){
                        if( e != null && e.getKey() != null  && e.getKey().trim().length() != 0 && ! conditions.containsKey(e.getKey()) ){
                            conditions.put(e.getKey(),e.getValue());
                        }
                    }
                }

                ele.setConditions(conditions);
            }
            if(ele != null)
                result.add(ele);
        }
        return result;
    }


    public List<String> checkEntityAndCpAndDatatype(String entity, String datatype) {
        String queryString = "SELECT DISTINCT ?class WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?cp a <http://hual.ai/new_standard#ComplexProperty>." +
                "?entity a ?eclass." +
                "?eclass rdfs:subClassOf* ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                "?class rdfs:subClassOf ?cprange.\n" +
                "?bn a ?class.\n" +
                String.format("?dp rdfs:domain ?clazz. ?dp rdfs:label '%s'.\n", datatype) +
                "?class rdfs:subClassOf* ?clazz.\n" +
                "}";
        return knowledge.selectOneAsList(queryString,"class");
    }

    public List<String> checkEntityAndCpAndDatatype(String entity, String datatype,String complex) {
        String queryString = "SELECT DISTINCT ?class WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n", entity) +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?entity a/rdfs:subClassOf* ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                "?class rdfs:subClassOf* ?cprange.\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?bn a ?class.\n" +
                String.format("?dp rdfs:domain ?clazz. ?dp rdfs:label '%s'.\n", datatype) +
                "?class rdfs:subClassOf* ?clazz.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("class"));
    }


    public List<String> checkEntityAndCpAndDatatypeUnderConditions(String entity,String datatype,List<String> ces){
        StringBuilder ceLabels = new StringBuilder();
        for(Integer i=0;i<ces.size();++i){
            ceLabels.append(String.format("'%s' ",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?class ?ceLabel WHERE {\n" +
                "VALUES ?ceLabel {" + ceLabels.toString() + "}\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                "?entity a/rdfs:subClassOf* ?cpdomain.\n" +
                "?class rdfs:subClassof* ?cprange.\n" +
                "?bn a ?class.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "?ce rdfs:label ?ceLabel.\n" +
                "?ce a ?cClass.\n" +
                "?dp rdfs:domain ?dpdomain.\n" +
                "?class rdfs:subClassOf* ?dpdomain.\n" +
                "?underconditon rdfs:domain ?underconditiondomain.\n" +
                "?underconditon rdfs:range ?underconditionrange.\n" +
                "?class rdfs:subClassOf* ?underconditiondomain.\n" +
                "?cClass rdfs:subClassOf* ?underconditionrange.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("class"));
    }


    public List<String> checkEntityAndCpUnderConditions(String entity,String complex,List<String> ces){
        StringBuilder ceLabels = new StringBuilder();
        for(Integer i=0;i<ces.size();++i){
            ceLabels.append(String.format("'%s' ",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?class ?ceLabel WHERE {\n" +
                "VALUES ?ceLabel {" + ceLabels.toString() + "}\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?entity a/rdfs:subClassOf* ?cpdomain.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("class"));
    }


    public List<String> checkEntityAndCpAndDatatypeUnderConditions(String entity,String complex,String datatype,List<String> ces){
        StringBuilder ceLabels = new StringBuilder();
        for(Integer i=0;i<ces.size();++i){
            ceLabels.append(String.format("'%s' ",ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?class ?ceLabel WHERE {\n" +
                "VALUES ?ceLabel {" + ceLabels.toString() + "}\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?cp rdfs:domain ?cpdomain.\n" +
                "?cp rdfs:range ?cprange.\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?entity a/rdfs:subClassOf* ?cpdomain.\n" +
                "?class rdfs:subClassof* ?cprange.\n" +
                "?bn a ?class.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "?ce rdfs:label ?ceLabel.\n" +
                "?ce a ?cClass.\n" +
                "?dp rdfs:domain ?dpdomain.\n" +
                "?class rdfs:subClassOf* ?dpdomain.\n" +
                "?underconditon rdfs:domain ?underconditiondomain.\n" +
                "?underconditon rdfs:range ?underconditionrange.\n" +
                "?class rdfs:subClassOf* ?underconditiondomain.\n" +
                "?cClass rdfs:subClassOf* ?underconditionrange.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("class"));
    }


    public List<Pair<String,String>> queryEntitiesPairWithYshape(List<String> entities){
        StringBuilder entityPairValues = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            String s1Label = entities.get(i);
            for (int j = i + 1; j < entities.size(); j++) {
                String s2Label = entities.get(j);
                entityPairValues.append("('").append(s1Label).append("' '").append(s2Label).append("')\n");
            }
        }
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label WHERE{\n" +
                "VALUES (?s1Label ?s2Label) {\n" +
                entityPairValues +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n"  +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                "}";

        List<Pair<String,String>> ret = new ArrayList<>();
        SelectResult result1 = knowledge.select(queryString);
        List<Binding> bindings = result1.getBindings();

        for(Binding binding:bindings){
                ret.add(new Pair<>(binding.value("s1Label"),binding.value("s2Label")));
        }

        return ret;
    }


    public List<Pair<String,String>> checkYshapeEntitiesPairsAndDpReturnIRIs(List<Pair<String,String>> entitiesPairs,String datatype){
        StringBuilder entityPairValues = new StringBuilder();
        for(Pair<String,String> ele:entitiesPairs){
            entityPairValues.append(String.format("('%s' '%s')\n",ele.getKey(),ele.getValue()));
        }
        String queryString = "SELECT DISTINCT ?s1 ?s2 WHERE{\n" +
                "VALUES (?s1Label ?s2Label) {\n" +
                entityPairValues +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "?dp rdfs:domain ?dpClass.\n" +
                "?bn rdf:type ?bnClass.\n" +
                "?bnClass rdfs:subClassOf* ?dpClass.\n" +
                "}";

        List<Pair<String,String>> ret = new ArrayList<>();
        SelectResult result1 = knowledge.select(queryString);
        List<Binding> bindings = result1.getBindings();

            for(Binding binding:bindings){
                ret.add(new Pair<>(binding.value("s1"),binding.value("s2")));
            }

        return ret;
    }


    public List<YshapeBNAndDPAndValue> queryYshapeBNLabelsAndDatatypes(List<Pair<String,String>> entitiesPairs,String datatype) {
        StringBuilder entityPairValues = new StringBuilder();
        for(Pair<String,String> ele:entitiesPairs){
            entityPairValues.append(String.format("(<%s> <%s>)\n",ele.getKey(),ele.getValue()));
        }
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label ?yp ?bn ?bnlabel ?dp ?value WHERE{\n" +
                "VALUES (?s1 ?s2) {\n" +
                entityPairValues.toString() +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n",datatype) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), ""), datatype,b.value("value")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    public List<Pair<String,String>>  checkValidationOfEYshapeEntitiesAndDpAndCESReturnIRI(List<Pair<String,String>> entitiesPairs,String datatype,Map<String, String> cpces) {
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        StringBuilder entitiesPairsAndcelabelclause = new StringBuilder();
        for (Pair<String, String> entitiesPair : entitiesPairs) {
            for (Integer i = 0; i < ces.size(); ++i) {
                entitiesPairsAndcelabelclause.append(String.format("(<%s> <%s> '%s')\n", entitiesPair.getKey(), entitiesPair.getValue(),ces.get(i)));
            }
        }
        String queryString = "SELECT DISTINCT ?s1 ?s2 WHERE{\n" +
                "VALUES (?s1 ?s2 ?celabel) {\n" +
                entitiesPairsAndcelabelclause.toString() +
                "}\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n", datatype) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                "?op rdfs:domain ?opdomainclass.\n" +
                "?bn rdf:type/rdfs:subClassOf* ?opdomainclass.\n" +
                "?op rdfs:range ?oprangeclass.\n" +
                "?ce rdf:type/rdfs:subClassOf* ?oprangeclass.\n" +
                "?ce rdfs:label ?celabel.\n" +
                "}";
        List<Pair<String,String>> ret = new ArrayList<>();
        SelectResult result1 = knowledge.select(queryString);
        List<Binding> bindings = result1.getBindings();

        for(Binding binding:bindings){
            ret.add(new Pair<>(binding.value("s1"),binding.value("s2")));
        }

        return ret;
    }

    public List<YshapeBNAndDPAndValue> queryYshapeBNLabelsAndDatatypesUnderConditions(List<Pair<String,String>> entitiesPairs,String datatype,List<String> ces){
        StringBuilder entityPairValues = new StringBuilder();
        for(Pair<String,String> ele:entitiesPairs){
            entityPairValues.append(String.format("(<%s> <%s>)\n",ele.getKey(),ele.getValue()));
        }
        StringBuilder cesclause = new StringBuilder();
        for(Integer i = 0; i<ces.size();++i){
            cesclause.append(String.format("?bn ?op_%s ?ce_%s.?ce_%s rdfs:label '%s'.\n",i.toString(),i.toString(),i.toString(),ces.get(i)));
        }
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label ?yp ?bn  ?dp ?value WHERE{\n" +
                "VALUES (?s1 ?s2) {\n" +
                entityPairValues.toString() +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n",datatype) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                cesclause.toString() +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), ""), datatype,b.value("value")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    public List<String> checkCpAndDp(String complex,String datatype) {
        String queryString = "SELECT DISTINCT ?clazz WHERE {\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?cp rdfs:range ?range_cp.\n" +
                "?bn a ?bnclass.\n" +
                "?bnclass rdfs:subClassOf* ?range_cp.\n" +
                String.format("?dp rdfs:domain ?clazz. ?dp rdfs:label '%s'.\n", datatype) +
                "?bnclass rdfs:subClassOf* ?clazz.\n" +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"clazz");
        return result;
    }

    public List<String> checkCpAndDpAndCES(String complex,String datatype,List<String> ces) {
        StringBuilder cevliadClause = new StringBuilder();
        for(Integer i=0;i<ces.size();++i) {
            cevliadClause.append(String.format("'%s' ", ces.get(i)));
        }

        String queryString = "SELECT DISTINCT ?clazz WHERE {\n" +
                "VALUES ?ce { " +
                cevliadClause.toString() +
                "}\n" +
                String.format("?cp rdfs:label '%s'.\n",complex) +
                "?cp rdfs:range ?range_cp.\n" +
                "?bn a ?bnclass.\n" +
                "?bnclass rdfs:subClassOf* ?range_cp.\n" +
                String.format("?dp rdfs:domain ?clazz. ?dp rdfs:label '%s'.\n", datatype) +
                "?bnclass rdfs:subClassOf* ?clazz.\n" +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"clazz");
        return result;
    }

    public List<EntityAndBNAndDatatypeAndValue> queryDatatypeOfComplexPropertyAndDataype(String complex,String datatype) {

        String queryString = "SELECT DISTINCT ?entity ?bn ?bnlabel ?datatype ?value WHERE {\n" +
                String.format("?entity ?cp ?bn . ?cp rdfs:label '%s'.\n", complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?bn ?datatype ?value .\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype)+
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new EntityAndBNAndDatatypeAndValue(b.value("entity"),
                        new BlankNode(b.value("bn"), b.value("bnlabel")),
                        new DatatypeAndValue(datatype, b.value("value"))),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("datatype")))
                .stream().distinct().collect(Collectors.toList());
    }

    public List<Pair<String,String>> checkEntitiesAndCp(List<String> entities,String complex){
        StringBuilder entityValues = new StringBuilder();
        for(String entity:entities){
            entityValues.append(String.format("'%s' ",entity));
        }

        String queryString = "SELECT DISTINCT ?elabel ?class WHERE {\n" +
                "VALUES ?elabel {" +
                entityValues.toString() +
                "}\n" +
                "?entity a ?class.\n" +
                "?entity rdfs:label ?elabel.\n"+
                "?cp rdfs:domain ?domain_cp.\n" +
                "?class rdfs:subClassOf* ?domain_cp.\n" +
                String.format("?cp a/rdfs:subClassOf* <%s>.\n",COMPLEX_PROPERTY) +
                String.format("?cp rdfs:label '%s'.\n", complex) +
                "}";
        SelectResult result = knowledge.select(queryString);
        List<Binding> bindings = result.getBindings();
        List<Pair<String,String>> ret = new ArrayList<>();
        for(Binding binding:bindings){
            ret.add(new Pair<>(binding.value("elabel"),binding.value("class")));
        }
        return ret;
    }


    public List<Pair<String,String>> checkEntitiesAndDp(List<String> entities,String datatype){
        StringBuilder entityValues = new StringBuilder();
        for(String entity:entities){
            entityValues.append(String.format("'%s' ",entity));
        }

        String queryString = "SELECT DISTINCT ?elabel ?class WHERE {\n" +
                "VALUES ?elabel {" +
                entityValues.toString() +
                "}\n" +
                "?entity rdfs:label ?elabel.\n"+
                "?entity a ?class.\n" +
                "{\n" +
                "?dp rdfs:domain ?domain_dp.\n" +
                "?class rdfs:subClassOf* ?domain_dp.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                String.format("?dp rdfs:label '%s'.\n", datatype) +
                "} UNION {\n" +
                "?cp rdfs:domain ?domain_cp.\n" +
                "?class rdfs:subClassOf* ?domain_cp.\n" +
                String.format("?cp a/rdfs:subClassOf* <%s>.\n",COMPLEX_PROPERTY) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?cp rdfs:range ?range_cp.\n" +
                "?bn a ?bnclass .\n" +
                "?bnclass rdfs:subClassOf* ?range_cp.\n" +
                "?datatype rdfs:domain ?domain_dp.\n" +
                "?bnclass rdfs:subClassOf* ?domain_dp.\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype)+
                "}\n" +
                "}";
        SelectResult result = knowledge.select(queryString);
        List<Binding> bindings = result.getBindings();
        List<Pair<String,String>> ret = new ArrayList<>();
        for(Binding binding:bindings){
            ret.add(new Pair<>(binding.value("elabel"),binding.value("class")));
        }
        return ret;
    }

    public List<Pair<String,String>> checkEntitiesAndCpAndDp(List<String> entities,String complex,String datatype){
        StringBuilder entityValues = new StringBuilder();
        for(String entity:entities){
            entityValues.append(String.format("'%s' ",entity));
        }

        String queryString = "SELECT DISTINCT ?elabel ?class WHERE {\n" +
                "VALUES ?elabel {" +
                entityValues.toString() +
                "}\n" +
                "?entity a ?class.\n" +
                "?entity rdfs:label ?elabel." +
                "?cp rdfs:domain ?domain_cp.\n" +
                "?class rdfs:subClassOf* ?domain_cp.\n" +
                String.format("?cp rdfs:label '%s'.\n", complex) +
                "?datatype rdf:type ?type. ?type rdfs:subClassOf* owl:DatatypeProperty .\n" +
                "?cp rdfs:range ?range_cp.\n" +
                "?bn a ?bnclass .\n" +
                "?bnclass rdfs:subClassOf* ?range_cp.\n" +
                "?datatype rdfs:domain ?domain_dp.\n" +
                "?bnclass rdfs:subClassOf* ?domain_dp.\n" +
                String.format("?datatype rdfs:label '%s'.\n",datatype)+
                "OPTIONAL { ?bn <http://hual.ai/special#bnlabel> ?bnlabel. }\n" +
                "}";
        SelectResult result = knowledge.select(queryString);
        List<Binding> bindings = result.getBindings();
        List<Pair<String,String>> ret = new ArrayList<>();
        for(Binding binding:bindings){
            ret.add(new Pair<>(binding.value("elabel"),binding.value("class")));
        }
        return ret;
    }


    public String queryCpWithEntityAndBN(String entity,String bn){
        String queryString = "SELECT DISTINCT ?cp ?cplabel WHERE {\n" +
                String.format("?entity ?cp <%s>.\n",bn) +
                String.format("?entity rdfs:label '%s'.\n",entity) +
                "optional {?cp rdfs:label ?cplabel.}\n" +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"cplabel");
        if(result.size() > 0)
            return result.get(0);
        return null;
    }


    public List<YshapeBNAndDPAndValue> queryYshapeBNAndDPWithEntitiesPairs(List<Pair<String, String>> entitiesPairs){
        StringBuilder entityPairValues = new StringBuilder();
        for(Pair<String,String> ele:entitiesPairs){
            entityPairValues.append(String.format("(<%s> <%s>)\n",ele.getKey(),ele.getValue()));
        }
        String queryString = "SELECT DISTINCT ?s1Label ?s2Label ?yp ?bn ?dp ?dpLabel ?value WHERE{\n" +
                "VALUES (?s1 ?s2) {\n" +
                entityPairValues.toString() +
                "}\n" +
                "?s1 rdfs:label ?s1Label . ?s2 rdfs:label ?s2Label.\n" +
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                "?bn ?dp ?value . \n" +
                "?dp rdfs:label ?dpLabel.\n" +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>  .\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(b.value("s1Label"), b.value("s2Label"), b.value("yp"),
                        new BlankNode(b.value("bn"), ""), b.value("dpLabel"),b.value("value")),
                instanceEnabled("bn"),
                propertyEnabled(b -> b.value("bn"), b -> b.value("dp")));
    }

    public String queryLabelWithIRI(String iri){
        String queryString = "SELECT DISTINCT ?label WHERE{\n" +
                String.format("<%s> rdfs:label ?label.\n",iri) +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"label");
        if (result == null || result.size() == 0){
            // actually iri is a label
            return iri;
        }
        return result.get(0);
    }

    public List<String> queryDatatypesWithBNIRI(List<String> bniris){
        Set<String> result_set = new HashSet<>();
        for(String bniri:bniris){
            String queryString = "SELECT DISTINCT ?dpLabel WHERE{\n" +
                    String.format("<%s> ?dp ?value . \n",bniri) +
                    "?dp rdfs:label ?dpLabel.\n" +
                    "?dp a/rdfs:subClassOf owl:DatatypeProperty  .\n" +
                    "}";
            result_set.addAll(knowledge.selectOneAsList(queryString,"dpLabel"));
        }
        return result_set.stream().collect(Collectors.toList());
    }

    public List<String> queryValuewithEntityAndDatatype(String entity,String datatype){
        String queryString = "SELECT DISTINCT  ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s' .\n",entity) +
                "?entity ?dp ?value.\n" +
                String.format("?dp rdfs:label '%s'.\n" ,datatype)+
                "\n" +
                "}";
        logger.debug("query: {}",queryString);
        return knowledge.selectOneAsList(queryString,"value");
    }

    public List<String> querydatatypeofBNWithCp(String entity,String cpiri){
        String queryString = "SELECT DISTINCT  ?dpLabel WHERE {\n" +
                String.format("?entity <%s> ?bn. ?entity rdfs:label '%s'.\n",cpiri,entity) +
                "?bn ?dp ?value.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                "}";
        logger.debug("query: {}",queryString);
        return knowledge.selectOneAsList(queryString,"dpLabel");
    }

    public List<EntityAndCpAndDatatypeAndValue> queryEntityAndCpAndDatatypeAndValueWithCp(String entity, ObjectProperty op) {
        String queryString = "SELECT DISTINCT ?entity ?dpLabel ?value WHERE {\n" +
                String.format("?entity rdfs:label '%s'.\n", entity) +
                String.format("?entity <%s> ?bn.\n", op.getUri()) +
                "?bn ?dp ?value.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new EntityAndCpAndDatatypeAndValue(b.value("entity"),
                op.getUri(),
                new DatatypeAndValue(b.value("dpLabel"),b.value("value"))));
    }

    public List<String> queryParentClass(String entity) {
        String queryString = "SELECT DISTINCT ?parentLabel WHERE {\n" +
                String.format("?entity rdfs:label '%s'.\n", entity) +
                "?entity a ?parent.\n" +
                "?parent rdfs:label ?parentLabel.\n" +
                "}";
        logger.debug("query: {}", queryString);
        return knowledge.selectOneAsList(queryString, "parentLabel");
    }

    public List<String> queryAllProperties(List<String> entities) {
        StringBuilder entityValues = new StringBuilder();
        for(String entity:entities){
            entityValues.append(String.format("'%s' ",entity));
        }
        String queryDpString = "select distinct ?sLabel ?dpLabel\n" +
                "where{\n" +
                "VALUES ?sLabel {" +
                entityValues.toString() +
                "}\n" +
                "?s rdfs:label ?sLabel.\n"+
                "?s a ?class.\n" +
                "?class rdfs:subClassOf* owl:Thing.\n" +
                "?s ?dp ?value.\n" +
                "?dp a ?pType.\n" +
                "?pType rdfs:subClassOf owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                "}";

        String queryOpString = "select distinct ?sLabel ?cpLabel ?dpLabel\n" +
                "where{\n" +
                "VALUES ?sLabel {" +
                entityValues.toString() +
                "}\n" +
                "?s rdfs:label ?sLabel.\n"+
                "?dp a ?pType.\n" +
                "?pType rdfs:subClassOf owl:DatatypeProperty.\n" +
                "?s ?cp ?bn.\n" +
                "?bn ?dp ?value.\n" +
                "?cp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "optional{?cp rdfs:label ?cpLabel.}\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                "}";
        logger.debug("SPARQL {}", queryDpString);
        List<String> combineQuerys = new ArrayList<>();
        for(Binding binding: knowledge.select(queryOpString).getBindings()){
            String entity = binding.value("sLabel");
            String cp = binding.value("cpLabel");
            String dp = binding.value("dpLabel");
            combineQuerys.add(String.format("%s%s的%s",entity,cp,dp));
        }
        for(Binding binding: knowledge.select(queryDpString).getBindings()){
            String entity = binding.value("sLabel");
            String dp = binding.value("dpLabel");
            combineQuerys.add(String.format("%s的%s",entity,dp));
        }

        return combineQuerys;
    }

    public ListMultimap<String,String> checkEntitiesAndCpAndDpUnderConditions(List<String> entities,String complex,String datatype,List<String> ces){
        ListMultimap<String,String> res = ArrayListMultimap.create();
        for(String entity:entities){
            List<String> tmp = checkEntityAndCpAndDatatypeUnderConditions(entity,complex,datatype,ces);
            if(tmp != null && tmp.size() != 0){
                for(String t: tmp){
                    res.put(entity,t);
                }
            }
        }
        return res;
    }

    public ListMultimap<String,String> checkEntitiesAndCpUnderConditions(List<String> entities,String complex,List<String> ces){
        ListMultimap<String,String> res = ArrayListMultimap.create();
        for(String entity:entities){
            List<String> tmp = checkEntityAndCpUnderConditions(entity,complex,ces);
            if(tmp != null && tmp.size() != 0){
                for(String t: tmp){
                    res.put(entity,t);
                }
            }
        }
        return res;
    }


    public List<String> queryCpSubProperties(String complex) {
        String queryString = "SELECT DISTINCT ?subPropertyLabel WHERE {\n" +
                String.format("?p rdfs:label '%s' .\n", complex) +
                "?subProperty rdfs:subPropertyOf ?p.\n"+
                "?subProperty rdfs:label ?subPropertyLabel.\n"+
                "}";
        return queryAndCheckStatus(queryString, b -> b.value("subPropertyLabel"));
    }

    public List<String> isEnumProperty(String datatype){
        String queryString = "SELECT DISTINCT ?p where {\n" +
                "?s ?p ?o.\n" +
                String.format("?p rdfs:label '%s' ; a <http://hual.ai/new_standard#EnumProperty>.\n",datatype) +
                "}";
        logger.debug("SPARQL {}", queryString);
        return knowledge.selectOneAsList(queryString,"p");
    }

    public List<EntityAndCpAndDatatypeAndValue> queryEntityAndCpAndDatatypeAndValueWithEntityAndDatatype(String entity,String datatype){
        String queryString = "select distinct ?entityLabel ?cp ?dpLabel ?value where {\n" +
                String.format("    values (?entityLabel ?dpLabel) { ('%s' '%s') }\n",entity,datatype) +
                "    ?dp rdfs:label ?dpLabel.\n" +
                "    {\n" +
                "        ?entity rdfs:label ?entityLabel.\n" +
                "        ?entity ?dp ?value.\n" +
                "    } UNION {\n" +
                "        ?entity rdfs:label ?entityLabel.\n" +
                "        ?entity ?cp ?bn.\n" +
                "        ?bn ?dp ?value.\n" +
                "    }\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new EntityAndCpAndDatatypeAndValue(b.value("entityLabel"),
                        b.value("cp"),
                        new DatatypeAndValue(b.value("dpLabel"),b.value("value"))));
    }


    public List<EntityAndCpAndDatatypeAndValue> queryEntityAndCpAndDatatypeAndValueWithClass(String clazz){
        String queryString = "select distinct ?entityLabel ?dpLabel ?value where {\n" +
                String.format("?entity a ?eclass. ?eclass rdfs:subClassOf* ?superClass. ?superClass rdfs:label '%s'.\n",clazz) +
                "    ?dp rdfs:label ?dpLabel.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "        ?entity rdfs:label ?entityLabel.\n" +
                "        ?entity ?dp ?value.\n" +
                "}";

        List<EntityAndCpAndDatatypeAndValue> result =  queryAndCheckStatus(queryString, b -> new EntityAndCpAndDatatypeAndValue(b.value("entityLabel"),
                "",
                new DatatypeAndValue(b.value("dpLabel"),b.value("value"))));

        String queryString1 = "select distinct ?entityLabel ?cp ?dpLabel ?value where {\n" +
                String.format("?entity a ?eclass. ?eclass rdfs:subClassOf* ?superClass. ?superClass rdfs:label '%s'.\n",clazz) +
                "    ?dp rdfs:label ?dpLabel.\n" +
                "    ?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "        ?entity rdfs:label ?entityLabel.\n" +
                "        ?entity ?cp ?bn.\n" +
                "        ?cp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "        ?bn ?dp ?value." +
                "}";
        result.addAll(queryAndCheckStatus(queryString, b -> new EntityAndCpAndDatatypeAndValue(b.value("entityLabel"),
                b.value("cp"),
                new DatatypeAndValue(b.value("dpLabel"),b.value("value")))));
        return result;
    }


    public String queryClass(String e){
        String queryString = "select distinct ?classLabel where {\n" +
                String.format("?s rdfs:label '%s'.\n",e) +
                "?s a ?class.\n" +
                "?class rdfs:label ?classLabel.\n" +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"classLabel");
        return result.size() > 0 ? result.get(0) : null;
    }


    public List<YshapeBNAndDPAndValue>  queryYshapeBNLabelsAndDatatypes(String entity,String bnuri) {
        String queryString = "select distinct ?s2Label ?yp ?dpLabel ?value where {\n" +
                String.format("?s1 rdfs:label '%s'.\n",entity) +
                "?s2 rdfs:label ?s2Label.\n" +
                String.format("?s1 ?yp <%s>.\n",bnuri) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                String.format("?s2 ?yp <%s>.\n",bnuri) +
                String.format("<%s> ?dp ?value.\n",bnuri) +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                String.format("filter (?s2Label != '%s')\n",entity) +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(entity, b.value("s2Label"), b.value("yp"),
                        new BlankNode(bnuri, ""), b.value("dpLabel"),b.value("value")));
    }

    public List<YshapeBNAndDPAndValue>  queryYshapeBNLabelsAndDatatypes(String entity,String bnuri,String otherentityclass) {
        // 限制YShape另一个otherentity的class
        String queryString = "select distinct ?s2Label ?yp ?dpLabel ?value where {\n" +
                String.format("?s1 rdfs:label '%s'.\n",entity) +
                "?s2 rdfs:label ?s2Label.\n" +
                String.format("?s2 a/rdfs:label '%s'.\n",otherentityclass) +
                String.format("?s1 ?yp <%s>.\n",bnuri) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                String.format("?s2 ?yp <%s>.\n",bnuri) +
                String.format("<%s> ?dp ?value.\n",bnuri) +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                String.format("filter (?s2Label != '%s')\n",entity) +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(entity, b.value("s2Label"), b.value("yp"),
                new BlankNode(bnuri, ""), b.value("dpLabel"),b.value("value")));
    }

    public List<String> queryDatatypesWithEntity(String entity){
        String queryString = "SELECT DISTINCT ?datatype where {\n" +
                String.format("?entity rdfs:label '%s'.\n", entity )+
                "?entity ?dp ?value.\n" +
                "?dp rdfs:label ?datatype.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "}";
        List<String> result = knowledge.selectOneAsList(queryString,"datatype");
        return result;
    }

    public List<String> CheckMedicalUnderwrittingEntity(String entity){
        String queryString = "select distinct ?e where {\n" +
                "values ?classLabel {'疾病' '险种' '特殊人群'}\n" +
                String.format("?e rdfs:label '%s'.\n",entity) +
                "?e a/rdfs:label ?classLabel.\n" +
                "}";
        return knowledge.selectOneAsList(queryString,"e");
    }

    public List<YshapeBNAndDPAndValue> queryOtherMedicalUnderwrittingWithEntityAndtypeofinsurance(String entity,String typeofinsurance){
        // entity和除typeofinsurance以外的险种的Yshape
        String queryString = "select distinct ?s2Label ?bn ?yp ?dpLabel ?value where {\n" +
                "values ?s1ClassLabel { '特殊人群' '疾病' } " +
                String.format("?s1 rdfs:label '%s'.\n",entity) +
                "?s1 a/rdfs:label ?s1ClassLabel.\n" +
                "?s2 rdfs:label ?s2Label.\n" +
                "?s2 a/rdfs:label '险种'.\n" +
                "?s1 ?yp ?bn.\n" +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "?s2 ?yp ?bn.\n" +
                "?bn ?dp ?value.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                "?dp rdfs:label ?dpLabel.\n" +
                String.format("filter (?s2Label != '%s')\n",typeofinsurance) +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(entity, b.value("s2Label"), b.value("yp"),
                new BlankNode(b.value("bn"), ""), b.value("dpLabel"),b.value("value")));
    }


    public List<YshapeBNAndDPAndValue>  queryYshapeBNLabelsAndDatatypesWithEntityAndClass(String entity,String clazz,String datatype) {
        String queryString = "select distinct ?s1Label ?s2Label ?yp ?value ?bn where {\n" +
                String.format("?s1 rdfs:label '%s' .\n",entity)+
                "?s2 rdfs:label ?s2Label.\n" +
                String.format("?s2 a/rdfs:label '%s'.\n",clazz) +
                "?s1 ?yp ?bn.\n" +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>.\n" +
                "?s2 ?yp ?bn.\n" +
                "?bn ?dp ?value.\n" +
                "?dp a/rdfs:subClassOf* owl:DatatypeProperty.\n" +
                String.format("?dp rdfs:label '%s'.\n",datatype) +
                "filter (?s2Label != ?s1Label)\n" +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(entity, b.value("s2Label"), b.value("yp"),
                new BlankNode(b.value("bn"), ""), datatype,b.value("value")));
    }

    public List<String> queryEntitiesDerivedFromClass(List<String> entities,String clazz){
        String queryString = "select distinct ?s1Label {\n" +
                String.format("values ?s1Label {%s}\n",String.join(" ",entities.stream().map(x -> String.format("'%s'",x)).collect(Collectors.toList()))) +
                "?s1 rdfs:label ?s1Label .\n"+
                String.format("?s1 a/rdfs:label '%s'.\n",clazz)+
                "}";
        return knowledge.selectOneAsList(queryString,"s1Label");
    }

    public List<String> queryEntitiesAndDatatypeThatHaveValue(List<String> entities,String datatype){
        String queryString = "SELECT DISTINCT  ?s1Label WHERE {\n" +
                String.format("values ?s1Label {%s}\n",String.join(" ",entities.stream().map(x -> String.format("'%s'",x)).collect(Collectors.toList()))) +
                "?entity rdfs:label ?s1Label .\n" +
                "?entity ?dp ?value.\n" +
                String.format("?dp rdfs:label '%s'.\n" ,datatype)+
                "\n" +
                "}";
        logger.debug("query: {}",queryString);
        return knowledge.selectOneAsList(queryString,"s1Label");
    }

    public Pair<String,String> checkEntitiesPairSatisfyClasses(Pair<String,String> entitiesPair,Pair<String,String> clazzes){
        // 检测entitiesPair是否满足clazzes 若满足,则按clazzes的顺序并调整entitiesPair,否则返回null
        String entityofclazz1 = null;
        String entityofclazz2 = null;
        String queryString = "select distinct ?s1Label where {\n" +
                String.format("values ?s1Label { '%s' '%s' }\n",queryLabelWithIRI(entitiesPair.getKey()),queryLabelWithIRI(entitiesPair.getValue())) +
                "?s rdfs:label ?s1Label.\n" +
                String.format("?s a/rdfs:label '%s'.\n", clazzes.getKey() )+
                "\n" +
                "}";
        List<String> entitiesofclazz1 = knowledge.selectOneAsList(queryString,"s1Label");
        if(entitiesofclazz1 == null || entitiesofclazz1.size() == 0){
            return null;
        }else{
            entityofclazz1 = entitiesofclazz1.get(0);
        }

        String queryString1 = "select distinct ?s1Label where {\n" +
                String.format("values ?s1Label { '%s' '%s' }\n",queryLabelWithIRI(entitiesPair.getKey()),queryLabelWithIRI(entitiesPair.getValue())) +
                "?s rdfs:label ?s1Label.\n" +
                String.format("?s a/rdfs:label '%s'.\n", clazzes.getValue() )+
                "\n" +
                "}";
        List<String> entitiesofclazz2 = knowledge.selectOneAsList(queryString1,"s1Label");
        if(entitiesofclazz2 == null || entitiesofclazz2.size() == 0){
            return null;
        }else{
            entityofclazz2 = entitiesofclazz2.get(0);
        }

        if(entityofclazz1 != null && entityofclazz2 != null)
        {
            return new Pair<>(entityofclazz1,entityofclazz2);
        }
        return null;
    }


    public List<YshapeBNAndDPAndValue> queryYshapeBNLabelsAndDatatypesWithE1AndE2AndDatatype(String e1, String e2,String datatype) {
        String queryString = "SELECT DISTINCT ?yp ?bn ?dp ?value WHERE{\n" +
                String.format("?s1 rdfs:label '%s' . ?s2 rdfs:label '%s'.\n",e1,e2 )+
                "?s1 ?yp ?bn . ?s2 ?yp ?bn .\n" +
                String.format("?bn ?dp ?value . ?dp rdfs:label '%s' .\n",datatype) +
                "?yp a <http://hual.ai/new_standard#ComplexProperty>." +
                "?dp a/rdfs:subClassOf owl:DatatypeProperty." +
                "}";
        return queryAndCheckStatus(queryString, b -> new YshapeBNAndDPAndValue(e1, e2, b.value("yp"),
                new BlankNode(b.value("bn"), ""), datatype,b.value("value")));
    }
}