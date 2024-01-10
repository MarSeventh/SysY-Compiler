import java.util.ArrayList;
import java.util.HashMap;

public class DAGMap {
    private ArrayList<DAGNode> leafNodes = new ArrayList<>();//叶子节点列表
    private HashMap<TableItem, DAGNode> identMap = new HashMap<>();//标识符表，主要为了表示某一item目前最新的值位于哪一个DAGNode中
    private HashMap<String, DAGNode> tempMap = new HashMap<>();//中间变量表，主要为了表示某一中间变量的值目前位于哪一个DAGNode中
    private ArrayList<DAGNode> dagNodeList = new ArrayList<>();//DAGNode列表

    public DAGNode getNumberLeafNode(int number) {
        for (DAGNode node : leafNodes) {
            if (node.getNodeType().equals("number") && node.getNodeNumber() == number) {
                return node;
            }
        }
        return null;
    }

    public DAGNode getTempNode(String tempName) {
        return tempMap.getOrDefault(tempName, null);
    }

    public void addLeafNode(DAGNode node) {
        leafNodes.add(node);
        if (!dagNodeList.contains(node)) {
            dagNodeList.add(node);
        }
    }

    public void addNode(DAGNode node) {
        if (!dagNodeList.contains(node)) {
            dagNodeList.add(node);
        }
    }

    public void setTempMap(String tempName, DAGNode node) {
        tempMap.put(tempName, node);
    }


    public void setIdentMap(TableItem ident, DAGNode node) {
        identMap.put(ident, node);
    }

    public TableItem getSameNodeIdent(DAGNode node, TableItem exceptItem) {
        for (TableItem ident : identMap.keySet()) {
            if (ident != exceptItem && identMap.get(ident) == node) {
                return ident;
            }
        }
        return null;
    }

    public void removeIdentMap(TableItem ident) {
        identMap.remove(ident);
    }

    public DAGNode getIdentNode(TableItem ident) {
        return identMap.getOrDefault(ident, null);
    }

    public DAGNode getInnerNode(IROperator operator, DAGNode leftChild, DAGNode rightChild) {
        for (DAGNode node : dagNodeList) {
            if (node.getNodeType().equals("operator") && node.getNodeOperator() == operator
                    && node.getLeftChild() == leftChild && node.getRightChild() == rightChild) {
                return node;
            }
        }
        return null;
    }

    public HashMap<String, DAGNode> getTempMap() {
        return tempMap;
    }
}
