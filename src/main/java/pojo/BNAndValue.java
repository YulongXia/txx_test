package pojo;

public class BNAndValue {

    private BlankNode bn;
    private String value;

    public BNAndValue(BlankNode bn, String value) {
        this.bn = bn;
        this.value = value;
    }

    public BlankNode getBN() {
        return bn;
    }

    public void setBN(BlankNode bn) {
        this.bn = bn;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
