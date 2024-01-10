import java.io.BufferedWriter;
import java.io.FileWriter;

public class Compiler {
    public static void main(String[] args) {
        try {
            //BufferedWriter out = new BufferedWriter(new FileWriter("output.txt"));
            boolean optimize = true;
            Parser parser = new Parser("testfile.txt", null);
            IRGenerator irGenerator = new IRGenerator(parser.parse());
            Error.print();
            if (Error.errorList.isEmpty()) {//无错误再生成目标代码 Error.errorList.isEmpty()
                irGenerator.setOptimize(optimize);
                irGenerator.genIRCode();
                //代码优化
                IROptimizer irOptimizer = new IROptimizer(irGenerator.getIRList());
                irOptimizer.setOptimize(optimize);
                irOptimizer.optimize();
                //目标代码生成
                MIPSGenerator mipsGenerator = new MIPSGenerator(irOptimizer.getOptimizedIRList());
                mipsGenerator.setOptimize(optimize);
                mipsGenerator.genMips();
            }
            //out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
