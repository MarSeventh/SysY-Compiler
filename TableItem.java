import java.util.ArrayList;

public class TableItem {
    private final String name;
    private final String type;
    private final String kind;
    private final int level;
    private final int dimension;
    private int parasNum;//形参个数

    private ArrayList<Integer> parasDimen = new ArrayList<>();//每一个形参的维数
    private ArrayList<Integer> arrayDimen = new ArrayList<>();//数组每一维的大小

    private int constInit;//常数初值
    private ArrayList<Integer> arrayConstInit = new ArrayList<>();//常数数组初值

    private boolean isGlobal = false;//是否是全局变量

    public TableItem(String name, String type, String kind, int level, int dimension) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.level = level;
        this.dimension = dimension;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }

    public void addPara(int paraDimen) {
        parasDimen.add(paraDimen);
        parasNum++;
    }

    public boolean isArray() {
        return dimension != 0;
    }

    public void setArrayDimen(ArrayList<Integer> arrayDimen) {
        this.arrayDimen = arrayDimen;
    }

    public void setArrayConstInit(ArrayList<Integer> arrayConstInit) {
        this.arrayConstInit = arrayConstInit;
    }

    public void setConstInit(int init) {
        constInit = init;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getKind() {
        return kind;
    }

    public int getLevel() {
        return level;
    }

    public int getDimension() {
        return dimension;
    }

    public int getParasNum() {
        return parasNum;
    }

    public ArrayList<Integer> getParasDimen() {
        return parasDimen;
    }

    public int getArrayDimen(int index) {
        return arrayDimen.get(index);
    }

    //数组大小
    public int getArraySize() {
        int size = 1;
        for (int i = 0; i < dimension; i++) {
            size *= arrayDimen.get(i);
        }
        return size;
    }

    public int getArrayConstInit(int index) {
        return arrayConstInit.get(index);
    }

    public int getConstInit() {
        return constInit;
    }

    public int getConstValue(int dimen1, int dimen2) {
        if (dimension == 0) {
            return getConstInit();
        } else if (dimension == 1) {
            return getArrayConstInit(dimen1);
        } else if (dimension == 2) {
            int index = 0;
            index += dimen1 * getArrayDimen(1);
            index += dimen2;
            return getArrayConstInit(index);
        }
        return 0;
    }

    public boolean isGlobal() {
        return isGlobal;
    }
}
