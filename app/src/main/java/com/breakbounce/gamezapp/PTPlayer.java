package com.breakbounce.gamezapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.apponboard.aob_sessionreporting.AOBReporting;
import com.buildbox.AdIntegratorManager;
import com.buildbox.AnalyticsIntegratorManager;
import com.buildbox.consent.ConsentActivity;
import com.buildbox.consent.ConsentHelper;
import com.buildbox.consent.SdkConsentInfo;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.secrethq.store.PTStoreBridge;

//import com.secrethq.ads.*;
import com.secrethq.utils.*;

import org.cocos2dx.lib.Cocos2dxActivity;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;
import org.cocos2dx.lib.Cocos2dxReflectionHelper;

import java.util.List;

public class PTPlayer extends Cocos2dxActivity {
	private boolean isPTStoreAvailable = false;
	static {
		System.loadLibrary("player");
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		try {
			Log.v("----------","onActivityResult: request: " + requestCode + " result: "+ resultCode);
			if(requestCode == PTServicesBridge.RC_SIGN_IN){
				SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = sharedPref.edit();

				if(resultCode == RESULT_OK){
					PTServicesBridge.instance().onActivityResult(requestCode, resultCode, data);
					editor.putBoolean("GooglePlayServiceSignInError", false);
					editor.commit();
				}
				else if(resultCode == GamesActivityResultCodes.RESULT_SIGN_IN_FAILED){
					int duration = Toast.LENGTH_SHORT;
					Toast toast = Toast.makeText(this, "Google Play Services: Sign in error", duration);
					toast.show();
					editor.putBoolean("GooglePlayServiceSignInError", true);
					editor.commit();
				}
				else if(resultCode == GamesActivityResultCodes.RESULT_APP_MISCONFIGURED){
					int duration = Toast.LENGTH_SHORT;
					Toast toast = Toast.makeText(this, "Google Play Services: App misconfigured", duration);
					toast.show();
				}
			}
		} catch (Exception e) {
			Log.v("-----------", "onActivityResult FAIL on iabHelper : " + e.toString());
		}
	}

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		launchConsentActivity();
		this.hideVirtualButton();

		boolean isDebug = ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
		if (!isDebug) {
			AOBReporting.initialize(this, getString(R.string.bb_version));
		}

		ResData.init(this);

		PTServicesBridge.initBridge(this, getString( R.string.app_id ));
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		AdIntegratorManager.onActivityCreated(this);
		AnalyticsIntegratorManager.onActivityCreated(this);
	}

	private void launchConsentActivity() {
		if (!hasSeenConsentForAllSdks()) {
			Intent intent = new Intent(this, ConsentActivity.class);
			startActivity(intent);
			finish();
		}
	}

	private boolean hasSeenConsentForAllSdks() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		List<SdkConsentInfo> consentInfos = ConsentHelper.getSdkConsentInfos();
		for (SdkConsentInfo consentInfo : consentInfos) {
			if (!preferences.contains(ConsentHelper.getConsentKey(consentInfo.getSdkId()))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onNativeInit(){
		initBridges();
	}

	private void initBridges(){
		AdIntegratorManager.initBridge(this);
		AnalyticsIntegratorManager.initBridge(this);
		PTStoreBridge.initBridge( this );
		isPTStoreAvailable = true;
	}

	@Override
	public Cocos2dxGLSurfaceView onCreateView() {
		Cocos2dxGLSurfaceView glSurfaceView = new Cocos2dxGLSurfaceView(this);
		glSurfaceView.setEGLConfigChooser(8, 8, 8, 0, 0, 0);

		return glSurfaceView;
	}

	@Override
	protected void onPause() {
		super.onPause();
		AdIntegratorManager.onActivityPaused(this);
		AnalyticsIntegratorManager.onActivityPaused(this);
	}

	@Override
	protected void onResume() {
		this.hideVirtualButton();
		super.onResume();
		AdIntegratorManager.onActivityResumed(this);
		AnalyticsIntegratorManager.onActivityResumed(this);
		if (isPTStoreAvailable) {
			PTStoreBridge.acknowledgePendingPurchases();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			this.hideVirtualButton();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		AdIntegratorManager.onActivityStarted(this);
		AnalyticsIntegratorManager.onActivityStarted(this);
		AOBReporting.startOrResumeSessionReporting();
	}

	@Override
	protected void onStop() {
		super.onStop();
		AdIntegratorManager.onActivityStopped(this);
		AnalyticsIntegratorManager.onActivityStopped(this);
		AOBReporting.pauseSessionReporting();
	}

	@Override
	protected void onDestroy() {
		AdIntegratorManager.onActivityDestroyed(this);
		AnalyticsIntegratorManager.onActivityDestroyed(this);
		AOBReporting.stopSessionReporting();
		super.onDestroy();
	}

	protected void hideVirtualButton() {
		if (Build.VERSION.SDK_INT >= 19) {
			// use reflection to remove dependence of API level

			Class viewClass = View.class;
			final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION");
			final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN");
			final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_HIDE_NAVIGATION");
			final int SYSTEM_UI_FLAG_FULLSCREEN = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_FULLSCREEN");
			final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_IMMERSIVE_STICKY");
			final int SYSTEM_UI_FLAG_LAYOUT_STABLE = Cocos2dxReflectionHelper
					.<Integer> getConstantValue(viewClass,
							"SYSTEM_UI_FLAG_LAYOUT_STABLE");

			// getWindow().getDecorView().setSystemUiVisibility();
			final Object[] parameters = new Object[] { SYSTEM_UI_FLAG_LAYOUT_STABLE
					| SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
					| SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
					| SYSTEM_UI_FLAG_IMMERSIVE_STICKY };
			Cocos2dxReflectionHelper.<Void> invokeInstanceMethod(getWindow()
							.getDecorView(), "setSystemUiVisibility",
					new Class[] { Integer.TYPE }, parameters);
		}
	}
}
