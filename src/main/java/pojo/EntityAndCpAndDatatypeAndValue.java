package pojo;


public class EntityAndCpAndDatatypeAndValue {

    private String entity;
    private String complex;
    private DatatypeAndValue datatypeAndValue;

    public EntityAndCpAndDatatypeAndValue(String entity,String complex, DatatypeAndValue datatypeAndValue) {
        this.entity = entity;
        this.complex = complex;
        this.datatypeAndValue = datatypeAndValue;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getComplex() {
        return complex;
    }

    public void setComplex(String complex) {
        this.complex = complex;
    }

    public DatatypeAndValue getDatatypeAndValue() {
        return datatypeAndValue;
    }

    public void setDatatypeAndValue(DatatypeAndValue datatypeAndValue) {
        this.datatypeAndValue = datatypeAndValue;
    }
}

