import java.util.HashMap;

public class Calculator {
    private static GrammarNode node;
    private static HashMap<Integer, Table> tableStack = new HashMap<>();

    private static int level = 0;

    private static boolean isConst = true;

    public static int calConstExp(GrammarNode pNode, HashMap<Integer, Table> pTable, int pLevel) {
        node = pNode;
        tableStack = pTable;
        level = pLevel;
        int result = 0;
        if (node.getChild(0) != null && node.getChild(0).getNodeName().equals("AddExp")) {
            result = calAddExp(node.getChild(0));
        } else {
            Error.calError();
        }
        return result;
    }

    public static int calAddExp(GrammarNode pNode) {
        int result = 0;
        if (pNode.getChild(0).getNodeName().equals("AddExp")) {
            int left = calAddExp(pNode.getChild(0));
            int right = calMulExp(pNode.getChild(2));
            if (pNode.getChild(1).getNodeName().equals("+")) {
                result = left + right;
            } else {
                result = left - right;
            }
        } else {
            result = calMulExp(pNode.getChild(0));
        }
        return result;
    }

    public static int calMulExp(GrammarNode node) {
        int result = 0;
        if (node.getChild(0).getNodeName().equals("MulExp")) {
            int left = calMulExp(node.getChild(0));
            int right = calUnaryExp(node.getChild(2));
            if (node.getChild(1).getNodeName().equals("*")) {
                result = left * right;
            } else if (node.getChild(1).getNodeName().equals("/")) {
                result = right == 0 ? 0 : left / right;
            } else {
                result = right == 0 ? 0 : left % right;
            }
        } else {
            result = calUnaryExp(node.getChild(0));
        }
        return result;
    }

    public static int calUnaryExp(GrammarNode node) {
        int result = 0;
        if (node.getChild(0).getNodeName().equals("PrimaryExp")) {
            result = calPrimaryExp(node.getChild(0));
        } else if (node.getChild(0).getNodeName().equals("Ident")) {
            Error.calError();
        } else if (node.getChild(0).getNodeName().equals("UnaryOp")) {
            int right = calUnaryExp(node.getChild(1));
            if (node.getChild(0).getChild(0).getNodeName().equals("-")) {
                result = -right;
            } else if (node.getChild(0).getChild(0).getNodeName().equals("+")) {
                result = right;
            } else if (node.getChild(0).getChild(0).getNodeName().equals("!")) {
                result = right == 0 ? 1 : 0;
            }
        } else {
            Error.calError();
        }
        return result;
    }

    public static int calPrimaryExp(GrammarNode node) {
        int result = 0;
        if (node.getChild(0).getNodeName().equals("(")) {
            result = calAddExp(node.getChild(1).getChild(0)); //(Exp)
        } else if (node.getChild(0).getNodeName().equals("LVal")) {
            result = calLVal(node.getChild(0));
        } else if (node.getChild(0).getNodeName().equals("Number")) {
            result = calNumber(node.getChild(0));
        }
        return result;
    }

    public static int calLVal(GrammarNode node) {
        int result = 0;
        if (node.getChild(0).getNodeName().equals("Ident")) {
            String name = node.getChild(0).getChild(0).getNodeName();
            if (isConst(name)) {
                int dimen1 = 0;//第一个维度
                int dimen2 = 0;//第二个维度
                if (node.getChild(1) != null && node.getChild(1).getNodeName().equals("[")) {
                    dimen1 = calAddExp(node.getChild(2).getChild(0));
                    if (node.getChild(4) != null && node.getChild(4).getNodeName().equals("[")) {
                        dimen2 = calAddExp(node.getChild(5).getChild(0));
                    }
                }
                result = getConstValue(name, dimen1, dimen2);
            } else {
                Error.calError();
            }
        } else {
            Error.calError();
        }
        return result;
    }

    public static int calNumber(GrammarNode node) {
        return Integer.parseInt(node.getChild(0).getNodeName());
    }

    public static boolean isConst(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasSameName(name)) {
                return tableStack.get(i).hasItem(name, "const");
            }
        }
        return false;
    }

    public static int getConstValue(String name, int dimen1, int dimen2) {
        TableItem constItem = null;
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "const")) {
                constItem = tableStack.get(i).getItem(name, "const");
                break;
            }
        }
        return constItem == null ? 0 : constItem.getConstValue(dimen1, dimen2);
    }

    public static void setIsConst(boolean isConst) {
        Calculator.isConst = isConst;
    }

    public static boolean getIsConst() {
        boolean temp = isConst;
        isConst = true;
        return temp;
    }

    public static void setTableStackAndLevel(HashMap<Integer, Table> pTable, int pLevel) {
        tableStack = pTable;
        level = pLevel;
    }
}
