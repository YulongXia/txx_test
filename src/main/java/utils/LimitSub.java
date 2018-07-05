package utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class LimitSub {

    public static <T> List<T> get5(Set<T> hs) {
        List<T> ls = new LinkedList<>();
        Iterator<T> it = hs.iterator();
        int index = 0;
        while (it.hasNext()) {
            ls.add(index, it.next());
            index++;
            if (index == 5) {
                break;
            }

        }
        return ls;
    }
}
