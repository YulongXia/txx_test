package pojo;

public class BNAndDatatypeAndValue {

    private BlankNode bn;
    private DatatypeAndValue datatypeAndValue;

    public BNAndDatatypeAndValue(BlankNode bn, DatatypeAndValue datatypeAndValue) {
        this.bn = bn;
        this.datatypeAndValue = datatypeAndValue;
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
}
