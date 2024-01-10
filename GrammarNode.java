import java.util.ArrayList;

public class GrammarNode {
    private String nodeName;
    private ArrayList<GrammarNode> children = new ArrayList<>();

    public GrammarNode(String nodeName) {
        this.nodeName = nodeName;
    }


    public void addChild(GrammarNode child) {
        children.add(child);
    }

    public String getNodeName() {
        return nodeName;
    }

    public ArrayList<GrammarNode> getChildren() {
        return children;
    }

    public GrammarNode getChild(int index) {
        if (index < children.size()) {
            return children.get(index);
        }
        return null;
    }

    public GrammarNode getLastChild() {
        if (children.isEmpty()) {
            return null;
        }
        return children.get(children.size() - 1);
    }

    public boolean isName(String name) {
        return nodeName.equals(name);
    }


    public void print() {
        System.out.print(nodeName + " => ");
        for (GrammarNode child : children) {
            System.out.print(child.getNodeName() + " ");
        }
        System.out.println();
        for (GrammarNode child : children) {
            if (!child.getChildren().isEmpty()) {
                child.print();
            }
        }
    }
}
