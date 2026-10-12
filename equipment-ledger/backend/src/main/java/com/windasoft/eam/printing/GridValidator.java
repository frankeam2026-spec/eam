package com.windasoft.eam.printing;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
public class GridValidator {
 public static void validate(JsonNode g,double width,double height){
  if(!g.isObject()||g.path("version").asInt()!=1||g.toString().length()>3000000)fail("模板格式无效或内容过大");
  var cols=g.path("columns");var rows=g.path("rows");if(!cols.isArray()||!rows.isArray()||cols.isEmpty()||cols.size()>100||rows.isEmpty()||rows.size()>500)fail("最多100列、500行");
  double total=0;for(var c:cols)total+=number(c,0.1,600);for(var r:rows)number(r,0.1,600);
  var m=g.path("margin");double left=number(m.path("left"),0,100),right=number(m.path("right"),0,100),top=number(m.path("top"),0,100),bottom=number(m.path("bottom"),0,100);if(total+left+right>width+0.01||top+bottom>=height)fail("表格宽度或页边距超出纸张");
  int headers=integer(g.path("repeatHeader"),0,rows.size()-1);double headerHeight=0;for(int i=0;i<headers;i++)headerHeight+=rows.get(i).asDouble();if(headerHeight>=height-top-bottom)fail("重复表头高度超出纸张");
  var area=g.path("area");if(!area.isNull()&&!area.isMissingNode()){int a=integer(area.path("startRow"),0,rows.size()-1),b=integer(area.path("endRow"),a,rows.size()-1),c=integer(area.path("startCol"),0,cols.size()-1),d=integer(area.path("endCol"),c,cols.size()-1);}
  var repeat=g.path("repeat");int repeatStart=-1,repeatEnd=-1;
  if(repeat.isObject()){repeatStart=integer(repeat.path("startRow"),headers,rows.size()-1);repeatEnd=integer(repeat.path("endRow"),repeatStart,rows.size()-1);if(!repeat.path("source").asText().equals("repairs")||area.isObject())fail("无效明细区或打印区域冲突");}
  boolean[][] used=new boolean[rows.size()][cols.size()];var cells=g.path("cells");if(!cells.isArray()||cells.size()>10000)fail("单元格数量过多");
  for(var cell:cells){int r=integer(cell.path("row"),0,rows.size()-1),c=integer(cell.path("col"),0,cols.size()-1),rs=integer(cell.path("rowSpan"),1,rows.size()-r),cs=integer(cell.path("colSpan"),1,cols.size()-c);if(repeatStart>=0&&r<=repeatEnd&&r+rs-1>=repeatStart&&(r<repeatStart||r+rs-1>repeatEnd))fail("合并单元格不能跨越明细区边界");if(r<headers&&r+rs>headers)fail("合并单元格不能跨越重复表头边界");for(int y=r;y<r+rs;y++)for(int x=c;x<c+cs;x++){if(used[y][x])fail("合并区域重叠");used[y][x]=true;}
   if(!Set.of("text","image","qrcode","barcode").contains(cell.path("kind").asText()))fail("单元格内容类型无效");number(cell.path("fontSize"),6,72);if(cell.path("content").asText().length()>4000)fail("内容过长");if(!Set.of("left","center","right").contains(cell.path("align").asText())||!Set.of("top","middle","bottom").contains(cell.path("vertical").asText())||!Set.of("wrap","clip","shrink").contains(cell.path("overflow").asText()))fail("样式无效");for(String key:java.util.List.of("color","background"))if(!cell.path(key).asText().matches("#[0-9a-fA-F]{6}"))fail("颜色无效");if(cell.has("borderSides")){var sides=cell.path("borderSides");if(!sides.isObject())fail("边框无效");for(String side:java.util.List.of("top","right","bottom","left"))if(!sides.path(side).isBoolean())fail("边框无效");}if(cell.has("borderWidth"))number(cell.path("borderWidth"),0.25,5);if(cell.has("borderColor")&&!cell.path("borderColor").asText().matches("#[0-9a-fA-F]{6}"))fail("边框颜色无效");String image=cell.path("image").asText();if(!image.isEmpty()&&!image.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+"))fail("图片仅支持PNG/JPEG");
   if(!area.isNull()&&!area.isMissingNode()){boolean intersects=r<=area.path("endRow").asInt()&&r+rs>area.path("startRow").asInt()&&c<=area.path("endCol").asInt()&&c+cs>area.path("startCol").asInt();if(intersects&&(r<area.path("startRow").asInt()||r+rs-1>area.path("endRow").asInt()||c<area.path("startCol").asInt()||c+cs-1>area.path("endCol").asInt()))fail("打印区域不能切割合并单元格");}
  }
 }
 private static double number(JsonNode n,double min,double max){double v=n.asDouble(Double.NaN);if(!n.isNumber()||!Double.isFinite(v)||v<min||v>max)fail("尺寸无效");return v;}
 private static int integer(JsonNode n,int min,int max){if(!n.isIntegralNumber()||n.asInt()<min||n.asInt()>max)fail("行列索引无效");return n.asInt();}
 private static void fail(String s){throw new IllegalArgumentException(s);}
}
