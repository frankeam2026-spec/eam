package com.windasoft.eam.service.knowledge;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Offline OCR subprocess with fixed arguments, deadline and bounded output. */
@Component
public class KnowledgeOcr {
  private final String executable,languages;private final boolean enabled;
  public KnowledgeOcr(@Value("${eam.ai.ocr-enabled:false}")boolean enabled,@Value("${eam.ai.tesseract-path:tesseract}")String executable,@Value("${eam.ai.ocr-languages:chi_sim+eng}")String languages){this.enabled=enabled;this.executable=executable;this.languages=languages;}
  public boolean enabled(){return enabled;}
  public String extract(BufferedImage image) throws Exception {
    if(!enabled)return "";
    Path dir=Files.createTempDirectory("eam-ocr-");Process process=null;
    try{
      Path input=dir.resolve("page.png"),output=dir.resolve("result"),error=dir.resolve("error.txt");ImageIO.write(image,"png",input.toFile());
      process=new ProcessBuilder(executable,input.toString(),output.toString(),"-l",languages,"--psm","3").redirectError(error.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
      if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("OCR处理超时");}
      Path text=dir.resolve("result.txt");
      if(process.exitValue()!=0||!Files.exists(text)||Files.size(text)>500_000)throw new IllegalStateException("本地OCR失败，请检查识别程序与语言包");
      return Files.readString(text).strip();
    }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("OCR已中断");}
    finally{
      if(process!=null&&process.isAlive()){process.destroyForcibly();try{process.waitFor(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
      try(var paths=Files.list(dir)){for(var path:paths.toList())Files.deleteIfExists(path);}Files.deleteIfExists(dir);
    }
  }
}
