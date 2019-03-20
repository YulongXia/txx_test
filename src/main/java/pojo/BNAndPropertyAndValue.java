package pojo;

public class BNAndPropertyAndValue {

    private String bn;
    private String property;
    private String value;

    public BNAndPropertyAndValue(String bn,String property, String value) {
        this.bn = bn;
        this.property = property;
        this.value = value;
    }



    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getBn() {
        return bn;
    }

    public void setBn(String bn) {
        this.bn = bn;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }
}
