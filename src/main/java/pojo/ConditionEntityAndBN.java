package pojo;

public class ConditionEntityAndBN {

    private ConditionEntity conditionEntity;
    private BlankNode bn;


    public ConditionEntityAndBN(ConditionEntity conditionEntity, BlankNode bn) {
        this.conditionEntity = conditionEntity;
        this.bn = bn;
    }

    public ConditionEntity getConditionEntity() {
        return conditionEntity;
    }

    public void setConditionEntity(ConditionEntity conditionEntity) {
        this.conditionEntity = conditionEntity;
    }

    public BlankNode getBN() {
        return bn;
    }

    public void setBN(BlankNode bn) {
        this.bn = bn;
    }

}
