package pojo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConditionClassesAndValues {
    private Map<String,List<String>> info;

    public ConditionClassesAndValues(){
        info = new HashMap<>();
    }
    public Map<String, List<String>> getInfo() {
        return info;
    }

    public void setInfo(Map<String, List<String>> info) {
        this.info = info;
    }

    public void add(String clazz,List<String> values){
        info.put(clazz,values);
    }

    public List<String> remove(String clazz){
        return info.remove(clazz);
    }
}
