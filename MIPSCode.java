import java.util.ArrayList;

public class MIPSCode {
    private MIPSOperator operator;
    private String opIdent1;
    private String opIdent2;
    private int opNum;
    private String resultIdent;
    private ArrayList<Integer> initList = new ArrayList<>();
    private TableItem defItem;

    private boolean op2IsNum = false;
    private boolean op1IsNum = false;

    private ArrayList<String> defineLabelList = new ArrayList<>();
    private boolean isDead = false;

    public MIPSCode(MIPSOperator operator, String opIdent1, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
    }

    public MIPSCode(MIPSOperator operator, String opIdent1, int opNum, String resultIdent) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opNum = opNum;
        op2IsNum = true;
        this.resultIdent = resultIdent;
    }

    public MIPSCode(MIPSOperator operator, String opIdent1, String opIdent2, String resultIdent, TableItem defItem) {
        this.operator = operator;
        this.opIdent1 = opIdent1;
        this.opIdent2 = opIdent2;
        this.resultIdent = resultIdent;
        this.defItem = defItem;
    }

    public MIPSCode(MIPSOperator operator, int opNum, String opIdent2, String resultIdent) {
        this.operator = operator;
        this.opIdent2 = opIdent2;
        this.opNum = opNum;
        op1IsNum = true;
        this.resultIdent = resultIdent;
    }

    public void addInit(int init) {
        initList.add(init);
    }

    public boolean isMemoryOperation() {
        return operator == MIPSOperator.lw || operator == MIPSOperator.sw;
    }

    public boolean isLoadImmediate() {
        return operator == MIPSOperator.li || operator == MIPSOperator.la;
    }

    public boolean isCalculate() {
        //add, addu, addi, addiu, sub, subu, subi, subiu, mult, multu, div, divu,
        //    slt, sltu, slti, sltiu, sgt, sle, sge, seq, sne,
        //    sll, srl, sra,
        //    and, or, xor, nor, andi, ori, xori
        return operator == MIPSOperator.add || operator == MIPSOperator.addu || operator == MIPSOperator.addi ||
                operator == MIPSOperator.addiu || operator == MIPSOperator.sub || operator == MIPSOperator.subu ||
                operator == MIPSOperator.subi || operator == MIPSOperator.subiu || operator == MIPSOperator.mult ||
                operator == MIPSOperator.multu || operator == MIPSOperator.div || operator == MIPSOperator.divu ||
                operator == MIPSOperator.slt || operator == MIPSOperator.sltu || operator == MIPSOperator.slti ||
                operator == MIPSOperator.sltiu || operator == MIPSOperator.sgt || operator == MIPSOperator.sle ||
                operator == MIPSOperator.sge || operator == MIPSOperator.seq || operator == MIPSOperator.sne ||
                operator == MIPSOperator.sll || operator == MIPSOperator.srl || operator == MIPSOperator.sra ||
                operator == MIPSOperator.and || operator == MIPSOperator.or || operator == MIPSOperator.xor ||
                operator == MIPSOperator.nor || operator == MIPSOperator.andi || operator == MIPSOperator.ori ||
                operator == MIPSOperator.xori;
    }

    public boolean isBranch() {
        //bne, beq, bgt, ble, bge, blt,
        return operator == MIPSOperator.bne || operator == MIPSOperator.beq || operator == MIPSOperator.bgt ||
                operator == MIPSOperator.ble || operator == MIPSOperator.bge || operator == MIPSOperator.blt ||
                operator == MIPSOperator.bgtz || operator == MIPSOperator.blez || operator == MIPSOperator.bgez ||
                operator == MIPSOperator.bltz || operator == MIPSOperator.beqz || operator == MIPSOperator.bnez;
    }

    public boolean isBranchZero() {
        return operator == MIPSOperator.bgtz || operator == MIPSOperator.blez || operator == MIPSOperator.bgez ||
                operator == MIPSOperator.bltz || operator == MIPSOperator.beqz || operator == MIPSOperator.bnez;
    }

    public boolean isJmp() {
        return operator == MIPSOperator.j || operator == MIPSOperator.jal || operator == MIPSOperator.jr;
    }

    public String print() {
        String printString = "";
        if (isMemoryOperation()) {
            printString += "\t";
            if (operator == MIPSOperator.lw) {
                printString += "lw ";
                printString = memoryOperationPrint(printString, resultIdent, opIdent1);
            } else {
                printString += "sw ";
                printString = memoryOperationPrint(printString, opIdent1, resultIdent);
            }
        } else if (isLoadImmediate()) {
            printString += "\t";
            if (!isRegister(resultIdent)) {
                throw new MIPSCodeError("load immediate error!");
            }
            if (operator == MIPSOperator.li) {
                printString += "li ";
                printString += resultIdent + ", " + opNum;
            } else {
                printString += "la ";
                printString += resultIdent + ", " + opIdent1;
                if (op2IsNum) {
                    printString += " + " + opNum;
                } else if (isRegister(opIdent2)) {
                    printString += "(" + opIdent2 + ")";
                } else if (opIdent2 != null) {
                    throw new MIPSCodeError("la op ident2 type error!");
                }
            }
        } else if (operator == MIPSOperator.move) {
            if (!isRegister(resultIdent) || !isRegister(opIdent1)) {
                throw new MIPSCodeError("move error!");
            }
            printString += "\tmove ";
            printString += resultIdent + ", " + opIdent1;
        } else if (operator == MIPSOperator.mflo) {
            if (!isRegister(resultIdent)) {
                throw new MIPSCodeError("mflo error!");
            }
            printString += "\tmflo ";
            printString += resultIdent;
        } else if (operator == MIPSOperator.mfhi) {
            if (!isRegister(resultIdent)) {
                throw new MIPSCodeError("mfhi error!");
            }
            printString += "\tmfhi ";
            printString += resultIdent;
        } else if (isCalculate()) {
            printString += "\t";
            printString += operator.name();
            printString += " ";
            if (isRegister(resultIdent)) {
                printString += resultIdent + ", ";
            } else if (operator != MIPSOperator.mult && operator != MIPSOperator.multu &&
                    operator != MIPSOperator.div && operator != MIPSOperator.divu) {
                throw new MIPSCodeError("calculate error!");
            }
            /*if (op1IsNum) {
                printString += opNum + ", ";
            } else if (opIdent1 != null) {
                printString += opIdent1 + ", ";
            }*/
            if (isRegister(opIdent1)) {
                printString += opIdent1 + ", ";
            } else {
                throw new MIPSCodeError("calculate error!");
            }
            if (op2IsNum) {
                printString += opNum;
            } else if (isRegister(opIdent2)) {
                printString += opIdent2;
            } else {
                throw new MIPSCodeError("calculate error!");
            }
        } else if (operator == MIPSOperator.lui) {
            printString += "\tlui ";
            printString += resultIdent + ", " + opNum;
        } else if (isBranch()) {
            printString += "\t";
            printString += operator.name();
            printString += " ";
            if (isRegister(opIdent1)) {
                printString += opIdent1 + ", ";
            } else {
                throw new MIPSCodeError("branch error!");
            }
            if (op2IsNum) {
                printString += opNum + ", ";
            } else if (isRegister(opIdent2)) {
                printString += opIdent2 + ", ";
            } else if (!isBranchZero()) {
                throw new MIPSCodeError("branch error!");
            }
            if (resultIdent != null) {
                printString += resultIdent;
            } else {
                throw new MIPSCodeError("branch error!");
            }
        } else if (isJmp()) {
            printString += "\t";
            printString += operator.name();
            printString += " ";
            if (resultIdent != null) {
                printString += resultIdent;
            } else {
                throw new MIPSCodeError("jmp error!");
            }
        } else if (operator == MIPSOperator.text) {
            printString += resultIdent;
        } else if (operator == MIPSOperator.label) {
            if (isRedefinedLable(resultIdent)) {
                throw new MIPSCodeError("redefined label error!");
            }
            defineLabelList.add(resultIdent);
            printString += resultIdent + ":";
        } else if (operator == MIPSOperator.syscall) {
            printString += "\tsyscall";
        } else if (operator == MIPSOperator.dataDefine) {
            if (isRedefinedLable(resultIdent)) {
                throw new MIPSCodeError("redefined label error!");
            }
            defineLabelList.add(resultIdent);
            printString += "\t" + resultIdent + ": .word ";
            if (initList.isEmpty()) {
                if (defItem.isArray()) {
                    printString += "0:";
                    printString += defItem.getArraySize();
                } else {
                    printString += "0";
                }
            } else {
                int i = 0;
                for (i = 0; i < initList.size(); i++) {
                    printString += initList.get(i);
                    if (i != initList.size() - 1) {
                        printString += ", ";
                    }
                }
                for (; i < defItem.getArraySize(); i++) {
                    printString += ", 0";
                }
            }
        } else if (operator == MIPSOperator.strDefine) {
            printString += "\t" + resultIdent + ": .asciiz " + "\"" + opIdent1 + "\"";
        } else if (operator == MIPSOperator.note) {
            printString += "\t# " + resultIdent;
        }
        printString += "\n";
        return printString;
    }

    private String memoryOperationPrint(String printString, String resultIdent, String opIdent1) {
        if (!isRegister(resultIdent)) {
            throw new MIPSCodeError("memory operation error!");
        }
        printString += resultIdent + ", ";
        if (isRegister(opIdent1)) {//是寄存器
            if (op2IsNum) {
                printString += opNum + "(" + opIdent1 + ")";
            } else if (opIdent2 != null) {
                printString += opIdent2 + "(" + opIdent1 + ")";
            } else {
                printString += "(" + opIdent1 + ")";
            }
        } else if (opIdent1 != null) {//是标识符
            if (op2IsNum && opNum != 0) {
                printString += opIdent1 + " + " + opNum;
            } else {
                printString += opIdent1;
            }
        } else {
            throw new MIPSCodeError("memory operation error!");
        }
        return printString;
    }

    public boolean isRegister(String name) {
        return name != null && name.startsWith("$");
    }

    public boolean isRedefinedLable(String name) {
        return defineLabelList.contains(name);
    }

    public MIPSOperator getOperator() {
        return operator;
    }

    public void setDead() {
        isDead = true;
    }

    public boolean isDead() {
        return isDead;
    }

    public String getOpIdent1() {
        return opIdent1;
    }

    public String getOpIdent2() {
        return opIdent2;
    }

    public String getResultIdent() {
        return resultIdent;
    }

    public void setOpIdent1(String opIdent1) {
        this.opIdent1 = opIdent1;
        op1IsNum = false;
    }

    public void setOpIdent2(String opIdent2) {
        this.opIdent2 = opIdent2;
        op2IsNum = false;
    }

    public boolean op1IsNum() {
        return op1IsNum;
    }

    public boolean op2IsNum() {
        return op2IsNum;
    }

    public int getOpNum2() {
        return opNum;
    }

    public int getOpNum1() {
        return opNum;
    }
}
