public enum IROperator {
    note,
    main_begin, main_end, block_begin, block_end, func_begin, func_end, OUTBLOCK,
    ADD, SUB, MUL, DIV, MOD, ASSIGN,
    AND, OR, NOT,
    SLT, SLE, SEQ, SNE, SGE, SGT,
    GETINT, PRINT_INT, PRINT_STR, PRINT_PUSH,
    ARRAY_GET,
    BNE, BEQ, BGE, BGT, BLE, BLT,
    JMP,
    LABEL,
    DEF,
    CALL, RET,
    PUSH, POP;

}
