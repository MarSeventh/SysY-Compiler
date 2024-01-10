public class RegisterError extends RuntimeException {
    public RegisterError() {
        super("Register Error");
    }

    public RegisterError(String msg) {
        super(msg);
    }
}
