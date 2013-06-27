package com.google.cordova.plugin;

import com.google.ads.Ad;
import com.google.ads.AdListener;
import com.google.ads.AdRequest;
import com.google.ads.AdRequest.ErrorCode;
import com.google.ads.AdSize;
import com.google.ads.AdView;
import com.google.ads.mediation.admob.AdMobAdapterExtras;

import android.util.Log;
import android.view.View;

import org.apache.cordova.LinearLayoutSoftKeyboardDetect;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.PluginResult;
import org.apache.cordova.api.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * This class represents the native implementation for the AdMob Cordova plugin.
 * This plugin can be used to request AdMob ads natively via the Google AdMob SDK.
 * The Google AdMob SDK is a dependency for this plugin.
 */
public class AdMobPlugin extends CordovaPlugin {
  /** The adView to display to the user. */
  private AdView adView;

  /** Whether or not the ad should be positioned at top or bottom of screen. */
  private boolean positionAtTop;

  /** Common tag used for logging statements. */
  private static final String LOGTAG = "AdMobPlugin";

  /** Cordova Actions. */
  private static final String ACTION_CREATE_BANNER_VIEW = "createBannerView";
  private static final String ACTION_REQUEST_AD = "requestAd";
  private CallbackContext callback ;

  /**
   * This is the main method for the AdMob plugin.  All API calls go through here.
   * This method determines the action, and executes the appropriate call.
   *
   * @param action The action that the plugin should execute.
   * @param inputs The input parameters for the action.
   * @param callbackId The callback ID.  This is currently unused.
   * @return A PluginResult representing the result of the provided action.  A
   *         status of INVALID_ACTION is returned if the action is not recognized.
   */
  @Override
  public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) {
    callback = callbackContext;
    if (ACTION_CREATE_BANNER_VIEW.equals(action)) {
      executeCreateBannerView(inputs);
      
      return true;
    } else if (ACTION_REQUEST_AD.equals(action)) {
      executeRequestAd(inputs);
     return true;
    } else if(action.equals("showHide")){ 
       showHide(inputs);
         return true;
    }else {
      Log.d(LOGTAG, String.format("Invalid action passed: %s", action));
      callback.error("Invalid Action");
    }
    return false;
  }

  /**
   * Parses the create banner view input parameters and runs the create banner
   * view action on the UI thread.  If this request is successful, the developer
   * should make the requestAd call to request an ad for the banner.
   *
   * @param inputs The JSONArray representing input parameters.  This function
   *        expects the first object in the array to be a JSONObject with the
   *        input parameters.
   * @return A PluginResult representing whether or not the banner was created
   *         successfully.
   */
  private void executeCreateBannerView(JSONArray inputs) {
    String publisherId;
    String size;
  
    // Get the input data.
    try {
    	publisherId 	= inputs.getString(0);
    	size			= inputs.getString(1);
      this.positionAtTop = Boolean.parseBoolean(inputs.getString(2));
    } catch (JSONException exception) {
      Log.w(LOGTAG, String.format("Got JSON Exception: %s", exception.getMessage()));
      callback.error(exception.getMessage());
      return;
    }

    // Create the AdView on the UI thread.
    this.executeRunnable(new CreateBannerViewRunnable(
        publisherId, adSizeFromSize(size)));
  }

  /**
   * Parses the request ad input parameters and runs the request ad action on
   * the UI thread.
   *
   * @param inputs The JSONArray representing input parameters.  This function
   *        expects the first object in the array to be a JSONObject with the
   *        input parameters.
   * @return A PluginResult representing whether or not an ad was requested
   *         succcessfully.  Listen for onReceiveAd() and onFailedToReceiveAd()
   *         callbacks to see if an ad was successfully retrieved. 
   */
  private void executeRequestAd(JSONArray inputs) {
    boolean isTesting = false;
    JSONObject inputExtras = null;

    // Get the input data.
    try {
      isTesting = Boolean.parseBoolean(inputs.getString(0));
      inputExtras = new JSONObject(inputs.getString(1));
    } catch (JSONException exception) {
      Log.w(LOGTAG, String.format("Got JSON Exception: %s", exception.getMessage()));
      callback.error(exception.getMessage());
    }

    // Request an ad on the UI thread.
    executeRunnable(new RequestAdRunnable(isTesting, inputExtras));
  }

  /**
   * Executes the runnable on the activity from the plugin's context.  This
   * is a blocking call that waits for a notification from the runnable
   * before it continues.
   *
   * @param runnable The AdMobRunnable representing the command to run.
   * @return A PluginResult representing the result of the command.
   */
  private void executeRunnable(AdMobRunnable runnable) {
    synchronized (runnable) {
      cordova.getActivity().runOnUiThread(runnable);
      try {
        runnable.wait();
      } catch (InterruptedException exception) {
        Log.w(LOGTAG, String.format("Interrupted Exception: %s", exception.getMessage()));
        callback.error(exception.getMessage()+" Interruption occurred when running on UI thread");
      }
    }
    runnable.getPluginResult();
  }

  /**
   * Represents a runnable for the AdMob plugin that will run on the UI thread.
   */
  private abstract class AdMobRunnable implements Runnable {
    protected PluginResult result;

    public PluginResult getPluginResult() {
    	
      return result;
    }
  }

  /** Runnable for the createBannerView action. */
  private class CreateBannerViewRunnable extends AdMobRunnable {
    private String publisherId;
    private AdSize adSize;

    public CreateBannerViewRunnable(String publisherId, AdSize adSize) {
      this.publisherId = publisherId;
      this.adSize = adSize;
      result = new PluginResult(Status.NO_RESULT);
    }

    @Override
    public void run() {
      if (adSize == null) {
        callback.error("AdSize is null. Did you use an AdSize constant?");
      } else {
        adView = new AdView(cordova.getActivity(), adSize, publisherId);
        adView.setAdListener(new BannerListener());
        LinearLayoutSoftKeyboardDetect parentView =
            (LinearLayoutSoftKeyboardDetect) webView.getParent();
        if (positionAtTop) {
          parentView.addView(adView, 0);
        } else {
          parentView.addView(adView);
         
        }
        // Notify the plugin.
        callback.success();
      }
      synchronized (this) {
        this.notify();
      }
    }
  }

  /** Runnable for the requestAd action. */
  private class RequestAdRunnable extends AdMobRunnable {
    private boolean isTesting;
    private JSONObject inputExtras;

    public RequestAdRunnable(boolean isTesting, JSONObject inputExtras) {
      this.isTesting = isTesting;
      this.inputExtras = inputExtras;
      result = new PluginResult(Status.NO_RESULT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      if (adView == null) {
    	  callback.error("AdView is null.  Did you call createBannerView?");
    	  return;
      } else {
        AdRequest request = new AdRequest();
        if (isTesting) {
          // This will request test ads on the emulator only.  You can get your
          // hashed device ID from LogCat when making a live request.  Pass
          // this hashed device ID to addTestDevice request test ads on your
          // device.
          request.addTestDevice(AdRequest.TEST_EMULATOR);
        }
        AdMobAdapterExtras extras = new AdMobAdapterExtras();
        Iterator<String> extrasIterator = inputExtras.keys();
        boolean inputValid = true;
        while (extrasIterator.hasNext()) {
          String key = extrasIterator.next();
          try {
            extras.addExtra(key, inputExtras.get(key));
          } catch (JSONException exception) {
            Log.w(LOGTAG, String.format("Caught JSON Exception: %s", exception.getMessage()));
            callback.error("Error grabbing extras");
            inputValid = false;
          }
        }
        if (inputValid) {
          extras.addExtra("cordova", 1);
          request.setNetworkExtras(extras);
          adView.loadAd(request);
          callback.success();
          }
      }
      synchronized (this) {
        this.notify();
      }
    }
  }

  /**
   * This class implements the AdMob ad listener events.  It forwards the events
   * to the JavaScript layer.  To listen for these events, use:
   *
   * document.addEventListener('onReceiveAd', function());
   * document.addEventListener('onFailedToReceiveAd', function(data));
   * document.addEventListener('onPresentScreen', function());
   * document.addEventListener('onDismissScreen', function());
   * document.addEventListener('onLeaveApplication', function());
   */
  private class BannerListener implements AdListener {
    @Override
    public void onReceiveAd(Ad ad) {
      webView.loadUrl("javascript:cordova.fireDocumentEvent('onReceiveAd');");
    }

    @Override
    public void onFailedToReceiveAd(Ad ad, ErrorCode errorCode) {
      webView.loadUrl(String.format(
          "javascript:cordova.fireDocumentEvent('onFailedToReceiveAd', { 'error': '%s' });",
          errorCode));
    }

    @Override
    public void onPresentScreen(Ad ad) {
      webView.loadUrl("javascript:cordova.fireDocumentEvent('onPresentScreen');");
    }

    @Override
    public void onDismissScreen(Ad ad) {
      webView.loadUrl("javascript:cordova.fireDocumentEvent('onDismissScreen');");
    }

    @Override
    public void onLeaveApplication(Ad ad) {
      webView.loadUrl("javascript:cordova.fireDocumentEvent('onLeaveApplication');");
    }
  }



  /**
   * Gets an AdSize object from the string size passed in from JavaScript.
   * Returns null if an improper string is provided.
   *
   * @param size The string size representing an ad format constant.
   * @return An AdSize object used to create a banner.
   */
  public static AdSize adSizeFromSize(String size) {
    if ("BANNER".equals(size)) {
      return AdSize.BANNER;
    } else if ("IAB_MRECT".equals(size)) {
      return AdSize.IAB_MRECT;
    } else if ("IAB_BANNER".equals(size)) {
      return AdSize.IAB_BANNER;
    } else if ("IAB_LEADERBOARD".equals(size)) {
      return AdSize.IAB_LEADERBOARD;
    } else if ("SMART_BANNER".equals(size)) {
      return AdSize.SMART_BANNER;
    } else {
      return null;
    }
  }
  
  public void showHide(JSONArray inputs){
	  String showorhide = null;
	  try {
		  showorhide = inputs.getString(0);
	} catch (JSONException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	  executeRunnable(new showHideRunnable(showorhide));
  }
  
  
  /** Runnable for the createBannerView action. */
  private class showHideRunnable extends AdMobRunnable {
	private String hideorshow;
    public showHideRunnable(String hideorshow) {
    	this.hideorshow = hideorshow;
        result = new PluginResult(Status.NO_RESULT);
    }

    @Override
    public void run() {
    	Log.d(LOGTAG,"SHOW OR HIDsE = "+hideorshow);
      if(hideorshow == null){
    	  callback.error("hideorshow is null. Did you use an hideorshow constant?");
      }  else if (adView == null) {
    	  callback.error("AdSize is null. Did you use an AdSize constant?");
      } else {
    	  if(hideorshow.equals("hide")){
    		  adView.setVisibility(View.GONE);
    	  }else if(hideorshow.equals("show")){
    		  Log.d(LOGTAG,"Shown");
    		  adView.setVisibility(View.VISIBLE);
    	  }
        callback.success();
      }
      synchronized (this) {
        this.notify();
      }
    }
  }
  
  @Override
  public void onDestroy() {
    if (adView != null) {
      adView.destroy();
    }
    super.onDestroy();
  }
}
