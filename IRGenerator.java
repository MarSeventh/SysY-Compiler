import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class IRGenerator {
    private final boolean print = true;
    private GrammarNode tree = null;
    private final ArrayList<IRCode> irCodes = new ArrayList<>();
    private final BufferedWriter out;
    private final HashMap<Integer, Table> tableStack = new HashMap<>();
    private int level = 0;
    private Table curTable = new Table();
    private TableItem nowFunc = null;

    private int tempReg = 0;
    private int labelNum = 0;
    private ArrayList<String> loopCheckInLabelList = new ArrayList<>();
    private ArrayList<String> loopOutLabelList = new ArrayList<>();
    private ArrayList<String> loopUpdateLabelList = new ArrayList<>();
    private ArrayList<String> loopInLabelList = new ArrayList<>();
    private int loopNum = 0;

    private int condReg = 0;
    private boolean unInitCond = true;

    private int condAndReg = 0;
    private boolean unInitCondAnd = true;

    private int orSum = 0;
    private int andSum = 0;

    private int initCount = 0;

    private boolean isGlobal = false;
    private int printStrNum = 0;

    boolean optimize = false;

    public IRGenerator(GrammarNode tree) {
        try {
            out = new BufferedWriter(new FileWriter("irCodes.txt"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.tree = tree;
        tableStack.put(level, curTable);
    }


    public void addIRCode(IRCode irCode) {
        irCodes.add(irCode);
    }

    public void genIRCode() {
        genCompUnit(tree);
        print();
    }

    public void genCompUnit(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (GrammarNode child : children) {
            if (child.isName("Decl")) {
                isGlobal = true;
                genDecl(child);
                isGlobal = false;
            } else if (child.isName("FuncDef")) {
                genFuncDef(child);
            } else if (child.isName("MainFuncDef")) {
                genMainFuncDef(child);
            }
        }
    }

    public void genDecl(GrammarNode node) {
        if (node.getChild(0) != null && node.getChild(0).isName("ConstDecl")) {
            genConstDecl(node.getChild(0));
        } else if (node.getChild(0) != null && node.getChild(0).isName("VarDecl")) {
            genVarDecl(node.getChild(0));
        }
    }

    public void genFuncDef(GrammarNode node) {
        String funcType = node.getChild(0).getChild(0).getNodeName();
        String funcName = node.getChild(1).getChild(0).getNodeName();
        TableItem funcItem = new TableItem(funcName, funcType, "func", level, 0);
        nowFunc = funcItem;
        curTable.addItem(funcItem);
        addLevel();
        addIRCode(new IRCode(IROperator.func_begin, funcName, null, null, funcItem));
        if (node.getChild(3) != null && node.getChild(3).isName("FuncFParams")) {
            genFuncFParams(node.getChild(3));
        }
        genBlock(node.getLastChild(), true);
        addIRCode(new IRCode(IROperator.func_end, funcName, null, null, funcItem));
        deleteLevel();
        nowFunc = null;
    }

    public void genMainFuncDef(GrammarNode node) {
        nowFunc = new TableItem("main", "int", "func", level, 0);
        curTable.addItem(nowFunc);
        addLevel();
        addIRCode(new IRCode(IROperator.main_begin, null, null, null, nowFunc));
        genBlock(node.getLastChild(), true);
        addIRCode(new IRCode(IROperator.main_end, null, null, null, nowFunc));
        nowFunc = null;
        deleteLevel();
    }

    public void genConstDecl(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (int i = 2; i < children.size(); i += 2) {
            genConstDef(children.get(i));
        }
    }

    public void genVarDecl(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (int i = 1; i < children.size(); i += 2) {
            genVarDef(children.get(i));
        }
    }

    public void genFuncFParams(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i += 2) {
            genFuncFParam(children.get(i));
        }
    }

    public void genConstDef(GrammarNode node) {
        initCount = 0;
        int pos = 0;
        String constName = node.getChild(pos++).getChild(0).getNodeName();//Ident
        int dimen = 0;
        ArrayList<Integer> dimenLength = new ArrayList<>();
        ArrayList<Integer> arrayConstInit = new ArrayList<>();
        while (node.getChild(pos) != null && node.getChild(pos).isName("[")) {
            pos++;//constExp
            dimen++;
            dimenLength.add(Calculator.calConstExp(node.getChild(pos++), tableStack, level));//]
            pos++;//[ / =
        }
        pos++;//constInitVal
        TableItem constItem = new TableItem(constName, "int", "const", level, dimen);
        constItem.setGlobal(isGlobal);//isGlobal?
        addIRCode(new IRCode(IROperator.DEF, "const", dimen == 0 ? null : "array", constName, constItem));
        genConstInitVal(node.getChild(pos), arrayConstInit, constItem);
        constItem.setArrayDimen(dimenLength);
        constItem.setArrayConstInit(arrayConstInit);
        if (dimen == 0) {
            constItem.setConstInit(arrayConstInit.get(0));//保存常数初值
        }
        curTable.addItem(constItem);
    }

    public void genVarDef(GrammarNode node) {
        initCount = 0;
        int pos = 0;
        String varName = node.getChild(pos++).getChild(0).getNodeName();//Ident
        int dimen = 0;
        ArrayList<Integer> dimenLength = new ArrayList<>();
        while (node.getChild(pos) != null && node.getChild(pos).isName("[")) {
            pos++;//constExp
            dimen++;
            dimenLength.add(Calculator.calConstExp(node.getChild(pos++), tableStack, level));//]
            pos++;//[ / =
        }
        TableItem varItem = new TableItem(varName, "int", "var", level, dimen);
        varItem.setGlobal(isGlobal);//isGlobal?
        varItem.setArrayDimen(dimenLength);
        addIRCode(new IRCode(IROperator.DEF, "var", dimen == 0 ? null : "array", varName, varItem));
        if (node.getChild(pos) != null && node.getChild(pos).isName("=")) {
            pos++;//InitVal
            genInitVal(node.getChild(pos), varItem);
        }
        curTable.addItem(varItem);
    }

    public void genConstInitVal(GrammarNode node, ArrayList<Integer> arrayConstInit, TableItem constItem) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (GrammarNode child : children) {
            if (child.isName("ConstExp")) {
                int initNum = Calculator.calConstExp(child, tableStack, level);
                arrayConstInit.add(initNum);
                addIRCode(new IRCode(IROperator.ASSIGN, initNum, initCount++, constItem.getName()));
            } else if (child.isName("ConstInitVal")) {
                genConstInitVal(child, arrayConstInit, constItem);
            }
        }
    }

    public void genInitVal(GrammarNode node, TableItem varItem) {
        ArrayList<GrammarNode> children = node.getChildren();
        for (GrammarNode child : children) {
            if (child.isName("Exp")) {
                int oriReg = 0;
                boolean isOriConst = false;
                oriReg = Calculator.calConstExp(child, tableStack, level);
                isOriConst = Calculator.getIsConst();
                if (!isOriConst) {
                    genExp(child);
                    oriReg = tempReg - 1;
                }
                if (varItem.getDimension() == 0) {
                    if (!isOriConst) {
                        addIRCode(new IRCode(IROperator.ASSIGN, "#t" + oriReg, null, varItem.getName()));
                    } else {
                        addIRCode(new IRCode(IROperator.ASSIGN, oriReg, null, varItem.getName()));
                    }
                } else {
                    if (!isOriConst) {
                        addIRCode(new IRCode(IROperator.ASSIGN, "#t" + oriReg, initCount++, varItem.getName()));
                    } else {
                        addIRCode(new IRCode(IROperator.ASSIGN, oriReg, initCount++, varItem.getName()));
                    }
                }
            } else if (child.isName("InitVal")) {
                genInitVal(child, varItem);
            }
        }
    }

    public void genExp(GrammarNode node) {
        boolean isExpConst = false;
        int expReg = 0;
        expReg = Calculator.calConstExp(node, tableStack, level);
        isExpConst = Calculator.getIsConst();
        if (!isExpConst) {
            genAddExp(node.getChild(0));
        } else {
            addIRCode(new IRCode(IROperator.ASSIGN, expReg, null, "#t" + tempReg++));
        }
    }

    public void genAddExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("AddExp")) {
            int leftReg = 0;
            int rightReg = 0;
            boolean isLeftConst = false;
            boolean isRightConst = false;
            Calculator.setTableStackAndLevel(tableStack, level);
            leftReg = Calculator.calAddExp(children.get(0));
            isLeftConst = Calculator.getIsConst();
            if (!isLeftConst) {
                genAddExp(children.get(0));
                leftReg = tempReg - 1;
            }
            Calculator.setTableStackAndLevel(tableStack, level);
            rightReg = Calculator.calMulExp(children.get(2));
            isRightConst = Calculator.getIsConst();
            if (!isRightConst) {
                genMulExp(children.get(2));
                rightReg = tempReg - 1;
            }
            if (children.get(1).isName("+")) {
                if (isLeftConst && isRightConst) {
                    addIRCode(new IRCode(IROperator.ASSIGN, leftReg + rightReg, null, "#t" + tempReg++));
                } else if (isLeftConst) {
                    addIRCode(new IRCode(IROperator.ADD, leftReg, "#t" + rightReg, "#t" + tempReg++));
                } else if (isRightConst) {
                    addIRCode(new IRCode(IROperator.ADD, "#t" + leftReg, rightReg, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.ADD, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
                }
            } else {
                if (isLeftConst && isRightConst) {
                    addIRCode(new IRCode(IROperator.ASSIGN, leftReg - rightReg, null, "#t" + tempReg++));
                } else if (isLeftConst) {
                    addIRCode(new IRCode(IROperator.SUB, leftReg, "#t" + rightReg, "#t" + tempReg++));
                } else if (isRightConst) {
                    addIRCode(new IRCode(IROperator.SUB, "#t" + leftReg, rightReg, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.SUB, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
                }
            }
        } else {
            int expReg = 0;
            Calculator.setTableStackAndLevel(tableStack, level);
            expReg = Calculator.calMulExp(children.get(0));
            if (!Calculator.getIsConst()) {
                genMulExp(children.get(0));
            } else {
                addIRCode(new IRCode(IROperator.ASSIGN, expReg, null, "#t" + tempReg++));
            }
        }
    }

    public void genMulExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("MulExp")) {
            int leftReg = 0;
            int rightReg = 0;
            boolean isLeftConst = false;
            boolean isRightConst = false;
            Calculator.setTableStackAndLevel(tableStack, level);
            leftReg = Calculator.calMulExp(children.get(0));
            isLeftConst = Calculator.getIsConst();
            if (!isLeftConst) {
                genMulExp(children.get(0));
                leftReg = tempReg - 1;
            }
            Calculator.setTableStackAndLevel(tableStack, level);
            rightReg = Calculator.calUnaryExp(children.get(2));
            isRightConst = Calculator.getIsConst();
            if (!isRightConst) {
                genUnaryExp(children.get(2));
                rightReg = tempReg - 1;
            }
            if (children.get(1).isName("*")) {
                if (isLeftConst && isRightConst) {
                    addIRCode(new IRCode(IROperator.ASSIGN, leftReg * rightReg, null, "#t" + tempReg++));
                } else if (isLeftConst) {
                    addIRCode(new IRCode(IROperator.MUL, leftReg, "#t" + rightReg, "#t" + tempReg++));
                } else if (isRightConst) {
                    addIRCode(new IRCode(IROperator.MUL, "#t" + leftReg, rightReg, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.MUL, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
                }
            } else if (children.get(1).isName("/")) {
                if (isLeftConst && isRightConst) {
                    addIRCode(new IRCode(IROperator.ASSIGN, leftReg / rightReg, null, "#t" + tempReg++));
                } else if (isLeftConst) {
                    addIRCode(new IRCode(IROperator.DIV, leftReg, "#t" + rightReg, "#t" + tempReg++));
                } else if (isRightConst) {
                    addIRCode(new IRCode(IROperator.DIV, "#t" + leftReg, rightReg, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.DIV, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
                }
            } else {
                if (isLeftConst && isRightConst) {
                    addIRCode(new IRCode(IROperator.ASSIGN, leftReg % rightReg, null, "#t" + tempReg++));
                } else if (isLeftConst) {
                    addIRCode(new IRCode(IROperator.MOD, leftReg, "#t" + rightReg, "#t" + tempReg++));
                } else if (isRightConst) {
                    addIRCode(new IRCode(IROperator.MOD, "#t" + leftReg, rightReg, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.MOD, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
                }
            }
        } else {
            int expReg = 0;
            Calculator.setTableStackAndLevel(tableStack, level);
            expReg = Calculator.calUnaryExp(children.get(0));
            if (!Calculator.getIsConst()) {
                genUnaryExp(children.get(0));
            } else {
                addIRCode(new IRCode(IROperator.ASSIGN, expReg, null, "#t" + tempReg++));
            }
        }
    }

    public void genUnaryExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("PrimaryExp")) {
            genPrimaryExp(children.get(0));
        } else if (children.get(0).isName("Ident")) {//函数调用
            String funcName = children.get(0).getChild(0).getNodeName();
            TableItem funcItem = getFuncItem(funcName);
            if (children.size() == 3) {//无参
                addIRCode(new IRCode(IROperator.CALL, funcName, null, null, funcItem));
            } else {//有参
                genFuncRParams(children.get(2), funcItem);
                addIRCode(new IRCode(IROperator.CALL, funcName, null, null, funcItem));
            }
            if (funcItem.getType().equals("int")) {
                addIRCode(new IRCode(IROperator.POP, null, null, "#t" + tempReg++));
            }
        } else if (children.get(0).isName("UnaryOp")) {
            genUnaryExp(children.get(1));
            int rightReg = tempReg - 1;
            if (children.get(0).getChild(0).isName("-")) {
                addIRCode(new IRCode(IROperator.SUB, 0, "#t" + rightReg, "#t" + tempReg));
            } else if (children.get(0).getChild(0).isName("+")) {
                addIRCode(new IRCode(IROperator.ADD, 0, "#t" + rightReg, "#t" + tempReg));
            } else {
                addIRCode(new IRCode(IROperator.NOT, 0, "#t" + rightReg, "#t" + tempReg));
            }
            tempReg++;
        }
    }

    public void genFuncRParams(GrammarNode node, TableItem funcItem) {
        ArrayList<GrammarNode> children = node.getChildren();
        int paraCount = 0;
        for (GrammarNode child : children) {
            if (child.isName("Exp")) {
                genExp(child);
                int expReg = tempReg - 1;
                addIRCode(new IRCode(IROperator.PUSH, "#t" + expReg, paraCount++, funcItem.getName(), funcItem));
            }
        }
    }

    public void genPrimaryExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("(")) {
            genExp(children.get(1));
        } else if (children.get(0).isName("LVal")) {
            genLVal(children.get(0), false);
        } else if (children.get(0).isName("Number")) {
            int num = Integer.parseInt(children.get(0).getChild(0).getNodeName());
            addIRCode(new IRCode(IROperator.ASSIGN, num, null, "#t" + tempReg));
            tempReg++;
        }
    }

    public void genLVal(GrammarNode node, boolean isLeft) {
        ArrayList<GrammarNode> children = node.getChildren();
        String name = children.get(0).getChild(0).getNodeName();
        TableItem lValItem = getLValItem(name);
        if (!isLeft) {//right
            if (children.size() == 1) {//0维或数组的基地址
                if (lValItem.getDimension() == 0) {
                    addIRCode(new IRCode(IROperator.ASSIGN, name, null, "#t" + tempReg++));
                } else {
                    addIRCode(new IRCode(IROperator.ARRAY_GET, name, null, "#t" + tempReg++, true));
                }
            } else {
                int dimenReg1 = 0;
                int dimenReg2 = 0;
                boolean isDimen1Const = false;
                boolean isDimen2Const = false;
                dimenReg1 = Calculator.calConstExp(children.get(2), tableStack, level);
                isDimen1Const = Calculator.getIsConst();
                if (!isDimen1Const) {
                    genExp(children.get(2));
                    dimenReg1 = tempReg - 1;
                }
                if (children.size() > 5 && children.get(5).isName("Exp")) {//2维
                    dimenReg2 = Calculator.calConstExp(children.get(5), tableStack, level);
                    isDimen2Const = Calculator.getIsConst();
                    if (!isDimen2Const) {
                        genExp(children.get(5));
                        dimenReg2 = tempReg - 1;
                    }
                    int indexReg = 0;
                    boolean isIndexConst = false;
                    if (!isDimen1Const) {
                        addIRCode(new IRCode(IROperator.MUL, "#t" + dimenReg1, lValItem.getArrayDimen(1), "#t" + tempReg++));
                        indexReg = tempReg - 1;
                    } else {
                        indexReg = dimenReg1 * lValItem.getArrayDimen(1);
                        isIndexConst = true;
                    }
                    if (isIndexConst && isDimen2Const) {
                        addIRCode(new IRCode(IROperator.ARRAY_GET, name, indexReg + dimenReg2, "#t" + tempReg++, false));
                    } else if (isIndexConst) {
                        addIRCode(new IRCode(IROperator.ADD, "#t" + dimenReg2, indexReg, "#t" + tempReg++));
                        indexReg = tempReg - 1;
                        addIRCode(new IRCode(IROperator.ARRAY_GET, name, "#t" + indexReg, "#t" + tempReg++, false));
                    } else if (isDimen2Const) {
                        addIRCode(new IRCode(IROperator.ADD, "#t" + indexReg, dimenReg2, "#t" + tempReg++));
                        indexReg = tempReg - 1;
                        addIRCode(new IRCode(IROperator.ARRAY_GET, name, "#t" + indexReg, "#t" + tempReg++, false));
                    } else {
                        addIRCode(new IRCode(IROperator.ADD, "#t" + indexReg, "#t" + dimenReg2, "#t" + tempReg++));
                        indexReg = tempReg - 1;
                        addIRCode(new IRCode(IROperator.ARRAY_GET, name, "#t" + indexReg, "#t" + tempReg++, false));
                    }
                } else {//1维或2维数组的第一维
                    if (lValItem.getDimension() == 2) {
                        int indexReg = 0;
                        if (isDimen1Const) {
                            indexReg = dimenReg1 * lValItem.getArrayDimen(1);
                            addIRCode(new IRCode(IROperator.ARRAY_GET, name, indexReg, "#t" + tempReg++, true));
                        } else {
                            addIRCode(new IRCode(IROperator.MUL, "#t" + dimenReg1, lValItem.getArrayDimen(1), "#t" + tempReg++));
                            indexReg = tempReg - 1;
                            addIRCode(new IRCode(IROperator.ARRAY_GET, name, "#t" + indexReg, "#t" + tempReg++, true));
                        }
                    } else {
                        if (isDimen1Const) {
                            addIRCode(new IRCode(IROperator.ARRAY_GET, name, dimenReg1, "#t" + tempReg++, false));
                        } else {
                            addIRCode(new IRCode(IROperator.ARRAY_GET, name, "#t" + dimenReg1, "#t" + tempReg++, false));
                        }
                    }
                }
            }
        } else { //左式，求出索引即可
            int dimenReg1 = 0;
            int dimenReg2 = 0;
            if (children.size() > 2 && children.get(2).isName("Exp")) {
                genExp(children.get(2));
                dimenReg1 = tempReg - 1;
                if (children.size() > 5 && children.get(5).isName("Exp")) {
                    genExp(children.get(5));
                    dimenReg2 = tempReg - 1;
                    addIRCode(new IRCode(IROperator.MUL, "#t" + dimenReg1, lValItem.getArrayDimen(1), "#t" + tempReg++));
                    int indexReg = tempReg - 1;
                    addIRCode(new IRCode(IROperator.ADD, "#t" + indexReg, "#t" + dimenReg2, "#t" + tempReg++));
                }
            }
        }
    }

    public void genFuncFParam(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        String paramType = children.get(0).getChild(0).getNodeName();
        String paramName = children.get(1).getChild(0).getNodeName();
        int paramDimen = 0;
        if (children.size() > 2 && children.size() < 5) {
            paramDimen = 1;
        } else if (children.size() >= 5) {
            paramDimen = 2;
        }
        TableItem paramItem = new TableItem(paramName, paramType, "param", level, paramDimen);
        ArrayList<Integer> dimenLength = new ArrayList<>();
        if (paramDimen == 1) {
            dimenLength.add(-1);
        } else if (paramDimen == 2) {
            dimenLength.add(-1);
            dimenLength.add(Calculator.calConstExp(children.get(5), tableStack, level));
        }
        paramItem.setArrayDimen(dimenLength);
        nowFunc.addPara(paramDimen);
        addIRCode(new IRCode(IROperator.DEF, "param", paramDimen == 0 ? null : "array", paramName, paramItem));
        curTable.addItem(paramItem);
    }

    public void genBlock(GrammarNode node, boolean isFuncDef) {
        if (!isFuncDef) {
            addLevel();
            addIRCode(new IRCode(IROperator.block_begin, null, null, null));
        }
        ArrayList<GrammarNode> children = node.getChildren();
        int pos = 1;
        while (children.get(pos) != null && children.get(pos).isName("BlockItem")) {
            genBlockItem(children.get(pos));
            pos++;
        }
        if (!isFuncDef) {
            deleteLevel();
            addIRCode(new IRCode(IROperator.OUTBLOCK, null, null, null));
            addIRCode(new IRCode(IROperator.block_end, null, null, null));
        }
    }

    public void genBlockItem(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("Decl")) {
            genDecl(children.get(0));
        } else if (children.get(0).isName("Stmt")) {
            genStmt(children.get(0));
        }
    }

    public void genStmt(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        GrammarNode firstChild = children.get(0);
        if (firstChild.isName("LVal")) {
            genLVal(firstChild, true);
            int indexReg = tempReg - 1;
            String LValName = firstChild.getChild(0).getChild(0).getNodeName();
            TableItem LValItem = getLValItem(LValName);
            if (children.get(2).isName("Exp")) {
                int expReg = 0;
                boolean isExpConst = false;
                expReg = Calculator.calConstExp(children.get(2), tableStack, level);
                isExpConst = Calculator.getIsConst();
                if (!isExpConst) {
                    genExp(children.get(2));
                    expReg = tempReg - 1;
                }
                if (LValItem.getDimension() == 0) {
                    if (!isExpConst) {
                        addIRCode(new IRCode(IROperator.ASSIGN, "#t" + expReg, null, LValName));
                    } else {
                        addIRCode(new IRCode(IROperator.ASSIGN, expReg, null, LValName));
                    }
                } else {
                    if (!isExpConst) {
                        addIRCode(new IRCode(IROperator.ASSIGN, "#t" + expReg, "#t" + indexReg, LValName));
                    } else {
                        addIRCode(new IRCode(IROperator.ASSIGN, expReg, "#t" + indexReg, LValName));
                    }
                }
            } else if (children.get(2).isName("getint")) {
                if (LValItem.getDimension() == 0) {
                    addIRCode(new IRCode(IROperator.GETINT, null, null, LValName));
                } else {
                    addIRCode(new IRCode(IROperator.GETINT, null, "#t" + indexReg, LValName));
                }
            }
        } else if (firstChild.isName("Exp")) {
            //仅函数调用时计算

            genExp(firstChild);
        } else if (firstChild.isName("Block")) {
            genBlock(firstChild, false);
        } else if (firstChild.isName("if")) {
            addIRCode(new IRCode(IROperator.note, null, null, "if"));
            if (children.size() == 5) {//if
                String inLabel = "if_in" + labelNum;
                String outLabel = "if_out" + labelNum++;
                genCond(children.get(2), outLabel, inLabel, false);
                if (!optimize) {
                    //优化模式下，直接在顶层LOr中跳转到出口
                    addIRCode(new IRCode(IROperator.BEQ, "#t" + condReg, 0, outLabel));
                }
                addIRCode(new IRCode(IROperator.LABEL, null, null, inLabel));
                genStmt(children.get(4));
                addIRCode(new IRCode(IROperator.LABEL, null, null, outLabel));
            } else if (children.size() == 7) {//if_else
                String inLabel = "if_in" + labelNum;
                String elseLabel = "else" + labelNum;
                String outLabel = "if_out" + labelNum++;
                genCond(children.get(2), elseLabel, inLabel, false);
                if (!optimize) {
                    //优化模式下，直接在顶层LOr中跳转到出口
                    addIRCode(new IRCode(IROperator.BEQ, "#t" + condReg, 0, elseLabel));
                }
                addIRCode(new IRCode(IROperator.LABEL, null, null, inLabel));
                genStmt(children.get(4));
                addIRCode(new IRCode(IROperator.JMP, null, null, outLabel));
                addIRCode(new IRCode(IROperator.LABEL, null, null, elseLabel));
                genStmt(children.get(6));
                addIRCode(new IRCode(IROperator.LABEL, null, null, outLabel));
            }
        } else if (firstChild.isName("for")) {
            addIRCode(new IRCode(IROperator.note, null, null, "for"));
            loopNum++;
            int nowLoopNum = loopNum - 1;
            loopOutLabelList.add(null);
            loopUpdateLabelList.add(null);
            loopCheckInLabelList.add(null);
            loopInLabelList.add(null);
            int pos = 2;
            if (children.get(pos).isName("ForStmt")) {
                genForStmt(children.get(pos));
                pos += 2;//跳过;
            } else {
                pos++;//跳过;
            }
            int loopLabelNum = labelNum++;
            String checkInLabel = null;
            if (!optimize) {
                checkInLabel = "for_checkin" + loopLabelNum;
                loopCheckInLabelList.set(nowLoopNum, checkInLabel);
            } else {
                loopCheckInLabelList.set(nowLoopNum, null);
            }
            String outLabel = "for_out" + loopLabelNum;
            loopOutLabelList.set(nowLoopNum, outLabel);
            String inLabel = "for_in" + loopLabelNum;
            loopInLabelList.set(nowLoopNum, inLabel);
            if (!optimize) {
                //优化前逻辑，每次无条件跳转回checkin
                addIRCode(new IRCode(IROperator.LABEL, null, null, checkInLabel));
            }
            boolean hasCheckIn = false;
            GrammarNode checkInNode = null;
            if (children.get(pos).isName("Cond")) {
                hasCheckIn = true;
                checkInLabel = "for_checkin" + loopLabelNum;
                loopCheckInLabelList.set(nowLoopNum, checkInLabel);
                checkInNode = children.get(pos);
                genCond(children.get(pos), outLabel, inLabel, false);
                //int condReg = tempReg - 1;
                if (!optimize) {
                    //优化模式下，直接在顶层LOr中跳转到出口
                    addIRCode(new IRCode(IROperator.BEQ, "#t" + condReg, 0, outLabel));
                }
                pos += 2;//跳过;
            } else {
                pos++;//跳过;
            }
            boolean hasUpdate = false;
            int updatePos = 0;
            String updateLabel = null;
            loopUpdateLabelList.set(nowLoopNum, null);
            if (children.get(pos).isName("ForStmt")) {
                hasUpdate = true;
                updatePos = pos;
                updateLabel = "for_update" + loopLabelNum;
                loopUpdateLabelList.set(nowLoopNum, updateLabel);
                pos += 2;//跳过)
            } else {
                pos++;//跳过)
            }
            addIRCode(new IRCode(IROperator.LABEL, null, null, inLabel));
            genStmt(children.get(pos));
            if (hasUpdate) {
                addIRCode(new IRCode(IROperator.LABEL, null, null, updateLabel));
                genForStmt(children.get(updatePos));
            }
            if (!optimize) {
                addIRCode(new IRCode(IROperator.JMP, null, null, checkInLabel));
            } else {
                //优化：首先判断是不是有checkin，有的话丢一个标签，并且解析Cond
                if (hasCheckIn) {
                    addIRCode(new IRCode(IROperator.LABEL, null, null, checkInLabel));
                    genCond(checkInNode, outLabel, inLabel, true);
                } else {
                    //没有的话直接跳转回for_in
                    addIRCode(new IRCode(IROperator.JMP, null, null, inLabel));
                }
            }
            addIRCode(new IRCode(IROperator.LABEL, null, null, outLabel));
            addIRCode(new IRCode(IROperator.note, null, null, "for_end"));
            loopNum--;
        } else if (firstChild.isName("break") || firstChild.isName("continue")) {
            int nowLoopNum = loopNum - 1;
            if (firstChild.isName("break")) {
                addIRCode(new IRCode(IROperator.note, null, null, "break"));
                addIRCode(new IRCode(IROperator.OUTBLOCK, null, null, null));
                addIRCode(new IRCode(IROperator.JMP, null, null, loopOutLabelList.get(nowLoopNum)));
            } else {
                addIRCode(new IRCode(IROperator.note, null, null, "continue"));
                if (loopUpdateLabelList.get(nowLoopNum) != null) {
                    addIRCode(new IRCode(IROperator.OUTBLOCK, null, null, null));
                    addIRCode(new IRCode(IROperator.JMP, null, null, loopUpdateLabelList.get(nowLoopNum)));
                } else {
                    addIRCode(new IRCode(IROperator.OUTBLOCK, null, null, null));
                    if (!optimize) {
                        addIRCode(new IRCode(IROperator.JMP, null, null, loopCheckInLabelList.get(nowLoopNum)));
                    } else {
                        //优化模式下，如果没有checkin需要跳转到for_in
                        addIRCode(new IRCode(IROperator.JMP, null, null,
                                loopCheckInLabelList.get(nowLoopNum) == null ? loopInLabelList.get(nowLoopNum) : loopCheckInLabelList.get(nowLoopNum)));
                    }
                }
            }
        } else if (firstChild.isName("return")) {
            if (children.size() == 2) {
                addIRCode(new IRCode(IROperator.RET, null, null, null));
            } else {
                genExp(children.get(1));
                int expReg = tempReg - 1;
                addIRCode(new IRCode(IROperator.RET, "#t" + expReg, null, null));
            }
        } else if (firstChild.isName("printf")) {
            addIRCode(new IRCode(IROperator.note, null, null, "printf"));
            //node.print();
            int pos = 4;//或许是第一个输出数字
            String formatString = children.get(2).getNodeName();//字符串
            formatString = formatString.substring(1, formatString.length() - 1);
            int print_int_num = 0;
            for (int i = 0; i < formatString.length(); i++) {
                if (formatString.charAt(i) == '%') {
                    if (children.get(pos) != null) {
                        genExp(children.get(pos));
                        pos += 2;
                    } else {
                        System.out.println("wrong printf params");
                    }
                    addIRCode(new IRCode(IROperator.PRINT_PUSH, "#t" + (tempReg - 1), print_int_num++, null));
                    i++;
                }
            }
            int print_int_sum = print_int_num - 1;
            print_int_num = 0;
            for (int i = 0; i < formatString.length(); i++) {
                if (formatString.charAt(i) == '%') {
                    addIRCode(new IRCode(IROperator.PRINT_INT, print_int_num++, print_int_sum, null));
                    i++;
                } else {
                    String printStr = "";
                    while (i < formatString.length() - 1 && formatString.charAt(i + 1) != '%') {
                        printStr += formatString.charAt(i);
                        i++;
                    }
                    printStr += formatString.charAt(i);
                    addIRCode(new IRCode(IROperator.PRINT_STR, printStr,
                            null, null, "print_str" + printStrNum++));
                }
            }
        }
    }

    //nearOut代表该条件表达式在出口的地方，否则在入口的地方
    public void genCond(GrammarNode node, String outLabel, String inLabel, boolean nearOut) {
        unInitCond = true;
        genLOrExp(node.getChild(0), true, outLabel, inLabel, nearOut);
        if (!optimize) {
            addIRCode(new IRCode(IROperator.LABEL, null, null, "or" + orSum++));
            addIRCode(new IRCode(IROperator.LABEL, null, null, "and" + andSum++));
        }
    }

    //isTop表示是否是顶层LOr
    public void genLOrExp(GrammarNode node, boolean isTop, String outLabel, String inLabel, boolean nearOut) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("LAndExp")) {
            unInitCondAnd = true;
            if (isTop) {
                //该and语句集没有下一个or，直接跳转到出口处
                genLAndExp(children.get(0), false, true, outLabel, inLabel, nearOut);
            } else {
                //该and语句集有下一个or，如果为0跳转到下一个or处
                genLAndExp(children.get(0), true, true, outLabel, inLabel, nearOut);
            }
            if (unInitCond) {
                condReg = condAndReg;
                unInitCond = false;
            }
        } else {
            if (!optimize) {
                genLOrExp(children.get(0), false, outLabel, inLabel, nearOut);
                addIRCode(new IRCode(IROperator.LABEL, null, null, "or" + orSum++));
                addIRCode(new IRCode(IROperator.BNE, "#t" + condReg, 0, "and" + andSum, true));
                //int leftReg = tempReg - 1;
                unInitCondAnd = true;
                genLAndExp(children.get(2), !isTop, true, outLabel, inLabel, nearOut);
                int rightReg = condAndReg;
                addIRCode(new IRCode(IROperator.OR, "#t" + condReg, "#t" + rightReg, "#t" + condReg));
            } else {
                genLOrExp(children.get(0), false, outLabel, inLabel, nearOut);
                addIRCode(new IRCode(IROperator.LABEL, null, null, "or" + orSum++));
                unInitCondAnd = true;
                genLAndExp(children.get(2), !isTop, true, outLabel, inLabel, nearOut);
            }
        }
    }

    //hasNextOr表示是否有下一个or
    //isLastOfOr表示是否是or语句集中的最后一个and，此时，如果是1应该跳转到下一个and
    public void genLAndExp(GrammarNode node, boolean hasNextOr, boolean isLastOfOr, String outLabel, String inLabel, boolean nearOut) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("EqExp")) {
            genEqExp(children.get(0));
            if (unInitCondAnd) {
                condAndReg = tempReg - 1;
                unInitCondAnd = false;
            }
            if (optimize) {
                int eqReg = tempReg - 1;
                if (!hasNextOr) {
                    //没有下一个or
                    if (!nearOut || !isLastOfOr) {
                        //不在出口前，或者在出口前但不是or语句集中的最后一个and，如果为0跳转到出口处
                        addIRCode(new IRCode(IROperator.BEQ, "#t" + eqReg, 0, outLabel));
                    } else {
                        //在出口前，且是or语句集中的最后一个and，如果为1跳转到入口处
                        addIRCode(new IRCode(IROperator.BNE, "#t" + eqReg, 0, inLabel));
                    }
                } else {
                    if (isLastOfOr) {
                        //如果有下一个or，且是or语句集中的最后一个and，如果为1跳转到入口
                        addIRCode(new IRCode(IROperator.BNE, "#t" + eqReg, 0, inLabel));
                    } else {
                        //如果有下一个or，且不是or语句集中的最后一个and，如果为0跳转到下一个or
                        addIRCode(new IRCode(IROperator.BEQ, "#t" + eqReg, 0, "or" + orSum));
                    }
                }
            }
        } else {
            if (!optimize) {
                genLAndExp(children.get(0), false, false, outLabel, inLabel, nearOut);
                addIRCode(new IRCode(IROperator.LABEL, null, null, "and" + andSum++));
                addIRCode(new IRCode(IROperator.BEQ, "#t" + condAndReg, 0, "or" + orSum, true));
                genEqExp(children.get(2));
                int rightReg = tempReg - 1;
                addIRCode(new IRCode(IROperator.AND, "#t" + condAndReg, "#t" + rightReg, "#t" + condAndReg));
            } else {
                genLAndExp(children.get(0), false, false, outLabel, inLabel, nearOut);
                addIRCode(new IRCode(IROperator.LABEL, null, null, "and" + andSum++));
                genEqExp(children.get(2));
                int rightReg = tempReg - 1;
                if (!hasNextOr) {
                    //没有下一个or
                    if (!nearOut || !isLastOfOr) {
                        //不在出口前，或者在出口前但不是or语句集中的最后一个and，如果为0跳转到出口处
                        addIRCode(new IRCode(IROperator.BEQ, "#t" + rightReg, 0, outLabel));
                    } else {
                        //在出口前，且是or语句集中的最后一个and，如果为1跳转到入口处
                        addIRCode(new IRCode(IROperator.BNE, "#t" + rightReg, 0, inLabel));
                    }
                } else {
                    if (isLastOfOr) {
                        //如果有下一个or，且是or语句集中的最后一个and，如果为1跳转到入口
                        addIRCode(new IRCode(IROperator.BNE, "#t" + rightReg, 0, inLabel));
                    } else {
                        //如果有下一个or，且不是or语句集中的最后一个and，如果为0跳转到下一个or
                        addIRCode(new IRCode(IROperator.BEQ, "#t" + rightReg, 0, "or" + orSum));
                    }
                }
            }
        }
    }

    public void genEqExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("RelExp")) {
            genRelExp(children.get(0));
        } else {
            genEqExp(children.get(0));
            int leftReg = tempReg - 1;
            genRelExp(children.get(2));
            int rightReg = tempReg - 1;
            if (children.get(1).isName("==")) {
                addIRCode(new IRCode(IROperator.SEQ, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            } else {
                addIRCode(new IRCode(IROperator.SNE, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            }
        }
    }

    public void genRelExp(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        if (children.get(0).isName("AddExp")) {
            genAddExp(children.get(0));
        } else {
            genRelExp(children.get(0));
            int leftReg = tempReg - 1;
            genAddExp(children.get(2));
            int rightReg = tempReg - 1;
            String resultIdent;

            if (children.get(1).isName("<")) {
                addIRCode(new IRCode(IROperator.SLT, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            } else if (children.get(1).isName("<=")) {
                addIRCode(new IRCode(IROperator.SLE, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            } else if (children.get(1).isName(">")) {
                addIRCode(new IRCode(IROperator.SGT, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            } else {
                addIRCode(new IRCode(IROperator.SGE, "#t" + leftReg, "#t" + rightReg, "#t" + tempReg++));
            }
        }
    }

    public void genForStmt(GrammarNode node) {
        ArrayList<GrammarNode> children = node.getChildren();
        genLVal(children.get(0), true);
        int indexReg = tempReg - 1;
        String LValName = children.get(0).getChild(0).getChild(0).getNodeName();
        TableItem LValItem = getLValItem(LValName);
        genExp(children.get(2));
        int expReg = tempReg - 1;
        if (LValItem.getDimension() == 0) {
            addIRCode(new IRCode(IROperator.ASSIGN, "#t" + expReg, null, LValName));
        } else {
            addIRCode(new IRCode(IROperator.ASSIGN, "#t" + expReg, "#t" + indexReg, LValName));
        }
    }

    public void print() {
        if (!print) {
            return;
        }
        try {
            for (IRCode irCode : irCodes) {
                out.write(irCode.print() + "\n");
            }
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addLevel() {
        level++;
        tableStack.put(level, new Table());
        curTable = tableStack.get(level);
    }

    public void deleteLevel() {
        tableStack.remove(level);
        level--;
        if (level >= 0) {
            curTable = tableStack.get(level);
        } else {
            curTable = null;
        }
    }

    public TableItem getLValItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "var")) {
                return tableStack.get(i).getItem(name, "var");
            } else if (tableStack.get(i).hasItem(name, "const")) {
                return tableStack.get(i).getItem(name, "const");
            } else if (tableStack.get(i).hasItem(name, "param")) {
                return tableStack.get(i).getItem(name, "param");
            }
        }
        return null;
    }

    public TableItem getFuncItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "func")) {
                return tableStack.get(i).getItem(name, "func");
            }
        }
        return null;
    }

    public ArrayList<IRCode> getIRList() {
        return irCodes;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }
}
