package responses;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.faq.FaqAnswer;
import ai.hual.labrador.faq.FaqRankResult;
import ai.hual.labrador.nlg.ResponseAct;
import ai.hual.labrador.nlu.constants.SystemIntents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FAQResponse {

    private static Logger logger = LoggerFactory.getLogger(FAQResponse.class);

    private AccessorRepository accessorRepository;

    public FAQResponse(AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
    }

    public ResponseExecutionResult faq(Context context) {
        return faq(context, true);
    }

    public ResponseExecutionResult faq(Context context, boolean useChatting) {
        logger.debug("FAQ response. useChatting: {}", useChatting);
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setInstructions(new ArrayList<>());

        FaqAnswer answer = (FaqAnswer) context.getSlots().get("sys.faqAnswer");

        if (answer != null && answer.getHits() != null) {
            for (FaqRankResult hit : answer.getHits()) {
                if (hit == null) {
                    continue;
                }
                boolean chatting = "chatting".equals(hit.getCategory());
                if (useChatting || !chatting) {
                    if (chatting) {
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
                        relations = relations.isEmpty() ? null : relations;
                        result.getInstructions().add(new Instruction("msginfo_faq_a")
                                .addParam("title", hit.getQuestion())
                                .addParam("quesId", hit.getQaid())
                                .addParam("answer", hit.getAnswer())
                                .addParam("ansId", hit.getQaid())
                                .addParam("score", hit.getScore())
                                .addParam("relations", relations));
                        result.setResponseAct(new ResponseAct("faq")
                                .put("result", hit.getAnswer())
                                .put("question", hit.getQuestion())
                                .put("relations", relations));
                    }
                    return result;
                }
            }
        }

        result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
        result.getInstructions().add(new Instruction("msginfo_more")
                .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct())));
        return result;
    }

}
