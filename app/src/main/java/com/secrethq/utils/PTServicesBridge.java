package com.secrethq.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.io.File;
import java.io.FileFilter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.nio.IntBuffer;

import org.cocos2dx.lib.Cocos2dxActivity;

import com.breakbounce.gamezapp.ResData;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;

import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.opengles.GL10;

import android.app.UiModeManager;
import android.content.res.Configuration;
import android.view.ViewGroup;
import android.content.*;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.net.Uri;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.content.IntentSender.SendIntentException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.graphics.Bitmap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PTServicesBridge extends BackupAgent
		implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
	private static PTServicesBridge sInstance;
	private static final String TAG = "PTServicesBridge";

	private static native String getLeaderboardId();
	private static native void warningMessageClicked(boolean accepted);
	public static native boolean removeAds();

	private static Cocos2dxActivity activity;
	private static WeakReference<Cocos2dxActivity> s_activity;

	private static GoogleApiClient mGoogleApiClient;

	private static String urlString;
	private static int scoreValue;

	private static BackupManager mBackupManager;
	public static ArrayList<HashMap<String , String>> iapInfo = new ArrayList<>();

	public static final int RC_SIGN_IN = 9001;
	private static final int REQUEST_LEADERBOARD = 5000;

	public static final String  kPTServicesFirstLaunchKey = "com.buildbox.kPTServicesFirstLaunchKey";

	public static final String kInAppIdsPrefs = "com.buildbox.kInAppIdsPrefs";
	public static final String kInAppIdsPrefsKey = "com.buildbox.kInAppIdsPrefs.key";

	public static native void noGameBackupData();
	public static native void shouldRestoreBackup(boolean shouldRestore);
	public static native void restoreCompleted(String progressData);

	// The name of the SharedPreferences file
	public static final String PREFS = "buildbox_bb3_user_progress";

	// A key to uniquely identify the set of backup data
	public  static final String PREFS_BACKUP_KEY = "progress";

	private static String MORE_GAMES_URL;
	private static String RATE_GAME_URL;
	private static String SHARE_MESSAGE;
	private static String PRIVACY_POLICY_URL;
	private static String SUPPORT_EMAIL_ADDRESS;

	public static PTServicesBridge instance() {
		if (sInstance == null)
			sInstance = new PTServicesBridge();
		return sInstance;
	}

	public static void initBridge(Cocos2dxActivity activity, String appId){
		Log.v(TAG, "PTServicesBridge  -- INIT");

		PTServicesBridge.s_activity = new WeakReference<Cocos2dxActivity>(activity);
		PTServicesBridge.activity = activity;

		PTServicesBridge.mBackupManager  = new BackupManager(activity);

		if(appId == null || appId.length() == 0 || appId.matches("[0-9]+") == false){
			return;
		}

		SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
		if(sharedPref.getBoolean("GooglePlayServiceSignInError", false) == true){
			Log.v(TAG, "Skipping logging in Google Play services");
			return;
		}

		// Create a GoogleApiClient instance
		PTServicesBridge.mGoogleApiClient = new GoogleApiClient.Builder(PTServicesBridge.activity)
				.addApi(Games.API).addScope(Games.SCOPE_GAMES)
				.addConnectionCallbacks(instance())
				.addOnConnectionFailedListener(instance())
				.build();
	}

	public static void openShareWidget( final String message ){
		View content = activity.findViewById(android.R.id.content);
		GLSurfaceView glView = null;
		if(content instanceof ViewGroup){
			// Diving one more level deeper to find a proper FrameLayout
			ViewGroup viewGroup = (ViewGroup)((ViewGroup)content).getChildAt(0);
			for (int i = 0; i < viewGroup.getChildCount(); i++) {
				View child = viewGroup.getChildAt(i);
				if(child instanceof GLSurfaceView){
					glView = (GLSurfaceView)child;
					break;
				}
			}
		}
		final int width = glView.getWidth();
		final int height = glView.getHeight();
		if(glView != null) {
			//We have to grab GL buffer right after we done rendering
			glView.queueEvent(new Runnable() {
				@Override
				public void run() {
					EGL10 egl = (EGL10)EGLContext.getEGL();
					GL10 gl = (GL10)egl.eglGetCurrentContext().getGL();

					Bitmap screenshot = createBitmapFromGLSurface(0, 0, width, height, gl);

					try {
						File outputDir = activity.getExternalCacheDir();
						final File file = File.createTempFile("screenshot", ".png", outputDir);
						FileOutputStream stream = new FileOutputStream(file);
						screenshot.compress(Bitmap.CompressFormat.PNG, 85, stream);
						stream.flush();
						stream.close();

						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
								StrictMode.setVmPolicy(builder.build());

								if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
									RATE_GAME_URL = ResData.getRateGamesUrl();
									// now finally we have screenshot so we need to call Intent and submit message and screenshot together
									Log.v(TAG, "PTServicesBridge  -- openShareWidget with text:" + message);
									String tempMessage = message.replace("share://share-game-link", RATE_GAME_URL);
									//ResData.Init(PTPlayer.getContext());
									SHARE_MESSAGE = ResData.getShareMessage();
									Log.d("SHARE_MESSAGE", SHARE_MESSAGE);
									String newMessage = tempMessage.replace("sharemessagedave", SHARE_MESSAGE);
									Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
									sharingIntent.setType("text/plain");
									sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, newMessage);
									//sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
									activity.startActivity(Intent.createChooser(sharingIntent, "Share"));
								} else{
									RATE_GAME_URL = ResData.getRateGamesUrl();
									// now finally we have screenshot so we need to call Intent and submit message and screenshot together
									Log.v(TAG, "PTServicesBridge  -- openShareWidget with text:" + message);
									String tempMessage = message.replace("share://share-game-link", RATE_GAME_URL);
									//ResData.Init(PTPlayer.getContext());
									SHARE_MESSAGE = ResData.getShareMessage();
									Log.d("SHARE_MESSAGE", SHARE_MESSAGE);
									String newMessage = tempMessage.replace("sharemessagedave", SHARE_MESSAGE);
									Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
									sharingIntent.setType("image/*");
									sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, newMessage);
									sharingIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
									activity.startActivity(Intent.createChooser(sharingIntent, "Share"));
								}

							}
						});

					}
					catch (Exception e) {
						Log.d(TAG, "Screenshot writing Error");
					}

				}
			});
		}
	}

	private static Bitmap createBitmapFromGLSurface(int x, int y, int w, int h, GL10 gl) {

		int bitmapBuffer[] = new int[w * h];
		int bitmapSource[] = new int[w * h];
		IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
		intBuffer.position(0);

		try {
			gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
			int offset1, offset2;
			for (int i = 0; i < h; i++) {
				offset1 = i * w;
				offset2 = (h - i - 1) * w;
				for (int j = 0; j < w; j++) {
					int texturePixel = bitmapBuffer[offset1 + j];
					int blue = (texturePixel >> 16) & 0xff;
					int red = (texturePixel << 16) & 0x00ff0000;
					int pixel = (texturePixel & 0xff00ff00) | red | blue;
					bitmapSource[offset2 + j] = pixel;
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "createBitmapFromGLSurface: " + e.getMessage(), e);
			return null;
		}

		return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
	}

	public static int availableProcessors() {
		int processorsNum = Runtime.getRuntime().availableProcessors();
		Log.d(TAG, "availableProcessors: " + processorsNum);
		return processorsNum;
	}

	public static int getCoresNumber() {

		class CpuFilter implements FileFilter {

			@Override
			public boolean accept(File pathname) {
				//Check if filename is "cpu", followed by a single digit number
				if(Pattern.matches("cpu[0-9]+", pathname.getName())) {
					return true;
				}

				return false;
			}
		}

		try {
			//Get directory containing CPU info
			File dir = new File("/sys/devices/system/cpu/");

			//Filter to only list the devices we care about
			File[] files = dir.listFiles(new CpuFilter());
			Log.d(TAG, "CPU Count: "+files.length);

			//Return the number of cores (virtual CPU devices)
			return files.length;

		} catch(Exception e) {
			//Print exception
			Log.d(TAG, "CPU Count: Failed.");
			e.printStackTrace();

			//Default to return 1 core
			return 1;
		}
	}

	public static void openUrl( String url ){
		Log.v(TAG, "PTServicesBridge  -- Open URL " + url);

		//ResData.Init(PTPlayer.getContext());

		MORE_GAMES_URL = ResData.getMoreGamesUrl();
		RATE_GAME_URL = ResData.getRateGamesUrl();
		PRIVACY_POLICY_URL = ResData.getPrivacyPolicyUrl() ;
		SUPPORT_EMAIL_ADDRESS = ResData.getSupportEmailAddress();

		Log.d("MORE_GAMES_URL", MORE_GAMES_URL);
		Log.d("RATE_GAME_URL", RATE_GAME_URL);

		if (url.contains("more")) {
			url = MORE_GAMES_URL;
		}

		if (url.contains("review")) {
			url = RATE_GAME_URL;
		}

		if (url.contains("privacypolicy")) {
			url = PRIVACY_POLICY_URL;
		}

		if (url.contains("threestars")) {
			url = "mailto:"+SUPPORT_EMAIL_ADDRESS+"?subject=Feedback about your game "+ResData.getAppName();
		}

		PTServicesBridge.urlString = url;

		PTServicesBridge.s_activity.get().runOnUiThread( new Runnable() {
			public void run() {
				try {
					final Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(PTServicesBridge.urlString));
					PTServicesBridge.activity.startActivity(intent);
				} catch(Exception e) {
					//Print exception
					Log.d(TAG, "OpenURL: Failed.");
					e.printStackTrace();
				}
			}
		});
	}


	public static void showLeaderboard( ){
		Log.v(TAG, "PTServicesBridge  -- Show Leaderboard ");

		if(PTServicesBridge.mGoogleApiClient == null || PTServicesBridge.mGoogleApiClient.isConnected() == false){
			Log.e(TAG, "Google play Servioces is not sigend");
			return;
		}

		PTServicesBridge.s_activity.get().runOnUiThread( new Runnable() {
			public void run() {
				String leaderboardId = PTServicesBridge.getLeaderboardId();
				if(leaderboardId == null || leaderboardId.isEmpty()){
					return;
				}
				PTServicesBridge.activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(PTServicesBridge.mGoogleApiClient,
						leaderboardId), REQUEST_LEADERBOARD);
			}
		});
	}

	public static void showCustomFullScreenAd() {
		Log.e(TAG, "PTServicesBridge  -- showCustomFullScreenAd");
	}

	public static void screenOnEnter(String name){
		Log.e(TAG, "PTServicesBridge  -- screenOnEnter");
	}

	public static void screenOnExit(String name){
		Log.e(TAG, "PTServicesBridge  -- screenOnExit");
	}

	public static void sceneOnEnter(String name){
		Log.e(TAG, "PTServicesBridge  -- sceneOnEnter");
	}

	public static void sceneOnExit(String name){
		Log.e(TAG, "PTServicesBridge  -- sceneOnExit");
	}

	public static void loadingDidComplete() {
		Log.e(TAG, "PTServicesBridge  -- loadingDidComplete");
	}

	public static void submitScrore( int score ){
		Log.v(TAG, "PTServicesBridge  -- Submit Score " + score);

		if(PTServicesBridge.mGoogleApiClient == null || PTServicesBridge.mGoogleApiClient.isConnected() == false){
			Log.e(TAG, "Google play Services is not sigend");
			return;
		}

		String leaderboardId = PTServicesBridge.getLeaderboardId();
		if(leaderboardId == null || leaderboardId.isEmpty()){
			return;
		}
		PTServicesBridge.scoreValue = score;

		if ( PTServicesBridge.mGoogleApiClient.isConnected() ) {
			Games.Leaderboards.submitScore(PTServicesBridge.mGoogleApiClient, leaderboardId, PTServicesBridge.scoreValue);
		}
	}

	public static boolean isRunningOnTV(){
		UiModeManager uiModeManager = (UiModeManager)PTServicesBridge.activity.getSystemService( Context.UI_MODE_SERVICE );
		if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
			Log.d("DeviceTypeRuntimeCheck", "Running on a TV Device");
			return true;

		} else {
			Log.d("DeviceTypeRuntimeCheck", "Running on a non-TV Device");
			return false;

		}
	}

	public static void showFacebookPage( final String facebookURL, final String facebookID){
		Log.v(TAG, "Show facebook page for URL: " + facebookURL + " ID: " + facebookID);

		PTServicesBridge.s_activity.get().runOnUiThread( new Runnable() {
			public void run() {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("fb://page/" + facebookID));
					PTServicesBridge.activity.startActivity(intent);
				} catch(Exception e) {
					Log.v(TAG, "Show facebook FAILED going to exception handler : " + e.getMessage());
					try {
						PTServicesBridge.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse( facebookURL )));
					} catch (Exception e2) {
						Log.v(TAG, "Show facebook exception handle FAILED : " + e2.getMessage());
					}

				}
			}
		});
	}

	public static void showWarningMessage(final String message){
		Log.v(TAG, "Show warning with message: " + message);
		PTServicesBridge.s_activity.get().runOnUiThread( new Runnable() {
			public void run() {
				AlertDialog.Builder dlgAlert  = new AlertDialog.Builder( PTServicesBridge.activity );

				dlgAlert.setMessage(message);
				dlgAlert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						PTServicesBridge.warningMessageClicked( false );
					}
				});
				dlgAlert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						PTServicesBridge.warningMessageClicked( true );
					}
				});
				dlgAlert.setCancelable(true);
				dlgAlert.create().show();
			}
		});

	}

	public static void loginGameServices( ){
		Log.v(TAG, "PTServicesBridge  -- Login Game Services ");

		if(PTServicesBridge.mGoogleApiClient != null){
			PTServicesBridge.mGoogleApiClient.connect();
		}
	}


	public static boolean isGameServiceAvialable( ){
		//Log.v(TAG, "PTServicesBridge  -- Is Game Service Avialable ");
		return (PTServicesBridge.mGoogleApiClient != null && PTServicesBridge.mGoogleApiClient.isConnected());
	}

	@Override
	public void onConnected(Bundle arg0) {
		Log.v(TAG, "PTServicesBridge  -- API Client Connected bundle:" + arg0);
	}

	@Override
	public void onConnectionSuspended(int arg0) {
		Log.v(TAG, "PTServicesBridge  -- API Client Connection Suspended ");
	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		Log.v(TAG, "PTServicesBridge  -- API Client Connection FAILED:" + connectionResult);

		if(connectionResult.hasResolution()){
			try {
				connectionResult.startResolutionForResult(activity, RC_SIGN_IN);
			} catch (SendIntentException e) {
				mGoogleApiClient.connect();
			}
		}
	}

	public static void backButtonPressed( ){
		Log.v(TAG, "PTServicesBridge -- backButtonPressed");
		PTServicesBridge.s_activity.get().runOnUiThread( new Runnable() {
			public void run() {
				PTServicesBridge.activity.moveTaskToBack(true);
			}
		});
	}

	public void  onActivityResult(int requestCode, int responseCode, Intent intent){
		if(requestCode == RC_SIGN_IN && responseCode == -1){
			mGoogleApiClient.connect();
		}
	}

	public static String sha1( byte[] data, int length) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.update(data, 0, length);
		byte[] sha1hash = md.digest();
		return convertToHex(sha1hash);
	}

	public static Object sha1Init() {
		MessageDigest digest = null;
		try{
			digest = MessageDigest.getInstance("SHA-1");
		}
		catch (NoSuchAlgorithmException e){
			Log.e(TAG, "PTServicesBridge -- NoSuchAlgorithmException");
		}
		return digest;
	}

	public static void sha1Update(Object digest, byte[] data, int length) {
		((MessageDigest)digest).update(data, 0, length);
	}

	public static String sha1Finish(Object digest) {
		byte[] hash = ((MessageDigest)digest).digest();

		StringBuilder sb = new StringBuilder();
		for (byte b: hash) {
			sb.append(String.format("%02x", b));
		}

		return sb.toString();
	}

	private static String convertToHex(byte[] data) {
		StringBuilder buf = new StringBuilder();
		for (byte b : data) {
			int halfbyte = (b >>> 4) & 0x0F;
			int two_halfs = 0;
			do {
				buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
				halfbyte = b & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	// Backup and Restore
	public static void backupData(String progressData, String gameId) {
		if (activity == null) {
			Log.d(TAG,"activity is null.");
			return;
		}
		try {
			String encryptText = AESHelper.encrypt(progressData);

			String name = PTServicesBridge.PREFS;
			SharedPreferences sharedpreferences = activity.getSharedPreferences(name, Context.MODE_PRIVATE);
			//SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
			SharedPreferences.Editor editor = sharedpreferences.edit();
			editor.putString(PTServicesBridge.PREFS_BACKUP_KEY,encryptText);
			editor.commit();

			// Queue backup request
			mBackupManager.dataChanged();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void restoreData(String gameId) {
		// NB: gameId is not used, it's needed on iOS for bbworld bits but on Android all contained games will be restored together
		boolean isFirstLaunch = PTServicesBridge.isFirstLaunch("");
		String name = PTServicesBridge.PREFS;
		SharedPreferences sharedpreferences = activity.getSharedPreferences(name, Context.MODE_PRIVATE);
		String dataToBackup = sharedpreferences.getString(PTServicesBridge.PREFS_BACKUP_KEY, "");
		if (!(dataToBackup == null || dataToBackup.isEmpty()) && isFirstLaunch) {
			Log.v(TAG, "game backup data found, prompt to restore");
			promptRestoreData();
		}
		else {
			Log.v(TAG, "no game backup data found");
			noGameBackupData();
		}
	}

	public static void promptRestoreData() {
		s_activity.get().runOnUiThread(() -> {
			AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
			alertDialog.setTitle("Restore Progress?");
			alertDialog.setMessage("We found records for your previous progress in this game from another installation. Would you like to keep it on this device?");
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							shouldRestoreBackup(true);
							dialog.dismiss();
						}
					});
			alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							showAppClosePrompt();
						}
					});
			alertDialog.show();
		});
	}

	public static boolean isFirstLaunch(String gameId) {
		Log.v(TAG, "PTServicesBridge -- check isFirstLaunch " + gameId);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		return (!prefs.getBoolean(kPTServicesFirstLaunchKey, false));
	}

	public static void setDidFirstLaunch (String gameId) {
		Log.v(TAG, "PTServicesBridge -- set didFirstLaunch " + gameId);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
		prefs.edit().putBoolean(kPTServicesFirstLaunchKey, true).apply();
	}

	public static void setInAppPurchaseInfo(@Nullable ArrayList<Pair<String , String>>  info) {
		Log.v(TAG, "PTServicesBridge -- setInAppPurchaseInfo");

		if (info != null && info.size() != 0) {
			ArrayList<HashMap<String, String>> idsList = new ArrayList<HashMap<String, String>>();
			for (Pair<String, String> p : info) {
				Map<String, String> map = new HashMap<String, String>();
				map.put("id", p.first);
				map.put("type", p.second);
				idsList.add((HashMap<String, String>) map);
			}
			iapInfo = idsList;
			Gson gson = new Gson();
			String jsonText = gson.toJson(idsList);

			SharedPreferences sharedPreferences = activity.getSharedPreferences(kInAppIdsPrefs, MODE_PRIVATE);
			SharedPreferences.Editor editor = sharedPreferences.edit();//Create a HashMap
			editor.putString(kInAppIdsPrefsKey,jsonText);
			editor.commit();
		}
		PTServicesBridge.getInAppIds();
	}

	public static  ArrayList<HashMap<String, String>> getInAppIds() {
		Gson gson = new Gson();
		SharedPreferences sharedPreferences = activity.getSharedPreferences(kInAppIdsPrefs, MODE_PRIVATE);
		String jsonText = sharedPreferences.getString(kInAppIdsPrefsKey, "{}");
		if (jsonText.equals("{}")) {
			return new ArrayList<>();
		}
		iapInfo = gson.fromJson(jsonText,  new TypeToken<ArrayList<HashMap<String,String>>>() {}.getType());
		return iapInfo;
	}

	private static void showAppClosePrompt() {
		s_activity.get().runOnUiThread(() -> {
			String gameName = "Game";
//			if (activity != null) {
//				gameName = activity.getString(R.string.app_name);
//			}
			AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
			alertDialog.setTitle("Clear Saved Data?");
			alertDialog.setMessage("To delete all the saved data, " + gameName + " will close itself. You can launch it again.");
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Cancel",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							shouldRestoreBackup(false);
							dialog.dismiss();
						}
					});
			alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Ok",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							clearAppData();
							shouldRestoreBackup(false);
							dialog.dismiss();
						}
					});
			alertDialog.show();
		});
	}

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
		Log.v(TAG, "PTServicesBridge -- onBackup");
		// Get the oldState input stream
		FileInputStream instream = new FileInputStream(oldState.getFileDescriptor());
		DataInputStream in = new DataInputStream(instream);
		String name = PTServicesBridge.PREFS;
		SharedPreferences sharedpreferences = activity.getSharedPreferences(name, Context.MODE_PRIVATE);
		String dataToBackup = sharedpreferences.getString(PTServicesBridge.PREFS_BACKUP_KEY, "");
		if (dataToBackup == "") {
			// Don't back up because the file hasn't changed
			return;
		}
		try {
			// The file has been modified, so do a backup
			// Or the time on the device changed, so be safe and do a backup

			// Create buffer stream and data output stream for our data
			ByteArrayOutputStream bufStream = new ByteArrayOutputStream();
			DataOutputStream outWriter = new DataOutputStream(bufStream);
			// Write structured data
			outWriter.writeUTF(dataToBackup);
			// Send the data to the Backup Manager via the BackupDataOutput
			byte[] buffer = bufStream.toByteArray();
			int len = buffer.length;
			data.writeEntityHeader(PREFS, len);
			data.writeEntityData(buffer, len);

			FileOutputStream outstream = new FileOutputStream(newState.getFileDescriptor());
			DataOutputStream out = new DataOutputStream(outstream);

			Date currentTime = Calendar.getInstance().getTime();
			long modified = currentTime.getTime();
			out.writeLong(modified);

		} catch (IOException e) {
			Log.d(TAG, "PTServicesBridge -- unable to create backup");
		}
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
		Log.v(TAG, "PTServicesBridge -- onRestore");
		String  progress = "";
		while (data.readNextHeader()) {
			String key = data.getKey();
			int dataSize = data.getDataSize();

			// If the key is ours (for saving progress). Note this key was used when
			// we wrote the backup entity header

			if (PTServicesBridge.PREFS.equals(key)) {
				// Create an input stream for the BackupDataInput
				byte[] dataBuf = new byte[dataSize];
				data.readEntityData(dataBuf, 0, dataSize);
				ByteArrayInputStream baStream = new ByteArrayInputStream(dataBuf);
				DataInputStream in = new DataInputStream(baStream);
				String  encryptedProgress = in.readUTF();
				try {
					progress = AESHelper.decrypt(encryptedProgress);
					restoreCompleted(progress);
				}
				catch (Exception e) {
					Log.d(TAG, "PTServicesBridge -- unable to restore backup");
				}
			} else {
				// We don't know this entity key. Skip it. (Shouldn't happen.)
				Log.d(TAG, "PTServicesBridge -- unrecognized entity key in restoring backup");
				data.skipEntityData();
			}
		}

		// Finally, write to the state blob (newState) that describes the restored data
		FileOutputStream outStream = new FileOutputStream(newState.getFileDescriptor());
		DataOutputStream out = new DataOutputStream(outStream);
		out.writeUTF(progress);
	}

	private static void clearAppData() {
		try {
			// clearing app data
			if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
				boolean status = ((ActivityManager)activity.getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData();
				if (status) {
					Log.d("","Clean Successful");
				}
				else {
					Log.d("","Clean Failed");
				}
			} else {
				String packageName = activity.getApplicationContext().getPackageName();
				Runtime runtime = Runtime.getRuntime();
				runtime.exec("pm clear "+packageName);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
