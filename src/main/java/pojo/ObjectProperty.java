package pojo;

public class ObjectProperty {

    public static final String CONDITION_PROPERTY = "http://hual.ai/standard#ConditionProperty";
    public static final String YSHAPE_PROPERTY = "http://hual.ai/standard#YshapeProperty";
    public static final String DIFFUSION_PROPERTY = "http://hual.ai/standard#DiffusionProperty";

    private String uri;
    private String label;
    private String type;
    private BlankNode bn;

    public ObjectProperty(String uri, String label, String type, BlankNode bn) {
        this.uri = uri;
        this.label = label;
        this.type = type;
        this.bn = bn;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BlankNode getBN() {
        return bn;
    }

    public void setBN(BlankNode bn) {
        this.bn = bn;
    }
}
