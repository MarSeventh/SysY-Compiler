public class DefPoint {
    private String pointName;
    private IRCode irCode;
    private TableItem defItem;
    private BasicBlock block;

    public DefPoint(String pointName, IRCode irCode, TableItem defItem, BasicBlock block) {
        this.pointName = pointName;
        this.irCode = irCode;
        this.defItem = defItem;
        this.block = block;
    }

    public String getPointName() {
        return pointName;
    }

    public TableItem getDefItem() {
        return defItem;
    }

    public IRCode getIrCode() {
        return irCode;
    }

    public BasicBlock getBlock() {
        return block;
    }
}
