public class Register {
    private final String name;

    private String identName;
    private boolean isAvailable = true;

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
}
