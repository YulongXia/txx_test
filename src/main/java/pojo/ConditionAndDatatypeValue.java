package pojo;

public class ConditionAndDatatypeValue {

    private String object;
    private String bnLabel;
    private String datatype;
    private String value;

    public ConditionAndDatatypeValue(String object, String bnLabel, String datatype, String value) {
        this.object = object;
        this.bnLabel = bnLabel;
        this.datatype = datatype;
        this.value = value;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getBnLabel() {
        return bnLabel;
    }

    public void setBnLabel(String bnLabel) {
        this.bnLabel = bnLabel;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
