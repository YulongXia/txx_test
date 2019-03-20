package pojo;

import java.util.List;
import java.util.Map;

public class BNAndDatatypeAndValueAndConditions {

    private BlankNode bn;
    private DatatypeAndValue datatypeAndValue;
    // map : {condition_label:condition_class_label,condition_label1:condition_class_label1}
    private Map<String,String> conditions;

    public BNAndDatatypeAndValueAndConditions(BlankNode bn, DatatypeAndValue datatypeAndValue,Map<String,String> conditions) {
        this.bn = bn;
        this.datatypeAndValue = datatypeAndValue;
        this.conditions = conditions;
    }

    public BlankNode getBn() {
        return bn;
    }

    public void setBn(BlankNode bn) {
        this.bn = bn;
    }

    public DatatypeAndValue getDatatypeAndValue() {
        return datatypeAndValue;
    }

    public void setDatatypeAndValue(DatatypeAndValue datatypeAndValue) {
        this.datatypeAndValue = datatypeAndValue;
    }

    public Map<String, String> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, String> conditions) {
        this.conditions = conditions;
    }
}
