public class Error {
    public static void lexerError(int lineNum, String curLine, String msg, String errorCode) {
        System.out.println("Error at line " + lineNum + ": (errorCode = " + errorCode + ")" + curLine + "\n" + msg + "\n");
    }

    public static void syntaxError(int lineNum, String msg, String errorCode) {
        System.out.println("Error at line " + lineNum + ": (errorCode = " + errorCode + ")\n" + msg + "\n");
    }
}
