package com.windasoft.eam.common.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.util.Map;
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.io.IOAccess;

/**
 * One request per disposable JVM. No Spring, datasource, application beans or inherited
 * credentials.
 */
public final class WorkflowScriptWorker {
  private WorkflowScriptWorker() {}

  private static final String DECIMAL =
      """
(()=>{
  const parse=v=>{ if(typeof v!=='string'||!/^[-+]?\\d+(\\.\\d+)?$/.test(v))throw Error('decimal expects a decimal string');
    const [a,b='']=v.split('.');return [BigInt(a+b),b.length];};
  const format=(v,s)=>{let sign=v<0n?'-':'';v=v<0n?-v:v;let t=v.toString().padStart(s+1,'0');
    if(s)t=t.slice(0,-s)+'.'+t.slice(-s);return sign+(s?t.replace(/0+$/,'').replace(/\\.$/,''):t);};
  const align=(a,b)=>{a=parse(a);b=parse(b);const s=Math.max(a[1],b[1]);return [a[0]*10n**BigInt(s-a[1]),b[0]*10n**BigInt(s-b[1]),s];};
  const add=(a,b)=>{const[x,y,s]=align(a,b);return format(x+y,s);};
  return Object.freeze({add,subtract:(a,b)=>{const[x,y,s]=align(a,b);return format(x-y,s);},
    multiply:(a,b)=>{a=parse(a);b=parse(b);return format(a[0]*b[0],a[1]+b[1]);},
    compare:(a,b)=>{const[x,y]=align(a,b);return x<y?-1:x>y?1:0;},
    divide:(a,b,scale=12)=>{if(!Number.isInteger(scale)||scale<0||scale>12)throw Error('scale must be 0..12');
      a=parse(a);b=parse(b);if(b[0]===0n)throw Error('division by zero');let x=a[0]*10n**BigInt(b[1]+scale),y=b[0]*10n**BigInt(a[1]);
      let q=x/y,r=x%y; if((r<0n?-r:r)*2n>=(y<0n?-y:y))q+=(x<0n)!==(y<0n)?-1n:1n;return format(q,scale);},
    sum:items=>items.reduce((a,b)=>add(a,b),'0')});
})()
""";

  public static void main(String[] args) {
    var mapper = new ObjectMapper();
    try {
      byte[] bytes = System.in.readNBytes(300001);
      if (bytes.length > 300000) throw new IllegalArgumentException("脚本输入过大");
      var request = mapper.readTree(bytes);
      String source = request.path("source").asText();
      if (source.isBlank() || source.length() > 32000) throw new IllegalArgumentException("脚本长度无效");
      try (var context =
          Context.newBuilder("js")
              .allowAllAccess(false)
              .allowHostAccess(HostAccess.NONE)
              .allowHostClassLookup(name -> false)
              .allowHostClassLoading(false)
              .allowIO(IOAccess.NONE)
              .allowCreateThread(false)
              .allowCreateProcess(false)
              .allowNativeAccess(false)
              .allowPolyglotAccess(PolyglotAccess.NONE)
              .out(OutputStream.nullOutputStream())
              .err(OutputStream.nullOutputStream())
              .option("engine.WarnInterpreterOnly", "false")
              .build()) {
        context.eval(
            "js",
            "for(const key of ['Java','Packages','Polyglot'])"
                + " Object.defineProperty(globalThis,key,{value:undefined,writable:false,configurable:false});");
        String function = "(function(input,decimal){'use strict';\n" + source + "\n})";
        if (request.path("validate").asBoolean()) {
          context.parse("js", function);
          System.out.print("{\"valid\":true}");
          return;
        }
        context
            .getBindings("js")
            .putMember("__input_json", mapper.writeValueAsString(request.get("values")));
        String code =
            "(()=>{const freeze=v=>{if(v&&typeof"
                + " v==='object'){Object.values(v).forEach(freeze);Object.freeze(v);}return"
                + " v;};return JSON.stringify({value:"
                + function
                + "(freeze(JSON.parse(__input_json)),"
                + DECIMAL
                + ")});})()";
        String output = context.eval("js", code).asString();
        if (output.length() > 12000) throw new IllegalArgumentException("脚本输出过大");
        System.out.print(output);
      }
    } catch (Throwable error) {
      try {
        String message =
            error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        System.out.print(
            mapper.writeValueAsString(
                Map.of("error", message.substring(0, Math.min(1000, message.length())))));
      } catch (Throwable ignored) {
        System.out.print("{\"error\":\"脚本执行失败\"}");
      }
    }
  }
}
