import java.io.BufferedWriter;
import java.io.FileWriter;

public class Compiler {
    public static void main(String[] args) {
        Lexer lexer = new Lexer("testfile.txt");
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter("output.txt"));
            while (lexer.getLexType() != LexType.EOF) {
                lexer.next();
                if (lexer.getLexType() != LexType.ERR && lexer.getLexType() != LexType.EOF) {
                    out.write(lexer.getLexType().toString() + " " + lexer.getToken() + "\n");
                }
            }
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
