import java.util.ArrayList;

public class Table {
    private final ArrayList<TableItem> tableItems = new ArrayList<>();

    public Table() {
    }

    public boolean addItem(TableItem item) {
        if (hasSameName(item.getName())) {
            return false;
        } else {
            tableItems.add(item);
            return true;
        }
    }

    public boolean hasItem(String name, String kind) {
        for (TableItem item : tableItems) {
            if (item.getName().equals(name) && item.getKind().equals(kind)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSameName(String name) {
        for (TableItem item : tableItems) {
            if (item.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public TableItem getItem(String name, String kind) {
        for (TableItem item : tableItems) {
            if (item.getName().equals(name) && item.getKind().equals(kind)) {
                return item;
            }
        }
        return null;
    }

    public TableItem getLastItem() {
        if (tableItems.isEmpty()) {
            return null;
        }
        return tableItems.get(tableItems.size() - 1);
    }
}
