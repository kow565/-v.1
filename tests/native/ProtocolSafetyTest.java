package dev.ojun.chatrpg;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class ProtocolSafetyTest {
 public static void main(String[] args)throws Exception {
  byte[] korean="한글 저장 테스트".getBytes(StandardCharsets.UTF_8);
  if(!ProtocolSafety.read(new ByteArrayInputStream(korean)).equals("한글 저장 테스트"))throw new AssertionError("UTF8");
  if(ProtocolSafety.read(new ByteArrayInputStream(new byte[ProtocolSafety.MAX])).length()!=ProtocolSafety.MAX)throw new AssertionError("limit boundary");
  try{ProtocolSafety.read(new ByteArrayInputStream(new byte[ProtocolSafety.MAX+1]));throw new AssertionError("oversize accepted");}catch(IOException expected){}
  if(!ProtocolSafety.httpError(401).equals(ProtocolSafety.httpError(403)))throw new AssertionError("auth mapping");
  if(!ProtocolSafety.httpError(429).contains("한도"))throw new AssertionError("rate mapping");
  if(!ProtocolSafety.httpError(503).contains("서버"))throw new AssertionError("server mapping");
  if(!ProtocolSafety.httpError(400).contains("모델"))throw new AssertionError("invalid request mapping");
  System.out.println("Native boundary, UTF8 and HTTP error mapping tests passed");
 }
}
