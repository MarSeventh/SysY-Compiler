import java.util.ArrayList;

public class DAGNode {
    private String NodeType;//number, ident, operator
    private int NodeNumber;//number
    private TableItem NodeIdent;//ident
    private IROperator NodeOperator;//operator
    private ArrayList<TableItem> equalIdentList = new ArrayList<>();//等价局部变量
    private ArrayList<String> equalTempList = new ArrayList<>();//等价临时变量
    private DAGNode leftChild = null;
    private DAGNode rightChild = null;
    private ArrayList<DAGNode> parentList = new ArrayList<>();//父节点列表

    public DAGNode(int number) {
        NodeType = "number";
        NodeNumber = number;
    }

    public DAGNode(TableItem ident) {
        NodeType = "ident";
        NodeIdent = ident;
    }

    public DAGNode(IROperator operator) {
        NodeType = "operator";
        NodeOperator = operator;
    }

    public void addParent(DAGNode parent) {
        parentList.add(parent);
    }

    public ArrayList<DAGNode> getParentList() {
        return parentList;
    }

    public String getNodeType() {
        return NodeType;
    }

    public int getNodeNumber() {
        return NodeNumber;
    }

    public TableItem getNodeIdent() {
        return NodeIdent;
    }

    public IROperator getNodeOperator() {
        return NodeOperator;
    }

    public DAGNode getLeftChild() {
        return leftChild;
    }

    public DAGNode getRightChild() {
        return rightChild;
    }

    public void addEqualTemp(String tempName) {
        equalTempList.add(tempName);
    }

    public void addEqualIdent(TableItem ident) {
        if (!equalIdentList.contains(ident)) {
            equalIdentList.add(ident);
        }
    }

    public void setLeftChild(DAGNode leftChild) {
        this.leftChild = leftChild;
    }

    public void setRightChild(DAGNode rightChild) {
        this.rightChild = rightChild;
    }
}
