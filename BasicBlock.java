import java.util.ArrayList;

public class BasicBlock {
    private String blockName;
    private int start;
    private int end;
    private ArrayList<IRCode> blockIRCodes = new ArrayList<>();
    private ArrayList<BasicBlock> nextBlocks = new ArrayList<>();
    private ArrayList<BasicBlock> preBlocks = new ArrayList<>();
    //到达定义
    private ArrayList<DefPoint> genPoints = new ArrayList<>();
    private ArrayList<DefPoint> killPoints = new ArrayList<>();
    private ArrayList<DefPoint> inDefPoints = new ArrayList<>();
    private ArrayList<DefPoint> outDefPoints = new ArrayList<>();
    //活跃变量
    private ArrayList<TableItem> defVarList = new ArrayList<>();
    private ArrayList<TableItem> useVarList = new ArrayList<>();
    private ArrayList<TableItem> inActiveVarList = new ArrayList<>();
    private ArrayList<TableItem> outActiveVarList = new ArrayList<>();

    public BasicBlock(String blockName) {
        this.blockName = blockName;
    }

    public void addIRCode(IRCode irCode) {
        blockIRCodes.add(irCode);
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public void addNextBlock(BasicBlock nextBlock) {
        if (!nextBlocks.contains(nextBlock)) {
            nextBlocks.add(nextBlock);
        }
    }

    public void addPreBlock(BasicBlock preBlock) {
        if (!preBlocks.contains(preBlock)) {
            preBlocks.add(preBlock);
        }
    }

    public void addGenPoints(DefPoint defPoint) {
        genPoints.add(defPoint);
    }

    public void addKillPoints(DefPoint defPoint) {
        killPoints.add(defPoint);
    }

    public void addInDefPoints(DefPoint defPoint) {
        inDefPoints.add(defPoint);
    }

    public void addOutDefPoints(DefPoint defPoint) {
        outDefPoints.add(defPoint);
    }

    public ArrayList<DefPoint> getInDefPoints() {
        return inDefPoints;
    }

    public ArrayList<DefPoint> getOutDefPoints() {
        return outDefPoints;
    }

    public ArrayList<TableItem> getDefVarList() {
        return defVarList;
    }

    public ArrayList<TableItem> getUseVarList() {
        return useVarList;
    }

    public ArrayList<TableItem> getInActiveVarList() {
        return inActiveVarList;
    }

    public ArrayList<TableItem> getOutActiveVarList() {
        return outActiveVarList;
    }

    public boolean hasDefVar(TableItem item) {
        for (TableItem tableItem : defVarList) {
            if (tableItem == item) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUseVar(TableItem item) {
        for (TableItem tableItem : useVarList) {
            if (tableItem == item) {
                return true;
            }
        }
        return false;
    }

    public void addDefVar(TableItem item) {
        if (!hasDefVar(item)) {
            defVarList.add(item);
        }
    }

    public void addUseVar(TableItem item) {
        if (!hasUseVar(item)) {
            useVarList.add(item);
        }
    }

    public ArrayList<BasicBlock> getPreBlocks() {
        return preBlocks;
    }

    public ArrayList<BasicBlock> getNextBlocks() {
        return nextBlocks;
    }

    public ArrayList<DefPoint> getGenPoints() {
        return genPoints;
    }

    public ArrayList<DefPoint> getKillPoints() {
        return killPoints;
    }

    public boolean hasLabel(String name) {
        for (IRCode irCode : blockIRCodes) {
            if (irCode.getOperator() == IROperator.LABEL && irCode.getResultIdent().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public String getBlockName() {
        return blockName;
    }

    public ArrayList<IRCode> getBlockIRCodes() {
        return blockIRCodes;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
