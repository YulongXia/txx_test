package responses;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.kg.KnowledgeStatus;
import ai.hual.labrador.nlg.ResponseAct;
import com.google.common.collect.*;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;
import utils.KnowledgeQueryUtils;
import utils.LimitSub;

import java.util.*;
import java.util.stream.Collectors;
import responses.FAQResponse;

public class KnowledgeQueryResponse {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryResponse.class);
    private KnowledgeQueryUtils kgUtil;
    private AccessorRepository accessorRepository;
    private Map<String, String> RECOMM = new HashMap<String, String>() {{
        put("公积金#联名卡", "公积金联名卡");
    }};

    public static final String DISEASE = "疾病";
    public static final String TYPES_OF_INSURANCE = "险种";
    public static final String SPECIAL_CROWD = "特殊人群";

    private static final String UNDERWRITTING_SCALE = "核保尺度";
    private static final String WITH_BN = "http://hual.ai/taikang#with_bn";

    public static final String HOT_ISSUE = "热门问题";

    public static final String DEFINATION = "定义";

    public KnowledgeQueryResponse(KnowledgeQueryUtils kgUtil, AccessorRepository accessorRepository) {
        this.kgUtil = kgUtil;
        this.accessorRepository = accessorRepository;
    }

    private ResponseExecutionResult checkDisabled(String entity, String datatype) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("disabled"));
        result.setInstructions(Collections.emptyList());

        String entityIRI = kgUtil.queryEntityIRI(entity);
        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(entityIRI) == KnowledgeStatus.DISABLED) {
            result.getResponseAct().put("instance", entity);
            return result;
        }

        String datatypeIRI = kgUtil.queryDatatypeIRI(datatype);
        if (accessorRepository.getKnowledgeStatusAccessor().propertyStatus(entityIRI, datatypeIRI) == KnowledgeStatus.DISABLED) {
            result.getResponseAct()
                    .put("instance", entity)
                    .put("property", datatype);
            return result;
        }

        return null;
    }

    private ResponseExecutionResult checkDisabled(BlankNode bn, String datatype) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("disabled"));
        result.setInstructions(Collections.emptyList());

        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(bn.getIri()) == KnowledgeStatus.DISABLED) {
            result.getResponseAct().put("instance", bn.getLabel());
            return result;
        }

        String datatypeIRI = kgUtil.queryDatatypeIRI(datatype);
        if (accessorRepository.getKnowledgeStatusAccessor().propertyStatus(bn.getIri(), datatypeIRI) == KnowledgeStatus.DISABLED) {
            result.getResponseAct()
                    .put("instance", bn.getLabel())
                    .put("property", datatype);
            return result;
        }

        return null;
    }

    private ResponseExecutionResult checkDisabled(String entity, BlankNode bn, String datatype) {
        String entityIRI = kgUtil.queryEntityIRI(entity);
        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(entityIRI) == KnowledgeStatus.DISABLED) {
            return disabled(entity);
        }

        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(bn.getIri()) == KnowledgeStatus.DISABLED) {
            return disabled(bn.getLabel());
        }

        String datatypeIRI = kgUtil.queryDatatypeIRI(datatype);
        if (accessorRepository.getKnowledgeStatusAccessor().propertyStatus(bn.getIri(), datatypeIRI) == KnowledgeStatus.DISABLED) {
            return disabled(bn.getLabel(), datatype);
        }

        return null;
    }

    private ResponseExecutionResult checkDisabled(YshapeBNAndDP yshapeBNAndDP) {
        String entityIRI1 = kgUtil.queryEntityIRI(yshapeBNAndDP.getEntity1());
        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(entityIRI1) == KnowledgeStatus.DISABLED) {
            return disabled(yshapeBNAndDP.getEntity1());
        }

        String entityIRI2 = kgUtil.queryEntityIRI(yshapeBNAndDP.getEntity2());
        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(entityIRI2) == KnowledgeStatus.DISABLED) {
            return disabled(yshapeBNAndDP.getEntity2());
        }

        if (accessorRepository.getKnowledgeStatusAccessor().instanceStatus(yshapeBNAndDP.getBN().getIri()) == KnowledgeStatus.DISABLED) {
            return disabled(yshapeBNAndDP.getBN().getLabel());
        }

        String datatypeIRI = kgUtil.queryDatatypeIRI(yshapeBNAndDP.getDatatype());
        if (accessorRepository.getKnowledgeStatusAccessor().propertyStatus(yshapeBNAndDP.getBN().getIri(), datatypeIRI) == KnowledgeStatus.DISABLED) {
            return disabled(yshapeBNAndDP.getBN().getLabel(), yshapeBNAndDP.getDatatype());
        }

        return null;
    }

    public ResponseExecutionResult disabled(String instance) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("disabled")
                .put("instance", instance));
        result.setInstructions(Collections.emptyList());
        return result;
    }

    public ResponseExecutionResult disabled(String instance, String property) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("disabled")
                .put("instance", instance)
                .put("property", property));
        result.setInstructions(Collections.emptyList());
        return result;
    }

    public ResponseExecutionResult guessYshapeDP(YshapeBNAndDP yshapeBNAndDP, Context context) {
        context.getSlots().put("contextEntity", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()));
        context.getSlots().put("contextBN", yshapeBNAndDP.getBN().getLabel());
        context.getSlots().put("contextObject", yshapeBNAndDP.getYshapeURI());
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        context.getSlots().put("guess.entity", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()));
        context.getSlots().put("guess.HualDataTypeProperty", yshapeBNAndDP.getDatatype());

        ResponseExecutionResult result = new ResponseExecutionResult();
//        result.setResponseAct(new ResponseAct("guess")
//                .put("entity", yshapeBNAndDP.getBN().getLabel())
//                .put("datatype", yshapeBNAndDP.getDatatype()));
        result.setResponseAct(new ResponseAct("recommendation"));
//        result.setInstructions(Collections.singletonList(
//                new Instruction("suggestion_yes_no")
//                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                        .addParam("entities", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()))
//                        .addParam("object", yshapeBNAndDP.getYshapeURI())
//                        .addParam("bnlabel", yshapeBNAndDP.getBN().getLabel())
//                        .addParam("condition", null)
//                        .addParam("datatype", yshapeBNAndDP.getDatatype())
//                        .addParam("suggestions", Arrays.asList("是", "否"))));

        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("关于%s,%s的问题", yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()))
                        .addParam("items", Arrays.asList(String.format("%s和%s的%s", yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2(), yshapeBNAndDP.getDatatype()))),
                new Instruction("feedback").addParam("display", "title")
        ));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeOfYshape(List<YshapeBNAndDP> yshapeBNAndDPs, Context context) {
        context.getSlots().put("contextEntity", Arrays.asList(yshapeBNAndDPs.get(0).getEntity1(), yshapeBNAndDPs.get(0).getEntity2()));
        context.getSlots().put("contextBN", yshapeBNAndDPs.get(0).getBN().getLabel());
        context.getSlots().put("contextObject", yshapeBNAndDPs.get(0).getYshapeURI());
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> dps = yshapeBNAndDPs.stream().map(YshapeBNAndDP::getDatatype).collect(Collectors.toSet());
        YshapeBNAndDP yshapeBNAndDP = yshapeBNAndDPs.get(0);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("entity", yshapeBNAndDP.getBN().getLabel())
                .put("datatypes", dps));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()))
                        .addParam("object", yshapeBNAndDP.getYshapeURI())
                        .addParam("bnlabel", yshapeBNAndDP.getBN().getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", dps)));
        return result;
    }

    public ResponseExecutionResult askWhichBNOfYshape(List<YshapeBNAndDP> yshapeBNAndDPs, Context context) {
        context.getSlots().put("contextEntity", Arrays.asList(yshapeBNAndDPs.get(0).getEntity1(), yshapeBNAndDPs.get(0).getEntity2()));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", yshapeBNAndDPs.get(0).getYshapeURI());
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> bns = yshapeBNAndDPs.stream().map(YshapeBNAndDP::getBN).map(BlankNode::getLabel).collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bns));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Arrays.asList(yshapeBNAndDPs.get(0).getEntity1(), yshapeBNAndDPs.get(0).getEntity2()))
                        .addParam("object", yshapeBNAndDPs.get(0).getYshapeURI())
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", bns)));
        return result;
    }

    public ResponseExecutionResult askWhichBNOfYshapeWithDatatype(String datatype, List<YshapeBNAndDP> yshapeBNAndDPs, Context context) {
        context.getSlots().put("contextEntity", Arrays.asList(yshapeBNAndDPs.get(0).getEntity1(), yshapeBNAndDPs.get(0).getEntity2()));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", yshapeBNAndDPs.get(0).getYshapeURI());
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> bns = yshapeBNAndDPs.stream().map(YshapeBNAndDP::getBN).map(BlankNode::getLabel).collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bns)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Arrays.asList(yshapeBNAndDPs.get(0).getEntity1(), yshapeBNAndDPs.get(0).getEntity2()))
                        .addParam("object", yshapeBNAndDPs.get(0).getYshapeURI())
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("suggestions", bns)));
        return result;
    }

    public ResponseExecutionResult askWhichEntity(Collection<String> entities, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("lastWhichEntity", entities);

        Set<String> ents = entities.stream().collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        List<String> ents5 = LimitSub.get5(ents);
        if (entities.size() > 5) {
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("entities", ents));
        } else {
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("entities", entities));
        }


