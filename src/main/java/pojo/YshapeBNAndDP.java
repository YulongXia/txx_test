package pojo;

public class YshapeBNAndDP {

    private String entity1;
    private String entity2;
    private String yshapeURI;
    private BlankNode bn;
    private String datatype;

    public YshapeBNAndDP(String entity1, String entity2, String yshapeURI, BlankNode bn, String datatype) {
        this.entity1 = entity1;
        this.entity2 = entity2;
        this.yshapeURI = yshapeURI;
        this.bn = bn;
        this.datatype = datatype;
    }

    public String getEntity1() {
        return entity1;
    }

    public void setEntity1(String entity1) {
        this.entity1 = entity1;
    }

    public String getEntity2() {
        return entity2;
    }

    public void setEntity2(String entity2) {
        this.entity2 = entity2;
    }

    public String getYshapeURI() {
        return yshapeURI;
    }

    public void setYshapeURI(String yshapeURI) {
        this.yshapeURI = yshapeURI;
    }

    public BlankNode getBN() {
        return bn;
    }

    public void setBN(BlankNode bn) {
        this.bn = bn;
    }

    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        this.datatype = datatype;
    }
}
