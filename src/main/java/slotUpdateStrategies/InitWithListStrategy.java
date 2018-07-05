package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public class InitWithListStrategy implements SlotUpdateStrategy {

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {

    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        return Optional.ofNullable(o).orElseGet(ArrayList::new);
    }

}
