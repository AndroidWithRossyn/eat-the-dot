package com.secrethq.store;

import java.lang.ref.WeakReference;

import org.cocos2dx.lib.Cocos2dxActivity;

import android.app.ProgressDialog;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.secrethq.store.util.*;

import kotlinx.coroutines.GlobalScope;


public class PTStoreBridge {
    private static boolean readyToPurchase = false;

    private static Cocos2dxActivity activity;
    private static WeakReference<Cocos2dxActivity> s_activity;
    private static final String TAG = "PTStoreBridge";

    private static native String licenseKey();

    public static native void purchaseDidComplete(String productId);

    public static native void purchaseDidCompleteRestoring(String productId);

    public static native boolean isProductConsumible(String productId);

    private static BillingDataSource billingDataSource;
    private static boolean inProgress = false;
    static public void initBridge(Cocos2dxActivity _activity) {
        activity = _activity;

        s_activity = new WeakReference<Cocos2dxActivity>(activity);
        billingDataSource = BillingDataSource.initialize(activity.getApplication(), GlobalScope.INSTANCE);

    }

    public static void acknowledgePendingPurchases() {
        s_activity.get().runOnUiThread(() -> {
            billingDataSource.acknowledgePendingPurchases(activity, (resultCode, message) -> {
                if (resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_OK()) {
                    purchaseDidCompleteRestoring(message);
                } else if (resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_RESTORE_COMPLETED()) {
                    s_activity.get().runOnUiThread(() -> {
                        Toast.makeText(activity, "All pending purchases have been acknowledged.", Toast.LENGTH_SHORT).show();
                    });
                }
                return null;
            });
        });
    }

    static public void purchase(final String storeId, boolean isConsumable) {
        if (inProgress) {
            s_activity.get().runOnUiThread(() -> {
                Toast.makeText(activity, "An In-app purchase flow is already in progress.", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        inProgress = true;
        s_activity.get().runOnUiThread(() -> {
            billingDataSource.launchBillingFlow(activity, storeId, isConsumable, (resultCode, message) -> {
                if (resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_OK() ||
                        resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED()) {
                    purchaseDidComplete(storeId);
                } else {
                    s_activity.get().runOnUiThread(() -> {
                        Toast.makeText(activity, "Unable to process the request. Try again later.", Toast.LENGTH_SHORT).show();
                    });

                }
                inProgress = false;
                return null;
            });
        });
    }

    static public void restorePurchases() {
        s_activity.get().runOnUiThread(new Runnable() {
            public void run() {
                final ProgressDialog progress;
                progress = ProgressDialog.show(activity, null,
                        "Restoring purchases...", true);

                billingDataSource.restorePreviousIAPs(activity, (resultCode, message) -> {
                    if (resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_OK()) {
                        purchaseDidCompleteRestoring(message);
                    } else if (resultCode == billingDataSource.getBILLING_RESPONSE_RESULT_RESTORE_COMPLETED()) {
                        progress.dismiss();
                        Toast.makeText(activity, "Successfully restored all the purchases.", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        progress.dismiss();
                        s_activity.get().runOnUiThread(() -> {
                            Toast.makeText(activity, "Unable to restore purchases. Try again later.", Toast.LENGTH_SHORT).show();
                        });

                    }
                    return null;
                });
            }
        });
    }
}