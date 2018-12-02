package com.mvgv70.mtc_keys;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import com.mvgv70.utils.IniFile;
import com.mvgv70.utils.Utils;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.app.Service;
import android.app.ActivityManager;

public class Microntek implements IXposedHookLoadPackage {

  private static Context mContext;
  private static Service mtcService;
  private static IniFile props = new IniFile();
  private static ActivityManager am;
  private static AudioManager mcu;
  private static Object im = null;
  private static String topActivity;
  private static String nextActivity;
  private static int clickDelay = 600;
  private static Handler handler = null;
  private static int lastClickCode = 0;
  private static long lastClickTime = 0;
  private static int clickCount = 1;
  // constants
  private static String EXTERNAL_SD = "/mnt/sdcard/";
  private static String MTC_KEYS_INI = EXTERNAL_SD + "mtc-keys/mtc-keys.ini";
  private final static String SETTINGS_SECTION = "settings";
  private final static String CLICK_SECTION = "click.";
  private final static String INTENT_MTC_KEYS_EVENT = "com.mvgv70.mtc-keys.event";
  private final static String TAG = "mtc-keys";

  @Override
  public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
    // MTCManager.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG, "onCreate");
        XposedBridge.log("com.ts.MainUI.MainService - onCreate - afterHookedMethod");
        // mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
        mContext = (Context) param.thisObject;
        am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mcu = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));
        mtcService = ((Service) param.thisObject);
        try {
          Context context = mtcService.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG, "version=" + version);
          Log.d(TAG, "android " + Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {
        }
        EXTERNAL_SD = Utils.getModuleSdCard();
        MTC_KEYS_INI = EXTERNAL_SD + "mtc-keys/mtc-keys.ini";
        //
        XposedBridge.log("EXTERNAL_SD - " + EXTERNAL_SD);
        XposedBridge.log(EXTERNAL_SD + " " + Environment.getStorageState(new File(EXTERNAL_SD)));
        Log.d(TAG, EXTERNAL_SD + " " + Environment.getStorageState(new File(EXTERNAL_SD)));
        if (Environment.getStorageState(new File(EXTERNAL_SD)).equals(Environment.MEDIA_MOUNTED))
          readSettings();
        else
          createMediaReceiver();
        createReceivers();
      }
    };

    //
    Log.d(TAG, "Loading package - " + lpparam.packageName);
    XposedBridge.log("Loaded app: " + lpparam.packageName);

    if (!lpparam.packageName.equals("com.ts.MainUI")) return;
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Log.d(TAG, "package com.ts.MainUI");

    XposedHelpers.findAndHookMethod("com.ts.MainUI.MainService", lpparam.classLoader, "onCreate", onCreate);

    XposedHelpers.findAndHookMethod("com.yyw.ts70xhw.Mcu", lpparam.classLoader, "GetPkey", new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Object clickedKeyCodeObject = param.getResult();
        int keyCode = Integer.parseInt(clickedKeyCodeObject.toString());

        if (keyCode > 0) {
          XposedBridge.log("Key!");
          XposedBridge.log(Integer.toString(keyCode));

          getActivityList();

          int decodeKey = keyNeedDecode(topActivity, clickCount, keyCode);
          if (decodeKey > 0) {
            Object result = replaceHookedMethod(param, decodeKey);
            param.setResult(result);
          } else {
            int keyHandle = keyNeedHandle(topActivity, clickCount, keyCode);
            if (keyHandle > 0) {
              Object result = replaceHookedMethod(param, 0);
              param.setResult(result);

              Intent keyIntent = new Intent(INTENT_MTC_KEYS_EVENT);
              keyIntent.putExtra("keyCode", keyCode);
              keyIntent.putExtra("section", keyHandle);
              keyIntent.putExtra("click", clickCount);
              keyIntent.putExtra("topActivity", topActivity);
              mContext.sendBroadcast(keyIntent);
            }
          }

          XposedBridge.log("event_" + keyCode + "=" + decodeKey);
        }
      }

      // method to replace Mcu GetPKey method with specified keyCode
      protected Object replaceHookedMethod(MethodHookParam param, int keyCode) throws Throwable {
        return keyCode;
      }
    });

