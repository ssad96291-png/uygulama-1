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
                if ("ipv6-connect".equals(mode)) {
                    // Documentation prefix is unreachable on our local test underlay.
                    // A VPN must not falsely complete this TCP handshake on its virtual stack.
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(InetAddress.getByName("2001:db8::1"), 443), 3000);
                        result.put("connected", true);
                    }
                } else if ("dns".equals(mode) || "hostname".equals(mode)) {
                    String hostname = "run-" + (id == null ? System.nanoTime() : id.replaceAll("[^a-zA-Z0-9]", "")) + ".akis.test.invalid";
                    InetAddress[] addresses = InetAddress.getAllByName(hostname);
                    boolean expected = false;
                    for (InetAddress address : addresses) if ("10.0.2.2".equals(address.getHostAddress())) expected = true;
                    if (!expected) throw new IOException("Unexpected test DNS response");
                    result.put("resolved", true);
                    if (!"dns".equals(mode)) received = fetch("http://" + hostname + ":18081/bytes/" + count);
                } else if ("udp".equals(mode)) {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.setSoTimeout(6000); byte[] payload = new byte[count]; java.util.Arrays.fill(payload, (byte) 65);
                        socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName("10.0.2.2"), 18082));
                        DatagramPacket reply = new DatagramPacket(new byte[65507], 65507); socket.receive(reply); received = reply.getLength();
                    }
                } else {
                    received = fetch("http://10.0.2.2:18081/bytes/" + count);
                }
                result.put("success", "ipv6-connect".equals(mode) ? result.optBoolean("connected")
                    : "dns".equals(mode) ? result.optBoolean("resolved") : received == count);
            } catch (Exception error) { try { result.put("success", false); result.put("error", error.getClass().getSimpleName()); } catch (Exception ignored) {} }
            try {
                result.put("mode", mode); result.put("bytes", received); result.put("elapsedMs", SystemClock.elapsedRealtime()-started);
                String safeId = id == null ? "manual" : id.replaceAll("[^a-zA-Z0-9]", "");
                try (FileOutputStream out = openFileOutput("result-"+safeId+".json", MODE_PRIVATE)) { out.write(result.toString().getBytes(StandardCharsets.UTF_8)); }
                runOnUiThread(() -> text.setText(result.toString()));
            } catch (Exception ignored) {}
        }, "probe-network").start();
    }

    private static int fetch(String url) throws IOException {
        int received = 0;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(6000); connection.setReadTimeout(12000);
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) >= 0) received += n;
        } finally { connection.disconnect(); }
        return received;
    }
}
