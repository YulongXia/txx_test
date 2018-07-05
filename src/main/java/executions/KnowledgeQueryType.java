package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.nlg.ResponseAct;
import ai.hual.labrador.nlu.constants.SystemIntents;

import java.util.Collections;

class KnowledgeQueryType {

    ExecutionResult execute(Context context, AccessorRepository accessorRepository) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct(SystemIntents.UNKNOWN));
        result.setInstructions(Collections.singletonList(new Instruction("msginfo_more")
                .addParam("answer",accessorRepository.getNLG().generate(result.getResponseAct()))));
        return result;

//        List<Instruction> instructions = new ArrayList<>();
//        result.setInstructions(instructions);
//        String entity = Optional.ofNullable(context.getSlots().get("entity")).map(Object::toString).orElse(null);
//        if (entity == null) {
//            result.setResponseAct(new ResponseAct("answer")
//                    .put("result", "which entity do you ask?"));
//            instructions.add(new Instruction("msginfo_more")
//                    .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct())));
//            return result;
//        }
//        result.setResponseAct(new ResponseAct("answer")
//                .put("result", entity));
//        instructions.add(new Instruction("msginfo_more")
//                .addParam("content", accessorRepository.getNLG().generate(result.getResponseAct())));
//        return result;
    }

}
