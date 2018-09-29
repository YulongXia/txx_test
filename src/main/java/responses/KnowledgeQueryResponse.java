package responses;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.kg.KnowledgeStatus;
import ai.hual.labrador.nlg.ResponseAct;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import pojo.BlankNode;
import pojo.ConditionEntity;
import pojo.ObjectProperty;
import pojo.YshapeBNAndDP;
import utils.KnowledgeQueryUtils;
import utils.LimitSub;

import java.util.*;
import java.util.stream.Collectors;

public class KnowledgeQueryResponse {

    private KnowledgeQueryUtils kgUtil;
    private AccessorRepository accessorRepository;

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
        result.setResponseAct(new ResponseAct("guess")
                .put("entity", yshapeBNAndDP.getBN().getLabel())
                .put("datatype", yshapeBNAndDP.getDatatype()));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_yes_no")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Arrays.asList(yshapeBNAndDP.getEntity1(), yshapeBNAndDP.getEntity2()))
                        .addParam("object", yshapeBNAndDP.getYshapeURI())
                        .addParam("bnlabel", yshapeBNAndDP.getBN().getLabel())
                        .addParam("condition", null)
                        .addParam("datatype", yshapeBNAndDP.getDatatype())
                        .addParam("suggestions", Arrays.asList("是", "否"))));
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
        if(entities.size() > 5) {
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("entities", ents));
        }else{
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
        ResponseExecutionResult result = new ResponseExecutionResult();
        if(entities.size() > 5 ){
            List<String> ents5 = LimitSub.get5(ents);

            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("entities", ents5)
                    .put("property", datatype));
        }else{
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("entities", entities)
                    .put("property", datatype));
        }


//        List<String> ents5 = LimitSub.get5(ents);
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
                        .addParam("datatype", datatype)
                        .addParam("suggestions", entities)));
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

        ResponseExecutionResult result = new ResponseExecutionResult();
        if(entities.size() > 5){
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("property", objectLabel)
                    .put("entities",ents5));
        }else{
            result.setResponseAct(new ResponseAct("whichEntity")
                    .put("property", objectLabel)
                    .put("entities",entities));
        }


//        for (int i = 0; i < ents5.size(); i++) {
//            result.getResponseAct().put("entity" + i, ents5.get(i));
//        }

        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_ents")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", null)
                        .addParam("object", object)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", entities)));
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

    public ResponseExecutionResult askWhichSubProperties(String entity,Collection<String> subProperties, Context context) {
        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichSubProperties")
                .put("entity",entity)
                .put("subProperties", subProperties));
        result.setInstructions(Collections.singletonList(
                new Instruction("suggestion_kb_dps")
                        .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", null)
                        .addParam("suggestions", subProperties)));
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
                .put("object",object.substring(object.indexOf("#")+1))
                .put("datatypes",datatypes)
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

        Set<String> bns = new LinkedHashSet<>();
        Set<String> properties = new LinkedHashSet<>(datatypes);
        for (ObjectProperty object : objects) {
            if (object.getBN().getLabel() != null) {
                bns.add(object.getBN().getLabel());
            } else if (object.getLabel() != null) {
                properties.add(object.getLabel());
            }
        }

        ResponseExecutionResult result = new ResponseExecutionResult();
        if (!properties.isEmpty() && bns.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichDatatype")
                    .put("entity", entity)
                    .put("datatypes", properties));
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_dps")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", null)
                            .addParam("suggestions", properties)));

        } else if (properties.isEmpty() && !bns.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichBN")
                    .put("bns", bns));
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_bns")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", null)
                            .addParam("suggestions", bns)));

        } else if (!properties.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichProperty")
                    .put("entity", entity)
                    .put("bns", bns)
                    .put("properties", properties));
            Set<String> sug = new LinkedHashSet<>();
            sug.addAll(bns);
            sug.addAll(properties);
            result.setInstructions(Collections.singletonList(
                    new Instruction("suggestion_kb_props")
                            .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct()))
                            .addParam("entities", Collections.singletonList(entity))
                            .addParam("object", null)
                            .addParam("bnlabel", null)
                            .addParam("condition", null)
                            .addParam("datatype", null)
                            .addParam("suggestions", sug)));
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


    public ResponseExecutionResult askWhichBN(List<String> bnlabels, Context context) {
        context.getSlots().put("contextEntity", null);
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", null);
        context.getSlots().put("contextConditionEntity", null);

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("whichBN")
                .put("bns",bnlabels ));
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
                ops.add(objectProperty.getLabel());
            }
        }

        ResponseExecutionResult result = new ResponseExecutionResult();
        if (!ops.isEmpty() && bns.isEmpty()) {
            result.setResponseAct(new ResponseAct("whichObject")
                    .put("entity", entity)
                    .put("datatype", datatype)
                    .put("ops",ops));
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

        context.getSlots().put("contextEntity", Collections.singletonList(entity));
        context.getSlots().put("contextBN", null);
        context.getSlots().put("contextObject", null);
        context.getSlots().put("contextDatatype", datatype);
        context.getSlots().put("contextConditionEntity", null);

        List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByKG(
                kgUtil.queryEntityIRI(entity), kgUtil.queryDatatypeIRI(datatype));
        relations = relations.isEmpty() ? null : relations;

        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("kg")
                .put("entity", entity)
                .put("datatype", datatype)
                .put("result", value)
                .put("relations", relations));
        result.setInstructions(Collections.singletonList(
                new Instruction("msginfo_kb_a")
                        .addParam("title", accessorRepository.getNLG().generate(new ResponseAct("kg")
                                .put("entity", entity)
                                .put("datatype", datatype)))
                        .addParam("answer", value)
                        .addParam("entities", Collections.singletonList(entity))
                        .addParam("object", null)
                        .addParam("bnlabel", null)
                        .addParam("condition", null)
                        .addParam("datatype", datatype)
                        .addParam("relations", relations)));
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
                        .addParam("clazz",clazz )));

        return result;
    }

}
