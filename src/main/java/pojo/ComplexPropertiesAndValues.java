package pojo;

import java.util.List;

public class ComplexPropertiesAndValues {
    private List<BNAndPropertyAndValue> BNAndOpsAndValues;
    private BNAndPropertyAndValue BNAndDpAndValue;

    public ComplexPropertiesAndValues(List<BNAndPropertyAndValue> BNAndOpsAndValues, BNAndPropertyAndValue BNAndDpAndValue) {
        this.BNAndOpsAndValues = BNAndOpsAndValues;
        this.BNAndDpAndValue = BNAndDpAndValue;
    }


    public List<BNAndPropertyAndValue> getBNAndOpsAndValues() {
        return BNAndOpsAndValues;
    }

    public void setBNAndOpsAndValues(List<BNAndPropertyAndValue> BNAndOpsAndValues) {
        this.BNAndOpsAndValues = BNAndOpsAndValues;
    }


    public BNAndPropertyAndValue getBNAndDpAndValue() {
        return BNAndDpAndValue;
    }

    public void setBNAndDpAndValue(BNAndPropertyAndValue BNAndDpAndValue) {
        this.BNAndDpAndValue = BNAndDpAndValue;
    }
}



