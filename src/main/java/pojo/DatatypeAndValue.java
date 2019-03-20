package pojo;

public class DatatypeAndValue {

    private String datatype;
    private String value;

    public DatatypeAndValue(String datatype, String value) {
        this.datatype = datatype;
        this.value = value;
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
