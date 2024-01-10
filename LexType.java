public enum LexType {
    IDENFR, INTCON, STRCON, MAINTK, CONSTTK, INTTK, BREAKTK, CONTINUETK, IFTK, ELSETK,
    NOT, AND, OR, FORTK, GETINTTK, PRINTFTK, RETURNTK, PLUS, MINU, VOIDTK,
    MULT, DIV, MOD, LSS, LEQ, GRE, GEQ, EQL, NEQ,
    ASSIGN, SEMICN, COMMA, LPARENT, RPARENT, LBRACK, RBRACK, LBRACE, RBRACE,
    EOF, ERR;

    public static LexType reserve(String str) {
        switch (str) {
            case "const":
                return CONSTTK;
            case "int":
                return INTTK;
            case "void":
                return VOIDTK;
            case "main":
                return MAINTK;
            case "if":
                return IFTK;
            case "else":
                return ELSETK;
            case "for":
                return FORTK;
            case "getint":
                return GETINTTK;
            case "printf":
                return PRINTFTK;
            case "return":
                return RETURNTK;
            case "break":
                return BREAKTK;
            case "continue":
                return CONTINUETK;
            default:
                return IDENFR;
        }
    }
}
