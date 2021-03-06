package com.segment.analytics.cordova;

import android.util.Log;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Properties.Product;
import com.segment.analytics.StatsSnapshot;
import com.segment.analytics.Traits;
import com.segment.analytics.Traits.Address;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AnalyticsPlugin extends CordovaPlugin {

    private static final String TAG = "AnalyticsPlugin";
    private AnalyticsPluginHelper analyticsPluginHelper;

    @Override protected void pluginInitialize() {
        analyticsPluginHelper = new AnalyticsPluginHelper(this.preferences,
                cordova.getContext(),
                cordova.getActivity().getPackageName());
        cordova.getThreadPool().execute(analyticsPluginHelper);
    }

    /** On android, when closing the app via the back button, a relaunch tries to reinitialize the segment instance.
    *   This causes a crash because the segment instance is already initialized as a singleton. So. We need to kill
    *   the current process on destroy in order to allow the app to relaunch and initialize segment successfully.
    *
    *  ... fucking android
    **/
    @Override
    public void onDestroy() {
       int pid = android.os.Process.myPid();
       android.os.Process.killProcess(pid);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (analyticsPluginHelper == null) {
            Log.e(TAG, "Error initializing");
            return false;
        }
        if ("identify".equals(action)) {
            identify(args);
            return true;
        } else if ("group".equals(action)) {
            group(args);
            return true;
        } else if ("track".equals(action)) {
            track(args);
            return true;
        } else if ("screen".equals(action)) {
            screen(args);
            return true;
        } else if ("alias".equals(action)) {
            alias(args);
            return true;
        } else if ("reset".equals(action)) {
            reset();
            return true;
        } else if ("flush".equals(action)) {
            flush();
            return true;
        } else if ("getSnapshot".equals(action)) {
            getSnapshot(callbackContext);
            return true;
        }

        return false;
    }

    private Boolean isAnalyticsSingeltonSet() {
        if(analyticsPluginHelper.getAnalytics() == null) {
            Log.e(TAG, "Error initializing");
            return false;
        }
        return true;
    }

    private void identify(JSONArray args) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).identify(
                    optArgString(args, 0),
                    makeTraitsFromJSON(args.optJSONObject(1)),
                    null // passing options is deprecated
            );
        });
    }

    private void group(JSONArray args) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).group(
                    optArgString(args, 0),
                    makeTraitsFromJSON(args.optJSONObject(1)),
                    null // passing options is deprecated
            );
        });
    }

    private void track(JSONArray args) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).track(
                    optArgString(args, 0),
                    makePropertiesFromJSON(args.optJSONObject(1)),
                    null // passing options is deprecated
            );
        });
    }

    private void screen(JSONArray args) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).screen(
                    optArgString(args, 0),
                    optArgString(args, 1),
                    makePropertiesFromJSON(args.optJSONObject(2)),
                    null // passing options is deprecated
            );
        });
    }

    private void alias(JSONArray args) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).alias(
                    optArgString(args, 0),
                    null // passing options is deprecated
            );
        });
    }

    private void reset() {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).reset();
        });
    }

    private void flush() {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            Analytics.with(cordova.getActivity().getApplicationContext()).flush();
        });
    }

    private void getSnapshot(CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            if(!isAnalyticsSingeltonSet()) return;

            StatsSnapshot snapshot = Analytics
                    .with(cordova.getActivity().getApplicationContext()).getSnapshot();
            JSONObject snapshotJSON = new JSONObject();

            try {
                snapshotJSON.put("timestamp", snapshot.timestamp);
                snapshotJSON.put("flushCount", snapshot.flushCount);
                snapshotJSON.put("flushEventCount", snapshot.flushEventCount);
                snapshotJSON.put("integrationOperationCount", snapshot.integrationOperationCount);
                snapshotJSON.put("integrationOperationDuration", snapshot.integrationOperationDuration);
                snapshotJSON.put("integrationOperationAverageDuration", snapshot.integrationOperationAverageDuration);
                snapshotJSON.put("integrationOperationDurationByIntegration", new JSONObject(snapshot.integrationOperationDurationByIntegration));

                PluginResult r = new PluginResult(PluginResult.Status.OK, snapshotJSON);
                r.setKeepCallback(false);
                callbackContext.sendPluginResult(r);
            } catch(JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private Traits makeTraitsFromJSON(JSONObject json) {
        Traits traits = new Traits();
        Map<String, Object> traitMap = mapFromJSON(json);

        if (traitMap != null) {
            if (traitMap.get("address") != null) {
                traitMap.put("address", new Address((Map<String, Object>) traitMap.get("address")));
            }

            traits.putAll(traitMap);
        }

        return traits;
    }

    private Properties makePropertiesFromJSON(JSONObject json) {
        Properties properties = new Properties();
        Map<String, Object> propertiesMap = mapFromJSON(json);

        if (propertiesMap != null) {
            List<Map<String, Object>> rawProducts = (List<Map<String, Object>>) propertiesMap.get("products");

            if (rawProducts != null) {
                List<Product> products = new ArrayList<Product>();

                for (Map<String, Object> rawProduct : rawProducts) {
                    Product product = new Product(
                        rawProduct.get("id") == null ? "" : (String) rawProduct.get("id"),
                        rawProduct.get("sku") == null ? "" : (String) rawProduct.get("sku"),
                        rawProduct.get("price") == null ? 0d : Double.valueOf(rawProduct.get("price").toString())
                    );

                    product.putAll(rawProduct);
                    products.add(product);
                }

                propertiesMap.put("products", products.toArray(new Product[products.size()]));
            }

            properties.putAll(propertiesMap);
        }

        return properties;
    }

    private static Map<String, Object> mapFromJSON(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<String> keysIter = jsonObject.keys();
        while (keysIter.hasNext()) {
            String key = keysIter.next();
            Object value = jsonObject.isNull(key) ? null : getObject(jsonObject.opt(key));

            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static List<Object> listFromJSON(JSONArray jsonArray) {
        List<Object> list = new ArrayList<Object>();
        for (int i = 0, count = jsonArray.length(); i < count; i++) {
            Object value = getObject(jsonArray.opt(i));
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private static Object getObject(Object value) {
        if (value instanceof JSONObject) {
            value = mapFromJSON((JSONObject) value);
        } else if (value instanceof JSONArray) {
            value = listFromJSON((JSONArray) value);
        }
        return value;
    }

    public static String optArgString(JSONArray args, int index)
    {
        return args.isNull(index) ? null :args.optString(index);
    }
}
