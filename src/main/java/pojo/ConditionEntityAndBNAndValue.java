package pojo;

public class ConditionEntityAndBNAndValue {

    private ConditionEntity conditionEntity;
    private BNAndValue bnAndValue;


    public ConditionEntityAndBNAndValue(ConditionEntity conditionEntity, BNAndValue bnAndValue) {
        this.conditionEntity = conditionEntity;
        this.bnAndValue = bnAndValue;
    }

    public ConditionEntity getConditionEntity() {
        return conditionEntity;
    }

    public void setConditionEntity(ConditionEntity conditionEntity) {
        this.conditionEntity = conditionEntity;
    }

    public BNAndValue getBNAndValue() {
        return bnAndValue;
    }

    public void setBnAndValue(BNAndValue bnAndValue) {
        this.bnAndValue = bnAndValue;
    }

}
