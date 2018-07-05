package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.kg.utils.KnowledgeQueryUtils;
import ai.hual.labrador.nlg.ResponseAct;
import utils.LimitSub;

import java.util.*;
import java.util.stream.Collectors;

class KnowledgeQueryInstances {

    ExecutionResult execute(Context context, AccessorRepository accessorRepository) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        List<Instruction> instructions = new ArrayList<>();
        result.setInstructions(instructions);
        String clazz = Optional.ofNullable(context.getSlots().get("class")).map(Object::toString).orElse(null);
        List<String> instances = KnowledgeQueryUtils.queryInstances(clazz, accessorRepository.getKnowledgeAccessor());
        result.setResponseAct(new ResponseAct("whichEntity")
                .put("class", clazz)
                .put("entities", instances));
        instructions.add(new Instruction("suggestion_kb_ents")
                .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct()))
                .addParam("entities", Collections.singletonList(clazz))
                .addParam("object",null)
                .addParam("bnlabel",null)
                .addParam("condition",null)
                .addParam("datatype",null)
                .addParam("suggestions", instances));
        return result;
    }

}
