import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Error {
    private static boolean print = true;
    public static BufferedWriter out;
    public static int errorNum = 0;
    public static HashMap<Integer, String> errorList = new HashMap<>();
    public static HashMap<Integer, Integer> errorLine = new HashMap<>();

    static {
        try {
            if (print) {
                out = new BufferedWriter(new FileWriter("error.txt"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void lexerError(int lineNum, String curLine, String msg, String errorCode) {
        System.out.println("Error at line " + lineNum + ": (errorCode = " + errorCode + ")" + curLine + "\n" + msg + "\n");
    }

    public static void syntaxError(int lineNum, String msg, String errorCode) {
        System.out.println("Error at line " + lineNum + ": (errorCode = " + errorCode + ")\n" + msg + "\n");
    }

    public static void meaningError(int lineNum, String msg, String errorCode) {
        System.out.println("Error at line " + lineNum + ": (errorCode = " + errorCode + ")\n" + msg + "\n");
    }

    public static void calError() {
        //System.out.println("calculate const exp error");
        Calculator.setIsConst(false);
    }

    public static void mipsError(String msg) {
        System.out.println("MIPS Error: " + msg + "\n");
    }

    public static void printError(int line, String errorCode) {
        if (print) {
            try {
                errorList.put(errorNum, errorCode);
                errorLine.put(errorNum++, line);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void print() {
        try {
            if (print) {
                List<Map.Entry<Integer, Integer>> linelist = new ArrayList<>(errorLine.entrySet());
                Collections.sort(linelist, Comparator.comparing(Map.Entry::getValue));
                for (Map.Entry<Integer, Integer> integerIntegerEntry : linelist) {
                    int line = integerIntegerEntry.getValue();
                    out.write(line + " " + errorList.get(integerIntegerEntry.getKey()) + "\n");
                }
                out.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