//        XposedHelpers.findAndHookMethod("com.yyw.ts70xhw.Mcu", lpparam.classLoader, "GetPkey", new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                Object asd = param.getResult();
//
//                if (Integer.parseInt(asd.toString()) != 0) {
//                    XposedBridge.log("Mcu.GetPkey() ----------------------------");
//                    XposedBridge.log(asd.toString());
//                }
//            }
//        });

    try {
      //
      Class<?> clazz = XposedHelpers.findClass("android.hardware.input.InputManager", lpparam.classLoader);
      im = XposedHelpers.callStaticMethod(clazz, "getInstance");
    } catch (Error e) {
      XposedBridge.log("Error!");
      Log.e(TAG, e.getMessage());
    }
    XposedBridge.log("com.microntek.service hook OK");
    Log.d(TAG, "com.microntek.service hook OK");
  }

  //
  private static void readSettings() {
    props.clear();
    //
    try {
      Log.d(TAG, "inifile load from " + MTC_KEYS_INI);
      //
      props.loadFromFile(MTC_KEYS_INI);
      Log.d(TAG, "ini file loaded");
      //
      clickDelay = props.getIntValue(SETTINGS_SECTION, "doubleclick.time", 400);
      Log.d(TAG, "doubleclick.time=" + clickDelay);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
  }

  //
  private static int keyNeedHandle(String packageName, int click, int keyCode) {
    if (findAnyKeySection(packageName + "." + click, keyCode))
      //
      return 2;
    if (findAnyKeySection(CLICK_SECTION + click, keyCode))
      //
      return 1;
    //
    return 0;
  }

  //
  private static int keyNeedDecode(String packageName, int click, int keyCode) {
    int result = findEventKeySection(packageName + "." + click, keyCode);
    if (result > 0)
      //
      return result;
    result = findEventKeySection(CLICK_SECTION + click, keyCode);
    if (result > 0)
      //
      return result;
    //
    return 0;
  }

  //
  private static boolean findAnyKeySection(String section, int keyCode) {
    String key;
    boolean result = false;
    Iterator<String> iterator = props.enumKeys(section);
    while (iterator.hasNext()) {
      key = (String) iterator.next();
      if (key.endsWith("_" + keyCode))
        if (!key.equals("event_" + keyCode)) {
          result = true;
          break;
        }
    }
    return result;
  }

  //
  private static int findEventKeySection(String section, int keyCode) {
    String key;
    int result = 0;
    Iterator<String> iterator = props.enumKeys(section);
    if (iterator == null) return 0;
    while (iterator.hasNext()) {
      key = (String) iterator.next();
      if (key.equals("event_" + keyCode)) {
        String value = props.getValue(section, "event_" + keyCode, "0");
        result = Integer.parseInt(value);
        break;
      }
    }
    return result;
  }

  //
  private static class KeyHandler extends Handler {
    public void handleMessage(Message msg) {
      int keyCode = msg.what;
      int clickNo = msg.arg1;
      String topActivity = (String) msg.obj;
      XposedBridge.log("KeyHandler");
      XposedBridge.log("handle " + keyCode + " (" + clickNo + ") " + msg.arg2 + " " + topActivity);
      Log.d(TAG, "handle " + keyCode + " (" + clickNo + ") " + msg.arg2 + " " + topActivity);
      if (msg.arg2 == 1)
        //
        buttonPressSection(keyCode, mtcService, CLICK_SECTION + clickNo);
      else
        //
        buttonPressSection(keyCode, mtcService, topActivity + "." + clickNo);
    }
  }

  ;

  //
  private static boolean buttonPressSection(int keyCode, Context context, String section) {
    boolean result = true;
    //
    String app = props.getValue(section, "app_" + keyCode);
    String action = props.getValue(section, "action_" + keyCode).trim();
    String activity = props.getValue(section, "activity_" + keyCode);
    String intentName = props.getValue(section, "intent_" + keyCode);
    String media = props.getValue(section, "media_" + keyCode).trim();
    int keyevent = props.getIntValue(section, "keyevent_" + keyCode, 0);
    String command = props.getValue(section, "command_" + keyCode);
    String mcucmd = props.getValue(section, "mcu_" + keyCode);
    String function = props.getValue(section, "function_" + keyCode).trim();
    int injectkey = props.getIntValue(section, "inject_" + keyCode, 0);
    if (!app.isEmpty()) {
      //
      if (!app.equals("null"))
        runApp(context, app);
    } else if (!activity.isEmpty())
      //
      runActivity(context, activity);
    else if (!action.isEmpty())
      //
      runAction(context, action);
    else if (!intentName.isEmpty())
      //
      sendIntent(context, intentName);
    else if (!media.isEmpty())
      //
      mediaPress(context, media);
    else if (keyevent > 0)
      //
      keyPress(context, keyevent);
    else if (!command.isEmpty())
      //
      executeCmd(command);
    else if (!mcucmd.isEmpty())
      //
      sendMcuCommand(mcucmd);
    else if (!function.isEmpty())
      //
      callFunction(function);
    else if (injectkey > 0)
      //
      injectKey(injectkey);
    else {
      Log.w(TAG, "can not handle " + keyCode + " in [" + section + "]");
      result = false;
    }
    return result;
  }

  //
  private static void runApp(Context context, String appName) {
    String runApp = appName;
    if (appName.equals(topActivity) || appName.isEmpty())
      //
      runApp = nextActivity;
    Log.d(TAG, "run app=" + runApp);
    Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(runApp);
    if (appIntent != null) {
      appIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      context.startActivity(appIntent);
    } else {
      Log.w(TAG, "no activity found for " + runApp);
      goLauncher(context);
    }
  }

  //
  private static void runActivity(Context context, String activity) {
    int i = activity.indexOf("/");
    if (i > 0) {
      String packageName = activity.substring(0, i);
      String className = activity.substring(i + 1);
      Log.d(TAG, "start activity " + packageName + "/" + className);
      //
      Intent appIntent = new Intent();
      ComponentName cn = new ComponentName(packageName, className);
      try {
        context.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
        appIntent.setComponent(cn);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(appIntent);
      } catch (NameNotFoundException e) {
        Log.w(TAG, "activity " + className + " not found");
        goLauncher(context);
      }
    } else
      Log.w(TAG, "wrong format for activity: " + activity);
  }

  //
  private static void sendIntent(Context context, String intentName) {
    Log.d(TAG, "intent " + intentName);
    context.sendBroadcast(new Intent(intentName));
  }

  //
  private static void runAction(Context context, String action) {
    Log.d(TAG, "action=" + action);
    if (action.equals("back"))
      injectKey(KeyEvent.KEYCODE_BACK);
    else if (action.equals("home"))
      injectKey(KeyEvent.KEYCODE_HOME);
    else if (action.equals("home"))
      injectKey(KeyEvent.KEYCODE_MENU);
    else if (action.equals("apps"))
      injectKey(KeyEvent.KEYCODE_APP_SWITCH);
    else if (action.equals("menu"))
      injectKey(KeyEvent.KEYCODE_MENU);
    else if (action.equals("screenshot"))
      callFunction("startScreenShot");
    else if (action.equals("sleep"))
      sendMcuCommand("ctl_key->power");
    else if (action.equals("screenoff"))
      sendMcuCommand("ctl_key->screenbrightness");
    else if (action.equals("settings"))
      readSettings();
    else if (action.equals("switch"))
      runApp(context, "");
    else if (action.equals("null")) {
      //
    } else
      Log.w(TAG, "invalid action: " + action);
  }

  //
  private static void getActivityList() {
    try {
      List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(2);
      topActivity = taskList.get(0).topActivity.getPackageName();
      XposedBridge.log("Top activity - " + topActivity);
      if (taskList.size() > 1)
        nextActivity = taskList.get(1).baseActivity.getPackageName();
      else
        nextActivity = "";
    } catch (Exception e) {
    }
  }

  //
  private static void goLauncher(Context context) {
    try {
      Intent intent = new Intent("android.intent.action.MAIN");
      intent.addCategory("android.intent.category.HOME");
      intent.addFlags(270532608);
      context.startActivity(intent);
    } catch (Exception e) {
    }
  }

  //
  public static void mediaPress(Context context, String command) {
    if (command.equalsIgnoreCase("play"))
      injectKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    else if (command.equalsIgnoreCase("next"))
      injectKey(KeyEvent.KEYCODE_MEDIA_NEXT);
    else if (command.equalsIgnoreCase("prev"))
      injectKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    else
      Log.e(TAG, "invalid media key code " + command);
  }

  //
  public static void keyPress(Context context, int keyCode) {
    sendMediaKey(context, keyCode);
  }

  //
  public static void sendMediaKey(Context context, int keyCode) {
    Log.d(TAG, "send media key " + keyCode);
    long eventTime = SystemClock.uptimeMillis();
    //
    Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    KeyEvent downEvent = new KeyEvent(eventTime - 20, eventTime - 20, KeyEvent.ACTION_DOWN, keyCode, 0);
    downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
    context.sendOrderedBroadcast(downIntent, null);
    //
    Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
    KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
    upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
    context.sendOrderedBroadcast(upIntent, null);
  }

  //
  private static void injectKey(int keyCode) {
    if (im == null) return;
    try {
      Log.d(TAG, "inject key " + keyCode);
      long eventTime = SystemClock.uptimeMillis();
      //
      KeyEvent downEvent = new KeyEvent(eventTime - 20, eventTime - 20, KeyEvent.ACTION_DOWN, keyCode, 0);
      XposedHelpers.callMethod(im, "injectInputEvent", downEvent, 0);
      //
      KeyEvent upEvent = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0);
      XposedHelpers.callMethod(im, "injectInputEvent", upEvent, 0);
    } catch (Exception e) {
      Log.e(TAG, "injectKey: " + e.getMessage());
    }
  }

  //
  private static void executeCmd(String command) {
    Log.d(TAG, "> " + command);
    //
    Process process = null;
    DataOutputStream os = null;
    InputStream err = null;
    try {
      process = Runtime.getRuntime().exec("su");
      os = new DataOutputStream(process.getOutputStream());
      err = process.getErrorStream();
      os.writeBytes(command + " \n");
      os.writeBytes("exit \n");
      os.flush();
      os.close();
      process.waitFor();
      //
      byte[] buffer = new byte[1024];
      int len = err.read(buffer);
      if (len > 0) {
        String errmsg = new String(buffer, 0, len);
        Log.e(TAG, errmsg);
      }
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
  }

  //
  private static void sendMcuCommand(String command) {
    command = command.replaceAll("->", "=");
    Log.d(TAG, "am.setParameters(" + command + ")");
    mcu.setParameters(command);
  }

  private static void callFunction(String function) {
    Log.d(TAG, function + "();");
    try {
      XposedHelpers.callMethod(mtcService, function);
    } catch (Error e) {
      Log.d(TAG, e.getMessage());
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
  }

  //
  private void createReceivers() {
    //
    IntentFilter ki = new IntentFilter();
    ki.addAction(INTENT_MTC_KEYS_EVENT);
    mtcService.registerReceiver(keysReceiver, ki);
    XposedBridge.log("key receiver created");
    Log.d(TAG, "key receiver created");
    //
    handler = new KeyHandler();
    XposedBridge.log("KeyHandler created");
    Log.d(TAG, "KeyHandler created");
  }

  //
  private void createMediaReceiver() {
    IntentFilter ui = new IntentFilter();
    ui.addAction(Intent.ACTION_MEDIA_MOUNTED);
    ui.addDataScheme("file");
    mtcService.registerReceiver(mediaReceiver, ui);
    Log.d(TAG, "media mount receiver created");
  }

  //
  private BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      String drivePath = intent.getData().getPath();
      Log.d(TAG, "media mounted:" + drivePath + " " + action);
      if (action.equals(Intent.ACTION_MEDIA_MOUNTED))
        //
        if (MTC_KEYS_INI.startsWith(drivePath)) {
          //
          readSettings();
          //
          mtcService.unregisterReceiver(this);
        }
    }
  };

  //
  private BroadcastReceiver keysReceiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      int keyCode = intent.getIntExtra("keyCode", 0);
      int clickNo = intent.getIntExtra("click", 1);
      int section = intent.getIntExtra("section", 1);
      String topActivity = intent.getStringExtra("topActivity");
      //
      handler.removeMessages(keyCode);
      Message msg = Message.obtain(handler, keyCode, clickNo, section, topActivity);
      //
      handler.sendMessage(msg);
    }
  };

};

