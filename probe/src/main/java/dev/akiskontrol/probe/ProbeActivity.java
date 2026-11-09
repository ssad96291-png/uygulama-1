package dev.akiskontrol.probe;
import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Separate UID produces real packets for local VPN instrumentation; no remote services. */
public class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this); text.setText("Yerel test aktarımı çalışıyor…"); text.setPadding(32,64,32,32); setContentView(text);
        final String id = getIntent().getStringExtra("runId");
        final String mode = getIntent().getStringExtra("mode");
        final int count = Math.max(1, Math.min(1048576, getIntent().getIntExtra("bytes", 32768)));
        new Thread(() -> {
            JSONObject result = new JSONObject(); long started = SystemClock.elapsedRealtime(); int received = 0;
            try {
                if ("udp".equals(mode)) {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.setSoTimeout(6000); byte[] payload = new byte[count]; java.util.Arrays.fill(payload, (byte) 65);
                        socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName("10.0.2.2"), 18082));
                        DatagramPacket reply = new DatagramPacket(new byte[65507], 65507); socket.receive(reply); received = reply.getLength();
                    }
                } else {
                    HttpURLConnection connection = (HttpURLConnection) new URL("http://10.0.2.2:18081/bytes/"+count).openConnection();
                    connection.setConnectTimeout(6000); connection.setReadTimeout(12000);
                    try (InputStream input = connection.getInputStream()) {
                        byte[] buffer = new byte[4096]; int n;
                        while ((n = input.read(buffer)) >= 0) received += n;
                    } finally { connection.disconnect(); }
                }
                result.put("success", received == count);
            } catch (Exception error) { try { result.put("success", false); result.put("error", error.getClass().getSimpleName()); } catch (Exception ignored) {} }
            try {
                result.put("bytes", received); result.put("elapsedMs", SystemClock.elapsedRealtime()-started);
                String safeId = id == null ? "manual" : id.replaceAll("[^a-zA-Z0-9]", "");
                try (FileOutputStream out = openFileOutput("result-"+safeId+".json", MODE_PRIVATE)) { out.write(result.toString().getBytes(StandardCharsets.UTF_8)); }
                runOnUiThread(() -> text.setText(result.toString()));
            } catch (Exception ignored) {}
        }, "probe-network").start();
    }
}
