import java.util.ArrayList;

public class MIPSTableItem {
    private final TableItem tableItem;
    private final int offset;

    //function params
    private final ArrayList<MIPSTableItem> params = new ArrayList<>();

    public MIPSTableItem(TableItem tableItem, int offset) {
        this.tableItem = tableItem;
        this.offset = offset;
    }

    public int getOffset() {
        return offset * 4;
    }

    public TableItem getTableItem() {
        return tableItem;
    }

    public String getName() {
        return tableItem.getName();
    }

    public String getKind() {
        return tableItem.getKind();
    }

    public void addParam(MIPSTableItem param) {
        params.add(param);
    }

}