//        for (int i = 0; i < ents5.size(); i++) {
//            result.getResponseAct().put("entity" + i, ents5.get(i));
//        }

        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_ents")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", ents)));
        return result;
    }

    public ResponseExecutionResult askWhichEntityWithDatatype(Collection<String> entities, String datatype, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("lastWhichEntity", entities);

        Set<String> ents = entities.stream().collect(Collectors.toSet());
        Set<String> items = new HashSet<>();
        String unique_answer = null;
        String answer_entity = null;
        String answer_cp = null;
        String answer_dp = null;
        for(String ent: ents){
            if(items.size() > 10)
                break;
            List<EntityAndCpAndDatatypeAndValue> ecdvs = kgUtil.queryEntityAndCpAndDatatypeAndValueWithEntityAndDatatype(ent,datatype);
            Map<String,List<EntityAndCpAndDatatypeAndValue>> aggregate = ecdvs.stream().collect(Collectors.groupingBy(x -> String.format("%s:%s:%s",x.getEntity(),x.getComplex(),
                                x.getDatatypeAndValue().getDatatype()),Collectors.toList()));
            List<List<EntityAndCpAndDatatypeAndValue>> candidates = aggregate.values().stream().filter(x -> x.size() == 1).collect(Collectors.toList());
            if(candidates !=null && candidates.size() != 0){
                for(List<EntityAndCpAndDatatypeAndValue> candidate: candidates){
                    String complex = kgUtil.queryLabelWithIRI(candidate.get(0).getComplex());
                    if(complex != null && complex.trim().length() != 0 && complex.equals("http://hual.ai/taikang/taikang_rs#with_bn"))
                        continue;
                    answer_entity = candidate.get(0).getEntity();
                    answer_cp = complex;
                    answer_dp = candidate.get(0).getDatatypeAndValue().getDatatype();
                    unique_answer = candidate.get(0).getDatatypeAndValue().getValue();
                    break;
                }
            }
            for(EntityAndCpAndDatatypeAndValue ecdv:ecdvs){
                String complex = kgUtil.queryLabelWithIRI(ecdv.getComplex());
                if(complex != null && complex.trim().length() != 0 && complex.equals("http://hual.ai/taikang/taikang_rs#with_bn"))
                    continue;
                if(answer_entity != null && answer_cp != null && answer_dp != null
                        && ecdv.getEntity().equals(answer_entity)
                        && ecdv.getComplex().equals(answer_cp)
                        && ecdv.getDatatypeAndValue().getDatatype().equals(answer_dp))
                    continue;
                items.add(String.format("%s%s的%s",ecdv.getEntity(),complex == null || complex.trim().length() == 0||complex.startsWith("http://")?"":complex,ecdv.getDatatypeAndValue().getDatatype()));
            }

        }
        items = processRecommendations(items.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("AnswerAndRecommendation"));
//        if(entities.size() > 5 ){
//            List<String> ents5 = LimitSub.get5(ents);
//
//            result.setResponseAct(new ResponseAct("whichEntity")
//                    .put("entities", ents5)
//                    .put("property", datatype));
//        }else{
//            result.setResponseAct(new ResponseAct("whichEntity")
//                    .put("entities", entities)
//                    .put("property", datatype));
//        }


//        List<String> ents5 = LimitSub.get5(ents);
//        for (int i = 0; i < ents5.size(); i++) {
//            result.getResponseAct().put("entity" + i, ents5.get(i));
//        }
        if(unique_answer != null) {
            result.setInstructions(Arrays.asList(
                    new Instruction("suggestion_kb_ents")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", null)
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", datatype)
                            .addParam("suggestions", entities),
                    new Instruction("info_card").addParam("title",String.format("%s%s的%s",answer_entity,answer_cp,answer_dp)).addParam("content",unique_answer),
                    new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", datatype))
                            .addParam("items", items.stream().collect(Collectors.toList())),
                    new Instruction("input_button")
                            .addParam("buttons",Arrays.asList(HOT_ISSUE)),
                    new Instruction("feedback").addParam("display", "true")));
        }else{
            result.setInstructions(Arrays.asList(
                    new Instruction("suggestion_kb_ents")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", null)
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", datatype)
                            .addParam("suggestions", entities),
                    new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", datatype))
                            .addParam("items", items.stream().collect(Collectors.toList())),
                    new Instruction("input_button")
                            .addParam("buttons",Arrays.asList(HOT_ISSUE)),
                    new Instruction("feedback").addParam("display", "true")));
        }

        return result;
    }

    public ResponseExecutionResult askWhichEntityWithObject(Collection<String> entities, String object, String objectLabel, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("lastWhichEntity", entities);

        Set<String> ents = entities.stream().collect(Collectors.toSet());
        List<String> ents5 = LimitSub.get5(ents);
        List<String> recommends ;
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("recommendation"));
        String label = kgUtil.queryLabelWithIRI(object);
        if (entities.size() > 5) {
//            result.setResponseAct(new ResponseAct("whichEntity")
//                    .put("property", objectLabel)
//                    .put("entities", ents5));
            recommends = ents5.stream().map(x -> String.format("%s%s",x,label)).collect(Collectors.toList());
        } else {
//            result.setResponseAct(new ResponseAct("whichEntity")
//                    .put("property", objectLabel)
//                    .put("entities", entities));
            recommends = entities.stream().map(x -> String.format("%s%s",x,label)).collect(Collectors.toList());
        }

        recommends = processRecommendations(recommends, context);

