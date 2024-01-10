import java.io.BufferedWriter;
import java.io.FileWriter;

public class Compiler {
    public static void main(String[] args) {
        try {
            //BufferedWriter out = new BufferedWriter(new FileWriter("output.txt"));
            Parser parser = new Parser("testfile.txt", null);
            parser.parse();
            //out.close();
            Error.print();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
