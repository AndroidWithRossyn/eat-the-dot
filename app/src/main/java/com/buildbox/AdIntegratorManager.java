package com.buildbox;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.apponboard.aob_sessionreporting.AOBReporting;
import com.breakbounce.gamezapp.ResData;
import com.buildbox.consent.ConsentHelper;
import com.buildbox.consent.SdkConsentInfo;
import com.secrethq.utils.PTJniHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


public class AdIntegratorManager implements AdIntegratorManagerInterface {

    private static HashMap<String, AdIntegratorInterface> integrators = new HashMap<>();
    private static AdIntegratorManager instance;
    private static WeakReference<Activity> activity;
    private static String TAG = "AdIntegratorManager";

    public static AdIntegratorManager getInstance() {
        if (instance == null) {
            instance = new AdIntegratorManager();
        }
        return instance;
    }

    public static void initBridge(Activity act) {
        activity = new WeakReference<>(act);
        /* ironsource */ /* integrators.put("ironsource", new com.buildbox.adapter.ironsource.AdIntegrator()); */ /* ironsource */
        /* admob */ integrators.put("admob", new com.buildbox.adapter.admob.AdIntegrator()); /* admob */
        /* adbox-applovin integrators.put("adbox-applovin", new com.buildbox.adapter.applovin.AdIntegrator()); adbox-applovin */
        /* adbox-vungle integrators.put("adbox-vungle", new com.buildbox.adapter.vungle.AdIntegrator()); adbox-vungle */
        /* custom */ /* integrators.put("custom", new com.buildbox.adapter.custom.CustomAdIntegrator()); */ /* custom */

    }

    public static void fetchRemoteConfig() {
        GetRemoteAdboxConfigTask task = new GetRemoteAdboxConfigTask();
        task.execute();
    }

