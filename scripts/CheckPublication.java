import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Confere o conteúdo staged sem revelar segredos. */
class CheckPublication {
 static byte[] git(String... args)throws Exception{var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));var process=new ProcessBuilder(command).start();var bytes=process.getInputStream().readAllBytes();if(process.waitFor()!=0)throw new IllegalStateException("Git falhou na verificação.");return bytes;}
 public static void main(String[] args)throws Exception{
   List<String> secrets=new ArrayList<>();for(String line:Files.readAllLines(Path.of(".env")))if(line.startsWith("JWT_SECRET=")||line.startsWith("SEED_ADMIN_PASSWORD="))secrets.add(line.substring(line.indexOf('=')+1));
   String[] files=new String(git("ls-files","-z"),StandardCharsets.UTF_8).split("\u0000");int java=0;
   for(String file:files){
      if(file.equals(".env")||file.startsWith("data/")||file.startsWith(".m2/")||file.startsWith("target/")||file.endsWith(".py")||file.endsWith(".js")||file.endsWith(".cjs"))throw new IllegalStateException("Arquivo indevido no staging: "+file);
      if(file.endsWith(".java"))java++;
      String data=new String(git("show",":"+file),StandardCharsets.ISO_8859_1);for(String secret:secrets)if(!secret.isEmpty()&&data.contains(secret))throw new IllegalStateException("Credencial local encontrada no staging.");
   }
   System.out.println("Staging verificado: "+files.length+" arquivos, "+java+" fontes Java; sem Python/Node, credenciais locais ou banco.");
 }
}
