public class Register {
    private final String name;

    private String identName;
    private boolean isAvailable = true;
    private MIPSTableItem globalItem = null;//对全局变量有效
    private MIPSTableItem tempItem = null;//对临时变量有效

    public Register(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setBusy(String name) {
        identName = name;
        isAvailable = false;
    }

    public void setAvailable() {
        isAvailable = true;
    }

    public String getIdentName() {
        return identName;
    }

    public void setGlobalBusy(MIPSTableItem item) {
        globalItem = item;
        isAvailable = false;
    }

    public MIPSTableItem getGlobalItem() {
        return globalItem;
    }

    public void setTempBusy(MIPSTableItem item) {
        tempItem = item;
        isAvailable = false;
    }

    public MIPSTableItem getTempItem() {
        return tempItem;
    }
}
