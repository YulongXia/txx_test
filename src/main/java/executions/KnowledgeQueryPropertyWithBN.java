package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ResponseExecutionResult;
import com.google.common.collect.ListMultimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.BNAndDatatypeAndValue;
import pojo.BNAndValue;
import pojo.BlankNode;
import pojo.ConditionEntityAndBNAndValue;
import responses.KnowledgeQueryResponse;
import utils.KnowledgeQueryUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class KnowledgeQueryPropertyWithBN {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryPropertyWithBN.class);

    private KnowledgeQueryResponse response;

    private KnowledgeQueryUtils kgUtil;

    KnowledgeQueryPropertyWithBN(AccessorRepository accessorRepository) {
        kgUtil = new KnowledgeQueryUtils(
                accessorRepository.getKnowledgeAccessor(),
                accessorRepository.getKnowledgeStatusAccessor());
        response = new KnowledgeQueryResponse(kgUtil, accessorRepository);
    }

    ResponseExecutionResult execute(Context context, String datatype) {
        List<String> bns = (List<String>) context.getSlots().get("bn");

        return bns == null || bns.isEmpty() ? null : execute(bns, datatype, context);
    }

    private ResponseExecutionResult execute(List<String> bnlabels, String datatype, Context context) {
        logger.debug("BN. 有BN");
        datatype = datatype == null ? (String) context.getSlots().get("contextDatatype") : datatype;
        if (bnlabels.size() > 1 && datatype != null) {
            return multipleBNWithDP(bnlabels, datatype, context);
        } else if (bnlabels.size() > 1) {
            return multipleBNWithoutDP(bnlabels, context);
        } else if (bnlabels.size() == 1 && datatype != null) {
            return singleBNWithDP(bnlabels.get(0), datatype, context);
        } else if (bnlabels.size() == 1) {
            return singleBNWithoutDP(bnlabels.get(0), context);
        } else {
            logger.debug("BN0. BN: 0");
            return null;
        }
    }

    ResponseExecutionResult multipleBNWithDP(List<String> bnlabels, String datatype, Context context) {
        logger.debug("BN1. 多种BN, 有DP. Query BN-DP-value");
        ListMultimap<String, BlankNode> bnsWithDP = kgUtil.queryValidBNsWithBNLabelsAndDP(bnlabels, datatype);
        if (bnsWithDP.keySet().size() > 1) {
            logger.debug("BN1.1. 多种BN都有DP. 反问whichBN的DP");
            return response.askWhichBNWithDatatype(bnlabels, datatype, context);
        } else if (bnsWithDP.keySet().size() == 1) {
            logger.debug("BN1.2. 一种BN有DP. 当成一种BN且有DP处理");
            return singleBNWithDP(bnsWithDP.keySet().iterator().next(), datatype, context);
        } else {
            logger.debug("BN1.3. 没有BN有DP. 当成多种BN且无DP处理");
            return multipleBNWithoutDP(bnlabels, context);
        }
    }

    ResponseExecutionResult multipleBNWithoutDP(List<String> bnlabels, Context context) {
        logger.debug("BN2. 多种BN, 无DP. Query BN-x-value");
        ListMultimap<String, BlankNode> bns = kgUtil.queryValidBNsWithBNLabels(bnlabels);
        if (bns.keySet().size() > 1) {
            logger.debug("BN2.1. 多种BN有DP. 反问whichBN");
            return response.askWhichBN(bnlabels, context);
        } else if (bns.keySet().size() == 1) {
            logger.debug("BN2.2. 一种BN有DP. 当成一种BN且无DP处理");
            return singleBNWithoutDP(bns.keySet().iterator().next(), context);
        } else {
            logger.debug("BN2.3. 没有BN有DP. 当成无BN且无DP处理");
            return null;
        }
    }

    ResponseExecutionResult singleBNWithDP(String bnlabel, String datatype, Context context) {
        logger.debug("BN3. 一种BN, 有DP.");
        String conditionEntity = Optional.ofNullable((String) context.getSlots().get("conditionEntity")).orElse((String) context.getSlots().get("contextConditionEntity"));
        if (conditionEntity != null) {
            return singleBNWithDPAndConditionEntity(bnlabel, datatype, conditionEntity, context);
        } else {
            return singleBNWithDPWithoutConditionEntity(bnlabel, datatype, context, true);
        }
    }

    ResponseExecutionResult singleBNWithDPAndConditionEntity(String bnlabel, String datatype, String conditionEntity, Context context) {
        logger.debug("BN3.1. 一种BN, 有DP, 有条件实体. Query BN-condition-DP-value");
        BNAndValue bnAndValue = kgUtil.queryWithBNLabelAndConditionEntityAndDatatype(bnlabel, conditionEntity, datatype);
        if (bnAndValue != null) {
            logger.debug("BN3.1.1. 查到值，回答BN在Condition的DP");
            return response.answerWithConditionWithBN(bnAndValue.getBN(), conditionEntity, datatype, bnAndValue.getValue(), context);
        } else {
            logger.debug("BN3.1.2. 没有查到，当作一种BN，有DP，无条件实体处理");
            return singleBNWithDPWithoutConditionEntity(bnlabel, datatype, context, true);
        }
    }

    ResponseExecutionResult singleBNWithDPWithoutConditionEntity(String bnlabel, String datatype, Context context, boolean dealWithNoValue) {
        logger.debug("BN3.2. 一种BN, 有DP, 无条件实体. Query BN-x-DP-value");
        List<ConditionEntityAndBNAndValue> conditionEntityAndBNAndValues = kgUtil.queryConditionEntitiesAndValuesWithBNLabelAndDatatype(bnlabel, datatype);
        Map<String, ConditionEntityAndBNAndValue> conditionEntities = conditionEntityAndBNAndValues.stream()
                .filter(x -> x.getConditionEntity().getLabel() != null)
                .collect(Collectors.toMap(x -> x.getConditionEntity().getLabel(), x -> x));
        if (conditionEntities.size() > 1) {
            logger.debug("BN3.2.1. 查到多个条件实体. 确认有没有默认");
            if (conditionEntities.containsKey("默认")) {
                logger.debug("BN3.2.1.1. 有默认. 回答BN的DP");
                ConditionEntityAndBNAndValue conditionEntityAndBNAndValue = conditionEntities.get("默认");
                return response.answerWithConditionWithBN(conditionEntityAndBNAndValue.getBNAndValue().getBN(),
                        conditionEntityAndBNAndValue.getConditionEntity().getLabel(),
                        datatype, conditionEntityAndBNAndValue.getBNAndValue().getValue(), context);
            } else {
                logger.debug("BN3.2.1.2. 没有默认. 反问BN在whichCondition的DP");
                return response.askWhichConditionEntityWithBNLabelAndDatatype(bnlabel, conditionEntities.values().stream().map(ConditionEntityAndBNAndValue::getConditionEntity).collect(Collectors.toList()), datatype, context);
            }
        } else if (conditionEntities.size() == 1) {
            logger.debug("BN3.2.2. 查到一个条件实体. 回答BN在Condition的DP");
            ConditionEntityAndBNAndValue conditionEntityAndBNAndValue = conditionEntities.values().iterator().next();
            return response.answerWithConditionWithBN(conditionEntityAndBNAndValue.getBNAndValue().getBN(),
                    conditionEntityAndBNAndValue.getConditionEntity().getLabel(),
                    datatype, conditionEntityAndBNAndValue.getBNAndValue().getValue(), context);
        } else if (conditionEntityAndBNAndValues.size() > 0) {
            logger.debug("BN3.2.3. 没有查到条件实体但是有值. 回答BN的DP");
            ConditionEntityAndBNAndValue conditionEntityAndBNAndValue = conditionEntityAndBNAndValues.get(0);
            return response.answerWithBN(conditionEntityAndBNAndValue.getBNAndValue().getBN(),
                    datatype, conditionEntityAndBNAndValue.getBNAndValue().getValue(), context);
        }
        // no value
        logger.debug("BN3.2.4. 没有查到值. 当作一种BN，无DP，无条件实体处理");
        if (dealWithNoValue) {
            return singleBNWithoutDPorConditionEntity(bnlabel, context);
        }
        logger.debug("BN3.2.4. 当作没有BN处理");
        return null;
    }

    ResponseExecutionResult singleBNWithoutDP(String bnlabel, Context context) {
        logger.debug("BN4. 一种BN, 无DP");
        String conditionEntity = Optional.ofNullable((String) context.getSlots().get("conditionEntity")).orElse((String) context.getSlots().get("contextConditionEntity"));
        if (conditionEntity != null) {
            return singleBNWithoutDPWithConditionEntity(bnlabel, conditionEntity, context);
        } else {
            return singleBNWithoutDPorConditionEntity(bnlabel, context);
        }
    }

    ResponseExecutionResult singleBNWithoutDPWithConditionEntity(String bnlabel, String conditionEntity, Context context) {
        logger.debug("BN4.1. 一种BN, 无DP, 有条件实体. Query BN-Condition-x-value");
        List<BNAndDatatypeAndValue> dpAndValues = kgUtil.queryConditionDatatypesAndValues(bnlabel, conditionEntity);
        Map<String, BNAndDatatypeAndValue> dps = dpAndValues.stream()
                .collect(Collectors.toMap(x -> x.getDatatypeAndValue().getDatatype(), x -> x));
        if (dps.size() > 1) {
            logger.debug("BN4.1.1. 查到多个DP. 反问BN在condition的whichDP");
            // TODO what if multiple BNs with same label but different IRI have the same condition entity?
            return response.askWhichDatatypeWithConditionWithBN(bnlabel, conditionEntity, dps.keySet(), context);
        } else if (dps.size() == 1) {
            logger.debug("BN4.1.2. 查到一个DP. 回答BN在condition的DP");
            BNAndDatatypeAndValue dpAndValue = dpAndValues.get(0);
            return response.answerWithConditionWithBN(dpAndValue.getBn(), conditionEntity,
                    dpAndValue.getDatatypeAndValue().getDatatype(), dpAndValue.getDatatypeAndValue().getValue(), context);
        } else {
            logger.debug("BN4.1.3. 没有查到，当作一种BN，无DP，无条件实体处理");
            return singleBNWithoutDPorConditionEntity(bnlabel, context);
        }
    }

    ResponseExecutionResult singleBNWithoutDPorConditionEntity(String bnlabel, Context context) {
        logger.debug("BN4.2. 一种BN, 无DP, 无条件实体. Query BN-x-x-value");
        List<String> datatypes = kgUtil.queryDatatypesWithBNLabel(bnlabel);
        if (datatypes.size() > 1) {
            logger.debug("BN4.2.2. 多种DP. 反问BN的whichDP");
            return response.askWhichDatatypeWithBN(bnlabel, datatypes, context);
        } else if (datatypes.size() == 1) {
            String datatype = datatypes.get(0);
            return singleBNWithDPWithoutConditionEntity(bnlabel, datatype, context, false);
        } else {
            logger.debug("BN4.2.3. BN没有DP，当作没有BN处理");
            return null;
        }
    }

}
