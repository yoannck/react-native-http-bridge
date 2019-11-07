package me.alwx.HttpServer;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Random;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.support.annotation.Nullable;
import android.util.Log;

public class Server extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String SERVER_EVENT_ID = "httpServerResponseReceived";

    private ReactContext reactContext;
    private Map<String, Response> responses;

    public Server(ReactContext context, int port) {
        super(port);
        reactContext = context;
        responses = new HashMap<>();

        Log.d(TAG, "Server started");
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Request received!");

        Random rand = new Random();
        String requestId = String.format("%d:%d", System.currentTimeMillis(), rand.nextInt(1000000));

        WritableMap request;
        try {
            request = fillRequestMap(session, requestId);
        } catch (Exception e) {
            return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage()
            );
        }

        this.sendEvent(reactContext, SERVER_EVENT_ID, request);

        while (responses.get(requestId) == null) {
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                Log.d(TAG, "Exception while waiting: " + e);
            }
        }
        Response response = responses.get(requestId);
        responses.remove(requestId);
        response.addHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    public void respond(String requestId, int code, String type, String body) {
        responses.put(requestId, newFixedLengthResponse(Status.lookup(code), type, body));
    }

    public void respondAudio(String requestId, String url) {
      try {
        RandomAccessFile aFile = new RandomAccessFile(url, "r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        inChannel.read(buffer);
        //buffer.rewind();
        buffer.flip();
        for (int i = 0; i < fileSize; i++) {
          System.out.print((char) buffer.get());
        }
        responses.put(requestId, newFixedLengthResponse(Status.lookup(200), MIME_PLAINTEXT, new ByteArrayInputStream(buffer.array()), fileSize));
        inChannel.close();
        aFile.close();
      } catch (Exception e) {
        Log.d(TAG, "Exception while reading audio: " + e);
      }
    }

    public String getMimeType(String path) {
      String mimetype = "text/html";
      String[] parts = path.split("\\.");
      String extension = parts[1];
      if (extension.equals("css")) {
        mimetype = "text/css";
      } else if (extension.equals("js")) {
        mimetype = "application/javascript";
      } else if (extension.equals("png")) {
        mimetype = "image/png";
      } else if (extension.equals("jpg") || extension.equals("jpeg")) {
        mimetype = "image/jpeg";
      } else if (extension.equals("woff2")) {
        mimetype = "font/woff2";
      } else if (extension.equals("woff")) {
        mimetype = "font/woff";
      } else if (extension.equals("tff")) {
        mimetype = "application/octet-stream";
      } else if (extension.equals("mp3")) {
        mimetype = "audio/mpeg3";
      } else if (extension.equals("gif")) {
        mimetype = "image/gif";
      } else if (extension.equals("svg")) {
        mimetype = "image/svg+xml";
      }
      return mimetype;
    }

    public void respondFile(String requestId, String path) {
      try {
        String mimetype = getMimeType(path);

        if (mimetype.equals("text/html")) {
          String answer = "";
          InputStream IS = reactContext.getResources().getAssets().open(path);
          BufferedReader reader = new BufferedReader(new InputStreamReader(IS, "UTF-8"));
          String line = "";
          while ((line = reader.readLine()) != null) {
            answer += line;
          }
          reader.close();
          responses.put(requestId, newFixedLengthResponse(Status.lookup(200), mimetype, answer));
        } else {
          InputStream IS = reactContext.getResources().getAssets().open(path);
          responses.put(requestId, newFixedLengthResponse(Status.lookup(200), mimetype, IS, IS.available()));
        }

      } catch (Exception e) {
        Log.d(TAG, "Exception: " + e);
      }
    }

    private WritableMap fillRequestMap(IHTTPSession session, String requestId) throws Exception {
        Method method = session.getMethod();
        WritableMap request = Arguments.createMap();
        request.putString("url", session.getUri());
        request.putString("type", method.name());
        request.putString("requestId", requestId);

        Map<String, String> files = new HashMap<String, String>();
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
          session.parseBody(files);
          if (files.get("postData") != null) {
            request.putString("postData", files.get("postData"));
          } else if (session.getQueryParameterString() != null) { // POST body
            request.putString("postData", session.getQueryParameterString());
          } else if (session.getParms().get("parameter") != null) { // POST request's parameters
            request.putString("postData", session.getParms().get("parameter"));
          }
        }

        return request;
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }
}
