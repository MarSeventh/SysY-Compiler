public class MIPSCodeError extends RuntimeException {
    public MIPSCodeError() {
        super("MIPSCode Error");
    }

    public MIPSCodeError(String msg) {
        super(msg);
    }
}