//        for (int i = 0; i < ents5.size(); i++) {
//            result.getResponseAct().put("entity" + i, ents5.get(i));
//        }

        result.setInstructions(Arrays.asList(
                new Instruction("suggestion_kb_ents")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", object)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", entities),
                new Instruction("recommendation").addParam("title",String.format("更多关于%s的问题",label)).addParam("items",recommends),
                new Instruction("input_button")
                    .addParam("buttons",Arrays.asList(HOT_ISSUE)),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult askWhichDatatype(String entity, String object, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichSubProperties(String entity, String datatype, Collection<String> subProperties, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        EntitiesAndTheirProperties entitiesAndTheirProperties = kgUtil.queryProperties(entity);
        ListMultimap<String, String> entitiesAndTheirDatatypes = entitiesAndTheirProperties.getEntitiesAndTheirDatatypes();
        ListMultimap<String, ObjectProperty> entitiesAndTheirObjects = entitiesAndTheirProperties.getEntitiesAndTheirObjects();
        logger.debug("entity {} and its datatypes {}.", entity, entitiesAndTheirDatatypes.get(entity));
        List<String> datatypes = entitiesAndTheirDatatypes.get(entity);
        datatypes.removeAll(subProperties);
        List<ObjectProperty> objects = entitiesAndTheirObjects.get(entity);

        Set<String> sentences = new HashSet<>();
        sentences.addAll(datatypes.stream().map(x -> String.format("%s的%s", entity, x)).collect(Collectors.toList()));
        for (ObjectProperty object : objects) {
            List<String> datatypesofbn = kgUtil.querydatatypeofBNWithCp(entity, object.getUri());
            for (String datatypeofbn : datatypesofbn) {
                sentences.add(String.format("%s%s的%s", entity
                        , object.getLabel() != null && object.getLabel().length() != 0 ? String.format("的%s", object.getLabel()) : object.getBN().getLabel() == null || object.getBN().getLabel().length() == 0 ? "" : String.format("的%s", object.getBN().getLabel())
                        , datatypeofbn
                ));
            }
        }
        sentences = processRecommendations(sentences.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());

        List<Instruction> instructions = new ArrayList<>();
        for (String subProperty : subProperties) {
            List<String> res = kgUtil.queryValuewithEntityAndDatatype(entity, subProperty);
            instructions.add(new Instruction("info_card").addParam("title", String.format("%s的%s", entity, subProperty)).addParam("content", res == null || res.size() == 0 ? "" : res.get(0)));
        }
        if (sentences.size() > 0) {
            instructions.add(new Instruction("recommendation").addParam("title", String.format("关于%s，您可以从更多方面了解：", entity))
                    .addParam("items", sentences.stream().collect(Collectors.toList())));
        }
        List<String> parent = kgUtil.queryParentClass(entity);
        logger.info("query parentClass {} of entity {}", parent, entity);
        instructions.add(new Instruction("input_button").addParam("buttons", Arrays.asList(String.format("了解更多%s", parent.get(0)), HOT_ISSUE)));

//        ResponseExecutionResult result = new ResponseExecutionResult();
//        result.setResponseAct(new ResponseAct("whichSubProperties")
//                .put("entity",entity)
//                .put("subProperties", subProperties));
//        result.setInstructions(Collections.singletonList(
//                new Instruction("suggestion_kb_dps")
//                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                        .addParam("entities", Collections.singletonList(entity))
//                        .addParam("object", null)
//                        .addParam("bnlabel", null)
//                        .addParam("condition", null)
//                        .addParam("datatype", null)
//                        .addParam("suggestions", subProperties)));
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichSubProperties")
                .put("entity", entity)
                .put("datatype", datatype));
        result.setInstructions(instructions);
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithBN(String bnlabel, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", bnlabel);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("entity", bnlabel)
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", bnlabel)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithBN(String entity, String object, BlankNode bn, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("entity", bn.getLabel())
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithBNs(String entity, String object, List<BlankNode> bns, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("entity", bns.stream().map(BlankNode::getLabel).collect(Collectors.toList()))
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bns.stream().map(BlankNode::getLabel).collect(Collectors.toList()))
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithConditionWithBN(String bnlabel, String conditionEntity, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", bnlabel);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", conditionEntity);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put(conditionEntity.equals("默认") ? "entity" : "bn", bnlabel)
                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", bnlabel)
                        .addParam("condition", conditionEntity)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithConditionWithBN(String entity, String object, BlankNode bn, String conditionEntity, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", conditionEntity);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", conditionEntity)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeWithEntity(String entity, String object, String bnlabel, List<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bnlabel);
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("object", object.substring(object.indexOf("#") + 1))
                .put("datatypes", datatypes)
                .put("entity", entity));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }


    public ResponseExecutionResult askWhichProperty(String entity, Collection<String> datatypes, Collection<ObjectProperty> objects, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);


        ResponseExecutionResult medicalunderwritting = answerMedicalUnderwritting(entity,objects);
        if (medicalunderwritting != null)
            return medicalunderwritting;

        logger.info("In KnowledgeQueryResponse::askWhichProperty. datatypes {} of entity {}.", datatypes, entity);

        String infoCard = null;
        String titleOfInfoCard = null;
        String markedDatatype = null;
        ObjectProperty markedObject = null;
        boolean getInfoCardOrNot = false;
        Iterator itOfDatatypes = datatypes.iterator();
        while(itOfDatatypes.hasNext()) {
            String datatype = (String)itOfDatatypes.next();
            List<String> value = kgUtil.queryValuewithEntityAndDatatype(entity, datatype);
            if (value != null && value.size() == 1) {
                infoCard = value.get(0);
                titleOfInfoCard = String.format("%s的%s", entity, datatype);
                logger.debug("infoCard get by dataProperty: {}, and title {}", infoCard, titleOfInfoCard);
                markedDatatype = datatype;
                getInfoCardOrNot = true;
                break;
            }
        }

        if (!getInfoCardOrNot) {     // there can not get value just from datatypes, so continue to try through objectproperty
            Iterator itOfObjects = objects.iterator();
            while(itOfObjects.hasNext()) {
                ObjectProperty op = (ObjectProperty)itOfObjects.next();
                logger.info("object: {}", op.getUri());
                List<EntityAndCpAndDatatypeAndValue> ecdv = kgUtil.queryEntityAndCpAndDatatypeAndValueWithCp(entity, op);
                Map<String, List<EntityAndCpAndDatatypeAndValue>> groupedecdv = ecdv.stream().collect(Collectors.groupingBy(x -> String.format("%s:%s", x.getEntity(), x.getComplex()), Collectors.toList()));
                if (groupedecdv.values().size() == 1) {
                    infoCard = groupedecdv.values().iterator().next().get(0).getDatatypeAndValue().getValue();
                    titleOfInfoCard = String.format("%s%s的%s", entity
                            , op.getLabel() != null && op.getLabel().length() != 0 ? String.format("的%s", op.getLabel()) : op.getBN().getLabel() == null || op.getBN().getLabel().length() == 0 ? "" : String.format("的%s", op.getBN().getLabel())
                            , groupedecdv.values().iterator().next().get(0).getDatatypeAndValue().getDatatype());
                    logger.debug("infoCard get by objectProperty: {} and title {}", infoCard, titleOfInfoCard);
                    markedObject = op;
                    break;
                }
            }
        }
        final String filterDatatype = markedDatatype;
        logger.debug("filterDatatype: {}", filterDatatype);
        Set<String> sentences = new HashSet<>();
        sentences.addAll(datatypes.stream().filter(x -> !x.equals(filterDatatype)).map(x -> String.format("%s的%s", entity, x)).collect(Collectors.toList()));
        for (ObjectProperty object : objects) {
            if (object == markedObject) {
                continue;
            }
            List<String> datatypesofbn = kgUtil.querydatatypeofBNWithCp(entity, object.getUri());
            for (String datatypeofbn : datatypesofbn) {
                sentences.add(String.format("%s%s的%s", entity
                        , object.getLabel() != null && object.getLabel().length() != 0 ? String.format("的%s", object.getLabel()) : object.getBN().getLabel() == null || object.getBN().getLabel().length() == 0 ? "" : String.format("的%s", object.getBN().getLabel())
                        , datatypeofbn
                ));
            }
        }
        logger.info("sentences {}", Arrays.asList(sentences));
        List<String> parent = kgUtil.queryParentClass(entity);
        logger.info("query parentClass {} of entity {}", parent, entity);

        sentences = processRecommendations(sentences.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("AnswerAndRecommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("info_card").addParam("title", titleOfInfoCard).addParam("content", infoCard)
                , new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", sentences.stream().collect(Collectors.toList()))
                , new Instruction("input_button").addParam("buttons", Arrays.asList(String.format("了解更多%s", parent.get(0)), HOT_ISSUE))
                , new Instruction("feedback").addParam("display", "true")
        ));

//        Set<String> bns = new LinkedHashSet<>();
//        Set<String> properties = new LinkedHashSet<>(datatypes);
//        for (ObjectProperty object : objects) {
//            if (object.getBN().getLabel() != null) {
//                bns.add(object.getBN().getLabel());
//            } else if (object.getLabel() != null) {
//                properties.add(object.getLabel());
//            }
//        }
//
//        ResponseExecutionResult result = new ResponseExecutionResult();
//        if (!properties.isEmpty() && bns.isEmpty()) {
//            result.setResponseAct(new ResponseAct("whichDatatype")
//                    .put("entity", entity)
//                    .put("datatypes", properties));
//            result.setInstructions(Collections.singletonList(
//                    new Instruction("suggestion_kb_dps")
//                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                            .addParam("entities", Collections.singletonList(entity))
//                            .addParam("object", null)
//                            .addParam("bnlabel", null)
//                            .addParam("condition", null)
//                            .addParam("datatype", null)
//                            .addParam("suggestions", properties)));
//
//        } else if (properties.isEmpty() && !bns.isEmpty()) {
//            result.setResponseAct(new ResponseAct("whichBN")
//                    .put("bns", bns));
//            result.setInstructions(Collections.singletonList(
//                    new Instruction("suggestion_kb_bns")
//                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                            .addParam("entities", Collections.singletonList(entity))
//                            .addParam("object", null)
//                            .addParam("bnlabel", null)
//                            .addParam("condition", null)
//                            .addParam("datatype", null)
//                            .addParam("suggestions", bns)));
//
//        } else if (!properties.isEmpty()) {
//            result.setResponseAct(new ResponseAct("whichProperty")
//                    .put("entity", entity)
//                    .put("bns", bns)
//                    .put("properties", properties));
//            Set<String> sug = new LinkedHashSet<>();
//            sug.addAll(bns);
//            sug.addAll(properties);
//            result.setInstructions(Collections.singletonList(
//                    new Instruction("suggestion_kb_props")
//                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                            .addParam("entities", Collections.singletonList(entity))
//                            .addParam("object", null)
//                            .addParam("bnlabel", null)
//                            .addParam("condition", null)
//                            .addParam("datatype", null)
//                            .addParam("suggestions", sug)));
//        } else {
//            result.setResponseAct(new ResponseAct("whichProperty")
//                    .put("entity", entity));
//            result.setInstructions(Collections.singletonList(
//                    new Instruction("suggestion_kb_props")
//                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
//                            .addParam("entities", Collections.singletonList(entity))
//                            .addParam("object", null)
//                            .addParam("bnlabel", null)
//                            .addParam("condition", null)
//                            .addParam("datatype", null)
//                            .addParam("suggestions", null)));
//        }
        return result;
    }


    public ResponseExecutionResult askWhichBN(List<String> bnlabels, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bnlabels));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", bnlabels)));
        return result;
    }


    public ResponseExecutionResult askWhichBN(String entity, List<BlankNode> bns, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> bnlabels = bns.stream().map(BlankNode::getLabel).collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bnlabels));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", bnlabels)));
        return result;
    }


    public ResponseExecutionResult askWhichBN(String entity, List<BlankNode> bns, String datatype, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> bnlabels = bns.stream().map(BlankNode::getLabel).collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bnlabels)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("suggestions", bnlabels)));
        return result;
    }


    public ResponseExecutionResult askWhichBNWithDatatype(List<String> bnlabels, String datatype, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns", bnlabels)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_bns")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("suggestions", bnlabels)));
        return result;
    }

    public ResponseExecutionResult askWhichConditionEntity(String entity, List<ConditionEntity> conditionEntities, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> conds = conditionEntities.stream().map(ConditionEntity::getLabel).collect(Collectors.toSet());

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(makeConditionClassesAndEntitiesResponseAct(entity, conditionEntities));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", entity)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", conds)));
        return result;
    }

    public ResponseExecutionResult askWhichConditionEntityWithBN(String entity, String bnlabel, List<ConditionEntity> conditionEntities, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bnlabel);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> conds = conditionEntities.stream().map(ConditionEntity::getLabel).collect(Collectors.toSet());

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(makeConditionClassesAndEntitiesResponseAct(bnlabel, conditionEntities));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", bnlabel)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", conds)));
        return result;

    }


    public ResponseExecutionResult askWhichConditionEntityWithDatatype(String entity, List<ConditionEntity> conditionEntities, String datatype, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> conds = conditionEntities.stream().map(ConditionEntity::getLabel).collect(Collectors.toSet());

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(makeConditionClassesAndEntitiesResponseAct(entity, conditionEntities)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", entity)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("suggestions", conds)));
        return result;
    }

    public ResponseExecutionResult askWhichConditionEntityWithBNLabelAndDatatype(String bnlabel, Collection<ConditionEntity> conditionEntities, String datatype, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", bnlabel);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> conds = conditionEntities.stream().map(ConditionEntity::getLabel).collect(Collectors.toSet());

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(makeConditionClassesAndEntitiesResponseAct(bnlabel, conditionEntities)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", bnlabel)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("suggestions", conds)));
        return result;
    }

    private ResponseAct makeConditionClassesAndEntitiesResponseAct(String bn, Collection<ConditionEntity> conditionEntities) {
        Multimap<String, String> conditionClassAndEntities = HashMultimap.create();
        for (ConditionEntity conditionEntity : conditionEntities) {
            if (conditionClassAndEntities.get(conditionEntity.getClassLabel()).size() < 5) {
                conditionClassAndEntities.put(conditionEntity.getClassLabel(), conditionEntity.getLabel());
            }
        }
        if (conditionClassAndEntities.keySet().size() == 1 && conditionClassAndEntities.containsKey(null)) {
            // only null as key (condition class)
            return new ResponseAct("whichConditionEntity")
                    .put("bn", bn)
                    .put("conditionEntities", conditionClassAndEntities.get(null));
        } else {
            String conditionClassesAndEntities = conditionClassAndEntities.keySet().stream()
                    .map(cls -> accessorRepository.getNLG().generate(new ResponseAct("conditionClassAndEntities")
                            .put("conditionClass", cls == null ? "条件" : cls)
                            .put("conditionEntities", conditionClassAndEntities.get(cls))))
                    .collect(Collectors.joining("，"));
            return new ResponseAct("whichConditionEntity")
                    .put("bn", bn)
                    .put("conditionClassesAndEntities", conditionClassesAndEntities);
        }
    }

    public ResponseExecutionResult askWhichObject(String entity, List<ObjectProperty> objectsOfdatatype, String datatype, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> bns = new HashSet<>();
        Set<String> ops = new HashSet<>();
        for (ObjectProperty objectProperty : objectsOfdatatype) {
            if (objectProperty.getBN().getLabel() != null) {
                bns.add(objectProperty.getBN().getLabel());
            } else if (objectProperty.getLabel() != null) {
//                if(objectProperty.getLabel().equals("condition_bn"))
//                    ops.add("本身");
//                else if (objectProperty.getLabel().equals("with bn"))
//                    ops.add(kgUtil.)
                ops.add(objectProperty.getLabel());
            }
        }

        ResponseExecutionResult result = new ResponseExecutionResult();
        if (!ops.isEmpty() && bns.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichObject")
                    .put("entity", entity)
                    .put("datatype", datatype)
                    .put("ops", ops));
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_ops")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", datatype)
                            .addParam("suggestions", ops)));
        } else if (ops.isEmpty() && !bns.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichBN")
                    .put("bns", bns)
                    .put("datatype", datatype));
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_bns")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", datatype)
                            .addParam("suggestions", bns)));
        } else if (!ops.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichObject")
                    .put("entity", entity)
                    .put("bns", bns)
                    .put("ops", ops)
                    .put("datatype", datatype));
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(bns);
            suggestions.addAll(ops);
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_ops")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", datatype)
                            .addParam("suggestions", suggestions)));
        } else {
            result.setResponseAct(new ResponseAct("whichProperty")
                    .put("entity", entity));
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_props")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", null)
                            .addParam("suggestions", null)));
        }
        return result;
    }

    public ResponseExecutionResult answerYshape(String value, YshapeBNAndDP yshapeBNAndDP, Context context) {
        ResponseExecutionResult disabled = checkDisabled(yshapeBNAndDP);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()));
        context.getSlots().put("contextBN", yshapeBNAndDP.getBN().getLabel());
        context.getSlots().put("contextObject", yshapeBNAndDP.getYshapeURI());
        context.getSlots().put("contextDatatype", yshapeBNAndDP.getDatatype());
        context.getSlots().put("contextConditionEntity", null);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                yshapeBNAndDP.getBN().getIri(), kgUtil.queryDatatypeIRI(yshapeBNAndDP.getDatatype()));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", yshapeBNAndDP.getBN().getLabel())
                .put("datatype", yshapeBNAndDP.getDatatype())
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", yshapeBNAndDP.getBN().getLabel())
                                .put("datatype", yshapeBNAndDP.getDatatype())))
                        .addParam("answer", value)
                        .addParam("entities", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()))
                        .addParam("object", yshapeBNAndDP.getYshapeURI())
                        .addParam("bnlabel", yshapeBNAndDP.getBN().getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", yshapeBNAndDP.getDatatype())
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answer(String entity, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, datatype);
        if (disabled != null) {
            return disabled;
        }

//        context.getSlots().put("contextEntity", Collections.singletonList(entity));
//        context.getSlots().put("contextBN", null);
//        context.getSlots().put("contextObject", null);
//        context.getSlots().put("contextDatatype", datatype);
//        context.getSlots().put("contextConditionEntity", null);

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);

        EntitiesAndTheirProperties entitiesAndTheirProperties = kgUtil.queryProperties(entity);
        ListMultimap<String, String> entitiesAndTheirDatatypes = entitiesAndTheirProperties.getEntitiesAndTheirDatatypes();
        ListMultimap<String, ObjectProperty> entitiesAndTheirObjects = entitiesAndTheirProperties.getEntitiesAndTheirObjects();
        logger.debug("entity {} and its datatypes {}.", entity, entitiesAndTheirDatatypes.get(entity));
        List<String> datatypes = entitiesAndTheirDatatypes.get(entity);
        datatypes.remove(datatype);
        List<ObjectProperty> objects = entitiesAndTheirObjects.get(entity);

        Set<String> sentences = new HashSet<>();
        sentences.addAll(datatypes.stream().map(x -> String.format("%s的%s", entity, x)).collect(Collectors.toList()));
        for (ObjectProperty object : objects) {
            List<String> datatypesofbn = kgUtil.querydatatypeofBNWithCp(entity, object.getUri());
            for (String datatypeofbn : datatypesofbn) {
                sentences.add(String.format("%s%s的%s", entity
                        , object.getLabel() != null && object.getLabel().length() != 0 ? String.format("的%s", object.getLabel()) : object.getBN().getLabel() == null || object.getBN().getLabel().length() == 0 ? "" : String.format("的%s", object.getBN().getLabel())
                        , datatypeofbn
                ));
            }
        }
        sentences = processRecommendations(sentences.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                kgUtil.queryEntityIRI(entity), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        List<Instruction> instructions = new ArrayList<>();
        instructions.add(new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", entity)
                                .put("datatype", datatype)))
                        .addParam("result", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations));
        instructions.add(new Instruction("info_card").addParam("title",String.format("%s%s",entity,datatype)).addParam("content",value));
