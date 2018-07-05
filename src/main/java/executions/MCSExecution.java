package executions;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.*;
import ai.hual.labrador.nlg.ResponseAct;

import java.util.Collections;
import java.util.Map;

public class MCSExecution implements Execution {

    AccessorRepository accessorRepository;

    @Override
    public void setUp(Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        this.accessorRepository = accessorRepository;
    }

    @Override
    public ExecutionResult execute(Context context) {
        ResponseExecutionResult result = new ResponseExecutionResult();
        result.setResponseAct(new ResponseAct("answer")
                .put("result","<p><span style=\"font-family: 微软雅黑, &quot;Microsoft YaHei&quot;;\">正在为您安排人工服务~如未跳转，请点击【<a href=\"http://custom-redirect?action=toArt\">人工客服</a>】</span></p>" )
        );
        result.setInstructions(Collections.singletonList(new Instruction("dm_action")
                .addParam("answer",accessorRepository.getNLG().generate(result.getResponseAct()))
                .addParam("action","转人工")));
        return result;
    }
}