    public static void initSdk(String sdkId, final HashMap<String, String> initValues) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity.get());
        final boolean userConsent = sharedPreferences.getBoolean(ConsentHelper.getConsentKey(sdkId), false);
        final boolean targetsChildren = PTJniHelper.targetsChildren();
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.setTargetsChildren(targetsChildren);
                    integrator.setUserConsent(userConsent);

                    if(sdkId.equals("admob")){
                        //android.util.Log.d("NATHAN-INIT-INT", "run: "+initValues.get("Interstitial ID"));
                        //android.util.Log.d("NATHAN-INIT-APPID", "run: "+initValues.get("App ID"));
                        //android.util.Log.d("NATHAN-INIT-BANNER", "run: "+initValues.get("Banner ID"));
                        //android.util.Log.d("NATHAN-INIT-REWARD", "run: "+initValues.get("Rewarded Video ID"));
                        initValues.put("Interstitial ID", ResData.getAdmobInterstitialId());
                        initValues.put("App ID",ResData.getAdmobAppId());
                        initValues.put("Banner ID",ResData.getAdmobBannerId());
                        initValues.put("Rewarded Video ID",ResData.getAdmobRewardId());
                    }

                    if(sdkId.equals("adbox-applovin")){
                        //android.util.Log.d("NATHAN-INIT-INT", "run: "+initValues.get("Interstitial ID"));
                        //android.util.Log.d("NATHAN-INIT-APPID", "run: "+initValues.get("App ID"));
                        //android.util.Log.d("NATHAN-INIT-BANNER", "run: "+initValues.get("Banner ID"));
                        //android.util.Log.d("NATHAN-INIT-REWARD", "run: "+initValues.get("Rewarded Video ID"));
                        initValues.put("integratorClassPath","");
                    }

                    if(sdkId.equals("adbox-vungle")){
                        //android.util.Log.d("NATHAN-INIT-INT", "run: "+initValues.get("Interstitial ID"));
                        //android.util.Log.d("NATHAN-INIT-APPID", "run: "+initValues.get("App ID"));
                        //android.util.Log.d("NATHAN-INIT-BANNER", "run: "+initValues.get("Banner ID"));
                        //android.util.Log.d("NATHAN-INIT-REWARD", "run: "+initValues.get("Rewarded Video ID"));
                        initValues.put("Vungle App ID","");
                        initValues.put("integratorClassPath","");
                        initValues.put("Vungle Banner Placement ID","");
                        initValues.put("Vungle Interstitial Placement ID","");
                        initValues.put("Vungle Rewarded Video Placement ID","");
                    }


                    if(sdkId.equals("admob")){
                        //android.util.Log.d("NATHAN-INIT-INT", "run: "+initValues.get("Interstitial ID"));
                        //android.util.Log.d("NATHAN-INIT-APPID", "run: "+initValues.get("App ID"));
                        //android.util.Log.d("NATHAN-INIT-BANNER", "run: "+initValues.get("Banner ID"));
                        //android.util.Log.d("NATHAN-INIT-REWARD", "run: "+initValues.get("Rewarded Video ID"));
                    }

                    //android.util.Log.d("NATHAN_SDK", ""+initValues);

                    integrator.initAds(initValues, activity, getInstance());
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
            Log.d(TAG, "failing ad network " + sdkId);
            getInstance().networkFailed(sdkId);
        }
    }

    public static void cleanupSdk(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            Log.d(TAG, "cleaning up sdk for " + sdkId);
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.cleanup();
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void initBanner(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.initBanner();
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void initInterstitial(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.initInterstitial();
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void initRewardedVideo(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.initRewardedVideo();
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static boolean showBanner(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            // activity.get().runOnUiThread(new Runnable() {
            //     @Override
            //     public void run() {
            //         integrator.showBanner();
            //     }
            // });
            // return true;

            // since we want to return if the ad will show, we have to let the Integrator manage the threading
            return integrator.showBanner();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
            return false;
        }
    }

    public static void hideBanner(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.hideBanner();
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static boolean showInterstitial(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            // activity.get().runOnUiThread(new Runnable() {
            //     @Override
            //     public void run() {
            //         integrator.showInterstitial();
            //     }
            // });
            // return true;

            // since we want to return if the ad will show, we have to let the Integrator manage the threading
            return integrator.showInterstitial();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
            return false;
        }
    }

    public static boolean showRewardedVideo(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            // activity.get().runOnUiThread(new Runnable() {
            //     @Override
            //     public void run() {
            //         integrator.showRewardedVideo();
            //     }
            // });
            // return true;

            // since we want to return if the ad will show, we have to let the Integrator manage the threading
            return integrator.showRewardedVideo();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
            return false;
        }
    }

    public static boolean isBannerVisible(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return integrator.isBannerVisible();
        }
        return false;
    }

    public static boolean isRewardedVideoAvailable(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return integrator.isRewardedVideoAvailable();
        }
        return false;
    }

    public static void setUserConsent(String sdkId, final boolean consentGiven) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    integrator.setUserConsent(consentGiven);
                }
            });
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void revokeAllConsent() {
        activity.get().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sharedPreferences = android.preference.PreferenceManager.getDefaultSharedPreferences(activity.get());
                for (SdkConsentInfo sdkConsentInfo : ConsentHelper.getSdkConsentInfos()) {
                    Log.d(TAG, "Revoking consent for " + sdkConsentInfo.getSdkId());
                    sharedPreferences
                            .edit()
                            .putBoolean(ConsentHelper.getConsentKey(sdkConsentInfo.getSdkId()), false)
                            .commit();
                    final AdIntegratorInterface integrator = integrators.get(sdkConsentInfo.getSdkId());
                    if (integrator!=null){
                        integrator.setUserConsent(false);
                    } else {
                        Log.e(TAG, "Ad network " + sdkConsentInfo.getSdkId() + " not found in map");
                    }
                }
                Toast toast = Toast.makeText(activity.get(), "Consent revoked for all ad networks", Toast.LENGTH_SHORT);
                toast.show();
            }
        });
    }

    public static boolean sdkNeedsInit(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return integrator.sdkNeedsInit();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
        return false;
    }

    public static boolean sdkIsReady(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return integrator.sdkIsReady();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
        return false;
    }

    public static int bannerLoadStateForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return (int)integrator.bannerLoadState();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
        return 0;
    }

    public static int interstitialLoadStateForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return (int)integrator.interstitialLoadState();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
        return 0;
    }

    public static int rewardedVideoLoadStateForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            return (int)integrator.rewardedVideoLoadState();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
        return 0;
    }

    public static void clearBannerLoadStateErrorsForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            integrator.clearBannerLoadStateErrors();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void clearInterstitialLoadStateErrorsForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            integrator.clearInterstitialLoadStateErrors();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public static void clearRewardedVideoLoadStateErrorsForNetwork(String sdkId) {
        final AdIntegratorInterface integrator = integrators.get(sdkId);
        if (integrator != null) {
            integrator.clearRewardedVideoLoadStateErrors();
        } else {
            Log.e(TAG, "Ad network " + sdkId + " not found in map");
        }
    }

    public void bannerImpression(String sdkId) {
        Log.d(TAG, "Banner impression");
        AOBReporting.bannerAdAttempt(sdkId, true);
    }

    public void interstitialImpression(String sdkId) {
        Log.d(TAG, "Interstitial impression");
        AOBReporting.interstitialAdAttempt(sdkId, true);
    }

    public void rewardedVideoImpression(String sdkId) {
        Log.d(TAG, "Rewarded video impression");
        AOBReporting.rewardedVideoAdAttempt(sdkId, true);
    }

    @Override
    public void bannerAdZoneAttempt(String networkName, String zoneId, boolean didFill) {
        AOBReporting.bannerAdZoneAttempt(networkName, zoneId, didFill);
    }

    @Override
    public void interstitialAdZoneAttempt(String networkName, String zoneId, boolean didFill) {
        AOBReporting.interstitialAdZoneAttempt(networkName, zoneId, didFill);
    }

    @Override
    public void rewardedVideoAdZoneAttempt(String networkName, String zoneId, boolean didFill) {
        AOBReporting.rewardedVideoAdZoneAttempt(networkName, zoneId, didFill);
    }

    public static void onActivityCreated(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityCreated(activity);
        }
    }

    public static void onActivityStarted(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityStarted(activity);
        }
    }

    public static void onActivityResumed(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityResumed(activity);
        }
    }

    public static void onActivityPaused(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityPaused(activity);
        }
    }

    public static void onActivityStopped(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityStopped(activity);
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        for (Map.Entry mapElement : integrators.entrySet()) {
            ((AdIntegratorInterface) mapElement.getValue()).onActivityDestroyed(activity);
        }
    }

    public void interstitialClosed(String sdkId) {
        interstitialClosedNative(sdkId);
    }

    public void rewardedVideoDidReward(String sdkId, boolean value) {
        rewardedVideoDidRewardNative(sdkId, value);
    }

    public void rewardedVideoDidEnd(String sdkId, boolean value) {
        rewardedVideoDidEndNative(sdkId, value);
    }

    public void bannerLoaded(String sdkId) {
        bannerLoadedNative(sdkId);
    }

    public void interstitialLoaded(String sdkId) {
        interstitialLoadedNative(sdkId);
    }

    public void rewardedVideoLoaded(String sdkId) {
        rewardedVideoLoadedNative(sdkId);
    }

    public void bannerFailed(String sdkId) {
        bannerFailedNative(sdkId);
        AOBReporting.bannerAdAttempt(sdkId, false);
    }

    public void interstitialFailed(String sdkId) {
        interstitialFailedNative(sdkId);
        AOBReporting.interstitialAdAttempt(sdkId, false);
    }

    public void rewardedVideoFailed(String sdkId) {
        rewardedVideoFailedNative(sdkId);
        AOBReporting.rewardedVideoAdAttempt(sdkId, false);
    }

    public void sdkLoaded(String sdkId) {
        sdkLoadedNative(sdkId);
    }

    /**
     * @deprecated Replaced with {@link #sdkLoaded(String)}.
     */
    public void networkLoaded(String adNetworkId) {
        sdkLoadedNative(adNetworkId);
    }

    public void sdkFailed(String sdkId) {
        sdkFailedNative(sdkId);
    }

    /**
     * @deprecated Replaced with {@link #sdkFailed(String)}.
     */
    public void networkFailed(String adNetworkId) {
        sdkFailedNative(adNetworkId);
    }

    public static CustomIntegrator getCustomIntegrator() {
        if (integrators.containsKey("custom")) {
            Integrator integrator = integrators.get("custom");

            if (integrator instanceof CustomIntegrator) {
                return (CustomIntegrator)integrator;
            }
        }

        return null;
    }

    public static native void sdkLoadedNative(String sdkId);

    public static native void sdkFailedNative(String sdkId);

    public static native void interstitialClosedNative(String sdkId);

    public static native void rewardedVideoDidRewardNative(String sdkId, boolean value);

    public static native void rewardedVideoDidEndNative(String sdkId, boolean value);

    public static native void bannerLoadedNative(String sdkId);

    public static native void interstitialLoadedNative(String sdkId);

    public static native void rewardedVideoLoadedNative(String sdkId);

    public static native void bannerFailedNative(String sdkId);

    public static native void interstitialFailedNative(String sdkId);

    public static native void rewardedVideoFailedNative(String sdkId);

    public static native void remoteConfigResultNative(boolean jsuccess, String jvalue);


    private static class GetRemoteAdboxConfigTask extends AsyncTask<String, Integer, String> {
        protected String doInBackground(String... params) {
            String content = "adbox-vungle,adbox-applovin";
            try {
                URL url = new URL("https://sdks.api.8cell.com/zones/network_priority.json");
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                InputStream is = connection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);

                String line;
                content = "";
                while ((line = br.readLine()) != null) {
                    content += line;
                }

                br.close();
            } catch (Exception e) {
                Log.e(TAG, "unable to fetch remote config");
                e.printStackTrace();
                remoteConfigResultNative(false, content);
            }
            return content;
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(String result) {
            activity.get().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    remoteConfigResultNative(true, result);
                }
            });
        }
    }
}
