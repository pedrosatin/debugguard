package dev.satin.debugguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

final class AndroidBackend implements Transaction.Settings, Transaction.Journal {
    private static final String PREF = "debugguard_journal";
    private static final String RECORD = "record";
    private final Context context;
    private final SharedPreferences preferences;

    AndroidBackend(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    @Override public String read(String key) {
        return Settings.Global.getString(context.getContentResolver(), key);
    }

    @Override public void write(String key, String value) {
        if (!Settings.Global.putString(context.getContentResolver(), key, value)) {
            throw new IllegalStateException("Settings provider rejected " + key);
        }
    }

    @Override public void awaitSettled() throws InterruptedException {
        Thread.sleep(1000);
    }

    @Override public Transaction.Record load() throws Exception {
        String serialized = preferences.getString(RECORD, null);
        if (serialized == null) return null;
        JSONObject json = new JSONObject(serialized);
        JSONObject values = json.getJSONObject("original");
        Map<String, String> original = new LinkedHashMap<>();
        for (String key : new String[]{Transaction.DEVELOPER, Transaction.USB, Transaction.WIFI}) {
            if (!values.has(key)) throw new IllegalStateException("Missing saved key: " + key);
            original.put(key, values.isNull(key) ? null : values.getString(key));
        }
        return new Transaction.Record(Transaction.Phase.valueOf(json.getString("phase")), original);
    }

    @Override public void save(Transaction.Record record) throws Exception {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, String> entry : record.original.entrySet()) {
            values.put(entry.getKey(), entry.getValue() == null ? JSONObject.NULL : entry.getValue());
        }
        JSONObject json = new JSONObject();
        json.put("phase", record.phase.name());
        json.put("original", values);
        if (!preferences.edit().putString(RECORD, json.toString()).commit()) {
            throw new IllegalStateException("Could not save recovery journal");
        }
    }

    @Override public void clear() {
        if (!preferences.edit().remove(RECORD).commit()) throw new IllegalStateException("Could not clear recovery journal");
    }
}