//                new Instruction("recommendation").addParam("title","您可能关注")
//                    .addParam("items",processRecommendations(FAQResponse.getRecommendations(),context)),
//        instructions.add(new Instruction("input_button").addParam("buttons",Arrays.asList(HOT_ISSUE)));
        instructions.add(new Instruction("feedback").addParam("display", "true"));

        if (sentences.size() > 0) {
            instructions.add(new Instruction("recommendation").addParam("title", String.format("关于%s，您可以从更多方面了解：", entity))
                    .addParam("items", sentences.stream().collect(Collectors.toList())));
        }
        List<String> parent = kgUtil.queryParentClass(entity);
        logger.info("query parentClass {} of entity {}", parent, entity);
        instructions.add(new Instruction("input_button").addParam("buttons", Arrays.asList(String.format("了解更多%s", parent.get(0)), HOT_ISSUE)));

        ResponseExecutionResult result = new ResponseExecutionResult();
//        result.setResponseAct(new ResponseAct("kg")
//                .put("entity", entity)
//                .put("datatype", datatype)
//                .put("result", value)
//                .put("relations", relations));
        result.setResponseAct(new ResponseAct("AnswerAndRecommendation"));
        result.setInstructions(instructions);
//        result.setInstructions(Collections.singletonList(
//                new Instruction("recommendation")
//                        .addParam("title", "test")
//                        .addParam("result", value)
//                        .addParam("entities", Collections.singletonList(entity))
//                        .addParam("object", null)
//                        .addParam("bnlabel", null)
//                        .addParam("condition", null)
//                        .addParam("datatype", datatype)
//                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answer(String entity, String complex, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, datatype);
        if (disabled != null) {
            return disabled;
        }

//        context.getSlots().put("contextEntity", Collections.singletonList(entity));
//        context.getSlots().put("contextBN", null);
//        context.getSlots().put("contextObject", null);
//        context.getSlots().put("contextDatatype", datatype);
//        context.getSlots().put("contextConditionEntity", null);

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);

        List<String> parent = kgUtil.queryParentClass(entity);
        logger.info("query parentClass {} of entity {}", parent, entity);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", entity)
                .put("complex", complex)
                .put("datatype", datatype)
                .put("result", value));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation").addParam("title","您可能关注")
                        .addParam("items",processRecommendations(FAQResponse.getRecommendations(),context)),
                new Instruction("input_button").addParam("buttons", Arrays.asList(String.format("了解更多%s", parent.get(0)), HOT_ISSUE)),
                new Instruction("feedback")
                        .addParam("display", "true")));
//        result.setInstructions(Collections.singletonList(
//                new Instruction("recommendation")
//                        .addParam("title", "test")
//                        .addParam("result", value)
//                        .addParam("entities", Collections.singletonList(entity))
//                        .addParam("object", null)
//                        .addParam("bnlabel", null)
//                        .addParam("condition", null)
//                        .addParam("datatype", datatype)
//                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answer(String entity, String complex, String datatype, String value, Map<String,String> cpces,Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, datatype);
        if (disabled != null) {
            return disabled;
        }

//        context.getSlots().put("contextEntity", Collections.singletonList(entity));
//        context.getSlots().put("contextBN", null);
//        context.getSlots().put("contextObject", null);
//        context.getSlots().put("contextDatatype", datatype);
//        context.getSlots().put("contextConditionEntity", null);

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);


        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", String.format("%s%s",entity,String.join(",",cpces.keySet())))
                .put("complex", complex)
                .put("datatype", datatype)
                .put("result", value));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation").addParam("title","您可能关注")
                        .addParam("items",processRecommendations(FAQResponse.getRecommendations(),context)),
                new Instruction("feedback")
                        .addParam("display", "true")));
//        result.setInstructions(Collections.singletonList(
//                new Instruction("recommendation")
//                        .addParam("title", "test")
//                        .addParam("result", value)
//                        .addParam("entities", Collections.singletonList(entity))
//                        .addParam("object", null)
//                        .addParam("bnlabel", null)
//                        .addParam("condition", null)
//                        .addParam("datatype", datatype)
//                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerWithBN(BlankNode bn, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(bn, datatype);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", bn.getLabel())
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", bn.getLabel())
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerWithBN(String entity, String object, BlankNode bn, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, bn, datatype);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", bn.getLabel())
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", bn.getLabel())
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerWithCondition(String entity, String object, BlankNode bn, String conditionEntity, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, bn, datatype);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", conditionEntity);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", conditionEntity)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerWithConditionWithBN(BlankNode bn, String conditionEntity, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(bn, datatype);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", conditionEntity);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", null)
                        .addParam("object", null)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", conditionEntity)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerWithConditionWithBN(String entity, String object, BlankNode bn, String conditionEntity, String datatype, String value, Context context) {
        ResponseExecutionResult disabled = checkDisabled(entity, bn, datatype);
        if (disabled != null) {
            return disabled;
        }

        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getLabel());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", conditionEntity);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put(conditionEntity.equals("默认") ? "entity" : "bn", bn.getLabel())
                                .put("conditionEntity", conditionEntity.equals("默认") ? null : conditionEntity)
                                .put("conditionClass", conditionEntity.equals("默认") ? null : kgUtil.queryConditionClass(conditionEntity))
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel())
                        .addParam("condition", conditionEntity)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }

    public ResponseExecutionResult answerNoValueWithDatatype(String entity, String datatype, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("noknowledge"));
//                .put("title",
//                        accessorRepository.getNLG().generate(new ResponseAct("kg")
//                                .put("entity", entity)
//                                .put("datatype", datatype))
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_na")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)));
        return result;
    }

    public ResponseExecutionResult answerNoValueWithObject(String entity, String object, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        String objectLabel = Optional.ofNullable(kgUtil.queryObjectLabel(object)).orElse(object);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("noknowledge"));
//                .put("title",
//                        accessorRepository.getNLG().generate(new ResponseAct("kg")
//                                .put("entity", entity)
//                                .put("datatype", objectLabel))
//                ));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_na")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)));
        return result;
    }

    public ResponseExecutionResult answerUnknown(List<String> clazz, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

//        String objectLabel = Optional.ofNullable(kgUtil.queryObjectLabel(object)).orElse(object);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("noknowledge"));
//                .put("title",
//                        accessorRepository.getNLG().generate(new ResponseAct("kg"))));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_unknown")
                        .addParam("clazz", clazz)));

        return result;
    }

    public ResponseExecutionResult answerToBN(String entity, String object, BlankNode bn, String datatype, String value, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", bn.getIri());
        context.getSlots().put("contextObject", object);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                bn.getIri(), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", bn.getLabel())
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", bn.getLabel())
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", object)
                        .addParam("bnlabel", bn.getLabel()) // bnlabel可能是空
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
        return result;
    }


    public ResponseExecutionResult askWhichCpConditionEntity(String entity, List<BNAndPropertyAndValue> conditionEntities, String datatype, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        Set<String> conds = conditionEntities.stream().map(BNAndPropertyAndValue::getProperty).collect(Collectors.toSet());

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichConditionEntity")
                .put("entity", entity)
                .put("conditions", conds)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", entity)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", conds)));
        return result;
    }

    public ResponseExecutionResult askWhichClassofCpConditionEntity(String entity, ConditionClassesAndValues conditionclassesandvalues, String datatype, Context context, String strategy) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> keys = conditionclassesandvalues.getInfo().keySet().stream().collect(Collectors.toList());

        int idx;
        switch (strategy) {
            case "random":
                Date date = new Date();
                Random rand = new Random(date.getTime());
                idx = rand.nextInt(keys.size());
                break;
            default:
            case "first":
                idx = 0;
                break;
        }
        String clazz = keys.get(idx);
        String values = String.join(",", conditionclassesandvalues.getInfo().get(clazz));
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichConditionEntity")
                .put("entity", entity)
                .put("classes", clazz)
