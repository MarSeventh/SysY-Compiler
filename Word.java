public class Word {
    private final String token;
    private final LexType type;

    private int number = 0;
    private final int lineNum;

    public Word(String token, LexType type, int number, int lineNum) {
        this.token = token;
        this.type = type;
        this.number = number;
        this.lineNum = lineNum;
    }

    public String getToken() {
        return token;
    }

    public LexType getType() {
        return type;
    }

    public int getNumber() {
        return number;
    }

    public int getLineNum() {
        return lineNum;
    }
}
