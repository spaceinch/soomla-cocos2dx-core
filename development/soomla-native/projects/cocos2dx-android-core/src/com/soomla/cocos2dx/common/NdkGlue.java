package com.soomla.cocos2dx.common;

import android.app.Activity;
import android.util.Log;
import com.soomla.SoomlaUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vedi
 *         date 5/24/13
 *         time 8:36 PM
 */
public class NdkGlue {

    private Map<String, CallHandler> callHandlers = new HashMap<String, CallHandler>();
    private Map<String, ExceptionHandler> exceptionHandlers = new HashMap<String, ExceptionHandler>();

    public WeakReference<Activity> getActivityRef() {
        return mActivityRef;
    }

    public static interface CallHandler {
        void handle(JSONObject params, JSONObject retParams) throws Exception;
    }

    public static interface ExceptionHandler {
        void handle(Exception exception, JSONObject params, JSONObject retParams) throws Exception;
    }

    private static final String TAG = "NdkGlue";
    private static NdkGlue INSTANCE;

    private WeakReference<Activity> mActivityRef = new WeakReference<Activity>(null);

    public static NdkGlue getInstance() {
        if (INSTANCE == null) {
            synchronized (NdkGlue.class) {
                if (INSTANCE == null) {
                    INSTANCE = new NdkGlue();
                }
            }
        }
        return INSTANCE;
    }

    public void registerCallHandler(String key, CallHandler callHandler) {
        callHandlers.put(key, callHandler);
    }

    public void registerExceptionHandler(String key, ExceptionHandler exceptionHandler) {
        exceptionHandlers.put(key, exceptionHandler);
    }

    public void sendMessageWithParameters(JSONObject paramList) {
        sendCppMessage(paramList.toString());
    }

    public void setActivity(Activity activity) {
        mActivityRef = new WeakReference<Activity>(activity);
    }

    private static native void sendCppMessage(String json);

    @SuppressWarnings("UnusedDeclaration")
    public static String receiveCppMessage(String json) {
        if (json != null) {
            try {
                JSONObject params = new JSONObject(json);
                JSONObject retParamsJson = getInstance().dispatchNdkCall(params);
                if (retParamsJson == null) {
                    retParamsJson = new JSONObject();
                }
                SoomlaUtils.LogDebug("NdkGlue", "retParamsJson: " + retParamsJson.toString());
                return retParamsJson.toString();
            } catch (JSONException e) {
                Log.e(TAG, "receiveCppMessage raised exception", e);
                SoomlaUtils.LogError("NdkGlue", "receiveCppMessage raised exception" + e);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "", e);
                SoomlaUtils.LogError("NdkGlue", "Unsupported operation (" + e + ")");
            } catch (Exception e) {
                Log.e(TAG, "", e);
                SoomlaUtils.LogError("NdkGlue", "Unknown exception (" + e + ")");
            }
            return "{\"errorInfo\": \"UnknownError\"}";
        } else {
            return "{}";
        }
    }

    public JSONObject dispatchNdkCall(JSONObject params) {
        JSONObject retParams = new JSONObject();
        try {
            String methodName = params.getString("method");

            CallHandler callHandler = callHandlers.get(methodName);
            if (callHandler != null) {
                try {
                    callHandler.handle(params, retParams);
                } catch (Exception e) {
                    ExceptionHandler exceptionHandler = exceptionHandlers.get(e.getClass().getName());
                    if (exceptionHandler != null) {
                        try {
                            exceptionHandler.handle(e, params, retParams);
                        } catch (Exception e1) {
                            throw new IllegalStateException(e1);
                        }
                    } else {
                        throw new IllegalStateException(e);
                    }
                }
            } else {
                throw new UnsupportedOperationException(methodName);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }

        return retParams;
    }
}