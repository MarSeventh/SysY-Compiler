public enum MIPSOperator {
    lw, sw,
    li, la,
    move, mflo, mfhi,

    add, addu, addi, addiu, sub, subu, subi, subiu, mult, multu, div, divu,
    slt, sltu, slti, sltiu, sgt, sle, sge, seq, sne,
    sll, srl, sra,
    and, or, xor, nor, andi, ori, xori,
    lui,
    bne, beq, bgt, ble, bge, blt,
    j, jal, jr,
    text, label, syscall,
    dataDefine, strDefine;
}
