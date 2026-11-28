package com.elektoryk.scan.printing;

import com.elektoryk.scan.model.CableCutPiece;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public final class LabelCommandBuilder {
    public enum Protocol { TSPL, ZPL, CPCL, ESC_POS }
    private LabelCommandBuilder(){}

    public static byte[] pair(CableCutPiece p,Protocol protocol){ByteArrayOutputStream out=new ByteArrayOutputStream();write(out,one(p,true,protocol));write(out,one(p,false,protocol));return out.toByteArray();}
    public static byte[] one(CableCutPiece p,boolean start,Protocol protocol){String end=start?"A":"B";String from=start?p.fromLabel:p.toLabel;String to=start?p.toLabel:p.fromLabel;String arrow=start?">":"<";String line1=p.code+" "+end;String line2=ascii(p.cableKey)+(p.protectionTag==null||p.protectionTag.isEmpty()?"":"  "+ascii(p.protectionTag))+"  CUT "+String.format(java.util.Locale.US,"%.1fm",p.cutLengthM);String line3=ascii(from)+" "+arrow+" "+ascii(to);switch(protocol){case ZPL:return zpl(line1,line2,line3);case CPCL:return cpcl(line1,line2,line3);case ESC_POS:return esc(line1,line2,line3);default:return tspl(line1,line2,line3);}}

    private static byte[] tspl(String a,String b,String c){String s="SIZE 50 mm,25 mm\r\nGAP 2 mm,0\r\nDIRECTION 1\r\nCLS\r\nTEXT 20,18,\"3\",0,1,1,\""+clean(a)+"\"\r\nTEXT 20,62,\"2\",0,1,1,\""+clean(b)+"\"\r\nTEXT 20,98,\"2\",0,1,1,\""+clean(c)+"\"\r\nPRINT 1,1\r\n";return bytes(s);}
    private static byte[] zpl(String a,String b,String c){return bytes("^XA^PW400^LL200^FO18,18^A0N,38,38^FD"+clean(a)+"^FS^FO18,68^A0N,26,26^FD"+clean(b)+"^FS^FO18,110^A0N,24,24^FD"+clean(c)+"^FS^XZ");}
    private static byte[] cpcl(String a,String b,String c){return bytes("! 0 200 200 220 1\r\nTEXT 4 0 18 18 "+clean(a)+"\r\nTEXT 4 0 18 70 "+clean(b)+"\r\nTEXT 4 0 18 118 "+clean(c)+"\r\nFORM\r\nPRINT\r\n");}
    private static byte[] esc(String a,String b,String c){ByteArrayOutputStream o=new ByteArrayOutputStream();write(o,new byte[]{0x1B,0x40,0x1B,0x45,0x01});write(o,bytes(a+"\n"));write(o,new byte[]{0x1B,0x45,0x00});write(o,bytes(b+"\n"+c+"\n\n"));return o.toByteArray();}
    private static String clean(String s){return ascii(s).replace("\"","'").replace("^","").replace("~","");}
    public static String ascii(String s){if(s==null)return "";String[][] m={{"Щ","SHCH"},{"щ","shch"},{"Ш","SH"},{"ш","sh"},{"Ч","CH"},{"ч","ch"},{"Ж","ZH"},{"ж","zh"},{"Х","KH"},{"х","kh"},{"Ц","TS"},{"ц","ts"},{"Ю","YU"},{"ю","yu"},{"Я","YA"},{"я","ya"},{"Є","YE"},{"є","ye"},{"Ї","YI"},{"ї","yi"},{"Й","Y"},{"й","y"},{"А","A"},{"а","a"},{"Б","B"},{"б","b"},{"В","V"},{"в","v"},{"Г","H"},{"г","h"},{"Ґ","G"},{"ґ","g"},{"Д","D"},{"д","d"},{"Е","E"},{"е","e"},{"З","Z"},{"з","z"},{"И","Y"},{"и","y"},{"І","I"},{"і","i"},{"К","K"},{"к","k"},{"Л","L"},{"л","l"},{"М","M"},{"м","m"},{"Н","N"},{"н","n"},{"О","O"},{"о","o"},{"П","P"},{"п","p"},{"Р","R"},{"р","r"},{"С","S"},{"с","s"},{"Т","T"},{"т","t"},{"У","U"},{"у","u"},{"Ф","F"},{"ф","f"},{"Ь",""},{"ь",""}};String r=s;for(String[] x:m)r=r.replace(x[0],x[1]);return r.replace('×','x').replace('→','>').replace('←','<');}
    private static byte[] bytes(String s){try{return s.getBytes("US-ASCII");}catch(Exception e){return s.getBytes(Charset.defaultCharset());}}
    private static void write(ByteArrayOutputStream o,byte[] b){try{o.write(b);}catch(Exception ignored){}}
}
