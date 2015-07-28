package com.mvgv70.mtc_keys;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.app.Service;
import android.app.ActivityManager;

public class Microntek implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver mtcReceiver;
  private static Context mContext;
  private static Service mtcService;
  private static Properties props = null;
  private static ActivityManager am;
  private static String topActivity;
  private static String nextActivity;
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-keys/mtc-keys.ini"; 
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCManager.onCreate()
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreate");
      	mContext = (Context)XposedHelpers.getObjectField(param.thisObject, "mContext");
      	am = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
      	mtcService = ((Service)param.thisObject);
      	mtcReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "CarkeyProc");
      	if (mtcReceiver != null)
      	{
      	  // ��������� receiver
       	  mtcService.unregisterReceiver(mtcReceiver);
          // ������� ��� � ������� ������� �������
      	  IntentFilter ni = new IntentFilter();
      	  mtcService.registerReceiver(mtcReceiver, ni);  
          // �������� ������ ��������, ����� �������� ������
    	  IntentFilter mi = new IntentFilter();
          mi.addAction("com.microntek.dvdClosed");
          mi.addAction("com.microntek.startApp");
          mi.addAction("com.microntek.light");
          mtcService.registerReceiver(otherReceiver, mi);
      	  // �������� receiver �� ��������� ������
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcService.registerReceiver(keyServiceReceiver, ki);
      	  // ������� ��������� ��������� ��������� ����� �� ��������
      	  Log.d(TAG,"Build.VERSION.SDK_INT="+Build.VERSION.SDK_INT);
      	  if (Build.VERSION.SDK_INT > 17)
      	  {
      		// ��� ������ 4.4
      		IntentFilter vi = new IntentFilter();
        	vi.addAction("com.microntek.VOLUME_CHANGED");
        	mtcService.registerReceiver(volumeReceiver, vi);
        	Log.d(TAG,"Manager. volume receiver created");
      	  }
      	  //
      	  Log.d(TAG,"Manager.Receivers changed");
      	}
      	File f = new File(INI_FILE_NAME);
      	Log.d(TAG,"file exist "+f.exists());
      	// ������ ������������ �����
      	try
      	{
      	  Log.d(TAG,"inifile load from "+INI_FILE_NAME);
      	  props = new Properties();
      	  props.load(new FileInputStream(INI_FILE_NAME));
      	  Log.d(TAG,"ini file loaded, line count="+props.size());
      	} catch (Exception e) {
          Log.e(TAG,e.getMessage());
        }
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals("android.microntek.service")) return;
    Log.d(TAG,"package android.microntek.service");
    XposedHelpers.findAndHookMethod("android.microntek.service.MicrontekServer", lpparam.classLoader, "onCreate", onCreate);
    Log.d(TAG,"com.microntek.service hook OK");
  }
  
  // ����� ���������� ��������� �������: dvdClosed, startApp, light
  private BroadcastReceiver otherReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      // �������� ���������� microntek
      mtcReceiver.onReceive(context, intent);
    }
  };
    
  // ����� ���������� ������� � ��������������� ������
  private BroadcastReceiver keyServiceReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      Log.d(TAG,"keyCode="+keyCode);
      // app = ����������, action = {back,home,tasks,apps,menu}, activity, intent
      String app = props.getProperty("app_"+keyCode, "").trim();
      String action = props.getProperty("action_"+keyCode, "").trim();
      String activity = props.getProperty("activity_"+keyCode, "").trim();
      String intentName = props.getProperty("intent_"+keyCode, "").trim();
      if (!app.isEmpty())
    	// ������ ���������� 
      	runApp(context, app);
      else if (!activity.isEmpty())
      	// ������ activity
        runActivity(context, activity);
      else if (!action.isEmpty())
        // ��������
    	runAction(context, action);
      else if (!intentName.isEmpty())
        // ������
      	sendIntent(context, intentName);
      else
        // �������� ���������� ��-���������, ���� �� ������� ������ �� ���������
        mtcReceiver.onReceive(context, intent);
    }
    
    // ������ ����������
    private void runApp(Context context, String appName)
    {
      String runApp = appName;
      getActivityList();
      if (appName.equals(topActivity))
        // ��������� ���������� ���������� � ������
        runApp = nextActivity;
      Log.d(TAG,"run app="+runApp);
      Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(runApp);
      if (appIntent != null)
      {
    	appIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(appIntent);
      }
      else
      {
        Log.w(TAG,"no activity found for "+runApp);
        goLauncher(context);
      }
    }
    
    // ������ activity
    private void runActivity(Context context, String activity)
    {
      int i = activity.indexOf("/");
      if (i > 0)
      {
        String packageName = activity.substring(0,i);
        String className = activity.substring(i+1);
        Log.d(TAG,"start activity "+packageName+"/"+className);
        // ��-������� �������� � ������������
        Intent appIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (appIntent != null)
        {
          ComponentName cn = new ComponentName(packageName, className);
          try 
          {
            context.getPackageManager().getActivityInfo(cn, PackageManager.GET_META_DATA);
            appIntent.setComponent(cn);
            appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            Log.d(TAG,appIntent.toString());
            context.startActivity(appIntent);
          } 
          catch (NameNotFoundException e) 
          {
            Log.w(TAG,"activity "+className+" not found");
            goLauncher(context);
          }
        }
        else
        {
          Log.w(TAG,"no activity found for package "+packageName);
          goLauncher(context);
        }
      }
      else
        Log.w(TAG,"wrong format for activity: "+activity);
    }
    
    // send intent
    private void sendIntent(Context context, String intentName)
    {
      Log.d(TAG,"intent "+intentName);
      context.sendBroadcast(new Intent(intentName));
    }
    
    // ��������� action: back, home, menu, apps
    private void runAction(Context context, String action)
    {
      Intent intent = new Intent(SystemUI.KEYS_ACTION);
      intent.putExtra("action", action);
      context.sendBroadcast(intent);
    }
    
    private void getActivityList()
    {
      try 
      {
    	List<ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(2);
    	topActivity = taskList.get(0).topActivity.getPackageName();
    	if (taskList.size() > 1)
    	  nextActivity = taskList.get(1).baseActivity.getPackageName();
    	else
    	  nextActivity = "";
      } catch (Exception e) {}
    }
    
    private void goLauncher(Context context)
    {
      XposedHelpers.callMethod(mtcService, "startHome", new Object[] {});
    }
        
  };
  
  // ��������� ���������� ���������� � ��������� ��������� �� ������� com.microntek.VOLUME_CHANGED
  private BroadcastReceiver volumeReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      int mCurVolume = XposedHelpers.getIntField(mtcService,"mCurVolume");
      int mNewVolume = intent.getIntExtra("volume", mCurVolume);
      if (mNewVolume > 0) 
        XposedHelpers.setIntField(mtcService,"mCurVolume",mNewVolume);
    }
  };
  
};

