package pojo;

public class ConditionEntity {

    private String label;
    private String classLabel;

    public ConditionEntity(String label, String classLabel) {
        this.label = label;
        this.classLabel = classLabel;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public void setClassLabel(String classLabel) {
        this.classLabel = classLabel;
    }
}
