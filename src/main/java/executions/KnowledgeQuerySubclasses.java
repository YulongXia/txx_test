package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ExecutionResult;
import ai.hual.labrador.dm.Instruction;
import ai.hual.labrador.dm.ResponseExecutionResult;
import ai.hual.labrador.kg.utils.KnowledgeQueryUtils;
import ai.hual.labrador.nlg.ResponseAct;
import utils.LimitSub;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class KnowledgeQuerySubclasses {

    ExecutionResult execute(Context context, AccessorRepository accessorRepository) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        List<Instruction> instructions = new ArrayList<>();
        result.setInstructions(instructions);
        String clazz = Optional.ofNullable(context.getSlots().get("class")).map(Object::toString).orElse(null);
        List<String> subclasses = KnowledgeQueryUtils.querySubclasses(clazz, accessorRepository.getKnowledgeAccessor());

//        Set<String> subs = subclasses.stream().map(str->"#"+str+"#").collect(Collectors.toSet());
        result.setResponseAct(new ResponseAct("whichClass")
                .put("class", clazz)
                .put("subClasses", subclasses));
        instructions.add(new Instruction("suggestion_kb_sug")
                .addParam("answer", accessorRepository.getNLG().generate(result.getResponseAct()))
                .addParam("entities", null)
                .addParam("object", null)
                .addParam("bnlabel", null)
                .addParam("condition", null)
                .addParam("datatype", null)
                .addParam("suggestions", subclasses));
        return result;
    }

}
