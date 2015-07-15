package com.mvgv70.mtc_keys;

import java.io.FileInputStream;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Environment;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;

public class Radio implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver radioReceiver;
  private static Activity mtcRadio;
  private static Properties props = null;
  private final static String INI_FILE_NAME = Environment.getExternalStorageDirectory().getPath()+"/mtc-keys/mtc-keys.ini"; 
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MTCRadio.onCreate(Bindle)
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreete");
      	mtcRadio = ((Activity)param.thisObject);
      	radioReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "mtckeyproc");
      	if (radioReceiver != null)
      	{
      	  // ��������� receiver
      	  mtcRadio.unregisterReceiver(radioReceiver);
      	  // ������� ��� � ������� ������� �������
      	  IntentFilter ni = new IntentFilter();
    	  mtcRadio.registerReceiver(radioReceiver, ni);
      	  // �������� receiver �� ��������� ������
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyUp");
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcRadio.registerReceiver(keyRadioReceiver, ki);
      	  //
      	  Log.d(TAG,"Radio.Receiver changed");
      	}
      	try
      	{
      	  props = new Properties();
      	  props.load(new FileInputStream(INI_FILE_NAME));
      	} catch (Exception e) {
			Log.e(TAG,e.getMessage());
		}
      }
    };
    // MTCRadio.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // ��������� receiver
    	Log.d(TAG,"onDestroy");
    	mtcRadio.unregisterReceiver(keyRadioReceiver);
      }
    };
    
	// start hooks  
    if (!lpparam.packageName.equals("com.microntek.radio")) return;
    Log.d(TAG,"package com.microntek.radio");
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onCreate", "android.os.Bundle", onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.radio.RadioActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.microntek.radio hook OK");
  }
    
  // ����� ���������� �������
  private BroadcastReceiver keyRadioReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      String mapApp = props.getProperty("app_"+keyCode, "").trim();
      if (mapApp.isEmpty())
        // �������� ���������� ��-��������� ���� �� ������ �������� �� �������
        radioReceiver.onReceive(context, intent);
    }
  };
  
};
