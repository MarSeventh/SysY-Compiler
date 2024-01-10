import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class IROptimizer {
    private ArrayList<IRCode> originIRList = new ArrayList<>();
    private ArrayList<IRCode> optimizedIRList = new ArrayList<>();
    private final HashMap<Integer, Table> tableStack = new HashMap<>();
    private final BufferedWriter out;
    private int level = 0;
    private Table curTable = new Table();
    //备份符号表
    private final HashMap<Integer, Table> tableStackBackup = new HashMap<>();
    private int levelBackup = 0;
    private Table curTableBackup = new Table();

    private boolean optimize = true;

    //全局寄存器池
    private HashMap<String, Boolean> globalRegisterPool = new HashMap<>();
    private HashMap<String, Integer> globalRegisterUseCount = new HashMap<>();
    private HashMap<String, Boolean> tempRegisterPool = new HashMap<>();
    private HashMap<String, Integer> tempRegisterUseCount = new HashMap<>();//每个基本块内部的临时寄存器使用次数

    public IROptimizer(ArrayList<IRCode> originIRList) {
        try {
            out = new BufferedWriter(new FileWriter("optimizedIRCodes.txt"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.originIRList = originIRList;
        this.optimizedIRList = originIRList;
        //初始化全局寄存器池
        for (int i = 0; i <= 7; i++) {
            globalRegisterPool.put("$s" + i, false);//false代表空闲
            globalRegisterUseCount.put("$s" + i, 0);//初始化使用次数为0
        }
        //初始化临时寄存器池
        for (int i = 6; i <= 9; i++) {
            tempRegisterPool.put("$t" + i, false);//false代表空闲
            tempRegisterUseCount.put("$t" + i, 0);//初始化使用次数为0
        }
        initTable();
    }

    public void optimize() {
        if (!optimize) {
            return;
        }
        boolean unFinished = true;
        while (unFinished) {
            deleteUnUsedTempVar();//消除无用中间变量
            optimizeFunc();//消除无用函数
            constSpread();//常量传播

            boolean unFinished1 = deleteUnusedDef();//删除无用的def语句以及对应的赋值语句
            boolean unFinished2 = optimizeCalculate();//优化计算
            //boolean unFinished3 = dagOptimize();//公共子表达式删除
            //unFinished = unFinished1 || unFinished2 || unFinished3;
            unFinished = unFinished1 || unFinished2;
        }

        //分配全局和临时寄存器
        optimizeGlobalAndTempReg();
        //最后划分一次基本块，将中间代码和基本块的对应关系确定下来
        bindBlockAndIr();
        //打印优化后的中间代码
        print();
    }

    public boolean dagOptimize() {
        boolean unFinished = false;
        initTable();
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                splitBasicBlocks(blockMap, i);//划分基本块

                backupTable();//备份符号表
                setDefUseVarList(blockMap, i);//设置活跃变量Use Def集合
                rollBackTable();//恢复符号表
                setInOutActiveVarList(blockMap);//迭代求解活跃变量in、out集合

                backupTable();//备份符号表
                HashMap<String, DefPoint> defPoints = new HashMap<>();
                setDefPoints(defPoints, blockMap, i);//设置定义点，即gen集合
                rollBackTable();//恢复符号表

                ArrayList<TableItem> allGlobalVarList = new ArrayList<>();//统计函数体内所有的跨基本块活跃变量
                for (BasicBlock block : blockMap.values()) {
                    ArrayList<TableItem> inActiveVarList = block.getInActiveVarList();
                    for (TableItem item : inActiveVarList) {
                        if (!allGlobalVarList.contains(item)) {
                            allGlobalVarList.add(item);
                        }
                    }
                }

                //分析每个基本块内部的公共子表达式
                backupTable();
                unFinished = dagAnalyse(blockMap, defPoints, allGlobalVarList, i);
                rollBackTable();
                if (unFinished) {
                    break;
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            }
        }
        return unFinished;
    }

    public boolean dagAnalyse(HashMap<String, BasicBlock> blockMap, HashMap<String, DefPoint> defPoints,
                              ArrayList<TableItem> globalItems, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            BasicBlock curBlock = getBlockByIRNum(blockMap, i);
            if (curBlock == null) {
                break;
            }
            int blockEnd = curBlock.getEnd();
            DAGMap dagMap = new DAGMap();
            for (int j = i; j <= blockEnd; j++) {
                IRCode irCode = optimizedIRList.get(j);
                IROperator operator = irCode.getOperator();
                if (operator == IROperator.DEF) {
                    curTable.addItem(irCode.getDefItem());
                } else if (operator == IROperator.block_begin) {
                    addLevel();
                } else if (operator == IROperator.block_end) {
                    deleteLevel();
                } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                    break;
                } else {
                    //ADD, SUB, MUL, DIV, MOD,
                    //AND, OR, NOT,
                    //SLT, SLE, SEQ, SNE, SGE, SGT,
                    //GETINT, ARRAY_GET, ASSIGN,
                    //顺序无关：ADD, MUL, AND, OR, SEQ, SNE 不予考虑：ARRAY_GET
                    if (operator == IROperator.ADD || operator == IROperator.MUL || operator == IROperator.AND
                            || operator == IROperator.OR || operator == IROperator.SEQ || operator == IROperator.SNE) {
                        //顺序无关的三元运算符
                        addCalculateToDAG(irCode, dagMap, true);
                    } else if (operator == IROperator.SUB || operator == IROperator.DIV || operator == IROperator.MOD ||
                            operator == IROperator.NOT || operator == IROperator.SLT || operator == IROperator.SLE
                            || operator == IROperator.SGE || operator == IROperator.SGT) {
                        //顺序有关的三元运算符
                        addCalculateToDAG(irCode, dagMap, false);
                    } else if (operator == IROperator.GETINT || operator == IROperator.ARRAY_GET) {
                        //GETINT和ARRAY_GET因为结果具有不确定性，因此将DAG图中所有该局部变量的信息抹除
                        TableItem resultItem = getVarConstItem(irCode.getResultIdent());
                        if (resultItem != null) {
                            dagMap.removeIdentMap(resultItem);//从标识符表中删除，代表清除信息
                        }
                    } else if (operator == IROperator.ASSIGN) {
                        //ASSIGN语句，分情况讨论
                        String resultName = irCode.getResultIdent();
                        String srcName = irCode.getOpIdent1();
                        if (resultName != null && srcName != null) {
                            if (resultName.startsWith("#")) {
                                //目标是临时变量
                                if (srcName.startsWith("#")) {
                                    //源也是临时变量
                                    DAGNode srcNode = dagMap.getTempNode(srcName);
                                    if (srcNode != null) {
                                        srcNode.addEqualTemp(resultName);
                                        dagMap.setTempMap(resultName, srcNode);
                                    }
                                    //srcNode要是null就不管了
                                } else {
                                    //源是标识符
                                    TableItem srcItem = getPartialVar(srcName);
                                    if (srcItem != null) {
                                        DAGNode srcNode = dagMap.getIdentNode(srcItem);
                                        if (srcNode != null) {
                                            //找到了这个标识符对应的DAGNode
                                            srcNode.addEqualTemp(resultName);
                                            dagMap.setTempMap(resultName, srcNode);
                                        } else {
                                            //没找到，新建该标识符的叶子结点
                                            DAGNode newNode = new DAGNode(srcItem);
                                            dagMap.addLeafNode(newNode);
                                            dagMap.setIdentMap(srcItem, newNode);
                                            newNode.addEqualTemp(resultName);
                                            dagMap.setTempMap(resultName, newNode);
                                        }
                                    }
                                }
                            } else {
                                //目标是标识符
                                TableItem resultItem = getPartialVar(resultName);
                                if (resultItem == null) {
                                    continue;
                                }
                                if (srcName.startsWith("#")) {
                                    //源是临时变量
                                    DAGNode srcNode = dagMap.getTempNode(srcName);
                                    if (srcNode != null) {
                                        //可以准备替换了
                                        dagMap.setIdentMap(resultItem, srcNode);
                                        TableItem sameNodeIdent = dagMap.getSameNodeIdent(srcNode, resultItem);
                                        srcNode.addEqualIdent(resultItem);
                                        if (sameNodeIdent != null) {
                                            //找到了等价的标识符，进行公共子表达式删除操作
                                            if (!globalItems.contains(resultItem) && nextDefPointInCurBlock(j, blockEnd, sameNodeIdent, defPoints) == null) {
                                                //待替换变量resultItem是跨基本块不活跃变量，且该基本块内后续没有对替换变量sameNodeIdent的重定义，可以全部替换，同时删除该定义
                                                for (int k = j; k <= blockEnd; k++) {
                                                    IRCode irCode1 = optimizedIRList.get(k);
                                                    if (irCode1.getOpIdent1() != null && irCode1.getOpIdent1().equals(resultName)) {
                                                        irCode1.setOpIdent1(sameNodeIdent.getName());
                                                        unFinished = true;
                                                    }
                                                    if (irCode1.getOpIdent2() != null && irCode1.getOpIdent2().equals(resultName)) {
                                                        irCode1.setOpIdent2(sameNodeIdent.getName());
                                                        unFinished = true;
                                                    }
                                                }
                                                optimizedIRList.get(j).setDead();
                                            } else {
                                                //跨基本块活跃变量，或者后面有对替换变量的重定义，替换下一次定义点之前的所有使用点，同时将本条语句换成一个赋值语句（A=B）
                                                IRCode irCode1 = nextDefPointInCurBlock(j, blockEnd, sameNodeIdent, defPoints);
                                                for (int k = j; k <= blockEnd; k++) {
                                                    IRCode irCode2 = optimizedIRList.get(k);
                                                    if (irCode2.getOpIdent1() != null && irCode2.getOpIdent1().equals(resultName)) {
                                                        irCode2.setOpIdent1(sameNodeIdent.getName());
                                                        unFinished = true;
                                                    }
                                                    if (irCode2.getOpIdent2() != null && irCode2.getOpIdent2().equals(resultName)) {
                                                        irCode2.setOpIdent2(sameNodeIdent.getName());
                                                        unFinished = true;
                                                    }
                                                    if (irCode2 == irCode1) {
                                                        break;
                                                    }
                                                }
                                                int tempRegNum = getMaxTempRegNum() + 1;
                                                IRCode newIrCode = new IRCode(IROperator.ASSIGN, sameNodeIdent.getName(), null, "#t" + tempRegNum);
                                                IRCode newIrCode1 = new IRCode(IROperator.ASSIGN, "#t" + tempRegNum, null, resultName);
                                                optimizedIRList.set(j, newIrCode);
                                                optimizedIRList.add(j + 1, newIrCode1);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            i = blockEnd;
            if (unFinished) {
                break;
            }
        }
        boolean unFinished1 = killDeadIrCode();
        unFinished = unFinished || unFinished1;
        return unFinished;
    }

    public IRCode nextDefPointInCurBlock(int start, int blockEnd, TableItem item, HashMap<String, DefPoint> defPoints) {
        for (int i = start; i <= blockEnd; i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            for (DefPoint defPoint : defPoints.values()) {
                if (defPoint.getDefItem() == item && defPoint.getIrCode() == irCode) {
                    return irCode;
                }
            }
        }
        return null;
    }

    public void addCalculateToDAG(IRCode irCode, DAGMap dagMap, boolean canReverse) {
        IROperator operator = irCode.getOperator();
        DAGNode node1 = null;
        DAGNode node2 = null;
        DAGNode opNode = null;
        if (irCode.op1IsNum()) {
            if (dagMap.getNumberLeafNode(irCode.getOpNum1()) == null) {
                node1 = new DAGNode(irCode.getOpNum1());
                dagMap.addLeafNode(node1);
            } else {
                node1 = dagMap.getNumberLeafNode(irCode.getOpNum1());
            }
        } else {
            String opIdent1 = irCode.getOpIdent1();//中间变量
            node1 = dagMap.getTempNode(opIdent1);
            if (node1 == null) {
                //中间变量都不在DAG图中，还怎么玩？
                return;
            }
        }
        if (irCode.op2IsNum()) {
            if (dagMap.getNumberLeafNode(irCode.getOpNum2()) == null) {
                node2 = new DAGNode(irCode.getOpNum2());
                dagMap.addLeafNode(node2);
            } else {
                node2 = dagMap.getNumberLeafNode(irCode.getOpNum2());
            }
        } else {
            String opIdent2 = irCode.getOpIdent2();//中间变量
            node2 = dagMap.getTempNode(opIdent2);
            if (node2 == null) {
                //中间变量都不在DAG图中，还怎么玩？
                return;
            }
        }
        if (node1 != null && node2 != null) {
            if (dagMap.getInnerNode(operator, node1, node2) != null) {
                opNode = dagMap.getInnerNode(operator, node1, node2);
                //找到同样的节点了，插入当前的result就行，这些运算符的result都是中间变量
                String resultIdent = irCode.getResultIdent();
                opNode.addEqualTemp(resultIdent);
                dagMap.setTempMap(resultIdent, opNode);
            } else if (canReverse && dagMap.getInnerNode(operator, node2, node1) != null) {
                opNode = dagMap.getInnerNode(operator, node2, node1);
                //同上
                String resultIdent = irCode.getResultIdent();
                opNode.addEqualTemp(resultIdent);
                dagMap.setTempMap(resultIdent, opNode);
            } else {
                //不存在这个操作符，新建一个
                opNode = new DAGNode(operator);
                dagMap.addNode(opNode);//加入DAG图
                dagMap.setTempMap(irCode.getResultIdent(), opNode);//加入中间变量表
                opNode.setLeftChild(node1);
                opNode.setRightChild(node2);
                node1.addParent(opNode);
                node2.addParent(opNode);
            }
        }
    }

    public void bindBlockAndIr() {
        HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                splitBasicBlocks(blockMap, i);//划分基本块
                //确定基本块和IR的对应关系
                for (BasicBlock block : blockMap.values()) {
                    for (int j = block.getStart(); j <= block.getEnd(); j++) {
                        optimizedIRList.get(j).setBasicBlock(block);
                        if (j == block.getStart()) {
                            optimizedIRList.get(j).setBasicBlockBegin();
                        }
                        if (j == block.getEnd()) {
                            optimizedIRList.get(j).setBasicBlockEnd();
                        }
                    }
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            }
        }
    }

    public void optimizeGlobalAndTempReg() {
        initTable();
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                splitBasicBlocks(blockMap, i);//划分基本块

                backupTable();//备份符号表
                setDefUseVarList(blockMap, i);//设置活跃变量Use Def集合
                rollBackTable();//恢复符号表
                setInOutActiveVarList(blockMap);//迭代求解活跃变量in、out集合

                //构造冲突图
                ConflictGraph conflictGraph = new ConflictGraph();
                ArrayList<TableItem> allGlobalVarList = new ArrayList<>();//统计函数体内所有的全局变量，为了后面给局部变量分配寄存器做准备
                for (BasicBlock block : blockMap.values()) {
                    ArrayList<TableItem> inActiveVarList = block.getInActiveVarList();
                    conflictGraph.addConflict(inActiveVarList);
                    for (TableItem item : inActiveVarList) {
                        if (!allGlobalVarList.contains(item)) {
                            allGlobalVarList.add(item);
                        }
                    }
                }
                //为全局变量分配寄存器
                conflictGraph.initAllocateStack(8);
                TableItem node = conflictGraph.getOneNode();
                while (node != null) {
                    boolean success = allocateGlobalReg(node);
                    if (!success) {
                        break;
                    } /*else {
                        System.out.println("分配全局寄存器成功：" + irCode.print() + " " + node.getName() + " " + node.getGlobalRegName());
                    }*/
                    node = conflictGraph.getOneNode();
                }
                //为局部变量分配寄存器
                optimizeTempReg(blockMap, allGlobalVarList);
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
                freeGlobalReg();
            }
        }
    }

    public void optimizeTempReg(HashMap<String, BasicBlock> blockMap, ArrayList<TableItem> allGlobalVarList) {
        for (BasicBlock block : blockMap.values()) {
            //得到所有定义先于使用的变量（因为局部变量不可能会出现使用先于定义）
            ArrayList<TableItem> defVarList = block.getDefVarList();
            for (TableItem varItem : defVarList) {
                if (allGlobalVarList.contains(varItem) || varItem.hasTempReg()) {
                    //全局变量或者已经分配了临时寄存器的变量不考虑
                    continue;
                }
                boolean success = allocateTempReg(varItem);
                if (!success) {
                    break;
                } /*else {
                    System.out.println("分配临时寄存器成功：" + varItem.getName() + " " + varItem.getTempRegName());
                }*/
            }
            //离开基本块，释放临时寄存器池
            freeTempReg();
        }
    }

    public boolean optimizeCalculate() {
        boolean unFinished = true;
        for (IRCode irCode : optimizedIRList) {
            IROperator operator = irCode.getOperator();
            boolean canDelete = false;
            if (irCode.getResultIdent() != null && irCode.getResultIdent().startsWith("#")) {
                if (irCode.op1IsNum() && irCode.getOpIdent2() != null && irCode.getOpIdent2().startsWith("#")) {
                    int opNum = irCode.getOpNum1();
                    String opIdent = irCode.getOpIdent2();
                    String resultIdent = irCode.getResultIdent();
                    if (opNum == 1) {
                        canDelete = calculateSpread1(operator, opIdent, resultIdent, true);
                    } else if (opNum == 0) {
                        canDelete = calculateSpread0(operator, opIdent, resultIdent, true);
                    }
                } else if (irCode.op2IsNum() && irCode.getOpIdent1() != null && irCode.getOpIdent1().startsWith("#")) {
                    int opNum = irCode.getOpNum2();
                    String opIdent = irCode.getOpIdent1();
                    String resultIdent = irCode.getResultIdent();
                    if (opNum == 1) {
                        canDelete = calculateSpread1(operator, opIdent, resultIdent, false);
                    } else if (opNum == 0) {
                        canDelete = calculateSpread0(operator, opIdent, resultIdent, false);
                    }
                }
            }
            if (canDelete) {
                irCode.setDead();
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }

    //ADD, SUB, MUL, DIV, MOD,
    //AND, OR,
    public boolean calculateSpread0(IROperator operator, String opIdent, String resultIdent, boolean isOp1Num) {
        boolean canSubstitute = false;
        String substituteIdent = null;
        int substituteNum = 0;
        boolean substituteIsNum = false;
        boolean finish = false;
        switch (operator) {
            case ADD:
                substituteIdent = opIdent;
                canSubstitute = true;
                break;
            case SUB:
                if (!isOp1Num) {
                    //只有op2是数字才能替换
                    substituteIdent = opIdent;
                    canSubstitute = true;
                }
                break;
            case MUL, AND:
                canSubstitute = true;
                substituteIsNum = true;
                break;
            case DIV, MOD:
                if (isOp1Num) {
                    //只有被除数是0可以替换
                    substituteIsNum = true;
                    canSubstitute = true;
                }
                break;
            default:
                break;
        }
        return substituteCalculate(resultIdent, canSubstitute, substituteIdent, substituteNum, substituteIsNum);
    }

    public boolean calculateSpread1(IROperator operator, String opIdent, String resultIdent, boolean isOp1Num) {
        boolean canSubstitute = false;
        String substituteIdent = null;
        int substituteNum = 0;
        boolean substituteIsNum = false;
        boolean finish = false;
        switch (operator) {
            case MUL:
                substituteIdent = opIdent;
                canSubstitute = true;
                break;
            case DIV:
                if (!isOp1Num) {
                    //只有op2是1才能替换
                    substituteIdent = opIdent;
                    canSubstitute = true;
                }
                break;
            case MOD:
                if (!isOp1Num) {
                    //只有op2是1才能替换
                    substituteNum = 0;
                    substituteIsNum = true;
                    canSubstitute = true;
                }
                break;
            case OR:
                substituteNum = 1;
                substituteIsNum = true;
                canSubstitute = true;
                break;
            default:
                break;
        }
        return substituteCalculate(resultIdent, canSubstitute, substituteIdent, substituteNum, substituteIsNum);
    }

    private boolean substituteCalculate(String resultIdent, boolean canSubstitute, String substituteIdent, int substituteNum, boolean substituteIsNum) {
        boolean finish;
        if (canSubstitute) {
            for (IRCode irCode : optimizedIRList) {
                if (irCode.getOpIdent1() != null && irCode.getOpIdent1().equals(resultIdent)) {
                    if (substituteIsNum) {
                        irCode.setOpNum1(substituteNum);
                        irCode.setOpIdent1(null);
                    } else {
                        irCode.setOpIdent1(substituteIdent);
                    }
                }
                if (irCode.getOpIdent2() != null && irCode.getOpIdent2().equals(resultIdent)) {
                    if (substituteIsNum) {
                        irCode.setOpNum2(substituteNum);
                        irCode.setOpIdent2(null);
                    } else {
                        irCode.setOpIdent2(substituteIdent);
                    }
                }
            }
        }
        finish = canSubstitute;
        return finish;
    }

    public boolean deleteUnusedDef() {
        //删除没有用的def语句以及对应的赋值语句
        boolean unFinished = false;
        initTable();
        HashMap<TableItem, IRCode> defMap = new HashMap<>();
        //HashMap<String, ArrayList<TableItem>> funcParasMap = new HashMap<>();//保存函数和参数的对应关系，方便删除无用参数的入栈语句
        //String nowFuncName = null;
        //先找到没用的def
        for (IRCode irCode : optimizedIRList) {
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
                if (!irCode.getDefItem().getKind().equals("param")) {
                    //参数先不考虑
                    irCode.setDead();//先默认没有用
                }
                defMap.put(irCode.getDefItem(), irCode);
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
                //nowFuncName = irCode.getFuncItem().getName();
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            } else {
                if (irCode.getOpIdent1() != null && defMap.containsKey(getVarConstItem(irCode.getOpIdent1()))) {
                    defMap.get(getVarConstItem(irCode.getOpIdent1())).setLive();
                }
                if (irCode.getOpIdent2() != null && defMap.containsKey(getVarConstItem(irCode.getOpIdent2()))) {
                    defMap.get(getVarConstItem(irCode.getOpIdent2())).setLive();
                }
                //特殊情况，参数数组，赋值也算使用
                /*if (irCode.getResultIdent() != null && defMap.containsKey(getVarConstItem(irCode.getResultIdent()))) {
                    TableItem resultItem = getVarConstItem(irCode.getResultIdent());
                    if (resultItem.isArray() && resultItem.getKind().equals("param")) {
                        defMap.get(getVarConstItem(irCode.getResultIdent())).setLive();
                    }
                }*/
            }
        }
        //删除没用的def对应的赋值语句
        initTable();
        for (IRCode irCode : optimizedIRList) {
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                addLevel();
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                deleteLevel();
            } else {
                if (irCode.getResultIdent() != null && defMap.containsKey(getVarConstItem(irCode.getResultIdent()))) {
                    if (defMap.get(getVarConstItem(irCode.getResultIdent())).isDead()) {
                        irCode.setDead();
                    }
                }
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }

    public TableItem getVarConstItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasSameName(name)) {
                return tableStack.get(i).getItemByName(name);
            }
        }
        return null;
    }

    public void constSpread() {
        boolean unFinished = true;
        while (unFinished) {
            initTable();
            TableItem funcItem = null;
            for (int i = 0; i < optimizedIRList.size(); i++) {
                IRCode irCode = optimizedIRList.get(i);
                IROperator operator = irCode.getOperator();
                if (operator == IROperator.DEF) {
                    curTable.addItem(irCode.getDefItem());
                } else if (operator == IROperator.func_begin || operator == IROperator.main_begin) {
                    HashMap<String, BasicBlock> blockMap = new HashMap<>();//初始化基本块
                    HashMap<String, DefPoint> defPoints = new HashMap<>();//初始化定义点列表
                    funcItem = irCode.getFuncItem();
                    curTable.addItem(funcItem);
                    addLevel();
                    splitBasicBlocks(blockMap, i);//划分基本块

                    backupTable();//备份符号表
                    setDefPoints(defPoints, blockMap, i);//设置定义点，即gen集合
                    rollBackTable();//恢复符号表

                    setKillPoints(defPoints, blockMap);//设置kill集合

                    setInOutDefPoints(blockMap);//迭代求解in、out集合

                    backupTable();//备份符号表
                    unFinished = constSpreadInBlock(defPoints, blockMap, i);//常量传播优化
                    rollBackTable();//恢复符号表

                    //进行块内无用定义点删除，因为基本块的划分可能发生改变，因此需要重新划分基本块并求解gen、kill、in、out集合
                    blockMap.clear();
                    defPoints.clear();
                    splitBasicBlocks(blockMap, i);
                    backupTable();//备份符号表
                    setDefPoints(defPoints, blockMap, i);//设置定义点，即gen集合
                    rollBackTable();//恢复符号表
                    setKillPoints(defPoints, blockMap);//设置kill集合
                    setInOutDefPoints(blockMap);//迭代求解in、out集合

                    backupTable();//备份符号表
                    setDefUseVarList(blockMap, i);//设置活跃变量Use Def集合
                    rollBackTable();//恢复符号表
                    setInOutActiveVarList(blockMap);//迭代求解活跃变量in、out集合

                    boolean unFinished2 = deleteDeadDef(defPoints, blockMap, i);//删除无用定义点

                    unFinished = unFinished || unFinished2;
                    if (unFinished) {
                        break;
                    }
                } else if (operator == IROperator.block_begin) {
                    addLevel();
                } else if (operator == IROperator.block_end) {
                    deleteLevel();
                } else if (operator == IROperator.func_end || operator == IROperator.main_end) {
                    deleteLevel();
                }
            }
        }
    }

    public boolean deleteDeadDef(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            boolean isDefPoint = isInPointMap(defPoints, irCode);
            if (isDefPoint) {
                DefPoint defPoint = getDefPointByIRCode(defPoints, irCode);
                TableItem defItem = defPoint.getDefItem();
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                int blockEnd = curBlock.getEnd();
                boolean isDeadDef = true;
                int nextDefPointNum = -1;
                if (irCode.getOperator() == IROperator.DEF && irCode.getDefItem().getKind().equals("param")) {
                    //参数豁免
                    isDeadDef = false;
                }
                for (int j = i + 1; j <= blockEnd; j++) {
                    //寻找块内该变量的下一个定义点
                    if (getSameItemDefPoint(optimizedIRList.get(j), defPoints, defItem) != null) {
                        nextDefPointNum = j;
                        break;
                    }
                }
                if (nextDefPointNum == -1) {
                    //块内该变量没有下一个定义点，判断该定义点的值是否在该块内使用
                    isDeadDef = isDeadDef(i, defItem, blockEnd, isDeadDef);
                    //判断是否在活跃变量的out集合中
                    if (curBlock.getOutActiveVarList().contains(defItem)) {
                        isDeadDef = false;
                    }
                } else {
                    //块内该变量有下一个定义点，判断该定义点的值是否被使用
                    isDeadDef = isDeadDef(i, defItem, nextDefPointNum, isDeadDef);
                }
                if (isDeadDef) {
                    irCode.setDead();
                }
            }
        }
        unFinished = killDeadIrCode();
        return unFinished;
    }

    private boolean isDeadDef(int i, TableItem defItem, int blockEnd, boolean isDeadDef) {
        for (int j = i + 1; j <= blockEnd; j++) {
            if (optimizedIRList.get(j).getOpIdent1() != null && optimizedIRList.get(j).getOpIdent1().equals(defItem.getName())) {
                isDeadDef = false;
                break;
            }
            if (optimizedIRList.get(j).getOpIdent2() != null && optimizedIRList.get(j).getOpIdent2().equals(defItem.getName())) {
                isDeadDef = false;
                break;
            }
        }
        return isDeadDef;
    }

    public void setDefUseVarList(HashMap<String, BasicBlock> blockMap, int start) {
        //活跃变量分析
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                //参数较特殊
                if (irCode.getDefItem().getKind().equals("param")) {
                    TableItem defItem = getPartialVar(irCode.getDefItem().getName());
                    if (curBlock != null && defItem != null && !curBlock.hasDefVar(defItem) && !curBlock.hasUseVar(defItem)) {
                        //定义先于使用
                        curBlock.addDefVar(irCode.getDefItem());
                    }
                }
            } else {
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                if (irCode.getOpIdent1() != null) {
                    TableItem opItem = getPartialVar(irCode.getOpIdent1());
                    if (opItem != null && !curBlock.hasDefVar(opItem) && !curBlock.hasUseVar(opItem)) {
                        curBlock.addUseVar(opItem);
                    }
                }
                if (irCode.getOpIdent2() != null) {
                    TableItem opItem = getPartialVar(irCode.getOpIdent2());
                    if (opItem != null && !curBlock.hasDefVar(opItem) && !curBlock.hasUseVar(opItem)) {
                        curBlock.addUseVar(opItem);
                    }
                }
                if (irCode.getResultIdent() != null) {
                    TableItem opItem = getPartialVar(irCode.getResultIdent());
                    if (opItem != null && !curBlock.hasDefVar(opItem) && !curBlock.hasUseVar(opItem)) {
                        curBlock.addDefVar(opItem);
                    }
                }
            }
        }
    }

    public void setInOutActiveVarList(HashMap<String, BasicBlock> blockMap) {
        boolean unFinished = true;
        while (unFinished) {
            unFinished = false;
            for (BasicBlock curBlock : blockMap.values()) {
                ArrayList<BasicBlock> nextBlocks = curBlock.getNextBlocks();
                ArrayList<TableItem> inActiveVarList = curBlock.getInActiveVarList();
                ArrayList<TableItem> outActiveVarList = curBlock.getOutActiveVarList();
                //求解活跃变量的out集合
                for (BasicBlock nextBlock : nextBlocks) {
                    for (TableItem outActiveVar : nextBlock.getInActiveVarList()) {
                        if (!outActiveVarList.contains(outActiveVar)) {
                            outActiveVarList.add(outActiveVar);
                            unFinished = true;
                        }
                    }
                }
                //求解out - def
                ArrayList<TableItem> outSubDefVarList = new ArrayList<>(outActiveVarList);
                outSubDefVarList.removeAll(curBlock.getDefVarList());
                //求解in：use 并 (out - def)
                ArrayList<TableItem> useVarList = curBlock.getUseVarList();
                for (TableItem useVar : useVarList) {
                    if (!inActiveVarList.contains(useVar)) {
                        inActiveVarList.add(useVar);
                        unFinished = true;
                    }
                }
                for (TableItem outSubDefVar : outSubDefVarList) {
                    if (!inActiveVarList.contains(outSubDefVar)) {
                        inActiveVarList.add(outSubDefVar);
                        unFinished = true;
                    }
                }
            }
        }
    }

    public boolean killDeadIrCode() {
        boolean unFinished = false;
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            if (irCode.isDead()) {
                unFinished = true;
                optimizedIRList.remove(i);
                i--;
            }
        }
        return unFinished;
    }

    public boolean isInPointMap(HashMap<String, DefPoint> defPoints, IRCode irCode) {
        for (DefPoint defPoint : defPoints.values()) {
            if (defPoint.getIrCode() == irCode) {
                return true;
            }
        }
        return false;
    }

    public boolean constSpreadInBlock(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        boolean unFinished = false;
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (getMayBeConstItem(irCode) != null) {
                TableItem mayBeConstItem = getMayBeConstItem(irCode);
                //System.out.println(mayBeConstItem.getName() + " " + i + " " + irCode.print());
                boolean isConst = false;//确实是常数
                int constNum = 0;

                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                int blockStart = curBlock.getStart();
                int blockEnd = curBlock.getEnd();
                //首先寻找离当前语句最近的块内定义点
                DefPoint defPointInCurBlock = null;
                for (int j = i; j >= blockStart; j--) {
                    defPointInCurBlock = getSameItemDefPoint(optimizedIRList.get(j), defPoints, mayBeConstItem);
                    if (defPointInCurBlock != null) {
                        break;
                    }
                }
                if (defPointInCurBlock != null) {
                    //块内该使用点有定义点
                    IRCode defIRCode = defPointInCurBlock.getIrCode();
                    if (defIRCode.getOperator() == IROperator.ASSIGN && defIRCode.op1IsNum()) {
                        isConst = true;
                        constNum = defIRCode.getOpNum1();
                    }
                } else {
                    //块内该使用点之前无定义点，寻找in中定义点
                    ArrayList<DefPoint> inDefPoints = curBlock.getInDefPoints();
                    HashMap<BasicBlock, DefPoint> inDefPointsMap = new HashMap<>();
                    //寻找in中每个块中最后一个该变量的定义点
                    for (DefPoint defPoint : inDefPoints) {
                        if (defPoint.getDefItem() == mayBeConstItem) {
                            inDefPointsMap.put(defPoint.getBlock(), defPoint);
                        }
                    }
                    //根据每个块中最后一个该变量的定义点判断是否为常数
                    for (DefPoint defPoint : inDefPointsMap.values()) {
                        if (defPoint.getIrCode().getOperator() == IROperator.ASSIGN && defPoint.getIrCode().op1IsNum()) {
                            if (!isConst) {
                                isConst = true;
                                constNum = defPoint.getIrCode().getOpNum1();
                            } else if (constNum != defPoint.getIrCode().getOpNum1()) {
                                isConst = false;
                                break;
                            }
                        } else {
                            isConst = false;
                            break;
                        }
                    }
                }
                if (isConst) {
                    //常量传播
                    irCode.setOpNum1(constNum);
                    String desName = irCode.getResultIdent();
                    if (desName != null && desName.startsWith("#")) {
                        //更新下一个定义点之前所有用到该中间变量的语句
                        for (int j = i; j < blockEnd; j++) {
                            IRCode nextIrCode = optimizedIRList.get(j);
                            if (getSameItemDefPoint(nextIrCode, defPoints, mayBeConstItem) != null) {
                                break;
                            }
                            if (nextIrCode.getOpIdent1() != null && nextIrCode.getOpIdent1().equals(desName)) {
                                unFinished = true;
                                nextIrCode.setOpNum1(constNum);
                                nextIrCode.setOpIdent1(null);
                            }
                            if (nextIrCode.getOpIdent2() != null && nextIrCode.getOpIdent2().equals(desName)) {
                                unFinished = true;
                                nextIrCode.setOpNum2(constNum);
                                nextIrCode.setOpIdent2(null);
                            }
                            if (nextIrCode.getResultIdent() != null && nextIrCode.getResultIdent().equals(desName)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        boolean unFinished2 = deleteUnUsedTempVar();
        boolean unFinished3 = innerConstTempVar();
        unFinished = unFinished || unFinished2 || unFinished3;
        return unFinished;
    }

    public TableItem getMayBeConstItem(IRCode irCode) {
        if (irCode.getOperator() == IROperator.ASSIGN) {
            return getPartialVar(irCode.getOpIdent1());
        }
        return null;
    }

    public DefPoint getDefPointByIRCode(HashMap<String, DefPoint> defPoints, IRCode irCode) {
        for (DefPoint defPoint : defPoints.values()) {
            if (defPoint.getIrCode() == irCode) {
                return defPoint;
            }
        }
        return null;
    }

    public DefPoint getSameItemDefPoint(IRCode irCode, HashMap<String, DefPoint> defPoints, TableItem defItem) {
        for (DefPoint defPoint : defPoints.values()) {
            if (defPoint.getIrCode() == irCode && defPoint.getDefItem() == defItem) {
                return defPoint;
            }
        }
        return null;
    }

    public void setInOutDefPoints(HashMap<String, BasicBlock> blockMap) {
        boolean unFinished = true;
        while (unFinished) {
            unFinished = false;
            for (BasicBlock block : blockMap.values()) {
                //求in集合
                ArrayList<BasicBlock> preBlocks = block.getPreBlocks();
                ArrayList<DefPoint> inDefPoints = block.getInDefPoints();
                ArrayList<DefPoint> outDefPoints = block.getOutDefPoints();
                for (BasicBlock preBlock : preBlocks) {
                    for (DefPoint inPoint : preBlock.getOutDefPoints()) {
                        if (!inDefPoints.contains(inPoint)) {
                            inDefPoints.add(inPoint);
                            unFinished = true;
                        }
                    }
                }
                //求in - kill
                ArrayList<DefPoint> inSubKillPoints = new ArrayList<>(inDefPoints);
                inSubKillPoints.removeAll(block.getKillPoints());
                //求out：gen 并 (in - kill)
                ArrayList<DefPoint> genPoints = block.getGenPoints();
                for (DefPoint genPoint : genPoints) {
                    if (!outDefPoints.contains(genPoint)) {
                        outDefPoints.add(genPoint);
                        unFinished = true;
                    }
                }
                for (DefPoint defPoint : inSubKillPoints) {
                    if (!outDefPoints.contains(defPoint)) {
                        outDefPoints.add(defPoint);
                        unFinished = true;
                    }
                }
            }
        }
    }

    public void setKillPoints(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap) {
        for (String blockName : blockMap.keySet()) {
            BasicBlock curBlock = blockMap.get(blockName);
            ArrayList<DefPoint> curGenPoints = curBlock.getGenPoints();
            for (DefPoint defPoint : curGenPoints) {
                for (DefPoint otherPoint : defPoints.values()) {
                    if (!defPoint.getPointName().equals(otherPoint.getPointName()) && defPoint.getDefItem() == otherPoint.getDefItem()) {
                        curBlock.addKillPoints(otherPoint);
                    }
                }
            }
        }
    }

    public void setDefPoints(HashMap<String, DefPoint> defPoints, HashMap<String, BasicBlock> blockMap, int start) {
        int n = 1;//定义点标号从d1开始
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
                //参数定义比较特殊
                TableItem item = getPartialVar(irCode.getResultIdent());
                if (item != null && item.getKind().equals("param")) {
                    BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                    DefPoint defPoint = new DefPoint("d" + n++, irCode, getPartialVar(irCode.getResultIdent()), curBlock);
                    defPoints.put(defPoint.getPointName(), defPoint);
                    curBlock.addGenPoints(defPoint);
                }
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (isDefOperation(irCode)) {
                BasicBlock curBlock = getBlockByIRNum(blockMap, i);
                DefPoint defPoint = new DefPoint("d" + n++, irCode, getPartialVar(irCode.getResultIdent()), curBlock);
                defPoints.put(defPoint.getPointName(), defPoint);
                curBlock.addGenPoints(defPoint);
            }
        }
    }

    public boolean isDefOperation(IRCode irCode) {
        if (irCode.getOperator() == IROperator.ASSIGN || irCode.getOperator() == IROperator.GETINT) {
            return getPartialVar(irCode.getResultIdent()) != null;
        }
        return false;
    }

    public TableItem getPartialVar(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "var")) {
                TableItem item = tableStack.get(i).getItem(name, "var");
                if (!item.isArray() && !item.isGlobal()) {
                    return item;
                } else {
                    break;
                }
            } else if (tableStack.get(i).hasItem(name, "param")) {
                TableItem item = tableStack.get(i).getItem(name, "param");
                if (!item.isGlobal() && !item.isArray()) {
                    return item;
                } else {
                    break;
                }
            }
        }
        return null;
    }

    public TableItem getVarParamItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "var")) {
                return tableStack.get(i).getItem(name, "var");
            } else if (tableStack.get(i).hasItem(name, "param")) {
                return tableStack.get(i).getItem(name, "param");
            }
        }
        return null;
    }

    public BasicBlock getBlockByIRNum(HashMap<String, BasicBlock> blockMap, int irNum) {
        for (String blockName : blockMap.keySet()) {
            if (irNum >= blockMap.get(blockName).getStart() && irNum <= blockMap.get(blockName).getEnd()) {
                return blockMap.get(blockName);
            }
        }
        return null;
    }

    public void splitBasicBlocks(HashMap<String, BasicBlock> blockMap, int start) {
        int n = 1;//第一个Block为B1
        BasicBlock curBlock = null;
        boolean nextGoto = false;
        //先切分基本块
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                //函数体最后一条语句一定是return???除了void外
                if (curBlock != null) {
                    curBlock.addIRCode(irCode);
                    curBlock.setEnd(i);
                }
                break;
            }
            if (isBlockStartSymbol(irCode) || i == start || nextGoto) {
                BasicBlock preBlock = curBlock;
                if (curBlock != null) {
                    curBlock.setEnd(i - 1);
                }
                nextGoto = false;
                curBlock = new BasicBlock("B" + n++);
                curBlock.setStart(i);
                curBlock.addIRCode(irCode);
                blockMap.put(curBlock.getBlockName(), curBlock);
                if (i > start && optimizedIRList.get(i - 1).getOperator() != IROperator.RET
                        && optimizedIRList.get(i - 1).getOperator() != IROperator.JMP) {
                    preBlock.addNextBlock(curBlock);
                    curBlock.addPreBlock(preBlock);
                }
            } else if (isGoto(operator)) {
                curBlock.addIRCode(irCode);
                curBlock.setEnd(i);
                nextGoto = true;
                String gotoDes = irCode.getResultIdent();
                for (String blockName : blockMap.keySet()) {
                    if (blockMap.get(blockName).hasLabel(gotoDes)) {
                        curBlock.addNextBlock(blockMap.get(blockName));
                        blockMap.get(blockName).addPreBlock(curBlock);
                    }
                }
            } else {
                curBlock.addIRCode(irCode);
            }
        }
        //再确定数据流关系......
        for (int i = start; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.func_end || operator == IROperator.main_end) {
                break;
            }
            curBlock = getBlockByIRNum(blockMap, i);
            if (isGoto(operator)) {
                String gotoDes = irCode.getResultIdent();
                for (String blockName : blockMap.keySet()) {
                    if (blockMap.get(blockName).hasLabel(gotoDes)) {
                        BasicBlock gotoBlock = blockMap.get(blockName);
                        if (!curBlock.getNextBlocks().contains(gotoBlock)) {
                            curBlock.addNextBlock(gotoBlock);
                            gotoBlock.addPreBlock(curBlock);
                        }
                    }
                }
            }
        }
    }

    public boolean isGoto(IROperator operator) {
        //BNE, BEQ, BGE, BGT, BLE, BLT,
        //JMP, RET
        return operator == IROperator.BNE || operator == IROperator.BEQ || operator == IROperator.BGE ||
                operator == IROperator.BGT || operator == IROperator.BLE || operator == IROperator.BLT ||
                operator == IROperator.JMP || operator == IROperator.RET;
    }

    public boolean isBlockStartSymbol(IRCode irCode) {
        return irCode.getOperator() == IROperator.LABEL;
    }

    public void optimizeFunc() {
        initTable();
        TableItem funcItem = null;
        boolean isDeadFunc = true;
        boolean isInFunc = false;
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.DEF) {
                curTable.addItem(irCode.getDefItem());
            } else if (operator == IROperator.func_begin) {
                isDeadFunc = true;
                funcItem = irCode.getFuncItem();
                curTable.addItem(funcItem);
                if (funcItem.getType().equals("int")) {
                    isDeadFunc = false;
                }
                isInFunc = true;
                addLevel();
            } else if (operator == IROperator.block_begin) {
                addLevel();
            } else if (operator == IROperator.block_end) {
                deleteLevel();
            } else if (operator == IROperator.PRINT_STR || operator == IROperator.PRINT_INT) {
                if (isInFunc) {
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.ASSIGN || operator == IROperator.GETINT) {
                String desName = irCode.getResultIdent();
                TableItem desItem = getVarParamItem(desName);
                if (isInFunc && desItem != null &&
                        (desItem.isGlobal() || (desItem.getKind().equals("param") && desItem.isArray()))) {//全局变量或者数组参数
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.CALL) {
                TableItem callFuncItem = irCode.getFuncItem();
                if (funcItem != null && !callFuncItem.getName().equals(funcItem.getName())) {
                    isDeadFunc = false;
                }
            } else if (operator == IROperator.func_end) {
                if (isDeadFunc) {
                    int originSize = optimizedIRList.size();
                    //删除所有调用该函数的语句
                    deleteDeadFunc(funcItem);
                    deleteLevel();
                    //重新运行该方法
                    if (originSize != optimizedIRList.size()) {
                        optimizeFunc();
                        return;
                    }
                }
                isInFunc = false;
                funcItem = null;
            }
        }
    }

    public void deleteDeadFunc(TableItem deadFuncItem) {
        for (int i = 0; i < optimizedIRList.size(); i++) {
            IRCode irCode = optimizedIRList.get(i);
            IROperator operator = irCode.getOperator();
            if (operator == IROperator.CALL) {
                TableItem callFuncItem = irCode.getFuncItem();
                if (callFuncItem.getName().equals(deadFuncItem.getName())) {
                    optimizedIRList.remove(i);
                    i--;
                }
            } else if (operator == IROperator.PUSH) {
                if (irCode.getResultIdent().equals(deadFuncItem.getName())) {
                    optimizedIRList.remove(i);
                    i--;
                }
            }
        }
        deleteUnUsedTempVar();
    }

    public boolean deleteUnUsedTempVar() {
        boolean unFinished = false;
        int originSize = optimizedIRList.size() - 1;
        while (originSize != optimizedIRList.size()) {
            originSize = optimizedIRList.size();
            for (int i = 0; i < optimizedIRList.size(); i++) {
                IRCode irCode = optimizedIRList.get(i);
                String desTempVar = irCode.getResultIdent();
                if (desTempVar == null || !desTempVar.startsWith("#")) {
                    continue;//不是中间变量
                }
                boolean isUsed = false;
                for (int j = i + 1; j < optimizedIRList.size(); j++) {
                    IRCode nextIrCode = optimizedIRList.get(j);
                    if (nextIrCode.getOpIdent1() != null && nextIrCode.getOpIdent1().equals(desTempVar)) {
                        isUsed = true;
                        break;
                    }
                    if (nextIrCode.getOpIdent2() != null && nextIrCode.getOpIdent2().equals(desTempVar)) {
                        isUsed = true;
                        break;
                    }
                }
                if (!isUsed) {
                    unFinished = true;
                    optimizedIRList.remove(i);
                    i--;
                }
            }
        }
        return unFinished;
    }

    public boolean innerConstTempVar() {
        boolean unFinished = true;
        boolean hasChanged = false;
        while (unFinished) {
            unFinished = false;
            for (int i = 0; i < optimizedIRList.size(); i++) {
                IRCode irCode = optimizedIRList.get(i);
                IROperator operator = irCode.getOperator();
                String constTempVar = irCode.getResultIdent();
                int result = 0;
                boolean isConst = false;
                if (constTempVar != null && constTempVar.startsWith("#")) {
                    if (operator == IROperator.ASSIGN && irCode.op1IsNum()) {
                        result = irCode.getOpNum1();
                        isConst = true;
                    } else if (irCode.op1IsNum() && irCode.op2IsNum()) {
                        isConst = true;
                        switch (operator) {
                            case ADD:
                                result = irCode.getOpNum1() + irCode.getOpNum2();
                                break;
                            case SUB:
                                result = irCode.getOpNum1() - irCode.getOpNum2();
                                break;
                            case MUL:
                                result = irCode.getOpNum1() * irCode.getOpNum2();
                                break;
                            case DIV:
                                result = irCode.getOpNum1() / irCode.getOpNum2();
                                break;
                            case MOD:
                                result = irCode.getOpNum1() % irCode.getOpNum2();
                                break;
                            case AND:
                                result = irCode.getOpNum1() != 0 && irCode.getOpNum2() != 0 ? 1 : 0;
                                break;
                            case OR:
                                result = irCode.getOpNum1() != 0 || irCode.getOpNum2() != 0 ? 1 : 0;
                                break;
                            case NOT:
                                result = irCode.getOpNum2() == 0 ? 1 : 0;
                                break;
                            case SLT:
                                result = irCode.getOpNum1() < irCode.getOpNum2() ? 1 : 0;
                                break;
                            case SLE:
                                result = irCode.getOpNum1() <= irCode.getOpNum2() ? 1 : 0;
                                break;
                            case SEQ:
                                result = irCode.getOpNum1() == irCode.getOpNum2() ? 1 : 0;
                                break;
                            case SNE:
                                result = irCode.getOpNum1() != irCode.getOpNum2() ? 1 : 0;
                                break;
                            case SGE:
                                result = irCode.getOpNum1() >= irCode.getOpNum2() ? 1 : 0;
                                break;
                            case SGT:
                                result = irCode.getOpNum1() > irCode.getOpNum2() ? 1 : 0;
                                break;
                            default:
                                isConst = false;
                                break;
                        }
                    }
                }
                if (isConst) {
                    for (int j = i + 1; j < optimizedIRList.size(); j++) {
                        IRCode nextIrCode = optimizedIRList.get(j);
                        if (nextIrCode.getOpIdent1() != null && nextIrCode.getOpIdent1().equals(constTempVar)) {
                            unFinished = true;
                            hasChanged = true;
                            nextIrCode.setOpNum1(result);
                            nextIrCode.setOpIdent1(null);
                        }
                        if (nextIrCode.getOpIdent2() != null && nextIrCode.getOpIdent2().equals(constTempVar)) {
                            unFinished = true;
                            hasChanged = true;
                            nextIrCode.setOpNum2(result);
                            nextIrCode.setOpIdent2(null);
                        }
                        if (nextIrCode.getResultIdent() != null && nextIrCode.getResultIdent().equals(constTempVar)) {
                            break;
                        }
                    }
                }
            }
            boolean unFinished2 = deleteUnUsedTempVar();
            unFinished = unFinished || unFinished2;
            hasChanged = hasChanged || unFinished2;
        }
        return hasChanged;
    }

    public ArrayList<IRCode> getOptimizedIRList() {
        if (optimize) {
            return optimizedIRList;
        } else {
            return originIRList;
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

    public void initTable() {
        curTable = new Table();
        level = 0;
        tableStack.clear();
        tableStack.put(level, curTable);
    }

    public void backupTable() {
        levelBackup = level;
        curTableBackup.clear();
        curTableBackup.colon(curTable);
        tableStackBackup.clear();
        for (int i = 0; i < level; i++) {
            tableStackBackup.put(i, tableStack.get(i));
        }
        tableStackBackup.put(level, curTableBackup);
    }

    public void rollBackTable() {
        level = levelBackup;
        curTable.clear();
        curTable.colon(curTableBackup);
        tableStack.clear();
        for (int i = 0; i < level; i++) {
            tableStack.put(i, tableStackBackup.get(i));
        }
        tableStack.put(level, curTable);
    }

    public void print() {
        try {
            for (IRCode irCode : optimizedIRList) {
                out.write(irCode.print() + "\n");
            }
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    //释放全局寄存器池
    public void freeGlobalReg() {
        for (int i = 0; i < 8; i++) {
            globalRegisterPool.put("$s" + i, false);
        }
    }

    public boolean allocateGlobalReg(TableItem item) {
        String tempReg = null;
        for (String regName : globalRegisterPool.keySet()) {
            if (!globalRegisterPool.get(regName) && (tempReg == null || globalRegisterUseCount.get(regName) < globalRegisterUseCount.get(tempReg))) {
                tempReg = regName;
            }
        }
        if (tempReg != null) {
            globalRegisterPool.put(tempReg, true);
            globalRegisterUseCount.put(tempReg, globalRegisterUseCount.get(tempReg) + 1);
            item.allocateGlobalReg(tempReg);
            return true;
        }
        return false;
    }

    public boolean allocateTempReg(TableItem item) {
        for (String regName : tempRegisterPool.keySet()) {
            if (!tempRegisterPool.get(regName)) {
                tempRegisterPool.put(regName, true);
                item.allocateTempReg(regName);
                return true;
            }
        }
        //没有空闲寄存器，需要释放已被占用的寄存器
        /*String tempReg = null;
        for (String regName : tempRegisterPool.keySet()) {
            if (tempReg == null || tempRegisterUseCount.get(regName) < tempRegisterUseCount.get(tempReg)) {
                tempReg = regName;
            }
        }
        if (tempReg != null) {
            tempRegisterPool.put(tempReg, true);
            tempRegisterUseCount.put(tempReg, tempRegisterUseCount.get(tempReg) + 1);
            item.allocateTempReg(tempReg);
            return true;
        }*/
        return false;
    }

    public void freeTempReg() {
        tempRegisterPool.replaceAll((n, v) -> false);
        tempRegisterUseCount.replaceAll((n, v) -> 0);
    }

    public int getMaxTempRegNum() {
        int maxNum = 0;
        for (IRCode irCode : optimizedIRList) {
            if (irCode.getResultIdent() != null && irCode.getResultIdent().startsWith("#")) {
                int num = Integer.parseInt(irCode.getResultIdent().substring(2));
                if (num > maxNum) {
                    maxNum = num;
                }
            }
            if (irCode.getOpIdent1() != null && irCode.getOpIdent1().startsWith("#")) {
                int num = Integer.parseInt(irCode.getOpIdent1().substring(2));
                if (num > maxNum) {
                    maxNum = num;
                }
            }
            if (irCode.getOpIdent2() != null && irCode.getOpIdent2().startsWith("#")) {
                int num = Integer.parseInt(irCode.getOpIdent2().substring(2));
                if (num > maxNum) {
                    maxNum = num;
                }
            }
        }
        return maxNum;
    }
}
