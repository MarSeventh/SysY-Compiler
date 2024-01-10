import java.util.ArrayList;
import java.util.HashMap;

public class ConflictGraph {
    HashMap<TableItem, ArrayList<TableItem>> graph = new HashMap<>();
    ArrayList<TableItem> allocateStack = new ArrayList<>();//寄存器分配栈

    public void addConflict(ArrayList<TableItem> conflictList) {
        for (TableItem item : conflictList) {
            if (!graph.containsKey(item)) {
                graph.put(item, new ArrayList<>());
            }
            for (TableItem item1 : conflictList) {
                if (item != item1 && !graph.get(item).contains(item1)) {
                    graph.get(item).add(item1);
                }
            }
        }
    }

    public void initAllocateStack(int regNum) {
        //图着色分配法
        while (!graph.isEmpty()) {
            TableItem item = null;
            for (TableItem key : graph.keySet()) {
                if (graph.get(key).size() < regNum && (item == null || graph.get(key).size() > graph.get(item).size())) {
                    //尽量选择满足条件的最大值
                    item = key;
                }
            }
            if (item == null) {
                //没有满足条件的，删除度数最大的
                for (TableItem key : graph.keySet()) {
                    if (item == null || graph.get(key).size() > graph.get(item).size()) {
                        item = key;
                    }
                }
                for (TableItem key : graph.keySet()) {
                    graph.get(key).remove(item);
                }
                graph.remove(item);
            } else {
                allocateStack.add(item);
                graph.remove(item);
                for (TableItem key : graph.keySet()) {
                    graph.get(key).remove(item);
                }
            }
        }
    }

    public TableItem getOneNode() {
        if (!allocateStack.isEmpty()) {
            TableItem item = allocateStack.get(allocateStack.size() - 1);
            allocateStack.remove(allocateStack.size() - 1);
            return item;
        }
        return null;
    }
}
