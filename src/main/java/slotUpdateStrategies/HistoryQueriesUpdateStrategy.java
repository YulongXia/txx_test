package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HistoryQueriesUpdateStrategy implements SlotUpdateStrategy {
    private final int MAX_SIZE = 10;
    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {

    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        String query = (String) context.getSlots().get("sys.query");
        if(o == null){
            List<String> result = new ArrayList<>();
            result.add(query);
            return result;
        }
        List<String> result = (List<String>)o;
        if(!result.contains(query))
        {
            if(result.size() >= MAX_SIZE)
                result.remove(0);
            result.add(query);
        }
        return result;
    }
}
