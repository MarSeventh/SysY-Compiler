import java.io.FileReader;
import java.util.Scanner;

public class Lexer {
    private final Scanner scanner;
    private int lineNum = 0;
    private String curLine = "";
    private char curChar;
    private int curPos = 0;
    private String token = "";
    private LexType lexType;
    private int number = 0;

    public Lexer(String filename) {
        try {
            scanner = new Scanner(new FileReader(filename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void next() {
        if (curPos >= curLine.length()) {
            if (!scanner.hasNextLine()) {
                lexType = LexType.EOF;
                return;
            }
            curLine = scanner.nextLine();
            curPos = 0;
            lineNum++;
        }
        clearToken();
        getChar();
        while (isSpace() || isNewLine() || isTab()) {
            getChar();
        }
        //仅有空字符或空行
        if (curChar == 0) {
            next();
            return;
        }
        if (isLetter() || isUnderline()) {
            while (isLetter() || isUnderline() || isDigit()) {
                catToken();
                getChar();
            }
            retract();
            lexType = LexType.reserve(token);
        } else if (isDigit()) {
            while (isDigit()) {
                catToken();
                getChar();
            }
            retract();
            lexType = LexType.INTCON;
            number = Integer.parseInt(token);
        } else if (curChar == '"') {
            catToken();
            getChar();
            while (curPos < curLine.length() && curChar != '"') {
                catToken();
                getChar();
            }
            if (curChar == '"') {
                catToken();
                lexType = LexType.STRCON;
            } else {
                error("expression lack of right \"");
                lexType = LexType.ERR;
            }
        } else if (curChar == '!') {
            catToken();
            getChar();
            if (curChar == '=') {
                catToken();
                lexType = LexType.NEQ;
            } else {
                retract();
                lexType = LexType.NOT;
            }
        } else if (curChar == '&') {
            catToken();
            getChar();
            if (curChar == '&') {
                catToken();
                lexType = LexType.AND;
            } else {
                retract();
                error("wrong character after &");
                lexType = LexType.ERR;
            }
        } else if (curChar == '|') {
            catToken();
            getChar();
            if (curChar == '|') {
                catToken();
                lexType = LexType.OR;
            } else {
                retract();
                error("wrong character after |");
                lexType = LexType.ERR;
            }
        } else if (curChar == '<') {
            catToken();
            getChar();
            if (curChar == '=') {
                catToken();
                lexType = LexType.LEQ;
            } else {
                retract();
                lexType = LexType.LSS;
            }
        } else if (curChar == '>') {
            catToken();
            getChar();
            if (curChar == '=') {
                catToken();
                lexType = LexType.GEQ;
            } else {
                retract();
                lexType = LexType.GRE;
            }
        } else if (curChar == '=') {
            catToken();
            getChar();
            if (curChar == '=') {
                catToken();
                lexType = LexType.EQL;
            } else {
                retract();
                lexType = LexType.ASSIGN;
            }
        } else if (curChar == '+') {
            catToken();
            lexType = LexType.PLUS;
        } else if (curChar == '-') {
            catToken();
            lexType = LexType.MINU;
        } else if (curChar == '*') {
            catToken();
            lexType = LexType.MULT;
        } else if (curChar == '/') {
            catToken();
            getChar();
            if (curChar == '/') {
                while (curPos < curLine.length()) {
                    getChar();
                }
                next();
            } else if (curChar == '*') {
                getChar();
                if (curChar == '/') {
                    next();
                    return;
                } else {
                    retract();
                }
                while (true) {
                    while (curPos < curLine.length()) {
                        getChar();
                        while (curChar == '*' && curPos < curLine.length()) {
                            getChar();
                            if (curChar == '/') {
                                next();
                                return;
                            }
                        }
                    }
                    //注释未结束
                    if (scanner.hasNextLine()) {
                        curLine = scanner.nextLine();
                        lineNum++;
                        curPos = 0;
                    } else {
                        return;
                    }
                }
            } else {
                retract();
                lexType = LexType.DIV;
            }
        } else if (curChar == '%') {
            catToken();
            lexType = LexType.MOD;
        } else if (isSemi()) {
            catToken();
            lexType = LexType.SEMICN;
        } else if (isComma()) {
            catToken();
            lexType = LexType.COMMA;
        } else if (isLpar()) {
            catToken();
            lexType = LexType.LPARENT;
        } else if (isRpar()) {
            catToken();
            lexType = LexType.RPARENT;
        } else if (curChar == '[') {
            catToken();
            lexType = LexType.LBRACK;
        } else if (curChar == ']') {
            catToken();
            lexType = LexType.RBRACK;
        } else if (curChar == '{') {
            catToken();
            lexType = LexType.LBRACE;
        } else if (curChar == '}') {
            catToken();
            lexType = LexType.RBRACE;
        } else {
            error("wrong character" + curChar);
            lexType = LexType.ERR;
        }
    }

    public String getToken() {
        return token;
    }

    public LexType getLexType() {
        return lexType;
    }

    public int getNumber() {
        return number;
    }

    public void error(String msg) {
        System.out.println("Error at line " + lineNum + ": " + curLine + "\n" + msg + "\n");
    }

    public void clearToken() {
        token = "";
        lexType = null;
        number = 0;
    }

    public boolean getChar() {
        if (curPos < curLine.length()) {
            curChar = curLine.charAt(curPos++);
            return true;
        } else {
            curChar = 0;
            curPos++;
            return false;
        }
    }

    public void catToken() {
        token += curChar;
    }

    public void retract() {
        curPos--;
        curChar = (curPos < curLine.length()) ? curLine.charAt(curPos) : 0;
    }

    public boolean isUnderline() {
        return curChar == '_';
    }

    public boolean isSpace() {
        return curChar == ' ';
    }

    public boolean isNewLine() {
        return curChar == '\n';
    }

    public boolean isTab() {
        return curChar == '\t';
    }

    public boolean isLetter() {
        return Character.isLetter(curChar);
    }

    public boolean isDigit() {
        return Character.isDigit(curChar);
    }

    public boolean isComma() {
        return curChar == ',';
    }

    public boolean isSemi() {
        return curChar == ';';
    }

    public boolean isLpar() {
        return curChar == '(';
    }

    public boolean isRpar() {
        return curChar == ')';
    }


}
