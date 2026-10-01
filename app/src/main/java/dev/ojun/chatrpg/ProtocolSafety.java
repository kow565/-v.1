package dev.ojun.chatrpg;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Pure helpers tested without Android; server bodies are never surfaced as errors. */
final class ProtocolSafety {
    static final int MAX=2*1024*1024;
    static String httpError(int status){
        if(status==401||status==403)return "API 키 또는 모델 접근 권한을 확인해 주세요.";
        if(status==429)return "API 사용 한도 또는 요청 제한에 도달했습니다. 결제와 한도를 확인해 주세요.";
        if(status>=500)return "AI 서버가 응답하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        return "AI 요청을 처리하지 못했습니다. 모델 설정을 확인해 주세요.";
    }
    static String read(InputStream stream)throws IOException {
        if(stream==null)throw new IOException();
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX)throw new IOException();out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
