import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.HashMap;

public class Parser {
    private final ArrayList<Word> words = new ArrayList<>();
    private final GrammarNode tree = new GrammarNode("CompUnit");
    private LexType curType;
    private String curToken;

    private Word curWord;
    private Word lastWord;
    private final BufferedWriter out;

    private int curPos = 0;
    private final HashMap<Integer, Table> tableStack = new HashMap<>();
    private int level = 0;
    private Table curTable = new Table();
    private boolean print = false;

    private int paraRDimen = 0;

    private int loopNum = 0;

    private TableItem nowFunc = null;


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
        tableStack.put(level, curTable);
    }

    public GrammarNode parse() {
        getWord();
        lastWord = curWord;
        if (curType != LexType.EOF) {
            parseCompUnit();
        }
        return tree;
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
        }
        return node;
    }

    public GrammarNode parseFuncDef() {
        GrammarNode node = new GrammarNode("FuncDef");
        String funcType = curToken;
        node.addChild(parseFuncType());
        String funcName = curToken;
        if (curTable.hasSameName(funcName)) {
            Error.meaningError(curWord.getLineNum(), "redefined func", "b");
            Error.printError(curWord.getLineNum(), "b");
        }
        node.addChild(parseIdent());
        nowFunc = new TableItem(funcName, funcType, "func", level, 0);
        curTable.addItem(new TableItem(funcName, funcType, "func", level, 0));
        addLevel();//new table
        if (curType != LexType.LPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "func define lack of '('", "e");
        } else {
            node.addChild(new GrammarNode("("));
        }
        getWord();
        if (curType != LexType.RPARENT && curType == LexType.INTTK) {
            node.addChild(parseFuncFParams());
        }
        if (curType != LexType.RPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "func define lack of ')'", "j");
            Error.printError(lastWord.getLineNum(), "j");
        } else {
            node.addChild(new GrammarNode(")"));
        }
        getWord();
        node.addChild(parseBlock(true));
        deleteLevel();//delete table
        nowFunc = null;
        printToFile("FuncDef");
        return node;
    }

    public GrammarNode parseMainFuncDef() {
        GrammarNode node = new GrammarNode("MainFuncDef");
        node.addChild(new GrammarNode("int"));
        getWord();//main
        if (curTable.hasSameName("main")) {
            Error.meaningError(curWord.getLineNum(), "redefined main func", "b");
            Error.printError(curWord.getLineNum(), "b");
        }
        nowFunc = new TableItem("main", "int", "func", level, 0);
        curTable.addItem(new TableItem("main", "int", "func", level, 0));
        addLevel();
        node.addChild(new GrammarNode("main"));
        getWord();
        if (curType != LexType.LPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "main func lack of '('", "e");
        } else {
            node.addChild(new GrammarNode("("));
        }
        getWord();
        if (curType != LexType.RPARENT) {
            Error.syntaxError(lastWord.getLineNum(), "main func lack of ')'", "j");
            Error.printError(lastWord.getLineNum(), "j");
        } else {
            node.addChild(new GrammarNode(")"));
        }
        getWord();
        node.addChild(parseBlock(true));
        deleteLevel();
        nowFunc = null;
        printToFile("MainFuncDef");
        return node;
    }

    public GrammarNode parseConstDecl() {
        GrammarNode node = new GrammarNode("ConstDecl");
        if (curType != LexType.CONSTTK) {
            Error.syntaxError(lastWord.getLineNum(), "wrong const declaration", "e");
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
            Error.printError(lastWord.getLineNum(), "i");
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
            Error.printError(lastWord.getLineNum(), "i");
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
        }
        return node;
    }

    public GrammarNode parseConstDef() {
        GrammarNode node = new GrammarNode("ConstDef");
        String constName = curToken;
        if (curTable.hasSameName(constName)) {
            Error.meaningError(curWord.getLineNum(), "redefined const", "b");
            Error.printError(curWord.getLineNum(), "b");
        }
        int dim = 0;
        ArrayList<Integer> dimenLength = new ArrayList<>();
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            dim++;
            node.addChild(new GrammarNode("["));
            getWord();
            GrammarNode node1 = parseConstExp();
            node.addChild(node1);
            dimenLength.add(Calculator.calConstExp(node1, tableStack, level));
            if (curType == LexType.RBRACK) {
                node.addChild(new GrammarNode("]"));
                getWord();
            } else {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                Error.printError(lastWord.getLineNum(), "k");
            }
        }
        ArrayList<Integer> arrayConstInit = new ArrayList<>();
        if (curType == LexType.ASSIGN) {
            node.addChild(new GrammarNode("="));
            getWord();
            node.addChild(parseConstInitVal(arrayConstInit));
            printToFile("ConstDef");
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong const define", "e");
        }
        TableItem constItem = new TableItem(constName, "int", "const", level, dim);
        constItem.setArrayDimen(dimenLength);//保存数组每一维大小
        constItem.setArrayConstInit(arrayConstInit);//保存数组初值
        if (dim == 0) {
            constItem.setConstInit(arrayConstInit.get(0));//保存常数初值
        }
        curTable.addItem(constItem);
        return node;
    }

    public GrammarNode parseVarDef() {
        GrammarNode node = new GrammarNode("VarDef");
        String varName = curToken;
        if (curTable.hasSameName(varName)) {
            Error.meaningError(curWord.getLineNum(), "redefined var", "b");
            Error.printError(curWord.getLineNum(), "b");
        }
        int dim = 0;
        ArrayList<Integer> dimenLength = new ArrayList<>();
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            dim++;
            node.addChild(new GrammarNode("["));
            getWord();
            GrammarNode node1 = parseConstExp();
            node.addChild(node1);
            dimenLength.add(Calculator.calConstExp(node1, tableStack, level));
            if (curType == LexType.RBRACK) {
                node.addChild(new GrammarNode("]"));
                getWord();
            } else {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                Error.printError(lastWord.getLineNum(), "k");
            }
        }
        TableItem varItem = new TableItem(varName, "int", "var", level, dim);
        varItem.setArrayDimen(dimenLength);
        curTable.addItem(varItem);
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

    public GrammarNode parseConstInitVal(ArrayList<Integer> arrayConstInit) {
        GrammarNode node = new GrammarNode("ConstInitVal");
        if (curType != LexType.LBRACE) {
            GrammarNode node1 = parseConstExp();
            node.addChild(node1);
            arrayConstInit.add(Calculator.calConstExp(node1, tableStack, level));
        } else {
            node.addChild(new GrammarNode("{"));
            getWord();
            if (curType != LexType.RBRACE) {
                node.addChild(parseConstInitVal(arrayConstInit));
            }
            while (curType == LexType.COMMA) {
                node.addChild(new GrammarNode(","));
                getWord();
                node.addChild(parseConstInitVal(arrayConstInit));
            }
            if (curType != LexType.RBRACE) {
                Error.syntaxError(lastWord.getLineNum(), "lack '}' after constInitVal", "e");
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
            if (curType != LexType.RBRACE) {
                node.addChild(parseInitVal());
            }
            while (curType == LexType.COMMA) {
                node.addChild(new GrammarNode(","));
                getWord();
                node.addChild(parseInitVal());
            }
            if (curType != LexType.RBRACE) {
                Error.syntaxError(lastWord.getLineNum(), "lack '}' after initVal", "e");
            } else {
                node.addChild(new GrammarNode("}"));
                getWord();
            }
        }
        printToFile("InitVal");
        return node;
    }

    public GrammarNode parseAddExp() {
        paraRDimen = 0;
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
        paraRDimen = 0;
        GrammarNode node = new GrammarNode("Exp");
        node.addChild(parseAddExp());
        printToFile("Exp");
        return node;
    }

    public GrammarNode parseMulExp() {
        paraRDimen = 0;
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
        paraRDimen = 0;
        GrammarNode node = new GrammarNode("UnaryExp");
        if (curType == LexType.LPARENT || (curType == LexType.IDENFR && nextWordType() != LexType.LPARENT)
                || curType == LexType.INTCON) {
            node.addChild(parsePrimaryExp());
        } else if (curType == LexType.IDENFR && nextWordType() == LexType.LPARENT) {
            String funcName = curToken;
            if (!hasDefinedFunc(funcName)) {
                Error.meaningError(curWord.getLineNum(), "undefined func", "c");
                Error.printError(curWord.getLineNum(), "c");
            }
            TableItem funcItem = getFuncItem(funcName);
            int funcLine = curWord.getLineNum();
            node.addChild(parseIdent());
            node.addChild(new GrammarNode("("));
            getWord();//'('的下一个
            ArrayList<Integer> parasRDimen = new ArrayList<>();
            if (curType != LexType.RPARENT && (curType == LexType.LPARENT || curType == LexType.IDENFR ||
                    curType == LexType.INTCON || curType == LexType.PLUS || curType == LexType.MINU)) {
                node.addChild(parseFuncRParams(parasRDimen));
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ')'", "j");
                Error.printError(lastWord.getLineNum(), "j");
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            if (funcItem != null) {
                int parasRNum = parasRDimen.size();
                if (parasRNum != funcItem.getParasNum()) {
                    Error.meaningError(funcLine, "wrong paras num", "d");
                    Error.printError(funcLine, "d");
                } else {
                    for (int i = 0; i < parasRNum; i++) {
                        if ((int) parasRDimen.get(i) != funcItem.getParasDimen().get(i)) {
                            Error.meaningError(funcLine, "wrong paras type " + i, "d");
                            Error.printError(funcLine, "e");
                        }
                    }
                }
                if (funcItem.getType().equals("void")) {//void func
                    paraRDimen = -1;
                }
            }
        } else if (curType == LexType.PLUS || curType == LexType.MINU || curType == LexType.NOT) {
            node.addChild(parseUnaryOp());
            node.addChild(parseUnaryExp());
        } else {
            Error.syntaxError(lastWord.getLineNum(), "wrong UnaryExp", "e");
        }

        printToFile("UnaryExp");
        return node;
    }

    public GrammarNode parsePrimaryExp() {
        paraRDimen = 0;
        GrammarNode node = new GrammarNode("PrimaryExp");
        if (curType == LexType.LPARENT) {
            node.addChild(new GrammarNode("("));
            getWord();
            node.addChild(parseExp());
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ')'", "j");
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
        }
        printToFile("PrimaryExp");
        return node;
    }

    public GrammarNode parseFuncRParams(ArrayList<Integer> parasRDimen) {
        GrammarNode node = new GrammarNode("FuncRParams");
        node.addChild(parseExp());
        parasRDimen.add(paraRDimen);
        paraRDimen = 0;
        while (curType == LexType.COMMA) {
            node.addChild(new GrammarNode(","));
            getWord();
            node.addChild(parseExp());
            parasRDimen.add(paraRDimen);
            paraRDimen = 0;
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
        }
        printToFile("UnaryOp");
        return node;
    }

    public GrammarNode parseLVal() {
        paraRDimen = 0;
        GrammarNode node = new GrammarNode("LVal");
        String varName = curToken;
        if (!hasDefinedVar(varName) && !isConst(varName)) {
            Error.meaningError(curWord.getLineNum(), "undefined var", "c");
            Error.printError(curWord.getLineNum(), "c");
        }
        TableItem varItem = getVarItem(varName);
        int tempParaDim = 0;
        if (varItem != null) {
            tempParaDim = varItem.getDimension();
        }
        node.addChild(parseIdent());
        while (curType == LexType.LBRACK) {
            tempParaDim--;
            node.addChild(new GrammarNode("["));
            getWord();
            node.addChild(parseExp());
            if (curType != LexType.RBRACK) {
                Error.syntaxError(lastWord.getLineNum(), "lack of ']'", "k");
                Error.printError(lastWord.getLineNum(), "k");
            } else {
                node.addChild(new GrammarNode("]"));
                getWord();
            }
        }
        paraRDimen = tempParaDim;
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

    public GrammarNode parseBlock(boolean isFuncDef) {
        if (!isFuncDef) {
            addLevel();
        }
        GrammarNode node = new GrammarNode("Block");
        if (curType != LexType.LBRACE) {
            Error.syntaxError(lastWord.getLineNum(), "block lack of '{'", "e");
        } else {
            node.addChild(new GrammarNode("{"));
            getWord();
        }
        while (curType != LexType.RBRACE) {
            if (curType == LexType.EOF) {
                Error.syntaxError(lastWord.getLineNum(), "block lack of '}'", "e");
                break;
            }
            node.addChild(parseBlockItem());
        }
        if (curType == LexType.RBRACE) {
            boolean hasReturn = false;
            if (nowFunc != null && nowFunc.getType().equals("void")) {
                hasReturn = true;
            }
            if (node.getLastChild() != null && node.getLastChild().getNodeName().equals("BlockItem")) {
                GrammarNode child = node.getLastChild();
                if (child.getLastChild() != null && child.getLastChild().getNodeName().equals("Stmt")) {
                    GrammarNode stmt = child.getLastChild();
                    if (stmt.getLastChild() != null && stmt.getChildren().get(0).getNodeName().equals("return") &&
                            (stmt.getChildren().get(1) != null && stmt.getChildren().get(1).getNodeName().equals("Exp"))) {
                        hasReturn = true;
                    }
                }
            }
            if (!hasReturn && isFuncDef) {
                Error.meaningError(curWord.getLineNum(), "func def lack of return", "g");
                Error.printError(curWord.getLineNum(), "g");
            }
            node.addChild(new GrammarNode("}"));
        }
        getWord();//go ahead from right brace
        printToFile("Block");
        if (!isFuncDef) {
            deleteLevel();
        }
        return node;
    }

    public GrammarNode parseFuncFParam() {
        GrammarNode node = new GrammarNode("FuncFParam");
        String paramType = curToken;
        node.addChild(parseBType());
        String paramName = curToken;
        if (curTable.hasSameName(paramName)) {
            Error.meaningError(curWord.getLineNum(), "redefined param", "b");
            Error.printError(curWord.getLineNum(), "b");
        }
        node.addChild(parseIdent());
        int paramDim = 0;
        if (curType == LexType.LBRACK) {
            paramDim++;
            node.addChild(new GrammarNode("["));
            getWord();
            if (curType != LexType.RBRACK) {
                Error.syntaxError(lastWord.getLineNum(), "funcFParam lack of ']'", "k");
                Error.printError(lastWord.getLineNum(), "k");
            } else {
                node.addChild(new GrammarNode("]"));
                getWord();
            }
            while (curType == LexType.LBRACK) {
                paramDim++;
                node.addChild(new GrammarNode("["));
                getWord();
                if (curType != LexType.RBRACK) {
                    node.addChild(parseConstExp());
                }
                if (curType != LexType.RBRACK) {
                    Error.syntaxError(lastWord.getLineNum(), "funcFParam lack of']'", "k");
                    Error.printError(lastWord.getLineNum(), "k");
                } else {
                    node.addChild(new GrammarNode("]"));
                    getWord();
                }
            }
        }
        curTable.addItem(new TableItem(paramName, paramType, "param", level, paramDim));
        tableStack.get(level - 1).getLastItem().addPara(paramDim);
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
            node.addChild(parseBlock(false));
        } else if (curType == LexType.IFTK) {
            node.addChild(new GrammarNode("if"));
            getWord();
            if (curType != LexType.LPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "if lack of '('", "e");
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            node.addChild(parseCond());
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "if lack of ')'", "j");
                Error.printError(lastWord.getLineNum(), "j");
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
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                node.addChild(parseForStmt());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ';'", "i");
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                node.addChild(parseCond());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ';'", "i");
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
            if (curType != LexType.RPARENT) {
                node.addChild(parseForStmt());
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "for lack of ')'", "j");
                Error.printError(lastWord.getLineNum(), "j");
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            loopNum++;
            node.addChild(parseStmt());
            loopNum--;
        } else if (curType == LexType.BREAKTK || curType == LexType.CONTINUETK) {
            if (loopNum == 0) {
                Error.meaningError(curWord.getLineNum(), "break or continue out of loop", "m");
                Error.printError(curWord.getLineNum(), "m");
            }
            node.addChild(new GrammarNode(curToken));
            getWord();
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "break or continue lack of ';'", "i");
                Error.printError(lastWord.getLineNum(), "i");
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
        } else if (curType == LexType.RETURNTK) {
            int returnLine = curWord.getLineNum();
            node.addChild(new GrammarNode("return"));
            getWord();
            if (nowFunc == null) {
                Error.meaningError(returnLine, "return out of func", "f");
                Error.printError(returnLine, "f");
            }
            if (curType != LexType.SEMICN) {
                if (nowFunc != null && nowFunc.getType().equals("void")) {
                    Error.meaningError(returnLine, "return exp in void func", "f");
                    Error.printError(returnLine, "f");
                }
                node.addChild(parseExp());
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "return lack of ';'", "i");
                Error.printError(lastWord.getLineNum(), "i");
            } else {
                node.addChild(new GrammarNode(";"));
                getWord();
            }
        } else if (curType == LexType.PRINTFTK) {
            int printLine = curWord.getLineNum();
            node.addChild(new GrammarNode("printf"));
            getWord();
            if (curType != LexType.LPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of '('", "e");
            } else {
                node.addChild(new GrammarNode("("));
                getWord();
            }
            int numCnt = 0;
            if (curType != LexType.STRCON) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of string", "e");
            } else {
                numCnt = curWord.getNumber();
                node.addChild(new GrammarNode(curToken));
                getWord();
            }
            while (curType == LexType.COMMA) {
                numCnt--;
                node.addChild(new GrammarNode(","));
                getWord();
                node.addChild(parseExp());
            }
            if (numCnt != 0) {
                Error.meaningError(printLine, "printf string and exp not match", "l");
                Error.printError(printLine, "l");
            }
            if (curType != LexType.RPARENT) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of ')'", "j");
                Error.printError(lastWord.getLineNum(), "j");
            } else {
                node.addChild(new GrammarNode(")"));
                getWord();
            }
            if (curType != LexType.SEMICN) {
                Error.syntaxError(lastWord.getLineNum(), "printf lack of ';'", "i");
                Error.printError(lastWord.getLineNum(), "i");
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
                    Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
                    Error.printError(lastWord.getLineNum(), "i");
                } else {
                    node.addChild(new GrammarNode(";"));
                    getWord();
                }
            } else if (tempType == LexType.ASSIGN) {
                if (tempPos < words.size() && words.get(tempPos).getType() == LexType.GETINTTK) { //LVal '=' 'getint''('')'';'
                    node.addChild(parseLVal());
                    if (curType != LexType.ASSIGN) {
                        Error.syntaxError(lastWord.getLineNum(), "wrong block", "e");
                    } else {
                        node.addChild(new GrammarNode("="));
                        getWord();
                    }
                    if (curType != LexType.GETINTTK) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of getint", "e");
                    } else {
                        node.addChild(new GrammarNode("getint"));
                        getWord();
                    }
                    if (curType != LexType.LPARENT) {
                        Error.syntaxError(lastWord.getLineNum(), "getint lack of '('", "e");
                    } else {
                        node.addChild(new GrammarNode("("));
                        getWord();
                    }
                    if (curType != LexType.RPARENT) {
                        Error.syntaxError(lastWord.getLineNum(), "getint lack of ')'", "j");
                        Error.printError(lastWord.getLineNum(), "j");
                    } else {
                        node.addChild(new GrammarNode(")"));
                        getWord();
                    }
                    if (curType != LexType.SEMICN) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
                        Error.printError(lastWord.getLineNum(), "i");
                    } else {
                        node.addChild(new GrammarNode(";"));
                        getWord();
                    }
                } else { //LVal '=' Exp ';'
                    String curIdent = curToken;
                    if (isConst(curIdent)) {
                        Error.meaningError(curWord.getLineNum(), "const can't be changed", "h");
                        Error.printError(curWord.getLineNum(), "h");
                    }
                    node.addChild(parseLVal());
                    if (curType != LexType.ASSIGN) {
                        Error.syntaxError(lastWord.getLineNum(), "wrong block", "e");
                    } else {
                        node.addChild(new GrammarNode("="));
                        getWord();
                    }
                    node.addChild(parseExp());
                    if (curType != LexType.SEMICN) {
                        Error.syntaxError(lastWord.getLineNum(), "lack of ';'", "i");
                        Error.printError(lastWord.getLineNum(), "i");
                    } else {
                        node.addChild(new GrammarNode(";"));
                        getWord();
                    }
                }
            } else {
                Error.syntaxError(lastWord.getLineNum(), "wrong statement", "e");
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

    public void addLevel() {
        level++;
        tableStack.put(level, new Table());
        curTable = tableStack.get(level);
    }

    public void deleteLevel() {
        tableStack.remove(level);
        level--;
        if (level >= 0) {
            curTable = tableStack.get(level);
        } else {
            curTable = null;
        }
    }

    public boolean hasDefinedVar(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "var") || tableStack.get(i).hasItem(name, "param")) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDefinedFunc(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "func")) {
                return true;
            }
        }
        return false;
    }

    public boolean isConst(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "const")) {
                return true;
            } else if (tableStack.get(i).hasItem(name, "var") || tableStack.get(i).hasItem(name, "param")) {
                return false;
            }
        }
        return false;
    }

    public TableItem getVarItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "var")) {
                return tableStack.get(i).getItem(name, "var");
            } else if (tableStack.get(i).hasItem(name, "param")) {
                return tableStack.get(i).getItem(name, "param");
            } else if (tableStack.get(i).hasItem(name, "const")) {
                return tableStack.get(i).getItem(name, "const");
            }
        }
        return null;
    }

    public TableItem getFuncItem(String name) {
        for (int i = level; i >= 0; i--) {
            if (tableStack.get(i).hasItem(name, "func")) {
                return tableStack.get(i).getItem(name, "func");
            }
        }
        return null;
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
