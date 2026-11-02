import java.nio.file.*;
import com.github.hvnbael.trnightmare.client.screen.CosmeticsSpanish;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class VerifyCosmeticsPatch {
    private static int checks;
    private static void check(String input,String expected) {
        String output=CosmeticsSpanish.display(input);
        if(!expected.equals(output)) throw new IllegalStateException(input+" => "+output);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        check("Body","Cuerpo");check("Dragon","Dragón");check("DRAGON","DRAGÓN");
        check("Race","Raza");check("Storm","Tormenta");check("No clan","Sin clan");
        check("From your skin","Colores de tu skin");
        check("Drag to turn, scroll to zoom","Arrastra para girar; usa la rueda para acercar");
        check("Cosmetics for Dragon races. Hide any you don't want.","Cosméticos de Dragón. Oculta los que no quieras.");
        check("trnightmare:dragon","trnightmare:dragon");check("storm","storm");
        check("body","body");check("#FFAABB","#FFAABB");check("%+.2f px","%+.2f px");
        ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);
        int methods=0;
        for(MethodNode method:node.methods) {
            if(method.instructions.size()==0) continue;
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,method);methods++;
        }
        System.out.println("Cosmetics text checks="+checks+"; bytecode methods verified="+methods+"; errors=0");
    }
}
