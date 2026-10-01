package dev.ojun.chatrpg;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.*;
import android.security.keystore.*;
import android.util.Base64;
import android.util.AtomicFile;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int MAX = 2 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WebView web;
    private String pendingId, pendingData;
    private volatile boolean destroyed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(0xff10131b);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url=request.getUrl().toString();
                if (!url.startsWith("file:///android_asset/")) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                return null;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        web.setOnApplyWindowInsetsListener((view, insets) -> {
            if(android.os.Build.VERSION.SDK_INT >= 30){
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(web);
        web.requestApplyInsets();
        web.loadUrl("file:///android_asset/index.html");
    }
    private final class Bridge {
        @JavascriptInterface public void call(final String id, final String method, final String args) {
            if (id == null || id.length()>128) return;
            worker.execute(() -> {
                try {
                    if(args == null || args.getBytes(StandardCharsets.UTF_8).length>MAX) throw new IOException();
                    JSONObject a = new JSONObject(args);
                    Object data;
                    switch(method) {
                    case "configGet": data=new JSONObject().put("hasKey",readKey()!=null).put("model",prefs().getString("model","gpt-6.1-sol")); break;
                    case "configSet":
                        String key=a.optString("key","").trim(), model=a.optString("model","gpt-6.1-sol").trim();
                        if(key.length()>1024 || (key.indexOf('\r')>=0 || key.indexOf('\n')>=0) || !model.matches("[a-zA-Z0-9._-]{1,100}")) throw new IOException();
                        if(!key.isEmpty()) writeKey(key);
                        if(!prefs().edit().putString("model",model).commit()) throw new IOException();
                        data=true; break;
                    case "close": runOnUiThread(() -> finish()); data=true; break;
                    case "deleteKey": if(!prefs().edit().remove("key").commit()) throw new IOException(); data=true; break;
                    case "load": data=loadSave(); break;
                    case "save": atomicSave(a.getString("data")); data=true; break;
                    case "request": data=request(a.getJSONObject("body")); break;
                    case "export": startDocument(id,a.getString("data"),true); return;
                    case "import": startDocument(id,null,false); return;
                    default: reply(id,null,"지원하지 않는 요청입니다."); return;
                    }
                    reply(id,data,null);
                } catch (ApiError e) { reply(id,null,e.getMessage()); }
                catch(Exception e) { reply(id,null,"처리하지 못했습니다. 설정과 저장 공간을 확인하고 다시 시도해 주세요."); }
            });
        }
    }
    private android.content.SharedPreferences prefs(){return getSharedPreferences("private",MODE_PRIVATE);}
    private javax.crypto.SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("chatrpg-key")){
            KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("chatrpg-key",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("chatrpg-key",null);
    }
    private void writeKey(String key)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secret());
        String v=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(key.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs().edit().putString("key",v).commit())throw new IOException();
    }
    private String readKey()throws Exception {
        String v=prefs().getString("key",null);if(v==null)return null;
        String[] p=v.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private static String read(InputStream stream)throws IOException { return ProtocolSafety.read(stream); }
    private Object loadSave()throws IOException {
        try{return read(new AtomicFile(new File(getFilesDir(),"campaigns.json")).openRead());}
        catch(FileNotFoundException e){return JSONObject.NULL;}
    }
    private void atomicSave(String data)throws IOException {
        byte[] bytes=data.getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX)throw new IOException();
        AtomicFile file=new AtomicFile(new File(getFilesDir(),"campaigns.json"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    private static class ApiError extends Exception { ApiError(String m){super(m);} }
    private Object request(JSONObject body)throws Exception {
        String key=readKey();if(key==null)throw new ApiError("API 키를 설정해 주세요.");
        body.put("model",prefs().getString("model","gpt-6.1-sol"));
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX)throw new IOException();
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();
        try{
            c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int status=c.getResponseCode();
            if(status<200||status>=300){
                throw new ApiError(ProtocolSafety.httpError(status));
            }
            return new JSONObject(read(c.getInputStream()));
        } catch(java.net.SocketTimeoutException e){throw new ApiError("응답 시간이 초과되었습니다. 다시 시도해 주세요.");}
        catch(IOException e){throw new ApiError("네트워크 연결을 확인하고 다시 시도해 주세요.");}
        finally{c.disconnect();}
    }
    private void startDocument(String id,String data,boolean export)throws Exception {
        if(data!=null&&data.getBytes(StandardCharsets.UTF_8).length>MAX)throw new IOException();
        runOnUiThread(() -> {
            if(destroyed)return;
            if(pendingId!=null){reply(id,null,"파일 작업이 이미 진행 중입니다.");return;}
            pendingId=id;pendingData=data;
            Intent i=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
            if(export)i.putExtra(Intent.EXTRA_TITLE,"chat-rpg-save.json");
            try{startActivityForResult(i,export?41:42);}catch(Exception e){pendingId=null;pendingData=null;reply(id,null,"파일 선택기를 열 수 없습니다.");}
        });
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent intent){
        super.onActivityResult(requestCode,resultCode,intent);
        if(requestCode!=41&&requestCode!=42)return;
        final String id=pendingId,data=pendingData;pendingId=null;pendingData=null;if(id==null)return;
        if(resultCode!=RESULT_OK||intent==null||intent.getData()==null){reply(id,null,"파일 작업을 취소했습니다.");return;}
        final android.net.Uri uri=intent.getData();
        worker.execute(() -> {try{
            if(requestCode==42){reply(id,read(getContentResolver().openInputStream(uri)),null);}
            else {try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(data.getBytes(StandardCharsets.UTF_8));}reply(id,true,null);}
        }catch(Exception e){reply(id,null,"파일을 읽거나 저장하지 못했습니다.");}});
    }
    private void reply(String id,Object data,String error){
        try{JSONObject result=new JSONObject().put("ok",error==null);if(error==null)result.put("data",data==null?JSONObject.NULL:data);else result.put("error",error);
            String script="window.NativeCallbacks&&window.NativeCallbacks.receive("+JSONObject.quote(id)+","+result.toString()+")";
            runOnUiThread(() -> {if(!destroyed)web.evaluateJavascript(script,null);});
        }catch(JSONException ignored){}
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.dispatchEvent(new Event('nativeback'))",null);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();web.removeJavascriptInterface("AndroidBridge");web.destroy();super.onDestroy();}
}
