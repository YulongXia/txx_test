package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.*;
import ai.hual.labrador.faq.FaqAnswer;
import ai.hual.labrador.faq.FaqRankResult;
import ai.hual.labrador.nlg.ResponseAct;
import ai.hual.labrador.nlu.constants.SystemIntents;
import responses.KnowledgeQueryResponse;
import utils.KnowledgeQueryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnknownExecution implements Execution {

    private AccessorRepository accessorRepository;
    private UnknownClazz unknownClazz;

    @Override
    public void setUp(Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
        this.unknownClazz = new UnknownClazz(accessorRepository);
    }

    @Override
    public ExecutionResult execute(Context context) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setInstructions(new ArrayList<>());

        FaqAnswer answer = (FaqAnswer) context.getSlots().get("sys.faqAnswer");
        if (answer == null || answer.getAnswer() == null ) {
            result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
            result.getInstructions().add(new Instruction("msginfo_more")
                    .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct())));
//            return unknownClazz.execute(context,accessorRepository);

        } else{
            FaqRankResult hit = answer.getHits().get(0);
            if ("chatting".equalsIgnoreCase(hit.getCategory())) {
                result.getInstructions().add(new Instruction("msginfo_chat")
                        .addParam("question", hit.getQuestion())
                        .addParam("quesId", hit.getQaid())
                        .addParam("answer", hit.getAnswer())
                        .addParam("ansId", hit.getQaid())
                        .addParam("score", hit.getScore()));
                result.setResponseAct(new ResponseAct("answer")
                        .put("result", hit.getAnswer()));
            } else {
                List<String> relations = accessorRepository.getRelatedQuestionAccessor().relatedQuestionByFAQ(hit.getQaid());
                result.setResponseAct(new ResponseAct("faq")
                        .put("result", hit.getAnswer())
                        .put("question", hit.getQuestion())
                        .put("relations", relations));
                result.getInstructions().add(new Instruction("msginfo_faq_a")
                        .addParam("title", hit.getQuestion())
                        .addParam("quesId", hit.getQaid())
                        .addParam("answer", hit.getAnswer())
                        .addParam("ansId", hit.getQaid())
                        .addParam("score", hit.getScore())
                        .addParam("relations", relations));
            }
        }


        return result;
    }

}
