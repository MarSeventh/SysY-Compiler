import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MIPSGenerator {
    private boolean print = true;
    private BufferedWriter out;
    private ArrayList<Register> registers = new ArrayList<>();
    private final ArrayList<MIPSCode> mipsCode = new ArrayList<>();
    private ArrayList<IRCode> irCodes = new ArrayList<>();
    private HashMap<Integer, MIPSTable> tableStack = new HashMap<>();
    private int level = 0;
    private MIPSTable curTable;
    private int offset = 0;
    private int irCodeIndex = 0;
    private IRCode curIRCode;

    private boolean isMainFunc = false;
    private MIPSTableItem nowFunc = null;

    private int busyRegNum = 0;
    ArrayList<String> busyRegs = new ArrayList<>();

    public MIPSGenerator(ArrayList<IRCode> irCodes) {
        for (int i = 0; i < 10; i++) {
            registers.add(new Register("$t" + i));
        }
        try {
            out = new BufferedWriter(new FileWriter("mips.txt"));
        } catch (Exception ignored) {
        }
        this.irCodes = irCodes;
        tableStack.put(level, new MIPSTable());
        curTable = tableStack.get(level);
    }

    public void genMips() {
        addMIPSCode(new MIPSCode(MIPSOperator.text, null, null, ".data"));
        getIRCode();
        genGlobalDef();
        addMIPSCode(new MIPSCode(MIPSOperator.text, null, null, ".text"));
        addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, "main"));
        while (curIRCode != null && curIRCode.isFuncDef()) {
            genFuncDef();
        }
        if (curIRCode != null && curIRCode.isMainFuncDef()) {
            genMainFuncDef();
        }
        print();
    }

    public void genGlobalDef() {
        while (curIRCode != null && curIRCode.isDataDef()) {
            curTable.addItem(new MIPSTableItem(curIRCode.getDataItem(), offset));
            MIPSCode code = new MIPSCode(MIPSOperator.dataDefine, null, null,
                    "Global_" + curIRCode.getDataName(), curIRCode.getDataItem());
            addMIPSCode(code);
            getIRCode();
            while (curIRCode != null && curIRCode.isAssign()) {
                code.addInit(curIRCode.getAssignNum());//给全局变量赋初值
                getIRCode();
            }
        }
        //初始化printf中的字符串
        int tempIrIndex = irCodeIndex;
        for (; tempIrIndex < irCodes.size(); tempIrIndex++) {
            if (irCodes.get(tempIrIndex).isPrintStr()) {
                MIPSCode code = new MIPSCode(MIPSOperator.strDefine, irCodes.get(tempIrIndex).getPrintStrContent(),
                        null, irCodes.get(tempIrIndex).getPrintStrName());
                addMIPSCode(code);
            }
        }
    }

    public void genFuncDef() {
        offset = 0;
        addMIPSCode(new MIPSCode(MIPSOperator.label, null, null, curIRCode.getFuncItem().getName()));
        addMIPSCode(new MIPSCode(MIPSOperator.move, "$sp", null, "$fp"));
        MIPSTableItem funcMIPSItem = new MIPSTableItem(curIRCode.getFuncItem(), offset);
        nowFunc = funcMIPSItem;
        curTable.addItem(funcMIPSItem);
        addLevel();
        getIRCode();
        int paraCount = 0;
        //int nowBusyRegNum = busyRegNum;
        while (curIRCode != null && curIRCode.isDataDef() && curIRCode.getDataType().equals("param")) {
            MIPSTableItem paramMIPSItem = new MIPSTableItem(curIRCode.getDataItem(), offset++);
            curTable.addItem(paramMIPSItem);
            if (paraCount < 4) {
                MIPSCode code = new MIPSCode(MIPSOperator.sw, "$a" + paraCount++, -paramMIPSItem.getOffset() - 4, "$fp");
                addMIPSCode(code);
            } else {
                String reg = getReg(curIRCode.getDataName());
                //int off = 4 * (funcMIPSItem.getTableItem().getParasNum() - paraCount - 1) + 8 + 4 * nowBusyRegNum;//fp+8向上读取栈中参数，入栈是正向，出栈是反向
                int off = 4 * (funcMIPSItem.getTableItem().getParasNum() - paraCount - 1) + 8;
                addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", off, reg));
                addMIPSCode(new MIPSCode(MIPSOperator.sw, reg, -paramMIPSItem.getOffset() - 4, "$fp"));//存参数到当前活动记录
                freeReg(reg);
                paraCount++;
            }
            getIRCode();
        }
        if (paraCount > 0) {
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -paraCount * 4, "$sp"));//参数入栈，栈顶指针下移
        }
        genNormal();
        if (!isMainFunc) {
            addMIPSCode(new MIPSCode(MIPSOperator.jr, null, null, "$ra"));
        }
        deleteLevel();
        getIRCode();//跳过函数结束
        nowFunc = null;
    }

    public void genMainFuncDef() {
        isMainFunc = true;
        offset = 0;
        addMIPSCode(new MIPSCode(MIPSOperator.label, null, null, "main"));
        addMIPSCode(new MIPSCode(MIPSOperator.move, "$sp", null, "$fp"));
        MIPSTableItem mainFuncMIPSItem = new MIPSTableItem(curIRCode.getFuncItem(), offset);
        nowFunc = mainFuncMIPSItem;
        curTable.addItem(mainFuncMIPSItem);
        addLevel();
        getIRCode();
        genNormal();
        deleteLevel();
        addMIPSCode(new MIPSCode(MIPSOperator.li, 10, null, "$v0"));
        addMIPSCode(new MIPSCode(MIPSOperator.syscall, null, null, null));
        getIRCode();//跳过main_end
        isMainFunc = false;
        nowFunc = null;
    }

    public void genNormal() {
        while (curIRCode != null && curIRCode.getOperator() != IROperator.func_end && curIRCode.getOperator() != IROperator.main_end) {
            if (curIRCode.isCalculate()) {
                genCalculate();
            } else if (curIRCode.isGetint()) {
                genGetint();
            } else if (curIRCode.isPrintStr()) {
                genPrintStr();
            } else if (curIRCode.isPrintInt()) {
                genPrintInt();
            } else if (curIRCode.getOperator() == IROperator.PRINT_PUSH) {
                genPrintPush();
            } else if (curIRCode.isArrayGet()) {
                genArrayGet();
            } else if (curIRCode.isBranch()) {
                genBranch();
            } else if (curIRCode.isJmp()) {
                genJmp();
            } else if (curIRCode.isLabel()) {
                genLabel();
            } else if (curIRCode.isDataDef()) {
                genDataDef();
            } else if (curIRCode.isAssign()) {
                genAssign();
            } else if (curIRCode.isFuncCall()) {
                genFuncCall();
            } else if (curIRCode.isFuncReturn()) {
                genFuncReturn();
            } else if (curIRCode.isParamPush()) {
                genParamPush();
            } else if (curIRCode.isRetValuePop()) {
                genRetValuePop();
            } else if (curIRCode.getOperator() == IROperator.block_begin) {
                addLevel();
            } else if (curIRCode.getOperator() == IROperator.block_end) {
                deleteLevel();
            } else if (curIRCode.getOperator() == IROperator.OUTBLOCK) {
                if (getLastOffset() != -1) {
                    //System.out.println(lastMIPSItem.getName());
                    offset = getLastOffset() + 1;
                } else {
                    offset = 0;
                }
                addMIPSCode(new MIPSCode(MIPSOperator.addi, "$fp", -offset * 4, "$sp"));//恢复sp寄存器
            } else {
                Error.mipsError("wrong irCode");
            }
            getIRCode();
        }
    }

    public void genRetValuePop() {
        String desReg = getReg(curIRCode.getPopDes());
        addMIPSCode(new MIPSCode(MIPSOperator.move, "$v0", null, desReg));
    }

    public void genParamPush() {
        String opReg = findReg(curIRCode.getOpIdent1());
        int paraCount = curIRCode.getOpNum2();
        if (paraCount == 0) {
            //临时寄存器入栈
            for (String busyRegName : busyRegs) {
                if (busyRegName.equals(opReg)) {
                    continue;
                }
                addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -4, "$sp"));
                addMIPSCode(new MIPSCode(MIPSOperator.sw, busyRegName, null, "$sp"));
            }
        }
        if (paraCount < 4) {
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -4, "$sp"));
            addMIPSCode(new MIPSCode(MIPSOperator.sw, "$a" + paraCount, null, "$sp"));//维护参数寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.move, opReg, null, "$a" + paraCount));
        } else {
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -4, "$sp"));
            addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg, null, "$sp"));
        }
        freeReg(opReg);
    }

    public void genFuncCall() {
        String funcName = curIRCode.getCallFuncName();
        TableItem funcItem = curIRCode.getFuncItem();
        int parasNum = funcItem.getParasNum();
        if (parasNum == 0) {
            //参数数量为0，保存临时寄存器
            for (String busyRegName : busyRegs) {
                addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -4, "$sp"));
                addMIPSCode(new MIPSCode(MIPSOperator.sw, busyRegName, null, "$sp"));
            }
        }
        //fp 入栈
        addMIPSCode(new MIPSCode(MIPSOperator.sw, "$fp", -4, "$sp"));
        //ra 入栈
        addMIPSCode(new MIPSCode(MIPSOperator.sw, "$ra", -8, "$sp"));
        //sp 下移
        addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -8, "$sp"));
        //跳转到函数入口
        addMIPSCode(new MIPSCode(MIPSOperator.jal, null, null, funcName));

        //返回后
        //恢复$sp
        addMIPSCode(new MIPSCode(MIPSOperator.move, "$fp", null, "$sp"));
        //从栈中取出$ra
        addMIPSCode(new MIPSCode(MIPSOperator.lw, "$sp", 0, "$ra"));
        //从栈中取出$fp
        addMIPSCode(new MIPSCode(MIPSOperator.lw, "$sp", 4, "$fp"));
        //恢复参数寄存器
        for (int i = 0; i < Math.min(parasNum, 4); i++) {
            addMIPSCode(new MIPSCode(MIPSOperator.lw, "$sp", 4 * (parasNum + 2) - 4 * (i + 1), "$a" + i));
        }
        //恢复临时寄存器
        for (int i = 0; i < busyRegs.size(); i++) {
            addMIPSCode(new MIPSCode(MIPSOperator.lw, "$sp", 4 * (parasNum + 2) + 4 * i, busyRegs.get(busyRegNum - 1 - i)));
        }
        //参数及临时寄存器退栈
        addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", 4 * (parasNum + 2 + busyRegNum), "$sp"));
    }

    public void genFuncReturn() {
        //准备返回值v0
        if (curIRCode.hasReturnValue()) {
            if (curIRCode.op1IsNum()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, "$v0"));
            } else {
                String op1Reg = findReg(curIRCode.getOpIdent1());
                addMIPSCode(new MIPSCode(MIPSOperator.move, op1Reg, null, "$v0"));
                freeReg(op1Reg);
            }
        }
        //返回到ra
        if (!isMainFunc) {
            addMIPSCode(new MIPSCode(MIPSOperator.jr, null, null, "$ra"));
        } else {
            addMIPSCode(new MIPSCode(MIPSOperator.li, 10, null, "$v0"));
            addMIPSCode(new MIPSCode(MIPSOperator.syscall, null, null, null));
        }
    }

    public void genAssign() {
        MIPSTableItem desMIPSItem = getVarConstParamItem(curIRCode.getAssignDes());
        if (desMIPSItem == null) {
            //临时变量（t*）
            String desReg = getReg(curIRCode.getAssignDes());
            if (curIRCode.op1IsNum()) {
                //assign, 0, null, t1
                addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, desReg));
            } else {
                MIPSTableItem srcMIPSItem = getVarConstParamItem(curIRCode.getOpIdent1());
                if (srcMIPSItem == null) {
                    //assign, t0, null, t1
                    String op1Reg = findReg(curIRCode.getOpIdent1());
                    addMIPSCode(new MIPSCode(MIPSOperator.move, op1Reg, null, desReg));
                    freeReg(op1Reg);
                } else {
                    //assign, a, null, t1
                    if (srcMIPSItem.getTableItem().isGlobal()) {
                        addMIPSCode(new MIPSCode(MIPSOperator.lw, "Global_" + curIRCode.getOpIdent1(), null, desReg));
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -srcMIPSItem.getOffset() - 4, desReg));
                    }
                }
            }
        } else if (desMIPSItem.getTableItem().isGlobal()) {
            //全局变量
            if (desMIPSItem.getTableItem().isArray()) {
                //数组
                String opReg1;
                if (curIRCode.op1IsNum()) {
                    opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (!opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                    }
                } else {
                    opReg1 = findReg(curIRCode.getOpIdent1());
                }
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, 4 * curIRCode.getAssignOffsetNum(), "Global_" + curIRCode.getAssignDes()));
                } else {
                    String opReg2 = findReg(curIRCode.getOpIdent2());
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg2, 2, opReg2));
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, "Global_" + curIRCode.getAssignDes(), opReg2));
                    freeReg(opReg2);
                }
                freeReg(opReg1);
            } else {
                //非数组
                if (curIRCode.op1IsNum()) {
                    String opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, null, "Global_" + curIRCode.getAssignDes()));
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, null, "Global_" + curIRCode.getAssignDes()));
                        freeReg(opReg1);
                    }
                } else {
                    String op1Reg = findReg(curIRCode.getOpIdent1());
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, op1Reg, null, "Global_" + curIRCode.getAssignDes()));
                    freeReg(op1Reg);
                }
            }
        } else if (desMIPSItem.getTableItem().getKind().equals("param")) {
            //参数
            if (desMIPSItem.getTableItem().isArray()) {
                //取出数组地址
                String addressReg = getReg(curIRCode.getAssignDes());
                addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -desMIPSItem.getOffset() - 4, addressReg));
                String opReg1;
                if (curIRCode.op1IsNum()) {
                    opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (!opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                    }
                } else {
                    opReg1 = findReg(curIRCode.getOpIdent1());
                }
                if (curIRCode.op2IsNum()) {
                    //assign, 0, 1, a
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, 4 * curIRCode.getAssignOffsetNum(), addressReg));
                } else {
                    //assign, 0, t1, a
                    String opReg2 = findReg(curIRCode.getOpIdent2());
                    String indexReg = getReg("index");
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg2, 2, indexReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.add, addressReg, indexReg, addressReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, null, addressReg));
                    freeReg(opReg2);
                    freeReg(indexReg);
                }
                freeReg(opReg1);
                freeReg(addressReg);
            } else {
                //非数组
                String opReg1;
                if (curIRCode.op1IsNum()) {
                    opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                        freeReg(opReg1);
                    }
                } else {
                    opReg1 = findReg(curIRCode.getOpIdent1());
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                    freeReg(opReg1);
                }
            }
        } else {
            //局部变量
            if (desMIPSItem.getTableItem().isArray()) {
                //数组
                String opReg1;
                if (curIRCode.op1IsNum()) {
                    opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (!opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                    }
                } else {
                    opReg1 = findReg(curIRCode.getOpIdent1());
                }
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4 + 4 * curIRCode.getAssignOffsetNum(), "$fp"));
                } else {
                    String opReg2 = findReg(curIRCode.getOpIdent2());
                    String indexReg = getReg("index");
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, opReg2, 2, indexReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.addi, "$fp", -desMIPSItem.getOffset() - 4, opReg2));//数组基地址
                    addMIPSCode(new MIPSCode(MIPSOperator.add, opReg2, indexReg, opReg2));
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, null, opReg2));
                    freeReg(opReg2);
                    freeReg(indexReg);
                }
                freeReg(opReg1);
            } else {
                //非数组
                String opReg1;
                if (curIRCode.op1IsNum()) {
                    opReg1 = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
                    if (opReg1.equals("$0")) {
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, opReg1));
                        addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                        freeReg(opReg1);
                    }
                } else {
                    opReg1 = findReg(curIRCode.getOpIdent1());
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, opReg1, -desMIPSItem.getOffset() - 4, "$fp"));
                    freeReg(opReg1);
                }
            }
        }
    }

    public void genDataDef() {
        TableItem dataItem = curIRCode.getDataItem();
        offset += dataItem.getArraySize() - 1;
        MIPSTableItem dataMIPSItem = new MIPSTableItem(curIRCode.getDataItem(), offset++);
        curTable.addItem(dataMIPSItem);
        if (dataItem.isArray()) {
            //数组
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp",
                    -4 * dataItem.getArraySize(), "$sp"));//数组入栈，栈顶指针下移
        } else {
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp",
                    -4, "$sp"));//变量入栈，栈顶指针下移
        }
    }

    public void genLabel() {
        addMIPSCode(new MIPSCode(MIPSOperator.label, null, null, curIRCode.getResultIdent()));
    }

    public void genJmp() {
        addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
    }

    public void genBranch() {
        switch (curIRCode.getOperator()) {
            case BEQ:
                genBeq();
                break;
            case BNE:
                genBne();
                break;
            case BGE:
                genBge();
                break;
            case BGT:
                genBgt();
                break;
            case BLE:
                genBle();
                break;
            case BLT:
                genBlt();
                break;
            default:
                Error.mipsError("wrong branch irCode");
        }
    }

    public void genBeq() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() == curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.beq, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.beq, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                //System.out.println(curIRCode.print());
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.beq, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genBne() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() != curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bne, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.bne, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bne, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genBge() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() >= curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.ble, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.bge, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bge, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genBgt() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() > curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.blt, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));//为考虑常数，进行一个转换
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.bgt, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bgt, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genBle() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() <= curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bge, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.ble, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.ble, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genBlt() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            if (curIRCode.getOpNum1() < curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.j, null, null, curIRCode.getResultIdent()));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.bgt, op2Reg, curIRCode.getOpNum1(), curIRCode.getResultIdent()));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.blt, op1Reg, curIRCode.getOpNum2(), curIRCode.getResultIdent()));
            if (!curIRCode.isNeedStay()) {
                freeReg(op1Reg);
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.blt, op1Reg, op2Reg, curIRCode.getResultIdent()));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genArrayGet() {
        String arrayName = curIRCode.getOpIdent1();
        int index = curIRCode.getOpNum2();
        MIPSTableItem arrayItem = getVarConstParamItem(arrayName);
        String desReg = getReg(curIRCode.getResultIdent());
        if (arrayItem.getTableItem().isGlobal()) {
            //全局变量
            if (!curIRCode.isAddress()) {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, "Global_" + arrayName, 4 * index, desReg));
                } else {
                    String offReg = findReg(curIRCode.getOpIdent2());
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, offReg, "Global_" + arrayName, desReg));
                    freeReg(offReg);
                }
            } else {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.la, "Global_" + arrayName, 4 * index, desReg));
                } else {
                    if (curIRCode.getOpIdent2() != null) {
                        String offReg = findReg(curIRCode.getOpIdent2());
                        addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                        addMIPSCode(new MIPSCode(MIPSOperator.la, "Global_" + arrayName, offReg, desReg));
                        freeReg(offReg);
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.la, "Global_" + arrayName, null, desReg));
                    }
                }
            }
        } else if (arrayItem.getTableItem().getKind().equals("param")) {
            //数组参数
            String addressReg = getReg(arrayName);
            addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -arrayItem.getOffset() - 4, addressReg));
            if (!curIRCode.isAddress()) {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, addressReg, 4 * index, desReg));
                } else {
                    String offReg = findReg(curIRCode.getOpIdent2());
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                    addMIPSCode(new MIPSCode(MIPSOperator.add, addressReg, offReg, addressReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, addressReg, null, desReg));
                    freeReg(offReg);
                }
            } else {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.addi, addressReg, 4 * index, desReg));
                } else {
                    if (curIRCode.getOpIdent2() != null) {
                        String offReg = findReg(curIRCode.getOpIdent2());
                        addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                        addMIPSCode(new MIPSCode(MIPSOperator.add, addressReg, offReg, desReg));
                        freeReg(offReg);
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.move, addressReg, null, desReg));
                    }
                }
            }
            freeReg(addressReg);
        } else {
            //局部变量
            if (!curIRCode.isAddress()) {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -arrayItem.getOffset() - 4 + 4 * index, desReg));
                } else {
                    String offReg = findReg(curIRCode.getOpIdent2());
                    String addressReg = getReg(arrayName);
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                    addMIPSCode(new MIPSCode(MIPSOperator.add, "$fp", -arrayItem.getOffset() - 4, addressReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.add, addressReg, offReg, addressReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.lw, addressReg, null, desReg));
                    freeReg(addressReg);
                    freeReg(offReg);
                }
            } else {
                if (curIRCode.op2IsNum()) {
                    addMIPSCode(new MIPSCode(MIPSOperator.addi, "$fp", -arrayItem.getOffset() - 4 + 4 * index, desReg));
                } else {
                    if (curIRCode.getOpIdent2() != null) {
                        String offReg = findReg(curIRCode.getOpIdent2());
                        String addressReg = getReg(arrayName);
                        addMIPSCode(new MIPSCode(MIPSOperator.sll, offReg, 2, offReg));//4对齐
                        addMIPSCode(new MIPSCode(MIPSOperator.add, "$fp", -arrayItem.getOffset() - 4, addressReg));
                        addMIPSCode(new MIPSCode(MIPSOperator.add, addressReg, offReg, desReg));
                        freeReg(addressReg);
                        freeReg(offReg);
                    } else {
                        addMIPSCode(new MIPSCode(MIPSOperator.addi, "$fp", -arrayItem.getOffset() - 4, desReg));
                    }
                }
            }
        }
    }

    public void genPrintStr() {
        addMIPSCode(new MIPSCode(MIPSOperator.li, 4, null, "$v0"));
        addMIPSCode(new MIPSCode(MIPSOperator.la, curIRCode.getPrintStrName(), null, "$a0"));
        addMIPSCode(new MIPSCode(MIPSOperator.syscall, null, null, null));
    }

    public void genPrintInt() {
        addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, "$v0"));
        int intSrcIndex = curIRCode.getOpNum1();
        int printIntSum = curIRCode.getOpNum2();
        String intSrcReg = getReg(String.valueOf(intSrcIndex));
        addMIPSCode(new MIPSCode(MIPSOperator.lw, "$sp", 4 * (printIntSum - intSrcIndex), intSrcReg));
        addMIPSCode(new MIPSCode(MIPSOperator.move, intSrcReg, null, "$a0"));
        addMIPSCode(new MIPSCode(MIPSOperator.syscall, null, null, null));
        freeReg(intSrcReg);
        if (intSrcIndex == printIntSum) {
            //print参数退栈
            addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", 4 * (printIntSum + 1), "$sp"));
        }
    }

    public void genPrintPush() {
        String intSrcReg = findReg(curIRCode.getOpIdent1());
        addMIPSCode(new MIPSCode(MIPSOperator.addi, "$sp", -4, "$sp"));
        addMIPSCode(new MIPSCode(MIPSOperator.sw, intSrcReg, null, "$sp"));
        freeReg(intSrcReg);
    }

    public void genGetint() {
        addMIPSCode(new MIPSCode(MIPSOperator.li, 5, null, "$v0"));
        addMIPSCode(new MIPSCode(MIPSOperator.syscall, null, null, null));
        String identName = curIRCode.getResultIdent();
        int index = curIRCode.getOpNum2();
        MIPSTableItem identItem = getVarConstParamItem(identName);
        if (identItem == null) {
            Error.mipsError(curIRCode.print() + ": getint变量未定义");
        } else if (identItem.getTableItem().isGlobal()) {
            //全局变量
            if (curIRCode.op2IsNum() || curIRCode.getOpIdent2() == null) {
                addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", 4 * index, "Global_" + identName));
            } else {
                String indexReg = findReg(curIRCode.getOpIdent2());
                addMIPSCode(new MIPSCode(MIPSOperator.sll, indexReg, 2, indexReg));//4对齐
                addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", "Global_" + identName, indexReg));
                freeReg(indexReg);
            }
        } else if (identItem.getTableItem().getKind().equals("param")) {
            if (!identItem.getTableItem().isArray()) {
                //非数组参数
                int offset = identItem.getOffset();
                addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", -offset - 4, "$fp"));
            } else {
                //数组参数，取出地址
                String addressReg = getReg(identName);
                addMIPSCode(new MIPSCode(MIPSOperator.lw, "$fp", -identItem.getOffset() - 4, addressReg));
                if (curIRCode.op2IsNum() || curIRCode.getOpIdent2() == null) {
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", -4 * index - 4, addressReg));
                } else {
                    String indexReg = findReg(curIRCode.getOpIdent2());
                    addMIPSCode(new MIPSCode(MIPSOperator.sll, indexReg, 2, indexReg));//4对齐
                    addMIPSCode(new MIPSCode(MIPSOperator.add, indexReg, addressReg, addressReg));
                    addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", null, addressReg));
                    freeReg(indexReg);
                }
                freeReg(addressReg);
            }
        } else {
            //局部变量
            if (curIRCode.op2IsNum() || curIRCode.getOpIdent2() == null) {
                int offset = identItem.getOffset();
                addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", -offset - 4 * index - 4, "$fp"));
            } else {
                String indexReg = findReg(curIRCode.getOpIdent2());
                String addressReg = getReg(identName);
                addMIPSCode(new MIPSCode(MIPSOperator.sll, indexReg, 2, indexReg));//4对齐
                addMIPSCode(new MIPSCode(MIPSOperator.add, "$fp", -identItem.getOffset() - 4, addressReg));
                addMIPSCode(new MIPSCode(MIPSOperator.add, indexReg, addressReg, addressReg));
                addMIPSCode(new MIPSCode(MIPSOperator.sw, "$v0", null, addressReg));
                freeReg(indexReg);
                freeReg(addressReg);
            }
        }
    }


    public void genCalculate() {
        switch (curIRCode.getOperator()) {
            case ADD:
                genAdd();
                break;
            case SUB:
                genSub();
                break;
            case MUL:
                genMul();
                break;
            case DIV:
                genDiv();
                break;
            case MOD:
                genMod();
                break;
            case AND:
                genAnd();
                break;
            case OR:
                genOr();
                break;
            case NOT:
                genNot();
                break;
            case SLT:
                genSlt();
                break;
            case SLE:
                genSle();
                break;
            case SEQ:
                genSeq();
                break;
            case SNE:
                genSne();
                break;
            case SGE:
                genSge();
                break;
            case SGT:
                genSgt();
                break;
            default:
                Error.mipsError("wrong calculate irCode");
        }
    }

    public void genAdd() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": add两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.addiu, op1Reg, curIRCode.getOpNum2(), desReg));
            freeReg(op1Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.addiu, op2Reg, curIRCode.getOpNum1(), desReg));
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.addiu, op1Reg, curIRCode.getOpNum2(), desReg));
            freeReg(op1Reg);
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.addu, op1Reg, op2Reg, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genSub() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": sub两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.subiu, op1Reg, curIRCode.getOpNum2(), desReg));
            freeReg(op1Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op1Reg = curIRCode.getOpNum1() == 0 ? "$0" : getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.subu, op1Reg, op2Reg, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.subiu, op1Reg, curIRCode.getOpNum2(), desReg));
            freeReg(op1Reg);
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.subu, op1Reg, op2Reg, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genMul() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": mul两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.mult, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.mult, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.mult, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op2Reg);//释放常数寄存器
            freeReg(op1Reg);
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.mult, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genDiv() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": div两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op2Reg);//释放常数寄存器
            freeReg(op1Reg);
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mflo, null, null, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genMod() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": mod两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum1(), null, op1Reg));//常数opNum1赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
            freeReg(op1Reg);//释放常数寄存器
            freeReg(op2Reg);
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.li, curIRCode.getOpNum2(), null, op2Reg));//常数opNum2赋值给寄存器
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
            freeReg(op2Reg);//释放常数寄存器
            freeReg(op1Reg);
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.div, op1Reg, op2Reg, null));
            addMIPSCode(new MIPSCode(MIPSOperator.mfhi, null, null, desReg));
            freeReg(op1Reg);
            freeReg(op2Reg);
        }
    }

    public void genAnd() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": and两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() != 0 && curIRCode.getOpNum2() != 0) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            if (curIRCode.getOpNum1() != 0) {
                addMIPSCode(new MIPSCode(MIPSOperator.sne, op2Reg, "$0", op2Reg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, op2Reg));
            }
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            if (curIRCode.getOpNum2() != 0) {
                addMIPSCode(new MIPSCode(MIPSOperator.sne, op1Reg, "$0", op1Reg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, op1Reg));
            }
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.sne, op2Reg, "$0", op2Reg));
            addMIPSCode(new MIPSCode(MIPSOperator.sne, op1Reg, "$0", op1Reg));
            addMIPSCode(new MIPSCode(MIPSOperator.and, op1Reg, op2Reg, op1Reg));
            freeReg(op2Reg);
        }
    }

    public void genOr() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": or两个操作数不应该同时为常数");
        }
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String op1Reg = getReg(String.valueOf(curIRCode.getOpNum1()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.ori, op1Reg, curIRCode.getOpNum2(), desReg));
            freeReg(op1Reg);//释放常数寄存器
        } else if (curIRCode.op1IsNum()) {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.ori, op2Reg, curIRCode.getOpNum1(), op2Reg));
        } else if (curIRCode.op2IsNum()) {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            addMIPSCode(new MIPSCode(MIPSOperator.ori, op1Reg, curIRCode.getOpNum2(), op1Reg));
        } else {
            String op1Reg = findReg(curIRCode.getOpIdent1());
            String op2Reg = findReg(curIRCode.getOpIdent2());
            addMIPSCode(new MIPSCode(MIPSOperator.or, op1Reg, op2Reg, op1Reg));
            freeReg(op2Reg);
        }
    }

    public void genNot() {
        if (curIRCode.op2IsNum()) {
            Error.mipsError(curIRCode.print() + ": not操作数不应该为常数");
        }
        if (curIRCode.op2IsNum()) {
            String op2Reg = getReg(String.valueOf(curIRCode.getOpNum2()));
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.seq, op2Reg, "$0", desReg));
            freeReg(op2Reg);//释放常数寄存器
        } else {
            String op2Reg = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.seq, op2Reg, "$0", desReg));
            freeReg(op2Reg);
        }
    }

    public void genSlt() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() < curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.slti, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sgt, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.slt, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void genSle() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() <= curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sge, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sle, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sle, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void genSeq() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() == curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.seq, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.seq, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.seq, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void genSne() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() != curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sne, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sne, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sne, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void genSge() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() >= curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sle, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sge, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sge, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void genSgt() {
        if (curIRCode.op1IsNum() && curIRCode.op2IsNum()) {
            String desReg = getReg(curIRCode.getResultIdent());
            if (curIRCode.getOpNum1() > curIRCode.getOpNum2()) {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 1, null, desReg));
            } else {
                addMIPSCode(new MIPSCode(MIPSOperator.li, 0, null, desReg));
            }
        } else if (curIRCode.op1IsNum()) {
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.slti, opReg2, curIRCode.getOpNum1(), desReg));
            freeReg(opReg2);
        } else if (curIRCode.op2IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sgt, opReg1, curIRCode.getOpNum2(), desReg));
            freeReg(opReg1);
        } else if (!curIRCode.op2IsNum() && !curIRCode.op1IsNum()) {
            String opReg1 = findReg(curIRCode.getOpIdent1());
            String opReg2 = findReg(curIRCode.getOpIdent2());
            String desReg = getReg(curIRCode.getResultIdent());
            addMIPSCode(new MIPSCode(MIPSOperator.sgt, opReg1, opReg2, desReg));
            freeReg(opReg1);
            freeReg(opReg2);
        }
    }

    public void addMIPSCode(MIPSCode code) {
        mipsCode.add(code);
    }

    public void addLevel() {
        level++;
        tableStack.put(level, new MIPSTable());
        curTable = tableStack.get(level);
    }

    public void deleteLevel() {
        level--;
        tableStack.remove(level + 1);
        curTable = tableStack.get(level);
    }

    public void print() {
        if (print) {
            try {
                for (MIPSCode code : mipsCode) {
                    out.write(code.print());
                }
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void getIRCode() {
        while (irCodeIndex < irCodes.size() && irCodes.get(irCodeIndex).isNote()) {
            irCodeIndex++;
        }
        if (irCodeIndex >= irCodes.size()) {
            curIRCode = null;
        } else {
            curIRCode = irCodes.get(irCodeIndex);
            irCodeIndex++;
        }
    }

    public String getReg(String name) {
        for (Register register : registers) {
            if (register.isAvailable()) {
                register.setBusy(name);
                busyRegs.add(register.getName());
                busyRegNum++;
                //System.out.println("set " + register.getName() + " : " + name);
                if (name.charAt(0) == '#' && isUnusedTempVar(name)) {
                    //System.out.println("unused temp var " + name);
                    register.setAvailable();
                    busyRegs.remove(register.getName());
                    busyRegNum--;
                }
                return register.getName();
            }
        }
        throw new RegisterError("exceed register number");
        //return null;
    }

    public String findReg(String name) {
        for (Register register : registers) {
            if (!register.isAvailable() && register.getIdentName().equals(name)) {
                //System.out.println("find " + register.getName() + " : " + name);
                return register.getName();
            }
        }
        throw new RegisterError("can't find register");
        //return null;
    }

    public void freeReg(String regName) {
        for (Register register : registers) {
            if (register.getName().equals(regName)) {
                register.setAvailable();
                busyRegs.remove(register.getName());
                busyRegNum--;
                //System.out.println("free " + register.getName());
                return;
            }
        }
    }

    public MIPSTableItem getVarConstParamItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).getMIPSItem(name, "var") != null) {
                return tableStack.get(i).getMIPSItem(name, "var");
            } else if (tableStack.get(i).getMIPSItem(name, "const") != null) {
                return tableStack.get(i).getMIPSItem(name, "const");
            } else if (tableStack.get(i).getMIPSItem(name, "param") != null) {
                return tableStack.get(i).getMIPSItem(name, "param");
            }
        }
        return null;
    }

    public boolean isUnusedTempVar(String tempVarName) {
        for (int i = irCodeIndex; i < irCodes.size(); i++) {
            if (irCodes.get(i).getOpIdent1() != null && irCodes.get(i).getOpIdent1().equals(tempVarName)) {
                return false;
            } else if (irCodes.get(i).getOpIdent2() != null && irCodes.get(i).getOpIdent2().equals(tempVarName)) {
                return false;
            }
        }
        return true;
    }

    public int getLastOffset() {
        for (int i = level - 1; i >= 1; i--) {
            if (tableStack.get(i).getLastMIPSTableItem() != null) {
                return tableStack.get(i).getLastMIPSTableItem().getOffset() / 4;
            }
        }
        return -1;
    }
}
