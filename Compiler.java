import java.io.BufferedWriter;
import java.io.FileWriter;

public class Compiler {
    public static void main(String[] args) {
        try {
            //BufferedWriter out = new BufferedWriter(new FileWriter("output.txt"));
            Parser parser = new Parser("testfile.txt", null);
            IRGenerator irGenerator = new IRGenerator(parser.parse());
            Error.print();
            if (Error.errorList.isEmpty()){//无错误再生成目标代码 Error.errorList.isEmpty()
                irGenerator.genIRCode();
                MIPSGenerator mipsGenerator = new MIPSGenerator(irGenerator.getIRList());
                mipsGenerator.genMips();
            }
            //out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
