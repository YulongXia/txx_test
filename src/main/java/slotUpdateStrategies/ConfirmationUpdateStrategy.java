package slotUpdateStrategies;

import ai.hual.labrador.dialog.AccessorRepository;
import ai.hual.labrador.dm.Context;
import ai.hual.labrador.dm.ContextedString;
import ai.hual.labrador.dm.SlotUpdateStrategy;
import ai.hual.labrador.nlu.QueryAct;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfirmationUpdateStrategy implements SlotUpdateStrategy {

    private List<String> guessSlots;

    @Override
    public void setUp(String s, Map<String, ContextedString> map, AccessorRepository accessorRepository) {
        guessSlots = Arrays.asList(
                "entity",
                "HualDataTypeProperty"
        );
    }

    @Override
    public Object update(QueryAct queryAct, Object o, Context context) {
        if (guessSlots.stream().map(x -> "guess." + x).map(x -> context.getSlots().get(x)).anyMatch(Objects::nonNull)) {
            if ("sys.no".equals(queryAct.getIntent())) {
                return true;
            }
            if ("sys.yes".equals(queryAct.getIntent())) {
                guessSlots.forEach(x -> copyGuessToSlots(context, x));
                return false;
            }
        }
        clearGuess(context);
        return false;
    }

    private void copyGuessToSlots(Context context, String key) {
        String guessKey = "guess." + key;
        context.getSlots().put(key, null);

        Object guessValue = context.getSlots().get(guessKey);
        if (guessValue != null) {
            context.getSlots().put(key, guessValue);
        }
    }


    private void clearGuess(Context context) {
        guessSlots.stream().map(slot -> "guess." + slot).forEach(context.getSlots()::remove);
    }


}
