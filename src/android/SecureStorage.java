package com.crypho.plugins;

import android.util.Log;
import android.util.Base64;

import android.content.Context;
import android.content.Intent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;
import org.json.JSONObject;
import javax.crypto.Cipher;

public class SecureStorage extends CordovaPlugin {
    private static final String TAG = "SecureStorage";

    private String ALIAS;
    private volatile CallbackContext initContext;
    private volatile boolean initContextRunning = false;

    @Override
    public void onResume(boolean multitasking) {
        if (initContext != null && !initContextRunning) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    initContextRunning = true;
                    try {
                        if (!RSA.isEntryAvailable(ALIAS)) {
                            RSA.createKeyPair(getContext(), ALIAS);
                        }
                        initContext.success();
                    } catch (Exception e) {
                        Log.e(TAG, "Init failed :", e);
                        initContext.error(e.getMessage());
                    } finally {
                        initContext = null;
                        initContextRunning = false;
                    }
                }
            });
        }
    }

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if ("init".equals(action)) {
            ALIAS = getContext().getPackageName() + "." + args.getString(0);
            if (!RSA.isEntryAvailable(ALIAS)) {
                initContext = callbackContext;
                unlockCredentials();
            } else {
                callbackContext.success();
            }
            return true;
        }
        if ("encrypt".equals(action)) {
            final String encryptMe = args.getString(0);
            final String adata = args.getString(1);

            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        Cipher encKeyCipher = RSA.createCipher(Cipher.ENCRYPT_MODE, ALIAS);
                        JSONObject result = AES.encrypt(encKeyCipher, encryptMe.getBytes(), adata.getBytes());
                        callbackContext.success(result);
                    } catch (Exception e) {
                        Log.e(TAG, "Encrypt failed :", e);
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            return true;
        }
        if ("decrypt".equals(action)) {
            // getArrayBuffer does base64 decoding
            final byte[] encKey = args.getArrayBuffer(0);
            final byte[] ct = args.getArrayBuffer(1);
            final byte[] iv = args.getArrayBuffer(2);
            final byte[] adata = args.getArrayBuffer(3);
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        byte[] key = RSA.decrypt(encKey, ALIAS);
                        String decrypted = new String(AES.decrypt(ct, key, iv, adata));
                        callbackContext.success(decrypted);
                    } catch (Exception e) {
                        Log.e(TAG, "Decrypt failed :", e);
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            return true;
        }
        if ("decrypt_dsa".equals(action)) {
            // getArrayBuffer does base64 decoding
            final byte[] decryptMe = args.getArrayBuffer(0);
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        byte[] decrypted = RSA.decrypt(decryptMe, ALIAS);
                        callbackContext.success(new String (decrypted));
                    } catch (Exception e) {
                        Log.e(TAG, "Decrypt failed :", e);
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            return true;
        }

        return false;
    }

    private void unlockCredentials() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Intent intent = new Intent("com.android.credentials.UNLOCK");
                startActivity(intent);
            }
        });
    }

    private Context getContext(){
        return cordova.getActivity().getApplicationContext();
    }

    private void startActivity(Intent intent){
        cordova.getActivity().startActivity(intent);
    }
}
