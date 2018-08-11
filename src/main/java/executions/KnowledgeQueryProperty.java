package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.exceptions.DMException;
import ai.hual.labrador.kg.KnowledgeStatus;
import ai.hual.labrador.nlu.constants.SystemIntents;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;
import responses.FAQResponse;
import responses.KnowledgeQueryResponse;
import utils.KnowledgeQueryUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pojo.ObjectProperty.*;

class KnowledgeQueryProperty {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryProperty.class);

    private KnowledgeQueryPropertyWithBN knowledgeQueryPropertyWithBN;

    private AccessorRepository accessorRepository;
    private KnowledgeQueryResponse response;
    private FAQResponse faqResponse;

    private KnowledgeQueryUtils kgUtil;

    KnowledgeQueryProperty(AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
        knowledgeQueryPropertyWithBN = new KnowledgeQueryPropertyWithBN(accessorRepository);
        kgUtil = new KnowledgeQueryUtils(
                accessorRepository.getKnowledgeAccessor(),
                accessorRepository.getKnowledgeStatusAccessor());
        response = new KnowledgeQueryResponse(kgUtil, accessorRepository);
        faqResponse = new FAQResponse(accessorRepository);

    }

    ExecutionResult execute(Context context) {
        List<String> bn = (List<String>) context.getSlots().get("bn");
        List<String> entities = (List<String>) context.getSlots().get("entity");
        List<String> mergedEntities = (List<String>) context.getSlots().get("mergedEntity");
        String yshape = (String) context.getSlots().get("YshapeProperty");
        String diffusion = (String) context.getSlots().get("DiffusionProperty");
        String condition = (String) context.getSlots().get("ConditionProperty");
        String object = (String) context.getSlots().get("HualObjectProperty");
        String datatype = (String) context.getSlots().get("HualDataTypeProperty");

        // remove bn with the same name as an entity
        bn.removeAll(entities);

        // return execute(context, bn, entities, mergedEntities, yshape, diffusion, condition, object, datatype);
        return execute(context, bn, entities, entities, yshape, diffusion, condition, object, datatype);
    }

    private ResponseExecutionResult execute(Context context, List<String> bn, List<String> entities, List<String> mergedEntities,
                                            String yshape, String diffusion, String condition, String object, String datatype) {
        List<String> properties = Stream.of(yshape, diffusion, condition, object, datatype)
                .filter(Objects::nonNull).collect(Collectors.toList());
        // bn
        if (bn != null && !bn.isEmpty()) {
            ResponseExecutionResult resultWithBN = knowledgeQueryPropertyWithBN.execute(context, datatype);
            if (resultWithBN != null) {
                return resultWithBN;
            }
        }

        // no bn
        //
//        ResponseExecutionResult result = execute(entities, yshape, diffusion, condition, object, datatype, properties, context);
//        if (("faq".equals(result.getResponseAct().getLabel()) || SystemIntents.UNKNOWN.equals(result.getResponseAct().getLabel())) &&
//                mergedEntities.size() > entities.size()) {
//            // no result
//            mergedEntities.removeAll(entities);
//            ResponseExecutionResult mergedEntitiesResult = execute(mergedEntities, yshape, diffusion, condition, object, datatype, properties, context);
//            if ("faq".equals(mergedEntitiesResult.getResponseAct().getLabel()) || SystemIntents.UNKNOWN.equals(mergedEntitiesResult.getResponseAct().getLabel())) {
//                return result;
//            } else {
//                return mergedEntitiesResult;
//            }
//        } else {
//            // has result
//            return result;
//        }
        ResponseExecutionResult result = execute(entities, yshape, diffusion, condition, object, datatype, properties, context);
        if (!"kg".equals(result.getResponseAct().getLabel()) && mergedEntities.size() > entities.size()) {
            // no result
            mergedEntities.removeAll(entities);
            return execute(mergedEntities, yshape, diffusion, condition, object, datatype, properties, context);
        } else {
            // has result
            return result;
        }
    }

    private ResponseExecutionResult execute(List<String> entities, String yshape, String diffusion, String condition, String object, String datatype,
                                            List<String> properties, Context context) {
        // use enabled entities
        if (entities != null && !entities.isEmpty()) {
            List<String> enabledEntities = new ArrayList<>();
            for (String entity : entities) {
                if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(kgUtil.queryEntityIRI(entity)) != KnowledgeStatus.DISABLED) {
                    enabledEntities.add(entity);
                }
            }
            if (enabledEntities.isEmpty()) {
                return response.disabled(entities.toString());
            }
            entities = enabledEntities;
        }


        if (properties.size() == 0) {
            if (entities == null || entities.size() == 0) {
                logger.debug("0. 没有识别到意图");
                String conditionEntity = (String) context.getSlots().get("conditionEntity");
                if (conditionEntity != null) {
                    logger.debug("0.1. 识别到条件实体，如果上文有BN实体属性等，则按照上文BN实体属性和条件实体进行查询，否则FAQ");
                    String bn = (String) context.getSlots().get("contextBN");
                    entities = Optional.ofNullable((List<String>) context.getSlots().get("contextEntity")).orElseGet(ArrayList::new);
                    String contextObject = (String) context.getSlots().get("contextObject");
                    if (contextObject != null) {
                        String objectType = kgUtil.queryObjectType(contextObject);
                        if (objectType != null) {
                            switch (objectType) {
                                case YSHAPE_PROPERTY:
                                    yshape = contextObject;
                                    break;
                                case DIFFUSION_PROPERTY:
                                    diffusion = contextObject;
                                    break;
                                case CONDITION_PROPERTY:
                                    condition = contextObject;
                                    break;
                            }
                        }
                    }
                    datatype = (String) context.getSlots().get("contextDatatype");
                    if (bn != null || !entities.isEmpty() ||
                            yshape != null || diffusion != null || condition != null || datatype != null) {
                        return execute(context, Collections.singletonList(bn), entities, entities,
                                yshape, diffusion, condition, object, datatype);
                    } else {
                        return faqResponse.faq(context, true);
                    }
                } else {
                    logger.debug("0.2. 没有条件实体，faq");
                    return faqResponse.faq(context, true);
                }
            } else if (entities.size() == 1) {
                logger.debug("1. 只识别到1个实体（没有识别到属性）");
                String entity = entities.get(0);
                EntitiesAndTheirProperties entitiesAndTheirProperties = kgUtil.queryProperties(entity);
                return singleEntityAndNoProperty(entity,
                        entitiesAndTheirProperties.getEntitiesAndTheirDatatypes().get(entity),
                        entitiesAndTheirProperties.getEntitiesAndTheirObjects().get(entity),
                        context);
            } else {
                logger.debug("2. 识别到多个实体（没有识别到属性）");

                logger.debug("2.0. 实体两两组合，查询是否有with_bn");
                List<YshapeBNAndDP> yshapeBNAndDPs = kgUtil.queryYshapeBNLabelsAndDatatypes(entities);
                if (yshapeBNAndDPs.stream().map(YshapeBNAndDP::getBN).map(BlankNode::getIri).distinct().count() == 1) {
                    logger.debug("2.0.1.只有1组withBN实体，查询DP");
                    if (yshapeBNAndDPs.size() == 1) {
                        logger.debug("2.0.1.1.只有1个DP时，反问：您是想问【BN】的【DP】吗？");
                        String contextDatatype = (String) context.getSlots().get("contextDatatype");
                        if (yshapeBNAndDPs.get(0).getDatatype().equals(contextDatatype)) {
                            logger.debug("2.0.1.1.1.上文存在该DP，直接回答");
                            String result = kgUtil.queryBNDatatype(yshapeBNAndDPs.get(0).getBN(), yshapeBNAndDPs.get(0).getDatatype());
                            return response.answerYshape(result, yshapeBNAndDPs.get(0), context);
                        }
                        return response.guessYshapeDP(yshapeBNAndDPs.get(0), context);
                    }
                    logger.debug("2.0.1.2.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    for (YshapeBNAndDP yshapeBNAndDP : yshapeBNAndDPs) {
                        if (yshapeBNAndDP.getDatatype().equals(contextDatatype)) {
                            logger.debug("2.0.1.2.1.上文存在DP之一，直接回答");
                            String result = kgUtil.queryBNDatatype(yshapeBNAndDP.getBN(), yshapeBNAndDP.getDatatype());
                            return response.answerYshape(result, yshapeBNAndDP, context);
                        }
                    }
                    return response.askWhichDatatypeOfYshape(yshapeBNAndDPs, context);
                } else if (yshapeBNAndDPs.size() > 0) {
                    logger.debug("2.0.2.多于1组withBN实体，反问：您是想问【BNs】中哪一个？");
                    return response.askWhichBNOfYshape(yshapeBNAndDPs, context);
                }

                List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                if (contextEntities != null) {
                    logger.debug("2.1. 实体加上上文中的实体后两两组合，查询是否有with_bn");
                    yshapeBNAndDPs = kgUtil.queryYshapeBNLabelsAndDatatypes(contextEntities, entities);
                    if (yshapeBNAndDPs.stream().map(YshapeBNAndDP::getBN).map(BlankNode::getIri).distinct().count() == 1) {
                        logger.debug("2.1.1.只有1组withBN实体，查询DP，");
                        if (yshapeBNAndDPs.size() == 1) {
                            logger.debug("2.1.1.1.只有1个DP时，反问：您是想问【BN】的【DP】吗？");
                            String contextDatatype = (String) context.getSlots().get("contextDatatype");
                            if (yshapeBNAndDPs.get(0).getDatatype().equals(contextDatatype)) {
                                logger.debug("2.1.1.1.1.上文存在该DP，直接回答");
                                String result = kgUtil.queryBNDatatype(yshapeBNAndDPs.get(0).getBN(), yshapeBNAndDPs.get(0).getDatatype());
                                return response.answerYshape(result, yshapeBNAndDPs.get(0), context);
                            }
                            return response.guessYshapeDP(yshapeBNAndDPs.get(0), context);
                        }
                        logger.debug("2.1.1.1.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                        String contextDatatype = (String) context.getSlots().get("contextDatatype");
                        for (YshapeBNAndDP yshapeBNAndDP : yshapeBNAndDPs) {
                            if (yshapeBNAndDP.getDatatype().equals(contextDatatype)) {
                                logger.debug("2.1.1.2.1.上文存在DP之一，直接回答");
                                String result = kgUtil.queryBNDatatype(yshapeBNAndDP.getBN(), yshapeBNAndDP.getDatatype());
                                return response.answerYshape(result, yshapeBNAndDP, context);
                            }
                        }
                        return response.askWhichDatatypeOfYshape(yshapeBNAndDPs, context);
                    } else if (yshapeBNAndDPs.size() > 0) {
                        logger.debug("2.1.2.多于1组withBN实体，反问：您是想问【BNs】中哪一个？");
                        return response.askWhichBNOfYshape(yshapeBNAndDPs, context);
                    }
                }

                // TODO sparql query can be optimized by the following commented code, but unicode ??? problem happens with virtuoso
                // EntitiesAndTheirProperties entitiesAndTheirProperties = kgUtil.queryProperties(entities);
                // ListMultimap<String, String> entitiesAndTheirDatatypes = entitiesAndTheirProperties.getEntitiesAndTheirDatatypes();
                // ListMultimap<String, ObjectProperty> entitiesAndTheirObjects = entitiesAndTheirProperties.getEntitiesAndTheirObjects();
                ListMultimap<String, String> entitiesAndTheirDatatypes = ArrayListMultimap.create();
                ListMultimap<String, ObjectProperty> entitiesAndTheirObjects = ArrayListMultimap.create();
                for (String entity : entities) {
                    EntitiesAndTheirProperties entitiesAndTheirProperties = kgUtil.queryProperties(entity);
                    entitiesAndTheirDatatypes.putAll(entitiesAndTheirProperties.getEntitiesAndTheirDatatypes());
                    entitiesAndTheirObjects.putAll(entitiesAndTheirProperties.getEntitiesAndTheirObjects());
                }

                Set<String> entitiesWithDPorOP = new LinkedHashSet<>();
                entitiesWithDPorOP.addAll(entitiesAndTheirDatatypes.keySet());
                entitiesWithDPorOP.addAll(entitiesAndTheirObjects.keySet());
                if (entitiesWithDPorOP.size() == 1) {
                    logger.debug("2.2. 只有1个实体有DP/OP，按只识别到1个实体处理");
                    String entity = entitiesWithDPorOP.iterator().next();
                    return singleEntityAndNoProperty(entity, entitiesAndTheirDatatypes.get(entity), entitiesAndTheirObjects.get(entity), context);
                }
                if (entitiesWithDPorOP.size() > 1) {
                    logger.debug("2.4. 多余1个实体有DP/OP，反问：您是想问【实体s】中的哪一个？");
                    return response.askWhichEntity(entitiesWithDPorOP, context);
                }
                logger.debug("2.5. 没有查询到DP/OP，转FAQ");
                return faqResponse.faq(context, false);
            }
        } else {
            if (entities == null || entities.size() == 0) {
                logger.debug("3. 只识别到1个意图");
                return noEntityAndSingleProperty(yshape, diffusion, condition, object, datatype, properties, context);
            } else if (entities.size() == 1) {
                logger.debug("4. 识别到1个实体1个意图");
                String entity = entities.get(0);
                return singleEntityAndSingleProperty(entity, yshape, diffusion, condition, object, datatype, properties, context);
            } else {
                logger.debug("5.识别到多个实体1个意图");
                logger.debug("5.1.实体两两组合查询，查询是否有with_bn");
                List<YshapeBNAndDP> yshapeBNAndDPs = kgUtil.queryYshapeBNsWithDatatypes(entities, datatype);
                if (yshapeBNAndDPs.size() == 1) {
                    logger.debug("5.1.1. 只有1个withBN实体，直接回答");
                    String result = kgUtil.queryBNDatatype(yshapeBNAndDPs.get(0).getBN(), datatype);
                    return response.answerYshape(result, yshapeBNAndDPs.get(0), context);
                } else if (yshapeBNAndDPs.size() > 1) {
                    logger.debug("5.1.2.多于1个withBN实体，反问：您是想问【BNs】中哪个的【DP/OP】？");
                    if (datatype == null) {
                        // object
                        return response.askWhichBNOfYshape(yshapeBNAndDPs, context);
                    } else {
                        // datatype
                        return response.askWhichBNOfYshapeWithDatatype(datatype, yshapeBNAndDPs, context);
                    }
                }

                logger.debug("5.2.遍历实体，查询是否有值");
                if (datatype != null) {
                    // datatype
                    Map<String, String> validEntitiesAndValues = kgUtil.queryValidEntitiesAndValuesWithDatatype(entities, datatype);
                    if (validEntitiesAndValues.size() == 1) {
                        logger.debug("5.2.1.只有1个实体有值");
                        logger.debug("5.2.1.0.DP，直接回答");
                        String entity = validEntitiesAndValues.keySet().iterator().next();
                        return response.answer(entity, datatype, validEntitiesAndValues.get(entity), context);
                    } else if (validEntitiesAndValues.size() > 1) {
                        logger.debug("5.2.2.多于1个实体有值，反问：您是想问【实体s】中哪个的【DP/OP】？");
                        return response.askWhichEntityWithDatatype(validEntitiesAndValues.keySet(), datatype, context);
                    }


                    // 5.2.0.查询【实体】OP【BN】属性【值】
                    ListMultimap<String, ObjectProperty> entitiesAndObjectsOfDatatype = ArrayListMultimap.create();
                    for (String entity : entities) {
                        List<ObjectProperty> objectsOfDatatype = kgUtil.queryObjectsOfDatatype(entity, datatype);
                        // filter objects
                        for (int i = 0; i < objectsOfDatatype.size(); i++) {
                            if (properties.contains(objectsOfDatatype.get(i).getUri())) {
                                objectsOfDatatype = Collections.singletonList(objectsOfDatatype.get(i));
                                break;
                            }
                        }
                        entitiesAndObjectsOfDatatype.putAll(entity, objectsOfDatatype);
                    }
                    if (entitiesAndObjectsOfDatatype.size() > 0) {
                        logger.debug("5.2.0.查询【实体】OP【BN】属性【值】");
                        if (entitiesAndObjectsOfDatatype.keySet().size() == 1) {
                            String entity = entitiesAndObjectsOfDatatype.keySet().iterator().next();
                            List<ObjectProperty> objectsOfDatatype = entitiesAndObjectsOfDatatype.get(entity);

                            // filter bns with the same name as entity
                            // if there are bns with the same label as entity, only these bn will be considered
                            List<ObjectProperty> objectsOfDatatypeWithSameLabelBN = objectsOfDatatype.stream()
                                    .filter(x -> Objects.equals(x.getBN().getLabel(), entity)).collect(Collectors.toList());
                            if (!objectsOfDatatypeWithSameLabelBN.isEmpty()) {
                                objectsOfDatatype = objectsOfDatatypeWithSameLabelBN;
                            }

                            Map<String, String> objectIRIsAndTypes = new HashMap<>();
                            for (ObjectProperty objectProperty : objectsOfDatatype) {
                                objectIRIsAndTypes.put(objectProperty.getUri(), objectProperty.getType());
                            }
                            if (objectIRIsAndTypes.size() == 1 && YSHAPE_PROPERTY.equals(objectIRIsAndTypes.values().iterator().next())) {
                                logger.debug("5.2.0.1.只存在with_bn");
                                String iri = objectIRIsAndTypes.keySet().iterator().next();
                                List<BNAndValue> yshapeBNsAndValues = kgUtil.query(entity, iri, datatype);
                                if (yshapeBNsAndValues.size() == 0) {
                                    return faqResponse.faq(context, false);
                                } else if (yshapeBNsAndValues.size() == 1) {
                                    logger.debug("5.2.0.1.1.只有1个，直接回答");
                                    return response.answerWithBN(entity, iri, yshapeBNsAndValues.get(0).getBN(), datatype, yshapeBNsAndValues.get(0).getValue(), context);
                                } else {
                                    logger.debug("5.2.0.1.2.多于1个，查询上文是否有相同的BN");
                                    String contextBN = (String) context.getSlots().get("contextBN");
                                    List<BlankNode> bns = yshapeBNsAndValues.stream().map(BNAndValue::getBN).collect(Collectors.toList());
                                    if (contextBN != null && bns.stream().map(BlankNode::getLabel).anyMatch(contextBN::equals)) {
                                        logger.debug("5.2.0.1.2.1.有相同的BN，按照1个BN1个属性处理");
                                        ResponseExecutionResult result = knowledgeQueryPropertyWithBN.singleBNWithDP(contextBN, datatype, context);
                                        if (result != null) {
                                            return result;
                                        }
                                    } else {
                                        logger.debug("5.2.0.1.2.2.反问：您是想问【BNs】哪个的【DP】？");
                                        return response.askWhichBN(entity, bns, datatype, context);
                                    }
                                }
                            } else if (objectIRIsAndTypes.size() == 1 && CONDITION_PROPERTY.equals(objectIRIsAndTypes.values().iterator().next())) {
                                logger.debug("5.2.0.2.只存在条件BN");
                                String iri = objectIRIsAndTypes.keySet().iterator().next();
                                List<ConditionEntityAndBNAndValue> conditionEntitiesAndValues = kgUtil.queryConditionEntitiesAndValuesWithDatatype(entity, iri, datatype);
                                if (conditionEntitiesAndValues.size() == 0) {
                                    return faqResponse.faq(context, false);
                                }
                                if (conditionEntitiesAndValues.size() == 1) {
                                    logger.debug("5.2.0.2.1.只有1个，直接回答");
                                    return response.answerWithConditionWithBN(entity, iri, conditionEntitiesAndValues.get(0).getBNAndValue().getBN(), conditionEntitiesAndValues.get(0).getConditionEntity().getLabel(), datatype, conditionEntitiesAndValues.get(0).getBNAndValue().getValue(), context);
                                }

                                String conditionEntity = (String) context.getSlots().get("conditionEntity");
                                if (conditionEntity != null) {
                                    for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                                        if (conditionEntity.equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                                            logger.debug("5.2.0.2.1.0. 结合condition实体查询，如果有答案则直接回答【实体】在【条件】下的【DP】为【答案】");
                                            return response.answerWithCondition(entity, iri, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntity, datatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                                        }
                                    }
                                }

                                for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                                    if ("默认".equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                                        logger.debug("5.2.0.2.1.存在defaultCondition，直接回答");
                                        return response.answerWithCondition(entity, iri, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntityAndValue.getConditionEntity().getLabel(), datatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                                    }
                                }

                                logger.debug("5.2.0.2.2.多于1个，反问：您是想问【实体】在什么条件【undercondition domain的类里所有condition实体】下的【DP】？");
                                return response.askWhichConditionEntityWithDatatype(entity, conditionEntitiesAndValues.stream().map(ConditionEntityAndBNAndValue::getConditionEntity).collect(Collectors.toList()), datatype, context);
                            } else {
                                logger.debug("5.2.0.3.存在OP");
                                if (objectsOfDatatype.size() == 0) {
                                    return faqResponse.faq(context, false);
                                } else if (objectsOfDatatype.size() == 1) {
                                    logger.debug("5.2.0.3.1.只有1个，直接回答");
                                    List<BNAndValue> result = kgUtil.query(entity, objectsOfDatatype.get(0).getUri(), datatype);
                                    return response.answerWithBN(entity, objectsOfDatatype.get(0).getUri(), result.get(0).getBN(), datatype, result.get(0).getValue(), context);
                                } else {
                                    String contextBN = (String) context.getSlots().get("contextBN");
                                    if (contextBN != null && objectsOfDatatype.stream().map(ObjectProperty::getBN).map(BlankNode::getLabel).anyMatch(contextBN::equals)) {
                                        logger.debug("5.2.0.3.2.上文有相同的BN，按照1个BN1个属性处理");
                                        ResponseExecutionResult result = knowledgeQueryPropertyWithBN.singleBNWithDP(contextBN, datatype, context);
                                        if (result != null) {
                                            return result;
                                        }
                                    } else {
                                        logger.debug("5.2.0.3.3.多于1个，反问：您是想问【实体】【OPs】，还是【BNs】哪个的【DP】？");
                                        return response.askWhichObject(entity, objectsOfDatatype, datatype, context);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // object
                    String objectIRI = properties.get(0);
                    ListMultimap<String, BNAndDatatypeAndValue> validEntitiesAndValues = ArrayListMultimap.create();
                    for (String entity : entities) {
                        validEntitiesAndValues.putAll(entity, kgUtil.queryDatatypeOfObject(entity, objectIRI));
                    }
                    if (validEntitiesAndValues.keySet().size() == 1) {
                        logger.debug("5.2.1.只有1个实体有值");
                        String entity = validEntitiesAndValues.keySet().iterator().next();
                        if (validEntitiesAndValues.size() == 1) {
                            logger.debug("5.2.1.1. OP且只有一个BN和DP，直接回答");
                            BNAndDatatypeAndValue value = validEntitiesAndValues.get(entity).get(0);
                            return response.answerWithBN(entity, objectIRI, value.getBn(), value.getDatatypeAndValue().getDatatype(), value.getDatatypeAndValue().getValue(), context);
                        } else {
                            List<String> bnLabels = validEntitiesAndValues.get(entity).stream().map(x -> x.getBn().getLabel()).distinct().collect(Collectors.toList());
                            if (bnLabels.size() > 1) {
                                logger.debug("5.2.1.2. OP且有多种BN，询问哪个BN");
                                return response.askWhichBN(entity, validEntitiesAndValues.get(entity).stream().map(BNAndDatatypeAndValue::getBn).collect(Collectors.toList()), context);
                            } else {
                                String bnLabel = bnLabels.get(0);
                                List<String> datatypeLabels = validEntitiesAndValues.get(entity).stream().map(x -> x.getDatatypeAndValue().getDatatype()).collect(Collectors.toList());
                                if (datatypeLabels.size() > 1) {
                                    logger.debug("5.2.1.3. OP且有一种BN和多个DP，询问哪个DP");
                                    return response.askWhichDatatypeWithBN(bnLabel, datatypeLabels, context);
                                } else {
                                    logger.debug("5.2.1.4. OP且有一种BN和一个DP，询问哪个条件实体");
                                    String datatypeLabel = datatypeLabels.get(0);
                                    List<ConditionEntityAndBN> conditionEntities = kgUtil.queryConditionEntities(entity, objectIRI);
                                    return response.askWhichConditionEntityWithDatatype(entity, conditionEntities.stream().map(ConditionEntityAndBN::getConditionEntity).collect(Collectors.toList()), datatypeLabel, context);
                                }
                            }
                        }
                    } else if (validEntitiesAndValues.keySet().size() > 1) {
                        logger.debug("5.2.2.多于1个实体有值，反问：您是想问【实体s】中哪个的【DP/OP】？");
                        return response.askWhichEntityWithObject(validEntitiesAndValues.keySet(), objectIRI, kgUtil.queryObjectLabel(objectIRI), context);
                    }
                }


                logger.debug("5.3.继承上文实体，查询到值，直接回答");
                List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                if (datatype != null && contextEntities != null) {
                    Map<String, String> validEntitiesAndValues = kgUtil.queryValidEntitiesAndValuesWithDatatype(contextEntities, datatype);
                    if (validEntitiesAndValues.size() == 1) {
                        logger.debug("5.3.1.只有1个实体有值，直接回答");
                        String entity = validEntitiesAndValues.keySet().iterator().next();
                        return response.answer(entity, datatype, validEntitiesAndValues.get(entity), context);
                    } else if (validEntitiesAndValues.size() > 1) {
                        logger.debug("5.3.2.多于1个实体有值，反问：您是想问【实体s】中哪个的【DP/OP】？");
                        return response.askWhichEntityWithDatatype(validEntitiesAndValues.keySet(), datatype, context);
                    }
                }

                logger.debug("5.4.继承上文属性，遍历实体，查询到值，直接回答");
                String contextDatatype = (String) context.getSlots().get("contextDatatype");
                String contextObject = (String) context.getSlots().get("contextObject");
                if (contextDatatype != null) {
                    if (contextObject != null) {
                        ListMultimap<String, BNAndValue> validEntitiesAndBNAndValues =
                                kgUtil.queryValidEntitiesAndBNAndValuesWithObjectAndDatatype(entities, contextObject, contextDatatype);
                        // TODO deal with multiple results
                        if (validEntitiesAndBNAndValues.size() == 1) {
                            String entity = validEntitiesAndBNAndValues.keySet().iterator().next();
                            BNAndValue bnAndValue = validEntitiesAndBNAndValues.get(entity).get(0);
                            return response.answerWithBN(entity, contextObject, bnAndValue.getBN(), contextDatatype, bnAndValue.getValue(), context);
                        }
                    }

                    // No OP or OP+DP not working
                    Map<String, String> validEntitiesAndValues = kgUtil.queryValidEntitiesAndValuesWithDatatype(entities, contextDatatype);
                    if (validEntitiesAndValues.size() == 1) {
                        logger.debug("5.4.1.只有1个实体有值，直接回答");
                        String entity = validEntitiesAndValues.keySet().iterator().next();
                        return response.answer(entity, contextDatatype, validEntitiesAndValues.get(entity), context);
                    } else if (validEntitiesAndValues.size() > 1) {
                        logger.debug("5.4.2.多于1个实体有值，反问：您是想问【实体s】中哪个的【DP/OP】？");
                        return response.askWhichEntityWithDatatype(validEntitiesAndValues.keySet(), contextDatatype, context);
                    }
                }

                ResponseExecutionResult result = noEntityAndSingleProperty(yshape, diffusion, condition, object, datatype, properties, context);
                if (result.getResponseAct() != null &&
                        !"faq".equals(result.getResponseAct().getLabel()) &&
                        !SystemIntents.UNKNOWN.equals(result.getResponseAct().getLabel())) {
                    logger.debug("5.5.按照只有一个意图的情况进行查询，如果结果不为FAQ则返回");
                    return result;
                }

                logger.debug("5.6.其余情况，反问：你是想了解【实体s】中的哪一个？");
                return response.askWhichEntity(entities, context);
            }
        }
    }

    private ResponseExecutionResult noEntityAndSingleProperty(String yshape, String diffusion, String condition, String object, String datatype,
                                                              List<String> properties, Context context) {
        if (datatype != null) {
            // datatype
            String cls = (String) context.getSlots().get("class");
            ListMultimap<String, String> entitiesAndBNsOfProperty = kgUtil.queryEntitiesAndBNsWithDatatype(datatype, cls);
            if (entitiesAndBNsOfProperty.size() == 0 && cls != null) {
                entitiesAndBNsOfProperty = kgUtil.queryEntitiesAndBNsWithDatatype(datatype, null);
            }

            Set<String> entitiesAndBNsSet = new HashSet<>();
            for (String entity : entitiesAndBNsOfProperty.keySet()) {
                for (String bn : entitiesAndBNsOfProperty.get(entity)) {
                    entitiesAndBNsSet.add(bn != null ? bn : entity);
                }
            }
            if (entitiesAndBNsSet.size() == 0) {
                logger.debug("3.3. 没有查询到实体时，转FAQ");
                return faqResponse.faq(context, false);
            } else if (entitiesAndBNsSet.size() == 1) {
                logger.debug("3.1. 查询到只有1个实体，则按识别出1个实体1个属性处理");
                String entity = null;
                List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                if (contextEntities != null) {
                    for (String contextEntity : contextEntities) {
                        if (entitiesAndBNsOfProperty.keySet().contains(contextEntity)) {
                            entity = contextEntity;
                            break;
                        }
                    }
                }
                if (entity == null) {
                    entity = entitiesAndBNsOfProperty.keySet().iterator().next();
                }
                return singleEntityAndSingleProperty(entity, yshape, diffusion, condition, object, datatype, properties, context);
            } else {
                logger.debug("3.2.查询到多个实体时");

                String contextBN = (String) context.getSlots().get("contextBN");
                if (contextBN != null && entitiesAndBNsOfProperty.values().contains(contextBN)) {
                    logger.debug("3.2.1. 上文中是否有相同的BN，有则按照识别出1个BN1个属性处理");
                    ResponseExecutionResult result = knowledgeQueryPropertyWithBN.singleBNWithDP(contextBN, datatype, context);
                    if (result != null) {
                        return result;
                    }
                }

                List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                if (contextEntities != null) {
                    contextEntities = contextEntities.stream().filter(entitiesAndBNsOfProperty::containsKey).collect(Collectors.toList());
                    if (contextEntities.size() == 1) {
                        String contextEntity = contextEntities.get(0);
                        logger.debug("3.2.2.上文中是否有相同的实体，有则按识别出1个实体1个属性处理");
                        return singleEntityAndSingleProperty(contextEntity, yshape, diffusion, condition, object, datatype, properties, context);
                    } else if (contextEntities.size() > 1) {
                        logger.debug("3.2.2.上文中有多个相同的BN或实体，按照相同的部分反问3.2.3.");
                        ListMultimap<String, String> oldEntitiesAndBNsOfProperty = entitiesAndBNsOfProperty;
                        entitiesAndBNsOfProperty = ArrayListMultimap.create();
                        for (String contextEntity : contextEntities) {
                            entitiesAndBNsOfProperty.putAll(contextEntity, oldEntitiesAndBNsOfProperty.get(contextEntity));
                        }
                    }
                }
                entitiesAndBNsSet = new HashSet<>();
                for (String entity : entitiesAndBNsOfProperty.keySet()) {
                    for (String bn : entitiesAndBNsOfProperty.get(entity)) {
                        entitiesAndBNsSet.add(bn != null ? bn : entity);
                    }
                }
                logger.debug("3.2.3.反问：您是想问【实体s】哪个的【DP/OP】？");
                return response.askWhichEntityWithDatatype(new ArrayList<>(entitiesAndBNsSet), datatype, context);
            }
        } else {
            // object
            String property = properties.get(0);
            List<String> entitiesOfProperty = kgUtil.queryEntityWithObject(property);
            if (entitiesOfProperty.size() == 0) {
                logger.debug("3.3. 没有查询到实体时，转FAQ");
                return faqResponse.faq(context, false);
            } else if (entitiesOfProperty.size() == 1) {
                logger.debug("3.1. 查询到只有1个实体，则按识别出1个实体1个属性处理");
                String entityOfDatatype = entitiesOfProperty.get(0);
                return singleEntityAndSingleProperty(entityOfDatatype, yshape, diffusion, condition, object, null, properties, context);
            } else {
                logger.debug("3.2.查询到多个实体时");
                List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                if (contextEntities != null) {
                    for (String contextEntity : contextEntities) {
                        if (entitiesOfProperty.contains(contextEntity)) {
                            logger.debug("3.2.2.上文中是否有相同的实体，有则按识别出1个实体1个属性处理");
                            return singleEntityAndSingleProperty(contextEntity, yshape, diffusion, condition, object, null, properties, context);
                        }
                    }
                }
                logger.debug("3.2.3.反问：您是想问【实体s】哪个的【DP/OP】？");
                String propertyLabel = kgUtil.queryObjectLabel(property);
                return response.askWhichEntityWithObject(entitiesOfProperty, property, propertyLabel, context);
            }
        }
    }

    // 识别出1个实体1个属性处理
    private ResponseExecutionResult singleEntityAndSingleProperty(String entity, String yshape, String diffusion, String condition, String object, String datatype,
                                                                  List<String> properties, Context context) {
        if (datatype == null) {
            logger.debug("4.0.OP，查OP接的BN实体的是否有条件，DP是1个还是多个？有1个就直接回答，有多个就反问，有条件的时候反问带有条件的");
            String propertyLabel = kgUtil.queryObjectLabel(properties.get(0));
            if (propertyLabel == null) {
                propertyLabel = properties.get(0);
            }
            if (yshape != null) {
                logger.debug("4.0.1.with_bn，查询BN实体、另一个实体和后续的DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, yshape);
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("4.0.1.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                } else if (datatypeLabels.size() == 1) {
                    logger.debug("4.0.1.1.只有1个DP时，直接回答值");
                    String datatypeOfObjectOfEntity = datatypeLabels.get(0);
                    List<BNAndValue> result = kgUtil.query(entity, yshape, datatypeOfObjectOfEntity);
                    return response.answer(entity, datatypeOfObjectOfEntity, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                } else {
                    logger.debug("4.0.1.2.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    if (datatypeLabels.contains(contextDatatype)) {
                        logger.debug("4.0.1.2.1.上文中存在DP之一，直接回答");
                        List<BNAndValue> result = kgUtil.query(entity, yshape, contextDatatype);
                        return response.answer(entity, contextDatatype, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                    }
                    List<String> bnlabels = datatypesOfObjectOfEntity.stream().map(x -> x.getBn().getLabel()).distinct().collect(Collectors.toList());
                    return response.askWhichDatatypeWithEntity(entity, yshape, bnlabels.size() == 1 ? bnlabels.get(0) : null, datatypeLabels, context);
                }
            } else if (condition != null) {
                logger.debug("4.0.2. to_bn，查询BN实体、条件实体和后续DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, condition);
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("4.0.2.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                } else if (datatypeLabels.size() == 1) {
                    logger.debug("4.0.2.1.只有1个DP时，判断条件BN");
                    List<ConditionEntityAndBNAndValue> conditionEntitiesAndValues = kgUtil.queryConditionEntitiesAndValuesWithDatatype(entity, condition, datatypeLabels.get(0));
                    if (conditionEntitiesAndValues.size() == 0) {
                        return faqResponse.faq(context, false);
                    }
                    if (conditionEntitiesAndValues.size() == 1) {
                        logger.debug("4.0.2.1.1.只有1个或者存在defaultCondition，直接回答");
                        return response.answerWithBN(entity, propertyLabel, conditionEntitiesAndValues.get(0).getBNAndValue().getBN(), datatypeLabels.get(0), conditionEntitiesAndValues.get(0).getBNAndValue().getValue(), context);
                    }

                    String conditionEntity = (String) context.getSlots().get("conditionEntity");
                    if (conditionEntity != null) {
                        for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                            if (conditionEntity.equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                                logger.debug("4.0.2.1.2. 结合condition实体查询，如果有答案则直接回答【实体】在【条件】下的【DP】为【答案】");
                                return response.answerWithCondition(entity, propertyLabel, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntity, datatypeLabels.get(0), conditionEntityAndValue.getBNAndValue().getValue(), context);
                            }
                        }
                    }

                    for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                        if ("默认".equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                            logger.debug("4.0.2.1.3.存在defaultCondition，直接回答");
                            return response.answerWithCondition(entity, propertyLabel, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntityAndValue.getConditionEntity().getLabel(), datatypeLabels.get(0), conditionEntityAndValue.getBNAndValue().getValue(), context);
                        }
                    }

                    logger.debug("4.0.2.1.4.多于1个，反问：您是想问【实体】在什么条件【undercondition domain的类里所有condition实体】下的【DP】？");
                    return response.askWhichConditionEntityWithDatatype(entity, conditionEntitiesAndValues.stream().map(ConditionEntityAndBNAndValue::getConditionEntity).collect(Collectors.toList()), datatypeLabels.get(0), context);
                } else {
                    logger.debug("4.0.2.2.多于1个DP时，反问：您是想问【BN】在【条件实体】时的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    if (datatypeLabels.contains(contextDatatype)) {
                        logger.debug("4.0.2.2.1.上文中存在DP之一");
                        List<ConditionEntityAndBNAndValue> conditionEntitiesAndValues = kgUtil.queryConditionEntitiesAndValuesWithDatatype(entity, condition, contextDatatype);
                        if (conditionEntitiesAndValues.size() == 0) {
                            return faqResponse.faq(context, false);
                        }
                        if (conditionEntitiesAndValues.size() == 1) {
                            logger.debug("4.0.2.2.1.1.只有1个或者存在defaultCondition，直接回答");
                            return response.answerWithBN(entity, propertyLabel, conditionEntitiesAndValues.get(0).getBNAndValue().getBN(), contextDatatype, conditionEntitiesAndValues.get(0).getBNAndValue().getValue(), context);
                        }

                        String conditionEntity = (String) context.getSlots().get("conditionEntity");
                        if (conditionEntity != null) {
                            for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                                if (conditionEntity.equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                                    logger.debug("4.0.2.2.1.2. 结合condition实体查询，如果有答案则直接回答【实体】在【条件】下的【DP】为【答案】");
                                    return response.answerWithCondition(entity, propertyLabel, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntity, contextDatatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                                }
                            }
                        }

                        for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                            if ("默认".equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                                logger.debug("4.0.2.2.1.3.存在defaultCondition，直接回答");
                                return response.answerWithCondition(entity, propertyLabel, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntityAndValue.getConditionEntity().getLabel(), contextDatatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                            }
                        }

                        logger.debug("4.0.2.2.1.4.多于1个，反问：您是想问【实体】在什么条件【undercondition domain的类里所有condition实体】下的【DP】？");
                        return response.askWhichConditionEntityWithDatatype(entity, conditionEntitiesAndValues.stream().map(ConditionEntityAndBNAndValue::getConditionEntity).collect(Collectors.toList()), datatypeLabels.get(0), context);
                    }
                    List<String> bnlabels = datatypesOfObjectOfEntity.stream().map(x -> x.getBn().getLabel()).distinct().collect(Collectors.toList());
                    return response.askWhichDatatypeWithEntity(entity, condition, bnlabels.size() == 1 ? bnlabels.get(0) : null, datatypeLabels, context);
                }
            } else {
                logger.debug("4.0.3.其余OP，查询BN实体和后续DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, properties.get(0));
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("4.0.3.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                } else if (datatypeLabels.size() == 1) {
                    logger.debug("4.0.3.1.只有1个DP时，直接回答值");
                    String datatypeOfObjectOfEntity = datatypeLabels.get(0);
                    List<BNAndValue> result = kgUtil.query(entity, properties.get(0), datatypeOfObjectOfEntity);
                    return response.answerWithBN(entity, properties.get(0), result.get(0).getBN(), datatypeOfObjectOfEntity, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                } else {
                    logger.debug("4.0.3.2.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    if (datatypeLabels.contains(contextDatatype)) {
                        logger.debug("4.0.2.2.1.上文中存在DP之一，直接回答");
                        List<BNAndValue> result = kgUtil.query(entity, properties.get(0), contextDatatype);
                        return response.answerWithBN(entity, properties.get(0), result.get(0).getBN(), contextDatatype, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                    }
                    List<String> bnlabels = datatypesOfObjectOfEntity.stream().map(x -> x.getBn().getLabel()).distinct().collect(Collectors.toList());
                    return response.askWhichDatatypeWithEntity(entity, properties.get(0), bnlabels.size() == 1 ? bnlabels.get(0) : null, datatypeLabels, context);
                }
            }
        }

        List<String> values = kgUtil.query(entity, datatype);
        if (values.size() > 0) {
            if(values.size() > 1){
                logger.debug("add.查到的值大于一，反问子属性");
                List<String> subProperty = kgUtil.querySubProperties(datatype);
                if(subProperty.size() > 1){
                    return response.askWhichSubProperties(entity,subProperty,context);
                }
              else{
                    logger.debug("4.1.查询到值，直接回答");
                    String value = values.size() == 1 ? values.get(0) : values.toString();
                    return response.answer(entity, datatype, value, context);
                }
            }
            else{
                logger.debug("4.1.查询到值，直接回答");
                String value = values.size() == 1 ? values.get(0) : values.toString();
                return response.answer(entity, datatype, value, context);

            }
        }

        logger.debug("4.2.查询【实体】OP【BN】属性【值】");
        List<ObjectProperty> objectsOfDatatype = kgUtil.queryObjectsOfDatatype(entity, datatype);
        // filter objects or context objects
        // if object property is mentioned in the current turn or the context, only this object property is considered
        for (int i = 0; i < objectsOfDatatype.size(); i++) {
            if (properties.contains(objectsOfDatatype.get(i).getUri())) {
                objectsOfDatatype = Collections.singletonList(objectsOfDatatype.get(i));
                break;
            }
        }
        String contextObject = (String) context.getSlots().get("contextObject");
        for (int i = 0; i < objectsOfDatatype.size(); i++) {
            if (Objects.equals(contextObject, objectsOfDatatype.get(i).getUri())) {
                objectsOfDatatype = Collections.singletonList(objectsOfDatatype.get(i));
                break;
            }
        }

        // filter bns with the same name as entity
        // if there are bns with the same label as entity, only these bn will be considered
        List<ObjectProperty> objectsOfDatatypeWithSameLabelBN = objectsOfDatatype.stream()
                .filter(x -> Objects.equals(x.getBN().getLabel(), entity)).collect(Collectors.toList());
        if (!objectsOfDatatypeWithSameLabelBN.isEmpty()) {
            objectsOfDatatype = objectsOfDatatypeWithSameLabelBN;
        }

        if (objectsOfDatatype.size() > 0) {
            Map<String, String> objectIRIsAndTypes = new HashMap<>();
            for (ObjectProperty objectProperty : objectsOfDatatype) {
                objectIRIsAndTypes.put(objectProperty.getUri(), objectProperty.getType());
            }
            if (objectIRIsAndTypes.size() == 1 && YSHAPE_PROPERTY.equals(objectIRIsAndTypes.values().iterator().next())) {
                logger.debug("4.2.1.只存在with_bn");
                String iri = objectIRIsAndTypes.keySet().iterator().next();
                List<BNAndValue> yshapeBNsAndValues = kgUtil.query(entity, iri, datatype);
                if (yshapeBNsAndValues.size() == 0) {
                    return faqResponse.faq(context, false);
                } else if (yshapeBNsAndValues.size() == 1) {
                    logger.debug("4.2.1.1.只有1个，直接回答");
                    return response.answerWithBN(entity, iri, yshapeBNsAndValues.get(0).getBN(), datatype, yshapeBNsAndValues.get(0).getValue(), context);
                } else {
                    logger.debug("4.2.1.2.多于1个，查询上文是否有相同的BN");
                    String contextBN = (String) context.getSlots().get("contextBN");
                    List<BlankNode> bns = yshapeBNsAndValues.stream().map(BNAndValue::getBN).collect(Collectors.toList());
                    if (contextBN != null && bns.stream().map(BlankNode::getLabel).anyMatch(contextBN::equals)) {
                        logger.debug("4.2.1.2.1.有相同的BN，按照1个BN1个属性处理");
                        ResponseExecutionResult result = knowledgeQueryPropertyWithBN.singleBNWithDP(contextBN, datatype, context);
                        if (result != null) {
                            return result;
                        }
                    } else {
                        logger.debug("4.2.1.2.2.反问：您是想问【BNs】哪个的【DP】？");
                        return response.askWhichBN(entity, bns, datatype, context);
                    }
                }
            } else if (objectIRIsAndTypes.size() == 1 && CONDITION_PROPERTY.equals(objectIRIsAndTypes.values().iterator().next())) {
                logger.debug("4.2.2.只存在条件BN");
                String iri = objectIRIsAndTypes.keySet().iterator().next();
                List<ConditionEntityAndBNAndValue> conditionEntitiesAndValues = kgUtil.queryConditionEntitiesAndValuesWithDatatype(entity, iri, datatype);
                if (conditionEntitiesAndValues.size() == 0) {
                    return faqResponse.faq(context, false);
                }
                if (conditionEntitiesAndValues.size() == 1) {
                    logger.debug("4.2.2.1.只有1个，直接回答");
                    return response.answerWithConditionWithBN(entity, iri, conditionEntitiesAndValues.get(0).getBNAndValue().getBN(), conditionEntitiesAndValues.get(0).getConditionEntity().getLabel(), datatype, conditionEntitiesAndValues.get(0).getBNAndValue().getValue(), context);
                }

                String conditionEntity = (String) context.getSlots().get("conditionEntity");
                if (conditionEntity != null) {
                    for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                        if (conditionEntity.equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                            logger.debug("4.2.2.0. 结合condition实体查询，如果有答案则直接回答【实体】在【条件】下的【DP】为【答案】");
                            return response.answerWithCondition(entity, iri, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntity, datatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                        }
                    }
                }

                for (ConditionEntityAndBNAndValue conditionEntityAndValue : conditionEntitiesAndValues) {
                    if ("默认".equals(conditionEntityAndValue.getConditionEntity().getLabel())) {
                        logger.debug("4.2.2.1.存在defaultCondition，直接回答");
                        return response.answerWithCondition(entity, iri, conditionEntityAndValue.getBNAndValue().getBN(), conditionEntityAndValue.getConditionEntity().getLabel(), datatype, conditionEntityAndValue.getBNAndValue().getValue(), context);
                    }
                }

                logger.debug("4.2.2.2.多于1个，反问：您是想问【实体】在什么条件【undercondition domain的类里所有condition实体】下的【DP】？");
                return response.askWhichConditionEntityWithDatatype(entity, conditionEntitiesAndValues.stream().map(ConditionEntityAndBNAndValue::getConditionEntity).collect(Collectors.toList()), datatype, context);
            } else {
                logger.debug("4.2.3.存在OP");
                if (objectsOfDatatype.size() == 0) {
                    return faqResponse.faq(context, false);
                } else if (objectsOfDatatype.size() == 1) {
                    logger.debug("4.2.3.1.只有1个，直接回答");
                    List<BNAndValue> result = kgUtil.query(entity, objectsOfDatatype.get(0).getUri(), datatype);
                    return response.answerWithBN(entity, objectsOfDatatype.get(0).getUri(), result.get(0).getBN(), datatype, result.get(0).getValue(), context);
                } else {
                    String contextBN = (String) context.getSlots().get("contextBN");
                    if (contextBN != null && objectsOfDatatype.stream().map(ObjectProperty::getBN).map(BlankNode::getLabel).anyMatch(contextBN::equals)) {
                        logger.debug("4.2.3.2.上文有相同的BN，按照1个BN1个属性处理");
                        ResponseExecutionResult result = knowledgeQueryPropertyWithBN.singleBNWithDP(contextBN, datatype, context);
                        if (result != null) {
                            return result;
                        }
                    } else {
                        logger.debug("4.2.3.3.多于1个，反问：您是想问【实体】【OPs】，还是【BNs】哪个的【DP】？");
                        return response.askWhichObject(entity, objectsOfDatatype, datatype, context);
                    }
                }
            }
        }

        logger.debug("4.5.检查实体属性的合法性");
        List<String> validClasses = kgUtil.queryValidClassesWithEntityAndDatatype(entity, datatype);
        if (validClasses.size() > 0) {
            logger.debug("4.5.1.允许实体和该属性，回答：不知道【实体】的【属性】");
            if (Objects.equals(datatype, properties.get(0))) {
                return response.answerNoValueWithDatatype(entity, datatype, context);
            } else {
                return response.answerNoValueWithObject(entity, properties.get(0), context);
            }
        } else {
            logger.debug("4.5.2.不合法，按只识别到1个意图处理/按只识别到1个实体处理（暂定前者）");
            logger.debug("3. 只识别到1个意图");
            return noEntityAndSingleProperty(yshape, diffusion, condition, object, datatype, properties, context);
        }
    }

    private ResponseExecutionResult singleEntityAndNoProperty(
            String entity, List<String> datatypesOfEntity, List<ObjectProperty> objectsOfEntity, Context context) {
        // handle bn with the same name as entity
        // 逾期-逾期(bn)-后果影响
        Set<String> datatypesOfEntityAndBN = new HashSet<>(datatypesOfEntity);
        List<Integer> toBeRemoved = new LinkedList<>();
        for (int i = 0; i < objectsOfEntity.size(); i++) {
            ObjectProperty objectProperty = objectsOfEntity.get(i);
            if (entity.equals(objectProperty.getBN().getLabel())) {
                if (toBeRemoved.isEmpty()) {
                    datatypesOfEntityAndBN.addAll(kgUtil.queryDatatypesWithBNLabel(entity));
                }
                toBeRemoved.add(0, i);
            }
        }
        for (int i : toBeRemoved) {
            objectsOfEntity.remove(i);
        }

        if (datatypesOfEntityAndBN.size() == 0 && objectsOfEntity.size() == 0) {
            logger.debug("1.4. 没有查询到DP/OP，转FAQ");
            return faqResponse.faq(context, false);
        } else if (datatypesOfEntityAndBN.size() == 1 && objectsOfEntity.size() == 0) {
            logger.debug("1.1. 查询到只有1个DP的值时，当作一个实体一个属性");
            String datatype = datatypesOfEntityAndBN.iterator().next();
            List<String> properties = Collections.singletonList(datatype);
            return singleEntityAndSingleProperty(entity, null, null, null, null, datatype, properties, context);
        } else if (datatypesOfEntityAndBN.size() == 0 && objectsOfEntity.size() == 1) {
            logger.debug("1.2. 查询到只有1个OP时");
            ObjectProperty objectOfEntity = objectsOfEntity.get(0);
            if (YSHAPE_PROPERTY.equals(objectOfEntity.getType())) {
                logger.debug("1.2.1.with_bn，查询BN实体、另一个实体和后续的DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, objectOfEntity.getUri());
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("1.2.1.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                } else if (datatypeLabels.size() == 1) {
                    logger.debug("1.2.1.1.只有1个DP时，直接回答值");
                    String datatypeOfObjectOfEntity = datatypeLabels.get(0);
                    List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), datatypeOfObjectOfEntity);
                    return response.answerWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), datatypeOfObjectOfEntity, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                } else {
                    logger.debug("1.2.1.2.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    if (datatypeLabels.contains(contextDatatype)) {
                        logger.debug("1.2.1.2.1.上文存在DP之一，直接回答");
                        List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), contextDatatype);
                        return response.answerWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), contextDatatype, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                    }
                    return response.askWhichDatatypeWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), datatypeLabels, context);
                }
            } else if (CONDITION_PROPERTY.equals(objectOfEntity.getType())) {
                logger.debug("1.2.2. to_bn，查询BN实体、条件实体和后续DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, objectOfEntity.getUri());
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("1.2.2.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                }
                List<ConditionEntityAndBNAndValue> conditionEntitiesAndValues = kgUtil.queryConditionEntitiesAndValuesWithDatatype(entity, objectOfEntity.getUri(), datatypeLabels.get(0));
                if (conditionEntitiesAndValues.size() == 0) {
                    throw new DMException("No condition entity and value with given ConditionProperty.");
                }
                String conditionEntity = conditionEntitiesAndValues.get(0).getConditionEntity().getLabel();
                if (datatypeLabels.size() == 1) {
                    logger.debug("1.2.2.1.只有1个DP时，直接回答值");
                    String datatypeOfObjectOfEntity = datatypeLabels.get(0);
                    List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), datatypeOfObjectOfEntity);
                    return response.answerWithConditionWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), conditionEntity, datatypeOfObjectOfEntity, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                }
                logger.debug("1.2.2.2.多于1个DP时，反问：您是想问【BN】在【条件实体】时的【DPs】中哪一个？");
                String contextDatatype = (String) context.getSlots().get("contextDatatype");
                if (datatypeLabels.contains(contextDatatype)) {
                    logger.debug("1.2.2.2.1.上文存在DP之一，直接回答");
                    List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), contextDatatype);
                    return response.answerWithConditionWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), conditionEntity, contextDatatype, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                }
                return response.askWhichDatatypeWithConditionWithBN(entity, objectOfEntity.getUri(), objectOfEntity.getBN(), conditionEntity, datatypeLabels, context);
            } else {
                logger.debug("1.2.4.其余OP，查询BN实体和后续DP");
                List<BNAndDatatypeAndValue> datatypesOfObjectOfEntity = kgUtil.queryDatatypeOfObject(entity, objectOfEntity.getUri());
                List<String> datatypeLabels = datatypesOfObjectOfEntity.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                if (datatypeLabels.size() == 0) {
                    logger.debug("1.2.4.0. 没有DP时，转FAQ");
                    return faqResponse.faq(context, false);
                } else if (datatypeLabels.size() == 1) {
                    logger.debug("1.2.4.1.只有1个DP时，直接回答值");
                    String datatypeOfObjectOfEntity = datatypeLabels.get(0);
                    List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), datatypeOfObjectOfEntity);
                    return response.answerWithBN(entity, objectOfEntity.getLabel(), result.get(0).getBN(), datatypeOfObjectOfEntity, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                } else {
                    logger.debug("1.2.4.2.多于1个DP时，反问：您是想问【BN】的【DPs】中哪一个？");
                    String contextDatatype = (String) context.getSlots().get("contextDatatype");
                    if (datatypeLabels.contains(contextDatatype)) {
                        logger.debug("1.2.4.2.1.上文存在DP之一，直接回答");
                        List<BNAndValue> result = kgUtil.query(entity, objectOfEntity.getUri(), contextDatatype);
                        return response.answerWithBN(entity, objectOfEntity.getLabel(), result.get(0).getBN(), contextDatatype, result.size() == 1 ? result.get(0).getValue() : result.stream().map(BNAndValue::getValue).collect(Collectors.toSet()).toString(), context);
                    }
                    return response.askWhichDatatype(entity, objectOfEntity.getUri(), datatypeLabels, context);
                }
            }
        } else {
            logger.debug("1.3. 查询到多个DP/OP时");
            String contextDatatype = (String) context.getSlots().get("contextDatatype");
            List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
            if(contextEntities != null){
                for(String contextEnitity : contextEntities ){
                    if(entity.equals(contextEnitity)){
                        logger.debug("add,上文的1个实体和该轮的一个实体相同时");
                        return response.askWhichProperty(entity,datatypesOfEntityAndBN,objectsOfEntity,context);
                    }
                }
            }

            if (contextDatatype != null && datatypesOfEntityAndBN.contains(contextDatatype)) {
                logger.debug("1.3.1.上文中是否有相同的DP/OP，有则按识别出1个实体1个属性处理");
                List<String> properties = Collections.singletonList(contextDatatype);
                return singleEntityAndSingleProperty(entity, null, null, null, null, contextDatatype, properties, context);
            }



            if (datatypesOfEntityAndBN.size() == 0) {
                // 1.3.2. 只有一种with_bn
                String uniqueYshape = null;
                for (ObjectProperty objectOfEntity : objectsOfEntity) {
                    if (YSHAPE_PROPERTY.equals(objectOfEntity.getType())) {
                        if (uniqueYshape == null) {
                            uniqueYshape = objectOfEntity.getUri();
                            continue;
                        }
                        if (uniqueYshape.equals(objectOfEntity.getUri())) {
                            continue;
                        }
                    }
                    uniqueYshape = null;
                    break;
                }
                if (uniqueYshape != null) {
                    logger.debug("1.3.2.只有一种with_bn，查询另一个实体，上文中是否有相同实体");
                    List<String> anotherEntities = kgUtil.queryAnotherYshapeEntities(entity, uniqueYshape);
//                    List<String> contextEntities = (List<String>) context.getSlots().get("contextEntity");
                    if (contextEntities != null) {
                        for (String contextEntity : contextEntities) {
                            if (anotherEntities.contains(contextEntity)) {
                                logger.debug("1.3.2.1.有则按识别出2个实体和with_bn处理");
                                List<YshapeBNAndDP> yshapeBNAndDPs = kgUtil.queryYshapeBNLabelsAndDatatypes(
                                        Collections.singletonList(entity), Collections.singletonList(contextEntity));
                                logger.debug("5.1.1. 只有1个withBN实体，直接回答");
                                String result = kgUtil.queryBNDatatype(yshapeBNAndDPs.get(0).getBN(), yshapeBNAndDPs.get(0).getDatatype());
                                return response.answerYshape(result, yshapeBNAndDPs.get(0), context);
                            }
                        }
                    }
                    logger.debug("1.3.2.2.没有则查询BN，反问：您是想问【BNs】哪个");
                    List<BlankNode> bns = objectsOfEntity.stream().map(ObjectProperty::getBN).collect(Collectors.toList());
                    return response.askWhichBN(entity, bns, context);
                }

                // 1.3.3. 只有一种to_bn
                String uniqueCondition = null;
                for (ObjectProperty objectOfEntity : objectsOfEntity) {
                    if (CONDITION_PROPERTY.equals(objectOfEntity.getType())) {
                        if (uniqueCondition == null) {
                            uniqueCondition = objectOfEntity.getUri();
                            continue;
                        }
                        if (uniqueCondition.equals(objectOfEntity.getUri())) {
                            continue;
                        }
                    }
                    uniqueCondition = null;
                    break;
                }
                if (uniqueCondition != null) {
                    if ("http://www.jdb.com/rrx_kf#to_bn".equals(uniqueCondition)) {
                        logger.debug("1.3.3.只有一种to_bn，查询条件实体，上文中是否有相同条件实体");

                        List<ConditionEntityAndBN> conditionEntities = kgUtil.queryConditionEntities(entity, uniqueCondition);
                        String contextConditionEntity = (String) Optional.ofNullable(context.getSlots().get("conditionEntity")).orElse(context.getSlots().get("contextConditionEntity"));
                        if (conditionEntities != null) {
                            for (ConditionEntityAndBN conditionEntityAndBN : conditionEntities) {
                                if (Objects.equals(conditionEntityAndBN.getConditionEntity().getLabel(), contextConditionEntity)) {
                                    logger.debug("1.3.3.1.有则按识别出1个实体和条件实体处理（直接回答）");
                                    List<DatatypeAndValue> datatypes = kgUtil.queryConditionDatatypesAndValues(entity, uniqueCondition, contextConditionEntity);
                                    if (datatypes.size() == 0) {
                                        logger.debug("1.3.3.1.0. 没有DP，faq");
                                        return faqResponse.faq(context, false);
                                    } else if (datatypes.size() == 1) {
                                        logger.debug("1.3.3.1.1. 只有1个DP，直接回答");
                                        return response.answerWithCondition(entity, uniqueCondition, conditionEntityAndBN.getBN(), contextConditionEntity, datatypes.get(0).getDatatype(), datatypes.get(0).getValue(), context);
                                    } else {
                                        logger.debug("1.3.3.1.2. 多个DP，反问：您是问【BN】的哪个【DP】");
                                        return response.askWhichDatatypeWithConditionWithBN(entity, uniqueCondition, conditionEntityAndBN.getBN(), contextConditionEntity, datatypes.stream().map(DatatypeAndValue::getDatatype).collect(Collectors.toSet()), context);
                                    }
                                }
                            }
                            for (ConditionEntityAndBN conditionEntity : conditionEntities) {
                                if (Objects.equals(conditionEntity.getConditionEntity().getLabel(), "默认")) {
                                    contextConditionEntity = "默认";
                                    logger.debug("1.3.3.2.存在defaultCondition，直接回答");
                                    List<DatatypeAndValue> datatypes = kgUtil.queryConditionDatatypesAndValues(entity, uniqueCondition, contextConditionEntity);
                                    if (datatypes.size() == 0) {
                                        logger.debug("1.3.3.2.0. 没有DP，faq");
                                        return faqResponse.faq(context, false);
                                    } else if (datatypes.size() == 1) {
                                        logger.debug("1.3.3.2.1. 只有1个DP，直接回答");
                                        return response.answerWithCondition(entity, uniqueCondition, conditionEntity.getBN(), contextConditionEntity, datatypes.get(0).getDatatype(), datatypes.get(0).getValue(), context);
                                    } else {
                                        logger.debug("1.3.3.2.2. 多个DP，反问：您是问【BN】的哪个【DP】");
                                        return response.askWhichDatatypeWithConditionWithBN(entity, uniqueCondition, conditionEntity.getBN(), contextConditionEntity, datatypes.stream().map(DatatypeAndValue::getDatatype).collect(Collectors.toSet()), context);
                                    }
                                }
                            }
                        }
                        logger.debug("1.3.3.3.没有则反问：您是想问什么条件【undercondition domain的类里所有condition实体】下的【BN】/【实体】【OP】");
                        if (conditionEntities == null || conditionEntities.isEmpty()) {
                            return faqResponse.faq(context, false);
                        }
                        List<String> bnlabels = conditionEntities.stream().map(ConditionEntityAndBN::getBN).map(BlankNode::getLabel).distinct().collect(Collectors.toList());
                        if (bnlabels.size() == 1) {
                            return response.askWhichConditionEntity(entity, conditionEntities.stream().map(ConditionEntityAndBN::getConditionEntity).collect(Collectors.toList()), context);
                        } else {
                            return response.askWhichConditionEntityWithBN(entity, bnlabels.get(0), conditionEntities.stream().map(ConditionEntityAndBN::getConditionEntity).collect(Collectors.toList()), context);
                        }
                    }
                }

                // 1.3.4. 只有一种普通OP
                String uniqueObject = null;
                for (ObjectProperty objectOfEntity : objectsOfEntity) {
                    if (uniqueObject == null) {
                        uniqueObject = objectOfEntity.getUri();
                        continue;
                    }
                    if (uniqueObject.equals(objectOfEntity.getUri())) {
                        continue;
                    }
                    uniqueObject = null;
                    break;
                }
                if (uniqueObject != null) {
                    logger.debug("1.3.4.只有一种普通OP，查询DP");
                    List<BlankNode> bns = kgUtil.queryBNs(entity, uniqueObject);
                    List<BNAndDatatypeAndValue> datatypes = kgUtil.queryDatatypeOfObject(entity, uniqueObject);
                    List<String> datatypeLabels = datatypes.stream().map(x -> x.getDatatypeAndValue().getDatatype()).distinct().collect(Collectors.toList());
                    if (datatypeLabels.size() == 0) {
                        return faqResponse.faq(context, false);
                    } else if (datatypeLabels.size() == 1) {
                        logger.debug("1.3.4.1.只有1个DP，反问：您是想问【BNs】在条件【】哪种情况下的【DP】？");
                        List<ConditionEntityAndBN> conditionEntities = kgUtil.queryConditionEntities(entity, uniqueObject);
                        String contextConditionEntity = (String) Optional.ofNullable(context.getSlots().get("conditionEntity")).orElse(context.getSlots().get("contextConditionEntity"));

                        if (conditionEntities != null) {
                            for (ConditionEntityAndBN conditionEntity : conditionEntities) {
                                if (Objects.equals(conditionEntity.getConditionEntity().getLabel(), contextConditionEntity)) {
                                    logger.debug("1.3.4.1.1.有则按识别出1个实体和条件实体处理（直接回答）");
                                    logger.debug("1.3.4.1.1.1. 只有1个DP，直接回答");
                                    List<DatatypeAndValue> values = kgUtil.queryConditionDatatypesAndValues(entity, uniqueObject, contextConditionEntity);
                                    return response.answerWithCondition(entity, uniqueObject, conditionEntity.getBN(), contextConditionEntity, datatypeLabels.get(0), values.get(0).getValue(), context);
                                }
                            }
                            for (ConditionEntityAndBN conditionEntity : conditionEntities) {
                                if (Objects.equals(conditionEntity.getConditionEntity().getLabel(), "默认")) {
                                    contextConditionEntity = "默认";
                                    logger.debug("1.3.4.2.2.存在defaultCondition，直接回答");
                                    logger.debug("1.3.4.2.2.1. 只有1个DP，直接回答");
                                    List<DatatypeAndValue> values = kgUtil.queryConditionDatatypesAndValues(entity, uniqueObject, contextConditionEntity);
                                    return response.answerWithCondition(entity, uniqueObject, conditionEntity.getBN(), contextConditionEntity, datatypeLabels.get(0), values.get(0).getValue(), context);
                                }
                            }
                            return response.askWhichConditionEntityWithDatatype(entity, conditionEntities.stream().map(ConditionEntityAndBN::getConditionEntity).collect(Collectors.toList()), datatypeLabels.get(0), context);
                        }
                    } else {
                        logger.debug("1.3.4.2.多于1个DP，反问：您是想问【BNs】的【DPs】中的哪一个？");
                        if (bns.size() == 1) {
                            return response.askWhichDatatypeWithBN(entity, uniqueObject, bns.get(0), datatypeLabels, context);
                        } else {
                            return response.askWhichDatatypeWithBNs(entity, uniqueObject, bns, datatypeLabels, context);
                        }
                    }
                }
            }


            logger.debug("1.3.5. 其余情况，反问：您是想了解【实体】的【DPs、OPs】或者【BNs】中哪一个？");

            return response.askWhichProperty(entity, datatypesOfEntityAndBN, objectsOfEntity, context);
        }
    }

}
