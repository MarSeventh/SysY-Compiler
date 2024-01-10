import java.util.ArrayList;

public class MIPSOptimizer {
    private ArrayList<MIPSCode> mipsCodeList = new ArrayList<>();

    public MIPSOptimizer(ArrayList<MIPSCode> mipsCodeList) {
        this.mipsCodeList = mipsCodeList;
    }

    public void optimize() {
        boolean unFinished = true;
        while (unFinished) {
            unFinished = false;
            boolean unFinished1 = mergeMoveUse();
            boolean unFinished2 = mergeAssignMove();
            boolean unFinished3 = deleteUnUseMove();
            //boolean unFinished4 = mergeSameAdd();
            boolean unFinished5 = sameAddressWrite();
            optimizeBranch();
            //unFinished = unFinished || unFinished1 || unFinished2 || unFinished3 || unFinished4 || unFinished5;
            unFinished = unFinished || unFinished1 || unFinished2 || unFinished3 || unFinished5;
        }
    }

    public boolean sameAddressWrite() {
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            MIPSOperator operator = mipsCode.getOperator();
            if (operator == MIPSOperator.sw) {
                String srcReg = mipsCode.getOpIdent1();
                String dstReg = mipsCode.getResultIdent();
                int offset = mipsCode.op2IsNum() ? mipsCode.getOpNum2() : 0;
                for (int j = i - 1; j >= 0; j--) {
                    MIPSCode lastMipsCode = mipsCodeList.get(j);
                    MIPSOperator lastOperator = lastMipsCode.getOperator();
                    if (lastMipsCode.isDead()) {
                        continue;
                    }
                    if (lastOperator == MIPSOperator.label || lastMipsCode.isBranch() || lastMipsCode.isBranchZero() || lastMipsCode.isJmp()) {
                        break;
                    }
                    if (lastMipsCode.getResultIdent() != null && lastMipsCode.getResultIdent().equals(dstReg)) {
                        //修改了目标寄存器（如sp,fp等的值）
                        break;
                    }
                    if (lastOperator == MIPSOperator.lw && lastMipsCode.getOpIdent1().equals(dstReg) && lastMipsCode.op2IsNum() && lastMipsCode.getOpNum2() == offset) {
                        //读取了目标地址的值
                        break;
                    }
                    if (lastOperator == MIPSOperator.sw && lastMipsCode.getResultIdent().equals(dstReg) && lastMipsCode.op2IsNum() && lastMipsCode.getOpNum2() == offset) {
                        //无用的写入
                        unFinished = true;
                        lastMipsCode.setDead();
                    }
                }
            }
            if (unFinished) {
                break;
            }
        }
        boolean unFinished1 = killDeadCode();
        unFinished = unFinished || unFinished1;
        return unFinished;
    }

    public boolean mergeSameAdd() {
        //addi $sp, $sp, -4
        //addi $sp, $sp, -4
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            MIPSOperator operator = mipsCode.getOperator();
            if (operator == MIPSOperator.addi) {
                for (int j = i - 1; j >= 0; j--) {
                    MIPSCode lastMipsCode = mipsCodeList.get(j);
                    MIPSOperator lastOperator = lastMipsCode.getOperator();
                    if (lastMipsCode.isDead()) {
                        continue;
                    }
                    if (lastOperator == MIPSOperator.label || lastMipsCode.isBranch() || lastMipsCode.isBranchZero() || lastMipsCode.isJmp()) {
                        break;
                    }
                    if (lastOperator == MIPSOperator.addi && lastMipsCode.getResultIdent().equals(mipsCode.getResultIdent()) && lastMipsCode.getOpIdent1().equals(mipsCode.getOpIdent1()) && lastMipsCode.op2IsNum() && mipsCode.op2IsNum()) {
                        unFinished = true;
                        int lastNum = lastMipsCode.getOpNum2();
                        int thisNum = mipsCode.getOpNum2();
                        int newNum = lastNum + thisNum;
                        MIPSCode newMIPSCode = new MIPSCode(MIPSOperator.addi, mipsCode.getOpIdent1(), newNum, mipsCode.getResultIdent());
                        mipsCodeList.set(i, newMIPSCode);
                        lastMipsCode.setDead();
                    } else if ((lastMipsCode.getOpIdent1() != null && lastMipsCode.getOpIdent1().equals(mipsCode.getResultIdent())) || (lastMipsCode.getOpIdent2() != null && lastMipsCode.getOpIdent2().equals(mipsCode.getResultIdent())) || (lastMipsCode.getResultIdent() != null && lastMipsCode.getResultIdent().equals(mipsCode.getResultIdent()))) {
                        //目标寄存器被使用
                        break;
                    }
                }
            }
        }
        boolean unFinished1 = killDeadCode();
        unFinished = unFinished || unFinished1;
        return unFinished;
    }

    public boolean deleteUnUseMove() {
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            MIPSOperator operator = mipsCode.getOperator();
            if (operator == MIPSOperator.move) {
                String srcReg = mipsCode.getOpIdent1();
                String dstReg = mipsCode.getResultIdent();
                if (srcReg.equals(dstReg)) {
                    unFinished = true;
                    mipsCodeList.remove(i);
                    i--;
                }
            }
        }
        return unFinished;
    }

    public boolean mergeAssignMove() {
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            MIPSOperator operator = mipsCode.getOperator();
            if (operator == MIPSOperator.move) {
                String srcReg = mipsCode.getOpIdent1();
                String dstReg = mipsCode.getResultIdent();
                MIPSCode lastMipsCode = null;
                MIPSOperator lastOperator = null;
                int lastMipsCodeIndex = i - 1;
                while (lastMipsCodeIndex >= 0) {
                    lastMipsCode = mipsCodeList.get(lastMipsCodeIndex);
                    lastOperator = lastMipsCode.getOperator();
                    if (lastOperator == MIPSOperator.note) {
                        lastMipsCodeIndex--;
                        continue;
                    }
                    if (lastOperator == MIPSOperator.label || lastMipsCode.isBranch() || lastMipsCode.isBranchZero() || lastMipsCode.isJmp()) {
                        break;
                    }
                    if (lastMipsCode.getResultIdent() != null && lastMipsCode.getResultIdent().equals(srcReg)) {
                        //lw $t0, 0($sp)
                        //move $a0, $t0
                        MIPSCode newMIPSCode = null;
                        if (lastMipsCode.op1IsNum()) {
                            newMIPSCode = new MIPSCode(lastOperator, lastMipsCode.getOpNum1(), lastMipsCode.getOpIdent2(), dstReg);
                        } else if (lastMipsCode.op2IsNum()) {
                            newMIPSCode = new MIPSCode(lastOperator, lastMipsCode.getOpIdent1(), lastMipsCode.getOpNum2(), dstReg);
                        } else {
                            newMIPSCode = new MIPSCode(lastOperator, lastMipsCode.getOpIdent1(), lastMipsCode.getOpIdent2(), dstReg);
                        }
                        unFinished = true;
                        mipsCodeList.set(lastMipsCodeIndex, newMIPSCode);
                        mipsCodeList.get(i).setDead();
                    }
                    break;
                }
                if (unFinished) {
                    break;
                }
            }
        }
        boolean unFinished1 = killDeadCode();
        unFinished = unFinished || unFinished1;
        return unFinished;
    }

    public boolean mergeMoveUse() {
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            MIPSOperator operator = mipsCode.getOperator();
            if (operator == MIPSOperator.move) {
                String srcReg = mipsCode.getOpIdent1();
                String dstReg = mipsCode.getResultIdent();
                /*if (srcReg.length() < 2 || dstReg.length() < 2 || srcReg.charAt(1) != 's' || dstReg.charAt(1) != 't') {
                    //只考虑全局寄存器赋值给临时寄存器
                    continue;
                }*/
                if (srcReg.length() < 2 || dstReg.length() < 2 || dstReg.charAt(1) != 't') {
                    //只考虑全局寄存器赋值给临时寄存器
                    continue;
                }
                //更新直到该寄存器下一次被赋值
                for (int j = i + 1; j < mipsCodeList.size(); j++) {
                    MIPSCode mipsCode1 = mipsCodeList.get(j);
                    if (mipsCode1.isBranchZero() || mipsCode1.isBranch() || mipsCode1.isJmp()) {
                        break;
                    }
                    if (mipsCode1.getOpIdent1() != null && mipsCode1.getOpIdent1().equals(dstReg)) {
                        unFinished = true;
                        mipsCode.setDead();
                        mipsCode1.setOpIdent1(srcReg);
                    }
                    if (mipsCode1.getOpIdent2() != null && mipsCode1.getOpIdent2().equals(dstReg)) {
                        unFinished = true;
                        mipsCode.setDead();
                        mipsCode1.setOpIdent2(srcReg);
                    }
                    if (mipsCode1.getResultIdent() != null && (mipsCode1.getResultIdent().equals(dstReg) || mipsCode1.getResultIdent().equals(srcReg))) {
                        //目标寄存器或者其等效寄存器被重新赋值
                        break;
                    }
                }
            }
        }
        boolean unFinished1 = killDeadCode();
        unFinished = unFinished || unFinished1;
        return unFinished;
    }

    public void optimizeBranch() {
        boolean unFinished = true;
        while (unFinished) {
            unFinished = false;
            for (int i = 0; i < mipsCodeList.size(); i++) {
                MIPSCode mipsCode = mipsCodeList.get(i);
                MIPSOperator operator = mipsCode.getOperator();
                MIPSCode nextMipsCode = null;
                MIPSOperator nextOperator = null;
                if (i < mipsCodeList.size() - 1) {
                    nextMipsCode = mipsCodeList.get(i + 1);
                    nextOperator = nextMipsCode.getOperator();
                }
                if (operator == MIPSOperator.sle && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.ble, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.ble, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.slt && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    newMIPSCode = new MIPSCode(MIPSOperator.blt, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.slti && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    newMIPSCode = new MIPSCode(MIPSOperator.blt, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.seq && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.beq, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.beq, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sne && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.bne, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.bne, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sge && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.bge, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.bge, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sgt && nextOperator == MIPSOperator.bnez && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.bgt, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.bgt, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.seq && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.bne, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.bne, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sne && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.beq, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.beq, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sge && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.blt, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.blt, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.sgt && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    if (mipsCode.op2IsNum()) {
                        newMIPSCode = new MIPSCode(MIPSOperator.ble, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    } else {
                        newMIPSCode = new MIPSCode(MIPSOperator.ble, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    }
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.slt && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    newMIPSCode = new MIPSCode(MIPSOperator.bge, mipsCode.getOpIdent1(), mipsCode.getOpIdent2(), nextMipsCode.getResultIdent());
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                } else if (operator == MIPSOperator.slti && nextOperator == MIPSOperator.beqz && mipsCode.getResultIdent().equals(nextMipsCode.getOpIdent1())) {
                    unFinished = true;
                    MIPSCode newMIPSCode = null;
                    newMIPSCode = new MIPSCode(MIPSOperator.bge, mipsCode.getOpIdent1(), mipsCode.getOpNum2(), nextMipsCode.getResultIdent());
                    mipsCodeList.set(i, newMIPSCode);
                    mipsCodeList.remove(i + 1);
                }
            }
        }
    }

    public boolean killDeadCode() {
        boolean unFinished = false;
        for (int i = 0; i < mipsCodeList.size(); i++) {
            MIPSCode mipsCode = mipsCodeList.get(i);
            if (mipsCode.isDead()) {
                unFinished = true;
                mipsCodeList.remove(i);
                i--;
            }
        }
        return unFinished;
    }
}
