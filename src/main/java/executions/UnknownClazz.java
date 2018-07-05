package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.kg.KnowledgeAccessor;
import ai.hual.labrador.kg.KnowledgeStatusAccessor;
import ai.hual.labrador.nlg.ResponseAct;
import responses.FAQResponse;
import responses.KnowledgeQueryResponse;
import utils.KnowledgeQueryUtils;
import utils.LimitSub;

import java.util.*;

class UnknownClazz {
    private AccessorRepository accessorRepository;
    private KnowledgeQueryUtils kgUtils;

    UnknownClazz(AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
        kgUtils = new KnowledgeQueryUtils(
                accessorRepository.getKnowledgeAccessor(),
                accessorRepository.getKnowledgeStatusAccessor());

    }
    ExecutionResult execute(Context context, AccessorRepository accessorRepository) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        List<Instruction> instructions = new ArrayList<>();
        result.setInstructions(instructions);
        String mainClazz = "集团总部";
        List<String> ClazzList = kgUtils.queryMainClazz(mainClazz);
        result.setResponseAct(new ResponseAct("suggestion_unknown")
                .put("clazz", ClazzList));
        instructions.add(new Instruction("suggestion_unknown")
                .addParam("answer", null)
                .addParam("entities", null)
                .addParam("object",null)
                .addParam("bnlabel",null)
                .addParam("condition",null)
                .addParam("datatype",null)
                .addParam("suggestions", null));
        return result;
    }
}
