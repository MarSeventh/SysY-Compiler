import java.io.BufferedWriter;
import java.util.ArrayList;

public class Parser {
    private final ArrayList<Word> words = new ArrayList<>();
    private final GrammarNode tree = new GrammarNode("CompUnit");
    private LexType curType;
    private String curToken;

    private Word curWord;
    private Word lastWord;
    private final BufferedWriter out;

    private int curPos = 0;
    private boolean print = true;

    public Parser(String filename, BufferedWriter out) {
        this.out = out;
        Lexer lexer = new Lexer(filename, out);
        while (lexer.getLexType() != LexType.EOF) {
            lexer.next();
            if (lexer.getLexType() != LexType.ERR) {
                words.add(new Word(lexer.getToken(), lexer.getLexType(), lexer.getNumber(), lexer.getLineNum()));
            }
        }
        lastWord = words.get(0);
    }

    public void parse() {
        getWord();
        lastWord = curWord;
        if (curType != LexType.EOF) {
            parseCompUnit();
        }
    }

    public void getWord() {
        if (curPos < words.size()) {
            lastWord = curWord;
            curWord = words.get(curPos);
            curType = words.get(curPos).getType();
            curToken = words.get(curPos).getToken();
            curPos++;
            if (print && lastWord != null) {
                try {
                    if (lastWord.getType() != LexType.ERR && lastWord.getType() != LexType.EOF) {
                        out.write(lastWord.getType().toString() + " " + lastWord.getToken() + "\n");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            curType = LexType.EOF;
        }
    }

    public LexType nextWordType() {
        if (curPos < words.size()) {
            return words.get(curPos).getType();
        } else {
            return LexType.EOF;
        }
    }

    public LexType next2WordType() {
        if (curPos + 1 < words.size()) {
            return words.get(curPos + 1).getType();
        } else {
            return LexType.EOF;
        }
    }

    public void parseCompUnit() {
        while (curType == LexType.CONSTTK || (curType == LexType.INTTK && nextWordType() == LexType.IDENFR && next2WordType() != LexType.LPARENT)) {
            tree.addChild(parseDecl());
        }
        while (curType == LexType.VOIDTK || (curType == LexType.INTTK && nextWordType() != LexType.MAINTK)) {
            tree.addChild(parseFuncDef());
        }
        if (curType == LexType.INTTK && nextWordType() == LexType.MAINTK) {
            tree.addChild(parseMainFuncDef());
        } else {
            Error.syntaxError(lastWord.getLineNum(), "no main function", "e");
            return;
        }
        printToFile("CompUnit");
    }

    public GrammarNode parseDecl() {
        GrammarNode node = new GrammarNode("Decl");
        if (curType == LexType.CONSTTK) {
            node.addChild(parseConstDecl());
        } else if (curType == LexType.INTTK) {
            node.addChild(parseVarDecl());
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong declaration", "e");
            return node;
        }
        return node;
    }

    public GrammarNode parseFuncDef() {
        GrammarNode node = new GrammarNode("FuncDef");
        node.addChild(parseFuncType());
        node.addChild(parseIdent());
        if (curType != LexType.LPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "func define lack of '('", "e");
            return node;
        } else {
            node.addChild(new GrammarNode("("));
        }
        getWord();
        if (curType != LexType.RPARENT) {
            node.addChild(parseFuncFParams());
        }
        if (curType != LexType.RPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "func define lack of ')'", "j");
            return node;
        } else {
            node.addChild(new GrammarNode(")"));
        }
        getWord();
        node.addChild(parseBlock());
        printToFile("FuncDef");
        return node;
    }

    public GrammarNode parseMainFuncDef() {
        GrammarNode node = new GrammarNode("MainFuncDef");
        node.addChild(new GrammarNode("int"));
        getWord();//main
        node.addChild(new GrammarNode("main"));
        getWord();
        if (curType != LexType.LPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "main func lack of '('", "e");
            return node;
        } else {
            node.addChild(new GrammarNode("("));
        }
        getWord();
        if (curType != LexType.RPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "main func lack of ')'", "j");
            return node;
        } else {
            node.addChild(new GrammarNode(")"));
        }
        getWord();
        node.addChild(parseBlock());
        printToFile("MainFuncDef");
        return node;
    }

    public GrammarNode parseConstDecl() {
        GrammarNode node = new GrammarNode("ConstDecl");
        if (curType != LexType.CONSTTK) {
            Error.syntaxError(lastWord.getLineNum(), "wrong const declaration", "e");
            return node;
        } else {
            node.addChild(new GrammarNode("const"));
        }
        getWord();//int
        node.addChild(parseBType());
        node.addChild(parseConstDef());
        while (curType == LexType.COMMA) {
            node.addChild(new GrammarNode(","));
            getWord();
            node.addChild(parseConstDef());
        }
        if (curType == LexType.SEMICN) {
            node.addChild(new GrammarNode(";"));
            getWord();
        } else {
            Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
            return node;
        }
        printToFile("ConstDecl");
        return node;
    }

    public GrammarNode parseVarDecl() {
        GrammarNode node = new GrammarNode("VarDecl");
        node.addChild(parseBType());
        node.addChild(parseVarDef());
        while (curType == LexType.COMMA) {
            node.addChild(new GrammarNode(","));
            getWord();
            node.addChild(parseVarDef());
        }
        if (curType == LexType.SEMICN) {
            node.addChild(new GrammarNode(";"));
            getWord();
        } else {
            Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
            return node;
        }
        printToFile("VarDecl");
        return node;
    }

    public GrammarNode parseBType() {
        GrammarNode node = new GrammarNode("BType");
        if (curType == LexType.INTTK) {
            node.addChild(new GrammarNode("int"));
            getWord();
        } else {
            Error.syntaxError(curWord.getLineNum(), "wrong Btype", "e");
            return node;
        }
        return node;
    }

    public GrammarNode parseConstDef() {
        GrammarNode node = new GrammarNode("ConstDef");
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            node.addChild(new GrammarNode("["));
            getWord();
            node.addChild(parseConstExp());
            if (curType == LexType.RBRACK) {
                node.addChild(new GrammarNode("]"));
                getWord();
            } else {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                return node;
            }
        }
        if (curType == LexType.ASSIGN) {
            node.addChild(new GrammarNode("="));
            getWord();
            node.addChild(parseConstInitVal());
            printToFile("ConstDef");
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong const define", "e");
            return node;
        }
        return node;
    }

    public GrammarNode parseVarDef() {
        GrammarNode node = new GrammarNode("VarDef");
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            node.addChild(new GrammarNode("["));
            getWord();
            node.addChild(parseConstExp());
            if (curType == LexType.RBRACK) {
                node.addChild(new GrammarNode("]"));
                getWord();
            } else {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                return node;
            }
        }
        if (curType == LexType.ASSIGN) {
            node.addChild(new GrammarNode("="));
            getWord();
            node.addChild(parseInitVal());
        }
        printToFile("VarDef");
        return node;
    }

    public GrammarNode parseIdent() {
        GrammarNode node = new GrammarNode("Ident");
        if (curType != LexType.IDENFR) {
            Error.syntaxError(lastWord.getLineNum(), "lack an identity", "e");
            return node;
        }
        node.addChild(new GrammarNode(curToken));
        getWord();
        return node;
    }

    public GrammarNode parseConstExp() {
        GrammarNode node = new GrammarNode("ConstExp");
        node.addChild(parseAddExp());
        printToFile("ConstExp");
        return node;
    }

    public GrammarNode parseConstInitVal() {
        GrammarNode node = new GrammarNode("ConstInitVal");
        if (curType != LexType.LBRACE) {
            node.addChild(parseConstExp());
        } else {
            node.addChild(new GrammarNode("{"));
            getWord();
            node.addChild(parseConstInitVal());
            while (curType == LexType.COMMA) {
                node.addChild(new GrammarNode(","));
                getWord();
                node.addChild(parseConstInitVal());
            }
            if (curType != LexType.RBRACE) {
                Error.syntaxError(lastWord.getLineNum(), "lack '}' after constInitVal", "e");
                return node;
            } else {
                node.addChild(new GrammarNode("}"));
                getWord();
            }
        }
        printToFile("ConstInitVal");
        return node;
    }

    public GrammarNode parseInitVal() {
        GrammarNode node = new GrammarNode("InitVal");
        if (curType != LexType.LBRACE) {
            node.addChild(parseExp());
        } else {
            node.addChild(new GrammarNode("{"));
            getWord();
            node.addChild(parseInitVal());
            while (curType == LexType.COMMA) {
                node.addChild(new GrammarNode(","));
                getWord();
                node.addChild(parseInitVal());
            }
            if (curType != LexType.RBRACE) {
                Error.syntaxError(lastWord.getLineNum(), "lack '}' after initVal", "e");
                return node;
            } else {
                node.addChild(new GrammarNode("}"));
                getWord();
            }
        }
        printToFile("InitVal");
        return node;
    }

    public GrammarNode parseAddExp() {
        GrammarNode node = new GrammarNode("AddExp");
        node.addChild(parseMulExp());
        while (curType == LexType.PLUS || curType == LexType.MINU) {
            printToFile("AddExp");
            GrammarNode newNode = new GrammarNode("AddExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseMulExp());
            node = newNode;
        }
        printToFile("AddExp");
        return node;
    }

    public GrammarNode parseExp() {
        GrammarNode node = new GrammarNode("Exp");
        node.addChild(parseAddExp());
        printToFile("Exp");
        return node;
    }

    public GrammarNode parseMulExp() {
        GrammarNode node = new GrammarNode("MulExp");
        node.addChild(parseUnaryExp());
        while (curType == LexType.MULT || curType == LexType.DIV || curType == LexType.MOD) {
            printToFile("MulExp");
            GrammarNode newNode = new GrammarNode("MulExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseUnaryExp());
            node = newNode;
        }
        printToFile("MulExp");
        return node;
    }

    public GrammarNode parseUnaryExp() {
        GrammarNode node = new GrammarNode("UnaryExp");
        if (curType == LexType.LPARENT || (curType == LexType.IDENFR && nextWordType() != LexType.LPARENT)
                || curType == LexType.INTCON) {
            node.addChild(parsePrimaryExp());
        } else if (curType == LexType.IDENFR && nextWordType() == LexType.LPARENT) {
            node.addChild(parseIdent());
            node.addChild(new GrammarNode("("));
            getWord();//'('的下一个
            if (curType != LexType.RPARENT) {
                node.addChild(parseFuncRParams());
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ')'", "j");
                return node;
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
        } else if (curType == LexType.PLUS || curType == LexType.MINU || curType == LexType.NOT) {
            node.addChild(parseUnaryOp());
            node.addChild(parseUnaryExp());
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong UnaryExp", "e");
            return node;
        }
        printToFile("UnaryExp");
        return node;
    }

    public GrammarNode parsePrimaryExp() {
        GrammarNode node = new GrammarNode("PrimaryExp");
        if (curType == LexType.LPARENT) {
            node.addChild(new GrammarNode("("));
            getWord();
            node.addChild(parseExp());
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ')'", "j");
                return node;
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
        } else if (curType == LexType.IDENFR) {
            node.addChild(parseLVal());
        } else if (curType == LexType.INTCON) {
            node.addChild(parseNumber());
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong primaryExp", "e");
            return node;
        }
        printToFile("PrimaryExp");
        return node;
    }

    public GrammarNode parseFuncRParams() {
        GrammarNode node = new GrammarNode("FuncRParams");
        node.addChild(parseExp());
        while (curType == LexType.COMMA) {
            node.addChild(new GrammarNode(","));
            getWord();
            node.addChild(parseExp());
        }
        printToFile("FuncRParams");
        return node;
    }

    public GrammarNode parseUnaryOp() {
        GrammarNode node = new GrammarNode("UnaryOp");
        if (curType == LexType.PLUS || curType == LexType.MINU || curType == LexType.NOT) {
            node.addChild(new GrammarNode(curToken));
            getWord();
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong unaryOp", "e");
            return node;
        }
        printToFile("UnaryOp");
        return node;
    }

    public GrammarNode parseLVal() {
        GrammarNode node = new GrammarNode("LVal");
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            node.addChild(new GrammarNode("["));
            getWord();
            node.addChild(parseExp());
            if (curType != LexType.RBRACK) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                return node;
            } else {
                node.addChild(new GrammarNode("]"));
                getWord();
            }
        }
        printToFile("LVal");
        return node;
    }

    public GrammarNode parseNumber() {
        GrammarNode node = new GrammarNode("Number");
        if (curType == LexType.INTCON) {
            node.addChild(new GrammarNode(curToken));
            getWord();
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong number", "e");
            return node;
        }
        printToFile("Number");
        return node;
    }

    public GrammarNode parseFuncType() {
        GrammarNode node = new GrammarNode("FuncType");
        if (curType == LexType.VOIDTK || curType == LexType.INTTK) {
            node.addChild(new GrammarNode(curToken));
            getWord();
        } else {
            Error.syntaxError(lastWord.getLineNum(), "func type error", "e");
            return node;
        }
        printToFile("FuncType");
        return node;
    }

    public GrammarNode parseFuncFParams() {
        GrammarNode node = new GrammarNode("FuncFParams");
        node.addChild(parseFuncFParam());
        while (curType == LexType.COMMA) {
            node.addChild(new GrammarNode(","));
            getWord();
            node.addChild(parseFuncFParam());
        }
        printToFile("FuncFParams");
        return node;
    }

    public GrammarNode parseBlock() {
        GrammarNode node = new GrammarNode("Block");
        if (curType != LexType.LBRACE) {
            Error.syntaxError(lastWord.getLineNum(), "block lack of '{'", "e");
            return node;
        } else {
            node.addChild(new GrammarNode("{"));
            getWord();
        }
        while (curType != LexType.RBRACE) {
            if (curType == LexType.EOF) {
                Error.syntaxError(lastWord.getLineNum(), "block lack of '}'", "e");
                return node;
            }
            node.addChild(parseBlockItem());
        }
        node.addChild(new GrammarNode("}"));
        getWord();//go ahead from right brace
        printToFile("Block");
        return node;
    }

    public GrammarNode parseFuncFParam() {
        GrammarNode node = new GrammarNode("FuncFParam");
        node.addChild(parseBType());
        node.addChild(parseIdent());
        if (curType == LexType.LBRACK) {
            node.addChild(new GrammarNode("["));
            getWord();
            if (curType != LexType.RBRACK) {
                Error.syntaxError(lastWord.getLineNum(), "funcFParam lack of ']'", "k");
                return node;
            } else {
                node.addChild(new GrammarNode("]"));
                getWord();
            }
            while (curType == LexType.LBRACK) {
                node.addChild(new GrammarNode("["));
                getWord();
                if (curType != LexType.RBRACK) {
                    node.addChild(parseConstExp());
                }
                if (curType != LexType.RBRACK) {
                    Error.syntaxError(lastWord.getLineNum(), "funcFParam lack of']'", "k");
                    return node;
                } else {
                    node.addChild(new GrammarNode("]"));
                    getWord();
                }
            }
        }
        printToFile("FuncFParam");
        return node;
    }

    public GrammarNode parseBlockItem() {
        GrammarNode node = new GrammarNode("BlockItem");
        if (curType == LexType.CONSTTK || curType == LexType.INTTK) {
            node.addChild(parseDecl());
        } else {
            node.addChild(parseStmt());
        }
        return node;
    }

    public GrammarNode parseStmt() {
        GrammarNode node = new GrammarNode("Stmt");
        if (curType == LexType.LBRACE) {
            node.addChild(parseBlock());
        } else if (curType == LexType.IFTK) {
            node.addChild(new GrammarNode("if"));
            getWord();
            if (curType != LexType.LPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "if lack of '('", "e");
                return node;
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            node.addChild(parseCond());
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "if lack of ')'", "j");
                return node;
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            node.addChild(parseStmt());
            if (curType == LexType.ELSETK) {
                node.addChild(new GrammarNode("else"));
                getWord();
                node.addChild(parseStmt());
            }
        } else if (curType == LexType.FORTK) {
            node.addChild(new GrammarNode("for"));
            getWord();
            if (curType != LexType.LPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of '('", "e");
                return node;
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                node.addChild(parseForStmt());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ';'", "i");
                return node;
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                node.addChild(parseCond());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ';'", "i");
                return node;
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
            if (curType != LexType.RPARENT) {
                node.addChild(parseForStmt());
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ')'", "j");
                return node;
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            node.addChild(parseStmt());
        } else if (curType == LexType.BREAKTK || curType == LexType.CONTINUETK) {
            node.addChild(new GrammarNode(curToken));
            getWord();
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "break or continue lack of ';'", "i");
                return node;
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
        } else if (curType == LexType.RETURNTK) {
            node.addChild(new GrammarNode("return"));
            getWord();
            if (curType != LexType.SEMICN) {
                node.addChild(parseExp());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "return lack of ';'", "i");
                return node;
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
        } else if (curType == LexType.PRINTFTK) {
            node.addChild(new GrammarNode("printf"));
            getWord();
            if (curType != LexType.LPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of '('", "e");
                return node;
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            if (curType != LexType.STRCON) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of string", "e");
                return node;
            } else {
                node.addChild(new GrammarNode(curToken));
                getWord();
            }
            while (curType == LexType.COMMA) {
                node.addChild(new GrammarNode(","));
                getWord();
                parseExp();
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of ')'", "j");
                return node;
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of ';'", "i");
                return node;
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
        } else {
            LexType tempType = curType;
            int tempPos = curPos;
            while (tempType != LexType.SEMICN && tempType != LexType.ASSIGN
                    && tempType != LexType.EOF && tempType != LexType.RBRACE) {
                if (tempPos < words.size()) {
                    tempType = words.get(tempPos++).getType();
                } else {
                    tempType = LexType.EOF;
                }
            }
            if (tempType == LexType.SEMICN) {//[Exp] ';'
                if (tempPos != curPos) {
                    node.addChild(parseExp());
                }
                if (curType != LexType.SEMICN) {
                    Error.syntaxError(lastWord.getLineNum(), "block lack of ')'", "i");
                    return node;
                } else {
                    node.addChild(new GrammarNode(";"));
                    getWord();
                }
            } else if (tempType == LexType.ASSIGN) {
                if (tempPos < words.size() && words.get(tempPos).getType() == LexType.GETINTTK) { //LVal '=' 'getint''('')'';'
                    node.addChild(parseLVal());
                    if (curType != LexType.ASSIGN) {
                        Error.syntaxError(lastWord.getLineNum(), "wrong block", "e");
                        return node;
                    } else {
                        node.addChild(new GrammarNode("="));
                        getWord();
                    }
                    if (curType != LexType.GETINTTK) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of getint", "e");
                        return node;
                    } else {
                        node.addChild(new GrammarNode("getint"));
                        getWord();
                    }
                    if (curType != LexType.LPARENT) {
                        Error.syntaxError(lastWord.getLineNum(), "getint lack of '('", "e");
                        return node;
                    } else {
                        node.addChild(new GrammarNode("("));
                        getWord();
                    }
                    if (curType != LexType.RPARENT) {
                        Error.syntaxError(lastWord.getLineNum(), "getint lack of ')'", "j");
                        return node;
                    } else {
                        node.addChild(new GrammarNode(")"));
                        getWord();
                    }
                    if (curType != LexType.SEMICN) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
                        return node;
                    } else {
                        node.addChild(new GrammarNode(";"));
                        getWord();
                    }
                } else { //LVal '=' Exp ';'
                    node.addChild(parseLVal());
                    if (curType != LexType.ASSIGN) {
                        Error.syntaxError(lastWord.getLineNum(), "wrong block", "e");
                        return node;
                    } else {
                        node.addChild(new GrammarNode("="));
                        getWord();
                    }
                    node.addChild(parseExp());
                    if (curType != LexType.SEMICN) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "e");
                        return node;
                    } else {
                        node.addChild(new GrammarNode(";"));
                        getWord();
                    }
                }
            } else {
                Error.syntaxError(lastWord.getLineNum(), "wrong statement", "e");
                return node;
            }
        }
        printToFile("Stmt");
        return node;
    }

    public GrammarNode parseCond() {
        GrammarNode node = new GrammarNode("Cond");
        node.addChild(parseLOrExp());
        printToFile("Cond");
        return node;
    }

    public GrammarNode parseForStmt() {
        GrammarNode node = new GrammarNode("ForStmt");
        node.addChild(parseLVal());
        if (curType != LexType.ASSIGN) {
            Error.syntaxError(lastWord.getLineNum(), "wrong ForStmt", "e");
            return node;
        } else {
            node.addChild(new GrammarNode("="));
            getWord();
        }
        node.addChild(parseExp());
        printToFile("ForStmt");
        return node;
    }

    public GrammarNode parseLOrExp() {
        GrammarNode node = new GrammarNode("LOrExp");
        node.addChild(parseLAndExp());
        while (curType == LexType.OR) {
            printToFile("LOrExp");
            GrammarNode newNode = new GrammarNode("LOrExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseLAndExp());
            node = newNode;
        }
        printToFile("LOrExp");
        return node;
    }

    public GrammarNode parseLAndExp() {
        GrammarNode node = new GrammarNode("LAndExp");
        node.addChild(parseEqExp());
        while (curType == LexType.AND) {
            printToFile("LAndExp");
            GrammarNode newNode = new GrammarNode("LAndExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseEqExp());
            node = newNode;
        }
        printToFile("LAndExp");
        return node;
    }

    public GrammarNode parseEqExp() {
        GrammarNode node = new GrammarNode("EqExp");
        node.addChild(parseRelExp());
        while (curType == LexType.EQL || curType == LexType.NEQ) {
            printToFile("EqExp");
            GrammarNode newNode = new GrammarNode("EqExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseRelExp());
            node = newNode;
        }
        printToFile("EqExp");
        return node;
    }

    public GrammarNode parseRelExp() {
        GrammarNode node = new GrammarNode("RelExp");
        node.addChild(parseAddExp());
        while (curType == LexType.LSS || curType == LexType.GRE || curType == LexType.LEQ || curType == LexType.GEQ) {
            printToFile("RelExp");
            GrammarNode newNode = new GrammarNode("RelExp");
            newNode.addChild(node);
            newNode.addChild(new GrammarNode(curToken));
            getWord();
            newNode.addChild(parseAddExp());
            node = newNode;
        }
        printToFile("RelExp");
        return node;
    }

    public void printToFile(String str) {
        if (print) {
            try {
                out.write("<" + str + ">\n");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void printTree() {
        if (print) {
            tree.print();
        }
    }
}
