import java.util.ArrayList;

public class MIPSTable {
    private final ArrayList<MIPSTableItem> tableItems = new ArrayList<>();

    public MIPSTable() {
    }

    public void addItem(MIPSTableItem item) {
        tableItems.add(item);
    }

    public MIPSTableItem getMIPSItem(String name, String kind) {
        for (MIPSTableItem item : tableItems) {
            //System.out.println(item);
            if (item.getTableItem().getName().equals(name) && item.getTableItem().getKind().equals(kind)) {
                return item;
            }
        }
        return null;
    }

    public MIPSTableItem getLastMIPSTableItem() {
        return tableItems.isEmpty() ? null : tableItems.get(tableItems.size() - 1);
    }
}
