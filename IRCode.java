public class IRCode {
    private IROperator operator;

    private String opIdent1;//操作数1
    private String opIdent2;//操作数2
    private String resultIdent;//结果

    private int opNum1;//操作数1
    private int opNum2;//操作数2
    private int resultNum;//结果

    private TableItem defItem = null;//符号表表项

    private boolean isAddress = false;//是否是地址

    private String printString = null;

    private boolean op1IsNum = false;
    private boolean op2IsNum = false;

    private String printStrName;

    private boolean needStay = false;

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent, boolean isAddress) {//是否是地址
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.isAddress = isAddress;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent, TableItem defItem) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.defItem = defItem;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, int opNum2, String resultIdent) { //给数组赋值
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        op2IsNum = true;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, int opNum1, int opNum2, String resultIdent) { //给数组赋值
        this.operator = operator;
        this.opNum1 = opNum1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        op1IsNum = true;
        op2IsNum = true;
        printString = operator.toString() + ", " + opNum1 + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, int opNum1, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opNum1 = opNum1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        op1IsNum = true;
        printString = operator.toString() + ", " + opNum1 + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, int opNum2, String resultIdent, boolean isAddress) {//是否是地址或者是否要保留操作符1
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        this.isAddress = isAddress;
        this.needStay = isAddress;
        op2IsNum = true;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, String opIdent2, String resultIdent, String printStrName) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.printStrName = printStrName;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                (opIdent2 == null ? "" : opIdent2) + ", " + (resultIdent == null ? "" : resultIdent);
    }

    public IRCode(IROperator operator, String opIdent1, int opNum2, String resultIdent, TableItem defItem) {//是否是地址或者是否要保留操作符1
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum2 = opNum2;
        this.resultIdent = resultIdent;
        this.defItem = defItem;
        op2IsNum = true;
        printString = operator.toString() + ", " + (opIdent1 == null ? "" : opIdent1) + ", " +
                opNum2 + ", " + (resultIdent == null ? "" : resultIdent);
    }


    public void setDefItem(TableItem defItem) {
        this.defItem = defItem;
    }

    public TableItem getDefItem() {
        return defItem;
    }

    public boolean isAddress() {
        return isAddress;
    }

    public boolean op1IsNum() {
        return op1IsNum;
    }

    public int getOpNum1() {
        return opNum1;
    }

    public String getOpIdent1() {
        return opIdent1;
    }

    public boolean op2IsNum() {
        return op2IsNum;
    }

    public int getOpNum2() {
        return opNum2;
    }

    public String getOpIdent2() {
        return opIdent2;
    }

    public IROperator getOperator() {
        return operator;
    }

    public String getResultIdent() {
        return resultIdent;
    }

    //note
    public boolean isNote() {
        return operator == IROperator.note;
    }

    //data define
    public boolean isDataDef() {
        return operator == IROperator.DEF;
    }

    public boolean isArrayDef() {
        return opIdent2 != null && opIdent2.equals("array");
    }

    public String getDataType() {
        return opIdent1;
    }

    public String getDataName() {
        return resultIdent;
    }

    public TableItem getDataItem() {
        //System.out.println(resultIdent + defItem);
        if (defItem == null) {
            System.out.println(resultIdent + ":defItem is null");
        }
        return defItem;
    }

    //是否保留操作符1
    public boolean isNeedStay() {
        return needStay;
    }

    //assign
    public boolean isAssign() {
        return operator == IROperator.ASSIGN;
    }

    public String getAssignDes() {
        return resultIdent;
    }

    public int getAssignNum() {
        return opNum1;
    }

    public int getAssignOffsetNum() {
        return opNum2;
    }

    public String getAssignOffsetReg() {
        return opIdent2;
    }

    //func define
    public boolean isFuncDef() {
        return operator == IROperator.func_begin;
    }

    public TableItem getFuncItem() {
        return defItem;
    }

    //calculate
    public boolean isCalculate() {
        return operator == IROperator.ADD || operator == IROperator.SUB || operator == IROperator.MUL ||
                operator == IROperator.DIV || operator == IROperator.MOD || operator == IROperator.AND ||
                operator == IROperator.OR || operator == IROperator.NOT || operator == IROperator.SLT ||
                operator == IROperator.SLE || operator == IROperator.SEQ || operator == IROperator.SNE ||
                operator == IROperator.SGE || operator == IROperator.SGT;
    }

    //getint
    public boolean isGetint() {
        return operator == IROperator.GETINT;
    }

    //print_int
    public boolean isPrintInt() {
        return operator == IROperator.PRINT_INT;
    }

    //print_str
    public boolean isPrintStr() {
        return operator == IROperator.PRINT_STR;
    }

    public String getPrintStrName() {
        return printStrName;
    }

    public String getPrintStrContent() {
        return opIdent1;
    }

    //arrayGet
    public boolean isArrayGet() {
        return operator == IROperator.ARRAY_GET;
    }

    //branch
    public boolean isBranch() {
        return operator == IROperator.BNE || operator == IROperator.BEQ || operator == IROperator.BGE ||
                operator == IROperator.BGT || operator == IROperator.BLE || operator == IROperator.BLT;
    }

    //jump
    public boolean isJmp() {
        return operator == IROperator.JMP;
    }

    //label
    public boolean isLabel() {
        return operator == IROperator.LABEL;
    }

    //func call
    public boolean isFuncCall() {
        return operator == IROperator.CALL;
    }

    public String getCallFuncName() {
        return opIdent1;
    }

    //func return
    public boolean isFuncReturn() {
        return operator == IROperator.RET;
    }

    public boolean hasReturnValue() {
        return opIdent1 != null;
    }

    //param push
    public boolean isParamPush() {
        return operator == IROperator.PUSH;
    }

    //retValue pop
    public boolean isRetValuePop() {
        return operator == IROperator.POP;
    }

    public String getPopDes() {
        return resultIdent;
    }

    //main def
    public boolean isMainFuncDef() {
        return operator == IROperator.main_begin;
    }

    public String print() {
        switch (operator) {
            case LABEL:
                return resultIdent + ":";
            case note:
                return "//" + resultIdent;
            case func_begin, main_begin:
                return "\n" + printString;
            default:
                return printString;
        }
    }
}
