import java.util.ArrayList;

public class TableItem {
    private final String name;
    private final String type;
    private final String kind;
    private final int level;
    private final int dimension;
    private int parasNum;

    private ArrayList<Integer> parasDimen = new ArrayList<>();

    public TableItem(String name, String type, String kind, int level, int dimension) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.level = level;
        this.dimension = dimension;
    }

    public void addPara(int paraDimen) {
        parasDimen.add(paraDimen);
        parasNum++;
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

}
