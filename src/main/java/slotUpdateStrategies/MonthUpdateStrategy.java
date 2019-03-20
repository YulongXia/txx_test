package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;
import ai.hual.labrador.utils.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public class MonthUpdateStrategy implements SlotUpdateStrategy {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {

    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        if (queryAct.getSlots().containsKey("日期")) {
            try {
                DateUtils.Date dateValue = mapper.readValue(
                        mapper.writeValueAsString(queryAct.getSlots().get("日期").get(0).getMatched()),
                        DateUtils.Date.class);
                if (dateValue.month != 0) {
                    return dateValue.month;
                }
            } catch (IOException e) {
                return o;
            }
        }
        return o;
    }
}
