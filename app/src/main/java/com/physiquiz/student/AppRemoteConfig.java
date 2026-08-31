package com.physiquiz.student;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads live app settings from the WordPress "PhysiQuiz" plugin (v1.17.0+),
 * NOT from the small standalone "PhysiQuiz App Manager" plugin.
 *
 * Endpoint: {site}/wp-json/physiquiz/v1/app-config
 * Registered in: includes/class-physiquiz-mobile-app.php -> app_config()
 *
 * That endpoint returns a FLAT json object, e.g.:
 * {
 *   "enabled": true,
 *   "app_name": "فیزیکوییز",
 *   "accent_color": "#2563eb",
 *   "background_color": "#f7f9fc",
 *   "maintenance_mode": false,
 *   "maintenance_message": "...",
 *   "support_url": "...",
 *   "latest_version_name": "2.0.0",
 *   "minimum_version_code": 3,
 *   "update_message": "...",
 *   "update_url": "...",
 *   "plugin_version": "1.17.0",
 *   ...
 * }
 *
 * If you deactivate the separate "PhysiQuiz App Manager" plugin (its settings
 * are not read by any part of this app), this becomes the single source of
 * truth for app branding/maintenance/update behaviour.
 */
public class AppRemoteConfig {
    public interface Callback { void onResult(JSONObject json); }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final String endpoint;

    public AppRemoteConfig(String baseUrl) {
        endpoint = baseUrl.replaceAll("/+$", "") + "/wp-json/physiquiz/v1/app-config";
    }

    public void load(Callback callback) {
        io.execute(() -> {
            JSONObject result = new JSONObject();
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/json");
                int status = c.getResponseCode();
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream()));
                StringBuilder b = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) b.append(line);
                r.close();
                if (status >= 200 && status < 300) {
                    result = new JSONObject(b.toString());
                }
                c.disconnect();
            } catch (Exception ignored) {
                // Network/parse failure: caller receives an empty JSONObject and should
                // treat that as "no remote config available right now" (fail-open),
                // not as maintenance mode or a forced update.
            }
            callback.onResult(result);
        });
    }
}