//                .put("valuess", values)
                .put("datatype", datatype));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_conds")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", entity)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", String.format("%s:%s", clazz, values))));
        return result;
    }


    public ResponseExecutionResult askWhichDatatypeOfComplexProperty(String entity, String complex, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", kgUtil.queryCpIRI(complex));
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("contextComplexProperty", complex);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("complexproperty", complex)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }

    public ResponseExecutionResult askWhichDatatypeOfMultiComplexProperty(String entity, Collection<String> datatypes, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("contextComplexProperty", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichDatatype")
                .put("datatypes", datatypes));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", datatypes)));
        return result;
    }


    public ResponseExecutionResult askWhichDatatypeOfMultiComplexProperty(String entity, List<Pair<String, String>> cpsAnddps, Map<String, String> cpces, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("contextComplexProperty", null);


        List<String> conditions = new ArrayList<>();
        if (cpces != null) {
            for (Map.Entry<String, String> entry : cpces.entrySet()) {
                conditions.add(entry.getKey());
            }
        }
        StringBuilder conditionSentence = genEntityAndCesPhrase(entity,conditions);
        List<String> items = new ArrayList<>();
        for (Pair<String, String> cpAnddp : cpsAnddps) {
            List<String> eAndces = new ArrayList<String>() {{
                add(entity);
                addAll(conditions);
            }};
            if (RECOMM.containsKey(String.join("#", eAndces)))
                items.add(String.format("%s的%s的%s", RECOMM.get(String.join("#", eAndces)), cpAnddp.getKey(), cpAnddp.getValue()));
            else
                items.add(String.format("%s%s的%s的%s", entity, conditionSentence.toString(), cpAnddp.getKey(), cpAnddp.getValue()));
        }

        items = processRecommendations(items,context);


        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("recommendation"));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", items),
                new Instruction("feedback")
                        .addParam("display", "true")));
        return result;
    }


    public ResponseExecutionResult answerNoValue(String entity, String complex, String datatype, List<String> ces, Context context) {
        logger.debug("In KnowledgeQueryResponse::answerNoValue. entity {}, complex {}, datatype {}, ces {}", entity, complex, datatype, ces);

        Map<String, String> map = new HashMap<String, String>() {{
            put("entity", entity);
            put("complex", complex);
            put("datatype", datatype);
            put("ces", ces == null || ces.size() == 0 ? null : String.join(",", ces));
        }};

        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("answerNoValue");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue() != null)
                ra.put(entry.getKey(), entry.getValue());
        }
        result.setResponseAct(ra);
        result.setInstructions(Collections.singletonList(
                new Instruction("feedback")
                        .addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult answerNotValid(String entity, String complex, String datatype, List<String> ces, Context context) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("temporary");
        result.setResponseAct(ra);
        return result;
    }

    public ResponseExecutionResult askMultiAnswerWithDp(String entity, String datatype, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> restConds, Context context) {

        logger.debug("In KnowledgeQueryResponse::askMultiAnswerWithDp. entity {} datatype {}");
        List<Map<String, String>> tmp = restConds.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Set<String> conditionClasses = new HashSet<>();
        List<List<String>> y = tmp.stream().map(x -> x.values().stream().collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> classes : y) {
            conditionClasses.addAll(classes);
        }
        if (tmp.size() > 3 && conditionClasses.size() > 1) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", null);
            context.getSlots().put("contextDatatype", datatype);
            context.getSlots().put("contextConditionEntity", cpces.keySet().stream().collect(Collectors.toList()));
            context.getSlots().put("cpContextConditionEntities", cpces);
            Map<String, List<String>> items = new HashMap<>();
            for (Map<String, String> m : tmp) {
                for (Map.Entry<String, String> entry : m.entrySet()) {
                    if (entry.getKey() != null) {
                        if (items.containsKey(entry.getValue())) {
                            if (!items.get(entry.getValue()).contains(entry.getKey()))
                                items.get(entry.getValue()).add(entry.getKey());
                        } else
                            items.put(entry.getValue(), new ArrayList<String>() {{
                                add(entry.getKey());
                            }});
                    }
                }
            }
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Collections.singletonList(
                    new Instruction("multiple_condition")
                            .addParam("title", String.format("更多关于%s的%s的情况", entity, datatype))
                            .addParam("items", items)));
            return result;
        }
        return askMultiAnswerWithEntityAndDpAndCEs(entity, datatype, cpces, restConds, context);


    }


    public ResponseExecutionResult askMultiAnswer(String entity, String complex, String datatype, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> restconds, Context context) {
        List<Map<String, String>> tmp = restconds.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Set<String> conditionClasses = new HashSet<>();
        List<List<String>> y = tmp.stream().map(x -> x.values().stream().collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> classes : y) {
            conditionClasses.addAll(classes);
        }
        if (tmp.size() > 3 && conditionClasses.size() > 1) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", kgUtil.queryCpIRI(complex));
            context.getSlots().put("contextDatatype", datatype);
            context.getSlots().put("contextConditionEntity", cpces.keySet().stream().collect(Collectors.toList()));
            context.getSlots().put("cpContextConditionEntities", cpces);
            //Map<String,List<Map<String,String>>> aggregate = tmp.stream().filter(x -> x.values() != null && x.values().size() != 0).collect(Collectors.groupingBy(x -> x.values().iterator().next(),Collectors.toList()));
            Map<String, List<String>> items = new HashMap<>();
            for (Map<String, String> m : tmp) {
                for (Map.Entry<String, String> entry : m.entrySet()) {
                    if (entry.getKey() != null) {
                        if (items.containsKey(entry.getValue())) {
                            if (!items.get(entry.getValue()).contains(entry.getKey()))
                                items.get(entry.getValue()).add(entry.getKey());
                        } else
                            items.put(entry.getValue(), new ArrayList<String>() {{
                                add(entry.getKey());
                            }});
                    }
                }
            }
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Arrays.asList(
                    new Instruction("multiple_condition")
                            .addParam("title", String.format("更多关于%s的%s的%s的情况", entity, complex, datatype))
                            .addParam("items", items),
                    new Instruction("feedback").addParam("display", "true")));
            return result;
        }
        return askMultiAnswerWithEntityAndCpAndDpAndCEs(entity, complex, datatype, cpces, restconds, context);


    }

    public ResponseExecutionResult askMultiAnswer(String entity, String complex, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> restConds, Context context) {
        List<Map<String, String>> tmp = restConds.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Set<String> conditionClasses = new HashSet<>();
        List<List<String>> y = tmp.stream().map(x -> x.values().stream().collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> classes : y) {
            conditionClasses.addAll(classes);
        }
        ;
        if (tmp.size() > 3 && conditionClasses.size() > 1) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", kgUtil.queryCpIRI(complex));
            context.getSlots().put("contextDatatype", null);
            context.getSlots().put("contextConditionEntity", cpces.keySet().stream().collect(Collectors.toList()));
            context.getSlots().put("cpContextConditionEntities", cpces);
            //Map<String,List<Map<String,String>>> aggregate = tmp.stream().filter(x -> x.values() != null && x.values().size() != 0).collect(Collectors.groupingBy(x -> x.values().iterator().next(),Collectors.toList()));
            Map<String, List<String>> items = new HashMap<>();
            for (Map<String, String> m : tmp) {
                for (Map.Entry<String, String> entry : m.entrySet()) {
                    if (entry.getKey() != null) {
                        if (items.containsKey(entry.getValue())) {
                            if (!items.get(entry.getValue()).contains(entry.getKey()))
                                items.get(entry.getValue()).add(entry.getKey());
                        } else
                            items.put(entry.getValue(), new ArrayList<String>() {{
                                add(entry.getKey());
                            }});
                    }
                }
            }
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Arrays.asList(
                    new Instruction("multiple_condition")
                            .addParam("title", String.format("更多关于%s的%s的情况", entity, complex))
                            .addParam("items", items),
                    new Instruction("feedback").addParam("display", "true")));
            return result;
        }
        return askMultiAnswerWithEntityAndCpAndCEs(entity, complex, cpces, restConds, context);


    }

    public ResponseExecutionResult askMultiAnswerWithEntityAndCpAndCEs(String entity, String complex, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> res, Context context) {
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions e : res) {
            Set<String> conditions = new HashSet<>();
            for (Map.Entry<String, String> entry : e.getConditions().entrySet()) {
                conditions.add(String.format("%s", entry.getKey()));
                conditions.addAll(ces);
            }
            StringBuilder conditionSentence = genEntityAndCesPhrase(entity,conditions.stream().collect(Collectors.toList()));
            items.add(String.format("%s%s的%s的%s", entity, conditionSentence.toString(), complex, e.getDatatypeAndValue().getDatatype()));
        }

        items = processRecommendations(items,context);


        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true"))
        );
        return result;
    }


    public ResponseExecutionResult askMultiAnswerWithEntityAndCpAndDp(String entity, String complex, String datatype, Context context) {
        List<BNAndDatatypeAndValueAndConditions> res = kgUtil.queryRestCondsWithEntityAndComplexAndDatatypeUnderConditions(entity, complex, datatype, null);
        List<BNAndDatatypeAndValueAndConditions> reswithoutConds = res.stream().filter(x -> x.getConditions() == null || x.getConditions().isEmpty()).collect(Collectors.toList());
        if (reswithoutConds.size() == 1) {
            return answer(entity, kgUtil.queryCpWithEntityAndBN(entity, reswithoutConds.get(0).getBn().getIri()), reswithoutConds.get(0).getDatatypeAndValue().getDatatype(), reswithoutConds.get(0).getDatatypeAndValue().getValue(), context);
        }
        List<Map<String, String>> tmp = res.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Map<String, Set<String>> aggregate = new HashMap<>();
        for (Map<String, String> conditions : tmp) {
            for (Map.Entry<String, String> entry : conditions.entrySet()) {
                if (!aggregate.containsKey(entry.getValue())) {
                    aggregate.put(entry.getValue(), new HashSet<String>() {{
                        add(entry.getKey());
                    }});
                } else {
                    aggregate.get(entry.getValue()).add(entry.getKey());
                }
            }
        }
        if (aggregate.keySet().size() > 1 && res.size() > 3) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", kgUtil.queryCpIRI(complex));
            context.getSlots().put("contextDatatype", datatype);
            Map<String, List<String>> items = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : aggregate.entrySet()) {
                List<String> contents = entry.getValue().stream().collect(Collectors.toList());
                items.put(entry.getKey(), contents);
            }
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Arrays.asList(
                    new Instruction("multiple_condition")
                            .addParam("title", String.format("更多关于%s的%s的%s的情况", entity, complex, datatype))
                            .addParam("items", items),
                    new Instruction("feedback").addParam("display", "true")));
            return result;
        }
        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions r : res) {
            String sentence = null;
            if (r.getConditions().isEmpty())
                sentence = String.format("%s的%s的%s"
                        , entity
                        , kgUtil.queryCpWithEntityAndBN(entity, r.getBn().getIri())
                        , r.getDatatypeAndValue().getDatatype());
            else
                sentence = String.format("%s%s的%s的%s"
                        , entity
                        , String.join(",", r.getConditions().keySet())
                        , kgUtil.queryCpWithEntityAndBN(entity, r.getBn().getIri())
                        , r.getDatatypeAndValue().getDatatype());
            items.add(sentence);
        }

        items = processRecommendations(items,context);

        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的%s的%s的情况", entity, complex, datatype))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult askMultiAnswerWithEntityAndCp(String entity, String complex, Context context) {
        List<BNAndDatatypeAndValueAndConditions> res = kgUtil.queryRestCondsWithEntityAndComplexUnderConditions(entity, complex, null);
        List<BNAndDatatypeAndValueAndConditions> reswithoutConds = res.stream().filter(x -> x.getConditions() == null || x.getConditions().isEmpty()).collect(Collectors.toList());
        if (reswithoutConds.size() == 1) {
            return answer(entity, kgUtil.queryCpWithEntityAndBN(entity, reswithoutConds.get(0).getBn().getIri()), reswithoutConds.get(0).getDatatypeAndValue().getDatatype(), reswithoutConds.get(0).getDatatypeAndValue().getValue(), context);
        }

        List<Map<String, String>> tmp = res.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Map<String, Set<String>> aggregate = new HashMap<>();
        for (Map<String, String> conditions : tmp) {
            for (Map.Entry<String, String> entry : conditions.entrySet()) {
                if (!aggregate.containsKey(entry.getValue())) {
                    aggregate.put(entry.getValue(), new HashSet<String>() {{
                        add(entry.getKey());
                    }});
                } else {
                    aggregate.get(entry.getValue()).add(entry.getKey());
                }
            }
        }

        if (aggregate.keySet().size() > 1 && res.size() > 3) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", kgUtil.queryCpIRI(complex));
            context.getSlots().put("contextDatatype", null);
            Map<String, List<String>> items = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : aggregate.entrySet()) {
                List<String> contents = entry.getValue().stream().collect(Collectors.toList());
                items.put(entry.getKey(), contents);
            }
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Arrays.asList(
                    new Instruction("multiple_condition")
                            .addParam("title", String.format("更多关于%s的%s的情况", entity, complex))
                            .addParam("items", items),
                    new Instruction("feedback").addParam("display", "true")));
            return result;
        }
        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions r : res) {
            String sentence = null;
            if (r.getConditions().isEmpty())
                sentence = String.format("%s的%s的%s"
                        , entity
                        , kgUtil.queryCpWithEntityAndBN(entity, r.getBn().getIri())
                        , r.getDatatypeAndValue().getDatatype());
            else
                sentence = String.format("%s%s的%s的%s"
                        , entity
                        , String.join(",", r.getConditions().keySet())
                        , kgUtil.queryCpWithEntityAndBN(entity, r.getBn().getIri())
                        , r.getDatatypeAndValue().getDatatype());
            items.add(sentence);
        }

        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的%s的%s的情况", entity, complex))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;


    }


    public ResponseExecutionResult askMultiAnswerWithEntityAndDpAndCEs(String entity, String datatype, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> res, Context context) {
        List<Map<String, String>> tmp = res.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Set<String> conditionClasses = new HashSet<>();
        List<List<String>> y = tmp.stream().map(x -> x.values().stream().collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> classes : y) {
            conditionClasses.addAll(classes);
        }

        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", cpces.keySet().stream().collect(Collectors.toList()));
        context.getSlots().put("cpContextConditionEntities", cpces);
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions e : res) {
            Set<String> conditions = new HashSet<>();
            for (Map.Entry<String, String> entry : e.getConditions().entrySet()) {
                conditions.add(String.format("%s", entry.getKey()));
                conditions.addAll(ces);
            }
            StringBuilder conditionSentence = genEntityAndCesPhrase(entity,conditions.stream().collect(Collectors.toList()));
            items.add(String.format("%s%s的%s", entity, conditionSentence.toString(), datatype));
        }

        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult askMultiAnswerInEntityCpDpWithEntityAndDp(String entity, String datatype, List<BNAndDatatypeAndValueAndConditions> res, Context context) {
        logger.debug("In KnowledgeQueryResponse::askMultiAnswerInEntityCpDpWithEntityAndDp.");
        List<BNAndDatatypeAndValueAndConditions> reswithoutConds = res.stream().filter(x -> x.getConditions() == null || x.getConditions().isEmpty()).collect(Collectors.toList());
        if (reswithoutConds.size() == 1) {
            return answer(entity, kgUtil.queryCpWithEntityAndBN(entity, reswithoutConds.get(0).getBn().getIri()), reswithoutConds.get(0).getDatatypeAndValue().getDatatype(), reswithoutConds.get(0).getDatatypeAndValue().getValue(), context);
        }
        List<Map<String, String>> tmp = res.stream().map(x -> x.getConditions()).collect(Collectors.toList());
        Set<String> conditionClasses = new HashSet<>();
        List<List<String>> y = tmp.stream().map(x -> x.values().stream().collect(Collectors.toList())).collect(Collectors.toList());
        for (List<String> classes : y) {
            conditionClasses.addAll(classes);
        }
        if (tmp.size() > 3 && conditionClasses.size() > 1) {
            context.getSlots().put("contextEntity", Collections.singletonList(entity));
            context.getSlots().put("contextBN", null);
            context.getSlots().put("contextObject", null);
            context.getSlots().put("contextDatatype", datatype);
            Map<String, List<String>> items = new HashMap<>();
            for (Map<String, String> m : tmp) {
                for (Map.Entry<String, String> entry : m.entrySet()) {
                    if (entry.getKey() != null) {
                        if (items.containsKey(entry.getValue())) {
                            if (!items.get(entry.getValue()).contains(entry.getKey()))
                                items.get(entry.getValue()).add(entry.getKey());
                        } else
                            items.put(entry.getValue(), new ArrayList<String>() {{
                                add(entry.getKey());
                            }});
                    }
                }
            }
            //Map<String,List<Map<String,String>>> aggregate = tmp.stream().filter(x -> x.values() != null && x.values().size() != 0).collect(Collectors.groupingBy(x -> x.values().iterator().next(),Collectors.toList()));
            ResponseExecutionResult result = new ResponseExecutionResult();
            ResponseAct ra = new ResponseAct("MultiConditionTable");
            result.setResponseAct(ra);
            result.setInstructions(Collections.singletonList(
                    new Instruction("multiple_condition")
                            .addParam("items", items)));
            return result;
        }


        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions e : res) {
            Set<String> conditions = new HashSet<>();
            for (Map.Entry<String, String> entry : e.getConditions().entrySet()) {
                conditions.add(String.format("%s", entry.getKey()));
            }
            StringBuilder conditionSentence = genEntityAndCesPhrase(entity,conditions.stream().collect(Collectors.toList()));
            String cp = kgUtil.queryCpWithEntityAndBN(entity, e.getBn().getIri());
            String cpsentence = cp == null || cp.length() == 0 ? "" : String.format("的%s", cp);
            items.add(String.format("%s%s%s的%s", entity, conditionSentence.toString(), cpsentence, datatype));
        }
        items.remove(String.format("%s的%s", entity, datatype));
        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    private ResponseExecutionResult askMultiAnswerWithEntityAndCpAndDpAndCEs(String entity, String complex, String datatype, Map<String, String> cpces, List<BNAndDatatypeAndValueAndConditions> res, Context context) {
        List<String> ces = cpces.keySet().stream().collect(Collectors.toList());
        List<String> items = new ArrayList<>();
        for (BNAndDatatypeAndValueAndConditions e : res) {
            Set<String> conditions = new HashSet<>();
            for (Map.Entry<String, String> entry : e.getConditions().entrySet()) {
                conditions.add(String.format("%s", entry.getKey()));
                conditions.addAll(ces);
            }
            StringBuilder conditionSentence = genEntityAndCesPhrase(entity,conditions.stream().collect(Collectors.toList()));
            items.add(String.format("%s%s的%s的%s", entity, conditionSentence.toString(), complex, datatype));
        }
        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title", String.format("更多关于%s的问题", entity))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult askWhichPropertyOfEntitiesPairs(List<Pair<String, String>> entitiesPairs) {
        entitiesPairs = entitiesPairs.stream().map(x -> new Pair<>(kgUtil.queryEntityIRI(x.getKey()), kgUtil.queryEntityIRI(x.getValue()))).collect(Collectors.toList());
        List<YshapeBNAndDPAndValue> res = kgUtil.queryYshapeBNAndDPWithEntitiesPairs(entitiesPairs);
        List<String> items = new ArrayList<>();
        for (YshapeBNAndDPAndValue r : res) {
            items.add(String.format("%s和%s的%s?", kgUtil.queryLabelWithIRI(r.getEntity1()), kgUtil.queryLabelWithIRI(r.getEntity2()), r.getDatatype()));
        }

        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("askWhichDpOfYshape");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                        .addParam("title", "更多相关问题")
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }


    public ResponseExecutionResult answerNoValueWithYshapeEntitiesPair(List<Pair<String, String>> entitiesPairs, String datatype, List<String> ces, Context context) {
        // if ces else
        StringBuilder sentences = new StringBuilder();
        if (ces != null) {
            for (Pair<String, String> entitiesPair : entitiesPairs) {
                sentences.append(String.format("%s和%s的%s在%s下没有值。\n", kgUtil.queryLabelWithIRI(entitiesPair.getKey()), kgUtil.queryLabelWithIRI(entitiesPair.getValue()), datatype, String.join(",", ces)));
            }
        } else {
            for (Pair<String, String> entitiesPair : entitiesPairs) {
                sentences.append(String.format("%s和%s的%s没有值。\n", kgUtil.queryLabelWithIRI(entitiesPair.getKey()), kgUtil.queryLabelWithIRI(entitiesPair.getValue()), datatype));
            }
        }
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("answerNoValueYshape");
        ra.put("sentences", sentences);
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }


    public ResponseExecutionResult answerWithYshapeEntitiesPair(List<YshapeBNAndDPAndValue> res, List<String> ces, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("answerYshape");
        String entity1 = kgUtil.queryLabelWithIRI(res.get(0).getEntity1());
        String entity2 = kgUtil.queryLabelWithIRI(res.get(0).getEntity2());
        ra.put("entity1",entity1 )
                .put("entity2", entity2 )
                .put("datatype", res.get(0).getDatatype())
                .put("value", res.get(0).getValue());
        if (ces != null && ces.size() != 0) {
            ra.put("ces", String.join(",", ces));
        }

        List<Instruction> instructions = new ArrayList<>();
        if(res.get(0).getDatatype().equals(UNDERWRITTING_SCALE)){
            String entity = null;
            String typesofinsurance = null;
            for(String e: Arrays.asList(entity1,entity2)){
                String clazz = kgUtil.queryClass(e);
                if( clazz != null && ! clazz.equals(TYPES_OF_INSURANCE) ){
                    entity = e;
                }
                if ( clazz != null && clazz.equals(TYPES_OF_INSURANCE) ){
                    typesofinsurance = e;
                }
            }
            if (entity != null && typesofinsurance != null) {
                List<String> datatypes = kgUtil.queryDatatypesWithEntity(entity);
                if(datatypes.stream().anyMatch(x -> x.equals(DEFINATION))){
                    List<String> vals = kgUtil.queryValuewithEntityAndDatatype(entity,DEFINATION);
                    if(vals.size() > 0)
                        instructions.add(new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", entity))
                                .addParam("items", Arrays.asList(String.format("%s的定义",entity))));
                }
                List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryOtherMedicalUnderwrittingWithEntityAndtypeofinsurance(entity,typesofinsurance);
                Set<String> items = new HashSet<>();
                for (YshapeBNAndDPAndValue ydv : ydvs) {
                    items.add(String.format("%s%s的%s", ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype()));
                }
                if(items.size() != 0){
                    instructions.add(new Instruction("recommendation").addParam("title", String.format("更多关于%s的核保问题",entity)).addParam("items",items));
                }
            }
        }

        instructions.add(new Instruction("feedback").addParam("display", "true"));
        result.setResponseAct(ra);
        result.setInstructions(instructions);
        return result;
    }

    public ResponseExecutionResult askMultiAnswerOfYshapeEntiesPair(List<YshapeBNAndDPAndValue> res, List<String> ces, Context context) {
        // if ces else
        List<String> items = new ArrayList<>();
        if (ces == null || ces.size() == 0) {
            for (YshapeBNAndDPAndValue r : res) {
                items.add(String.format("%s和%s的%s?\n", kgUtil.queryLabelWithIRI(r.getEntity1()), kgUtil.queryLabelWithIRI(r.getEntity2()), r.getDatatype()));
            }
        } else {
            for (YshapeBNAndDPAndValue r : res) {
                items.add(String.format("%s和%s在%s下的%s在%s下?\n", kgUtil.queryLabelWithIRI(r.getEntity1()), kgUtil.queryLabelWithIRI(r.getEntity2()), String.join(",", ces), r.getDatatype()));
            }
        }

        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("askWhichDpOfYshape");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                        .addParam("title", "更多相关问题")
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }


    public ResponseExecutionResult askMultiAnswer(List<EntityAndBNAndDatatypeAndValue> res, List<String> ces, Context context) {
        Set<String> items = new HashSet<>();
        if (ces != null) {
            for (EntityAndBNAndDatatypeAndValue r : res) {
                String complex = kgUtil.queryCpWithEntityAndBN(kgUtil.queryLabelWithIRI(r.getEntity()), r.getBn().getIri());
                items.add(String.format("%s在%s下%s的%s?", kgUtil.queryLabelWithIRI(r.getEntity()), String.join(",", ces), complex == null ? "" : complex, r.getDatatypeAndValue().getDatatype()));
            }
        } else {
            for (EntityAndBNAndDatatypeAndValue r : res) {
                String complex = kgUtil.queryCpWithEntityAndBN(kgUtil.queryLabelWithIRI(r.getEntity()), r.getBn().getIri());
                items.add(String.format("%s%s的%s?", kgUtil.queryLabelWithIRI(r.getEntity()), complex == null ? "" : complex, r.getDatatypeAndValue().getDatatype()));
            }
        }

        items = processRecommendations(items.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                        .addParam("title", "更多相关问题")
                        .addParam("items", items.stream().collect(Collectors.toList())),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }


    public ResponseExecutionResult askWhichEntityOfCp(String complex, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", complex);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);
        context.getSlots().put("cpContextConditionEntities", null);
        List<String> entities = kgUtil.queryEntityWithObject(kgUtil.queryCpIRI(complex));
        Map<String, List<String>> items = new HashMap<>();
        items.put("实体", entities);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("MultiConditionTable");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("multiple_condition")
                        .addParam("title", String.format("需要您更多关于%s的信息", complex))
                        .addParam("items", items),
                new Instruction("feedback").addParam("display", "true")));
        return result;
    }

    public ResponseExecutionResult askEntities(List<String> entities) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation").addParam("title", "更多的实体").addParam("items", entities)
                , new Instruction("feedback").addParam("display", "true")
        ));
        return result;
    }

    public ResponseExecutionResult askEntitiesWithCpAndDp(List<String> entities, String complex, String datatype, Map<String, String> cpces, Context context) {
        List<String> sentences = new ArrayList<>();
        List<String> ces = cpces == null || cpces.size() == 0 ? new ArrayList<>() : cpces.keySet().stream().collect(Collectors.toList());
        for (String entity : entities) {
            sentences.add(String.format("%s%s的%s的%s", kgUtil.queryLabelWithIRI(entity), ces.size() == 0 ? "" : String.join(",", ces), complex, datatype));
        }
        sentences = processRecommendations(sentences,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation").addParam("title", "更多的问题")
                        .addParam("items", sentences)
                , new Instruction("feedback").addParam("display", "true")
        ));
        return result;
    }

    public ResponseExecutionResult askEntitiesWithCp(List<String> entities, String complex, Map<String, String> cpces, Context context) {
        List<String> sentences = new ArrayList<>();
        List<String> ces = cpces == null || cpces.size() == 0 ? new ArrayList<>() : cpces.keySet().stream().collect(Collectors.toList());
        for (String entity : entities) {
            sentences.add(String.format("%s%s的%s", kgUtil.queryLabelWithIRI(entity), ces == null || ces.size() == 0 ? "" : String.join(",", ces), complex));
        }
        sentences = processRecommendations(sentences,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        ResponseAct ra = new ResponseAct("recommendation");
        result.setResponseAct(ra);
        result.setInstructions(Arrays.asList(new Instruction("recommendation").addParam("title", "更多的问题")
                        .addParam("items", sentences)
                , new Instruction("feedback").addParam("display", "true")
        ));
        return result;
    }

    private StringBuilder genEntityAndCesPhrase(String entity, List<String> conditions) {
        StringBuilder conditionSentence = new StringBuilder();
        if (conditions.size() != 0) {
            List<String> eAndces = new ArrayList<String>() {{
                add(entity);
                addAll(conditions);
            }};
            if (RECOMM.containsKey(String.join("#", eAndces)))
                conditionSentence.append(String.join(",", conditions));
            else {
                conditionSentence.append("在");
                conditionSentence.append(String.join(",", conditions));
                conditionSentence.append("的时候");
            }
        }
        return conditionSentence;
    }


    public ResponseExecutionResult askWhichSubPropertiesOfCp(String entity, String complex, Collection<String> subProperties, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        List<String> items = new ArrayList<>();
        for(String subProperty:subProperties){
            items.add(String.format("%s%s",entity,subProperty));
        }
        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("recommendation"));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title",String.format("更多关于%s%s的问题",entity,complex))
                        .addParam("items",items),
                new Instruction("feedback").addParam("display","true")
        ));
        return result;
    }


    public ResponseExecutionResult askWhichSubPropertiesOfCp(String entity, String complex,String datatype, Collection<String> subProperties, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> items = new ArrayList<>();
        for(String subProperty:subProperties){
            items.add(String.format("%s%s的%s",entity,subProperty,datatype));
        }
        items = processRecommendations(items,context);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("recommendation"));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title",String.format("更多关于%s%s的%s的问题",entity,complex,datatype))
                        .addParam("items",items),
                new Instruction("feedback").addParam("display","true")
        ));
        return result;
    }

    public ResponseExecutionResult askWhichEntityAndCpAndDp(String clazz, Context context) {

        logger.debug("In KnowledgeQueryResponse::askWhichEntityAndCpAndDp.");

        List<EntityAndCpAndDatatypeAndValue> ecdvs = kgUtil.queryEntityAndCpAndDatatypeAndValueWithClass(clazz);
        Set<String> items = new HashSet<>();
        for(EntityAndCpAndDatatypeAndValue ecda: ecdvs){
            if(items.size() > 5)
                break;
            String complex = kgUtil.queryLabelWithIRI(ecda.getComplex());
            if(complex != null && complex.trim().length() != 0 && complex.equals("http://hual.ai/taikang/taikang_rs#with_bn"))
                continue;
            items.add(String.format("%s%s的%s",ecda.getEntity(),complex == null || complex.trim().length() == 0||complex.startsWith("http://")?"":complex,ecda.getDatatypeAndValue().getDatatype()));
        }
        Set<String> buttons = ecdvs.stream().map(x -> x.getEntity()).collect(Collectors.toSet()).stream().limit(5).collect(Collectors.toSet());
        buttons.add(HOT_ISSUE);

        items = processRecommendations(items.stream().collect(Collectors.toList()), context).stream().collect(Collectors.toSet());
        ResponseExecutionResult result = new ResponseExecutionResult();
        //result.setResponseAct(new ResponseAct("recommendation"));
//        result.setResponseAct(new ResponseAct());
        result.setResponseAct(new ResponseAct("EmptyAnswer"));
        result.setInstructions(Arrays.asList(
                new Instruction("recommendation")
                        .addParam("title",String.format("%s包含多项概念, 您可能想了解",clazz))
                        //.addParam("title",String.format("更多关于%s的问题",clazz))
                        .addParam("items",items.stream().collect(Collectors.toList())),
                new Instruction("input_button")
                        .addParam("buttons",buttons.stream().collect(Collectors.toList())),
                new Instruction("feedback").addParam("display","true")
        ));
        return result;
    }

    public static List<String> processRecommendations(List<String> recommdations,Context context){
        List<String> history_queries = (List<String>) context.getSlots().get("HistoryQueries");
        List<String> unasked_items = new ArrayList<>(recommdations);
        if(history_queries != null && history_queries.size()>0)
            unasked_items.removeAll(history_queries);
        if(unasked_items.size() != 0){
            if(unasked_items.size() <= 3)
                return unasked_items;
            else
                return unasked_items.subList(0,3);
        }
        return recommdations;
    }


    public ResponseExecutionResult answerMedicalUnderwritting(String entity,Collection<ObjectProperty> objects){
        String clazz = kgUtil.queryClass(entity);
        if(clazz != null && clazz.equals(DISEASE)){
            List<ObjectProperty> hbs = objects.stream().filter(x -> x.getUri().equals(WITH_BN)).collect(Collectors.toList());
            ResponseExecutionResult result = new ResponseExecutionResult();
            List<Instruction> instructions = new ArrayList<>();
            if(hbs.size() == 0){
                result.setResponseAct(new ResponseAct("NoMedicalUnderwritting").put("entity",entity));
            }else{
                result.setResponseAct(new ResponseAct("AnswerMedicalUnderwritting"));
                for(ObjectProperty hb:hbs) {
                    List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryYshapeBNLabelsAndDatatypes(entity, hb.getBN().getIri());
                    for (YshapeBNAndDPAndValue ydv : ydvs) {
                        instructions.add(new Instruction("info_card").addParam("title", String.format("%s%s的%s", ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype())).addParam("content", ydv.getValue()));
                    }
                }
                if(instructions.size() == 0)
                    result.setResponseAct(new ResponseAct("NoMedicalUnderwritting").put("entity",entity));
            }
            List<String> datatypes = kgUtil.queryDatatypesWithEntity(entity);
            if(datatypes.stream().anyMatch(x -> x.equals(DEFINATION))){
                List<String> vals = kgUtil.queryValuewithEntityAndDatatype(entity,DEFINATION);
                if(vals.size() > 0)
                    instructions.add(new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", entity))
                            .addParam("items", Arrays.asList(String.format("%s的定义",entity))));
            }
            result.setInstructions(instructions);
            return result;

        }

        if(clazz != null && clazz.equals(TYPES_OF_INSURANCE)){
            List<ObjectProperty> hbs = objects.stream().filter(x -> x.getUri().equals(WITH_BN)).collect(Collectors.toList());
            ResponseExecutionResult result = new ResponseExecutionResult();
            result.setResponseAct(new ResponseAct("AnswerMedicalUnderwritting"));
            List<String> datatypes = kgUtil.queryDatatypesWithEntity(entity);
            List<Instruction> instructions = new ArrayList<>();
            if(datatypes.stream().anyMatch(x -> x.equals(DEFINATION))){
                List<String> vals = kgUtil.queryValuewithEntityAndDatatype(entity,DEFINATION);
                if(vals.size() > 0)
                    instructions.add(new Instruction("info_card").addParam("title", String.format("%s的%s", entity,DEFINATION))
                            .addParam("content", Arrays.asList(vals.get(0))));
            }
            if(instructions.size() == 0)
                result.setResponseAct(new ResponseAct("answerNoValue").put("entity",entity).put("datatype",DEFINATION));
            if(hbs.size() > 0){
                Set<String> items = new HashSet<>();
                for(ObjectProperty hb:hbs) {
                    List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryYshapeBNLabelsAndDatatypes(entity, hb.getBN().getIri(),DISEASE);
                    for (YshapeBNAndDPAndValue ydv : ydvs) {
                        items.add(String.format("%s%s的%s",ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype()));
                    }
                }
                if(items.size() > 0)
                    instructions.add(new Instruction("recommendation").addParam("title", String.format("更多关于%s的内容",entity)).addParam("items",items.stream().collect(Collectors.toList())));
            }
            result.setInstructions(instructions);
            return result;

        }

        if(clazz != null && clazz.equals(SPECIAL_CROWD)){
            List<ObjectProperty> hbs = objects.stream().filter(x -> x.getUri().equals(WITH_BN)).collect(Collectors.toList());
            ResponseExecutionResult result = new ResponseExecutionResult();
            List<Instruction> instructions = new ArrayList<>();
            if(hbs.size() == 0){
                result.setResponseAct(new ResponseAct("NoMedicalUnderwritting").put("entity",entity));
            }else{
                result.setResponseAct(new ResponseAct("AnswerMedicalUnderwritting"));
                for(ObjectProperty hb:hbs) {
                    List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryYshapeBNLabelsAndDatatypes(entity, hb.getBN().getIri(),TYPES_OF_INSURANCE);
                    for (YshapeBNAndDPAndValue ydv : ydvs) {
                        instructions.add(new Instruction("info_card").addParam("title", String.format("%s%s的%s", ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype())).addParam("content", ydv.getValue()));
                    }
                }
                if(instructions.size() == 0)
                    result.setResponseAct(new ResponseAct("NoMedicalUnderwritting").put("entity",entity));
            }
            List<String> datatypes = kgUtil.queryDatatypesWithEntity(entity);
            if(datatypes.stream().anyMatch(x -> x.equals(DEFINATION))){
                List<String> vals = kgUtil.queryValuewithEntityAndDatatype(entity,DEFINATION);
                if(vals.size() > 0)
                    instructions.add(new Instruction("recommendation").addParam("title", String.format("更多关于%s的问题", entity))
                            .addParam("items", Arrays.asList(String.format("%s的定义",entity))));
            }
            result.setInstructions(instructions);
            return result;

        }


        return null;
    }


    public ResponseExecutionResult answerUnderwrittingScale(String property,Context context){
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("answerUnderwrittingScale"));
        result.setInstructions(Arrays.asList(new Instruction("recommendation").addParam("title","您可能关注")
                .addParam("items",processRecommendations(FAQResponse.getRecommendations(property),context))));
        return result;
    }

    public ResponseExecutionResult answerEntitiesAndUnderwrittingScale(List<String> entities,String datatype,Context context){
        List<String> diseases = kgUtil.queryEntitiesDerivedFromClass(entities,DISEASE);
        List<String> crowds = kgUtil.queryEntitiesDerivedFromClass(entities,SPECIAL_CROWD);
        List<YshapeBNAndDPAndValue> diseaseAndinsurance = new ArrayList<>();
        for(String disease:diseases)
            diseaseAndinsurance.addAll(kgUtil.queryYshapeBNLabelsAndDatatypesWithEntityAndClass(disease,TYPES_OF_INSURANCE,datatype));
        List<YshapeBNAndDPAndValue> crowdsAndinsurance = new ArrayList<>();
        for(String crowd:crowds)
            crowdsAndinsurance.addAll(kgUtil.queryYshapeBNLabelsAndDatatypesWithEntityAndClass(crowd,TYPES_OF_INSURANCE,datatype));
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("AnswerMedicalUnderwritting"));
        List<Instruction> instructions = new ArrayList<>();
        for(List<YshapeBNAndDPAndValue> ydvs: Arrays.asList(diseaseAndinsurance,crowdsAndinsurance)){
            for (YshapeBNAndDPAndValue ydv : ydvs) {
                instructions.add(new Instruction("info_card").addParam("title", String.format("%s%s的%s", ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype())).addParam("content", ydv.getValue()));
            }
        }
        if(instructions.size() == 0)
            result.setResponseAct(new ResponseAct("NoMedicalUnderwritting").put("entities",entities));

        for(List<String> es: Arrays.asList(diseases,crowds)){
            Set<String> items = new HashSet<>();
            List<String> esthathasvalue = kgUtil.queryEntitiesAndDatatypeThatHaveValue(es,DEFINATION);
            for(String ethathasvalue: esthathasvalue){
                items.add(String.format("%s的%s",ethathasvalue,DEFINATION));
            }
            if(items.size()>0){
                instructions.add(new Instruction("recommendation").addParam("title", "为您推荐")
                        .addParam("items", items));
            }

        }
        result.setInstructions(instructions);
        return result;
    }

    public ResponseExecutionResult answerUnderwrittingScaleWithEntitiesPairs(List<Pair<String,String>> entitiesPairs,String datatype,Context context){
        List<Pair<String,String>> crowdsAndInsurances = new ArrayList<>();
        List<Pair<String,String>> diseasesAndInsurances = new ArrayList<>();
        for(Pair<String,String> entitiesPair: entitiesPairs){
            Pair<String,String> x =  kgUtil.checkEntitiesPairSatisfyClasses(entitiesPair,new Pair<>(SPECIAL_CROWD,TYPES_OF_INSURANCE));
            if(x != null){
                crowdsAndInsurances.add(x);
                continue;
            }
            Pair<String,String> y =  kgUtil.checkEntitiesPairSatisfyClasses(entitiesPair,new Pair<>(DISEASE,TYPES_OF_INSURANCE));
            if(y != null){
                diseasesAndInsurances.add(y);
                continue;
            }
        }
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("AnswerMedicalUnderwritting"));
        List<Instruction> instructions = new ArrayList<>();
        List<String> items = new ArrayList<>();
        for(Pair<String,String> crowdAndInsurance:crowdsAndInsurances){
            List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryYshapeBNLabelsAndDatatypesWithE1AndE2AndDatatype(crowdAndInsurance.getKey(),crowdAndInsurance.getValue(),datatype);
            for (YshapeBNAndDPAndValue ydv : ydvs) {
                instructions.add(new Instruction("info_card").addParam("title", String.format("%s%s的%s", ydv.getEntity1(), ydv.getEntity2(), ydv.getDatatype())).addParam("content", ydv.getValue()));
            }
            List<YshapeBNAndDPAndValue> crowdsAndotherinsurances = kgUtil.queryOtherMedicalUnderwrittingWithEntityAndtypeofinsurance(crowdAndInsurance.getKey(),crowdAndInsurance.getValue());
            for (YshapeBNAndDPAndValue coi : crowdsAndotherinsurances) {
                items.add(0,String.format("%s%s的%s",coi.getEntity1(), coi.getEntity2() ,coi.getDatatype()));
            }
        }



        for(Pair<String,String> diseaseAndInsurance: diseasesAndInsurances){
            List<YshapeBNAndDPAndValue> ydvs = kgUtil.queryYshapeBNLabelsAndDatatypesWithE1AndE2AndDatatype(diseaseAndInsurance.getKey(),diseaseAndInsurance.getValue(),datatype);
            for (YshapeBNAndDPAndValue ydv : ydvs) {
                items.add(0,String.format("%s%s的%s",ydv.getEntity1(), ydv.getEntity2() ,ydv.getDatatype()));
            }
        }

        if(items.size()>0){
            instructions.add(new Instruction("recommendation").addParam("title", "为您推荐")
                    .addParam("items", items.stream().distinct().collect(Collectors.toList())));
        }
        if(instructions.size() == 0){
            result.setResponseAct(new ResponseAct("NoMedicalUnderwritting"));
        }
        result.setInstructions(instructions);
        return result;
    }
}
