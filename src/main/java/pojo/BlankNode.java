package pojo;

public class BlankNode {

    private String iri;
    private String label;

    public BlankNode(String iri, String label) {
        this.iri = iri;
        this.label = label;
    }

    public String getIri() {
        return iri;
    }

    public String getLabel() {
        return label;
    }

}
