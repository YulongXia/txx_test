package pojo;

public class EntityAndBNAndDatatypeAndValue {

    private String entity;
    private BlankNode bn;
    private DatatypeAndValue datatypeAndValue;

    public EntityAndBNAndDatatypeAndValue(String entity,BlankNode bn, DatatypeAndValue datatypeAndValue) {
        this.entity = entity;
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

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }
}
