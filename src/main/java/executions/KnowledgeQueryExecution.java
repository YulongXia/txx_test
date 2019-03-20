package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dialog.AccessorRepositoryImpl;
import ai.hual.labrador.dialog.accessors.RelatedQuestionAccessor;
import ai.hual.labrador.dm.*;
import ai.hual.labrador.dm.java.DialogConfig;
import ai.hual.labrador.kg.KnowledgeStatus;
import ai.hual.labrador.kg.KnowledgeStatusAccessor;
import ai.hual.labrador.nlg.ResponseAct;
import ai.hual.labrador.nlu.NLUResult;
import ai.hual.labrador.nlu.constants.SystemIntents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.KnowledgeQueryUtils;

import java.util.*;
import java.util.stream.Collectors;

public class KnowledgeQueryExecution implements Execution {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryExecution.class);

    private AccessorRepository accessorRepository;

    private KnowledgeQuerySubclasses askSubclasses = new KnowledgeQuerySubclasses();
    private KnowledgeQueryInstances askInstances = new KnowledgeQueryInstances();
    private KnowledgeQueryType askType = new KnowledgeQueryType();
    private KnowledgeQueryProperty askProperty;

    @Override
    public void setUp(Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;

        // TODO remove after real knowledge status accessor utilized
        if (accessorRepository.getKnowledgeStatusAccessor() == null && accessorRepository instanceof AccessorRepositoryImpl) {
            ((AccessorRepositoryImpl) accessorRepository).withKnowledgeStatusAccessor(new KnowledgeStatusAccessor() {
                @Override
                public KnowledgeStatus instanceStatus(String s) {
                    return KnowledgeStatus.ENABLED;
                }

                @Override
                public KnowledgeStatus propertyStatus(String s, String s1) {
                    return KnowledgeStatus.ENABLED;
                }
            });
        }
        if (accessorRepository.getRelatedQuestionAccessor() == null && accessorRepository instanceof AccessorRepositoryImpl) {
            ((AccessorRepositoryImpl) accessorRepository).withRelatedQuestionAccessor(new RelatedQuestionAccessor() {
                @Override
                public List<String> relatedQuestionByFAQ(int i) {
                    return Collections.emptyList();
                }

                @Override
                public List<String> relatedQuestionByKG(String s, String s1) {
                    return Collections.emptyList();
                }
            });
        }

        this.askProperty = new KnowledgeQueryProperty(accessorRepository);
    }

    @Override
    public ExecutionResult execute(Context context) {
        String chosenMatcher = context.<NLUResult>get(DialogConfig.SYSTEM_NLU_RESULT_NAME).getChosenMatcher();
        Double chosenScore = context.<NLUResult>get(DialogConfig.SYSTEM_NLU_RESULT_NAME).getChosenScore();
        ResponseExecutionResult result = new ResponseExecutionResult();
        if (chosenMatcher.equals("ClassifierIntentMatcher") && chosenScore < 0.3) {
            result = (ResponseExecutionResult) askProperty.execute(context);
            if(!result.getResponseAct().getSlots().get("result").isEmpty()) {
                List<String> entities = (List<String>)context.getSlots().get("entity");
                String cp = (String) context.getSlots().get("ComplexProperty");
                String dp = (String) context.getSlots().get("datatype");
                if(entities.size() > 0){
                    List<String> allQuerys = new KnowledgeQueryUtils(accessorRepository.getKnowledgeAccessor(), accessorRepository.getKnowledgeStatusAccessor()).queryAllProperties(entities);
                    entities.forEach(x -> String.format("%s%s的%s",x,cp,dp));
                    allQuerys.retainAll(entities);
                    if(allQuerys.size() > 0){
                        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                                        .addParam("title", "您想了解的可能是以下内容")
                                        .addParam("items", allQuerys),
                                new Instruction("feedback").addParam("display","true")));
                        return result;
                    }
                }
                result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
                result.setInstructions(null);
                return result;
            }else{
                return result;
            }
        }
        else if(chosenMatcher.equals("ClassifierIntentMatcher") && chosenScore >= 0.3 && chosenScore < 0.8){
            result = (ResponseExecutionResult) askProperty.execute(context);
            if(!result.getResponseAct().getSlots().get("result").isEmpty()) {
                List<String> entities = (List<String>)context.getSlots().get("entity");
                String cp = (String) context.getSlots().get("ComplexProperty");
                String dp = (String) context.getSlots().get("datatype");
                if(entities.size() > 0){
                    List<String> allQuerys = new KnowledgeQueryUtils(accessorRepository.getKnowledgeAccessor(), accessorRepository.getKnowledgeStatusAccessor()).queryAllProperties(entities);
                    entities.forEach(x -> String.format("%s%s的%s",x,cp,dp));
                    allQuerys.retainAll(entities);
                    if(allQuerys.size() > 0){
                        result.setInstructions(Arrays.asList(new Instruction("recommendation")
                                        .addParam("title", "您想了解的可能是以下内容")
                                        .addParam("items", allQuerys),
                                new Instruction("feedback").addParam("display","true")));
                        return result;
                    }else{
                        context.getSlots().remove("entity");
                        return askProperty.execute(context);
                    }
                }
                return result;
            }else{
                return result;
            }
        }
        if (Optional.ofNullable(context.getSlots().get("反问被否认")).map(x -> (boolean) x).orElse(false)) {
            logger.debug("Suggest alternatives");
            return suggestAlternatives(context);
        } else {
            logger.debug("Knowledge query");
            return query(context);
        }
    }

    private ExecutionResult suggestAlternatives(Context context) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setInstructions(new ArrayList<>());

        List<List<String>> alternatives = (List<List<String>>) context.getSlots().get("alternatives");
        if (alternatives == null || alternatives.isEmpty()) {
            result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
            result.getInstructions().add(new Instruction("msginfo_more")
                    .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct())));
            return result;
        }

        List<String> questions = new ArrayList<>();
        List<Float> scores = new ArrayList<>();
        for (List<String> alternative : alternatives) {
            StringBuilder sb = new StringBuilder();
            if (alternative.get(0) != null) {
                sb.append(alternative.get(0));
            }
            if (alternative.get(1) != null) {
                String object = new KnowledgeQueryUtils(accessorRepository.getKnowledgeAccessor(), accessorRepository.getKnowledgeStatusAccessor()).queryObjectLabel(alternative.get(1));
                if (object != null) {
                    sb.append(object);
                }
            }
            if (alternative.get(2) != null) {
                sb.append(alternative.get(2));
            }
            String question = sb.toString();
            if (!question.isEmpty()) {
                questions.add(question);
                scores.add(Float.parseFloat(alternative.get(3)));
            }
        }

        context.getSlots().put("suggested_kg", questions);
        questions = questions.stream().map(str -> "#" + str + "#").collect(Collectors.toList());
        result.setResponseAct(new ResponseAct("whichEntityAndDPOP")
                .put("entitiesAndDPOPs", questions));
        result.getInstructions().add(new Instruction("suggestion_kb_sug")
                .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct()))
                .addParam("entities", Collections.EMPTY_LIST)
                .addParam("object", null)
                .addParam("bnlabel", null)
                .addParam("condition", null)
                .addParam("datatype", null)
                .addParam("suggestions", questions)
                .addParam("scores", scores));
        return result;
    }

    private ExecutionResult query(Context context) {
        // user replies which_entity with a class name
        if (context.getSlots().get("turn_ask_which_entity") != null) {
            int turnAskWhichEntity = (int) context.getSlots().get("turn_ask_which_entity");
            if (turnAskWhichEntity == (int) context.getSlots().get("sys.turn") - 1 &&
                    context.getSlots().get("class") != null && context.getSlots().get("entity") == null) {
                context.getSlots().put("turn_ask_which_entity", context.getSlots().get("sys.turn"));
                return askProperty.execute(context);
            }
        }

        // no target
        String target = Optional.ofNullable(context.getSlots().get(SystemIntents.KNOWLEDGE_QUERY_SLOT_TARGET)).map(Object::toString).orElse(null);
        if (target == null) {
            return askProperty.execute(context);
        }

        // depends on target
        switch (target) {
            case SystemIntents.KNOWLEDGE_QUERY_TARGET_VALUE_SUBCLASSES:
                return askSubclasses.execute(context, accessorRepository);
            case SystemIntents.KNOWLEDGE_QUERY_TARGET_VALUE_INSTANCES:
                return askInstances.execute(context, accessorRepository);
            case SystemIntents.KNOWLEDGE_QUERY_TARGET_VALUE_TYPE:
                return askType.execute(context, accessorRepository);
            case SystemIntents.KNOWLEDGE_QUERY_TARGET_VALUE_PROPERTY:
                return askProperty.execute(context);
            default:
                return askProperty.execute(context);
        }
    }

}
