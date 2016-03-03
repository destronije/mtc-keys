package com.mvgv70.mtc_keys;

import java.io.FileInputStream;
import java.util.Properties;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.os.Bundle;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;

public class Music implements IXposedHookLoadPackage 
{
	
  private static BroadcastReceiver musicReceiver;
  private static Activity mtcMusic;
  private static Properties props = null;
  private final static String TAG = "mtc-keys";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // MusicActivity.onCreate(Bindle)
    XC_MethodHook onCreate = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    	Log.d(TAG,"onCreate");
      	mtcMusic = ((Activity)param.thisObject);
      	musicReceiver = (BroadcastReceiver)XposedHelpers.getObjectField(param.thisObject, "mtckeyproc");
      	if (musicReceiver != null)
      	{
      	  // ��������� receiver
      	  mtcMusic.unregisterReceiver(musicReceiver);
      	  // ������� ��� � ������� ������� �������
      	  IntentFilter ni = new IntentFilter();
    	  mtcMusic.registerReceiver(musicReceiver, ni);
      	  // �������� receiver �� ��������� ������
      	  IntentFilter ki = new IntentFilter();
      	  ki.addAction("com.microntek.irkeyUp");
      	  ki.addAction("com.microntek.irkeyDown");
      	  mtcMusic.registerReceiver(keyMusicReceiver, ki);
      	  //
      	  Log.d(TAG,"Radio.Receiver changed");
      	}
      	// ��������� ������
      	try
      	{
      	  String iniFileName = Microntek.getIniFileName();
      	  props = new Properties();
      	  props.load(new FileInputStream(iniFileName));
      	} catch (Exception e) {
		  Log.e(TAG,e.getMessage());
		}
      }
    };
    
    // MusicActivity.onDestroy()
    XC_MethodHook onDestroy = new XC_MethodHook() {
	           
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        // ��������� receiver
    	Log.d(TAG,"onDestroy");
    	mtcMusic.unregisterReceiver(keyMusicReceiver);
      }
    };
    
	// start hooks  
    if (!lpparam.packageName.equals("com.microntek.music")) return;
    Log.d(TAG,"package com.microntek.music");
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreate);
    XposedHelpers.findAndHookMethod("com.microntek.music.MusicActivity", lpparam.classLoader, "onDestroy", onDestroy);
    Log.d(TAG,"com.microntek.music hook OK");
  }
    
  // ����� ���������� �������
  private BroadcastReceiver keyMusicReceiver = new BroadcastReceiver()
  {
	  
    public void onReceive(Context context, Intent intent)
    {
      int keyCode = intent.getIntExtra("keyCode", -1);
      String app = props.getProperty("app_"+keyCode, "").trim();
      String action = props.getProperty("action_"+keyCode, "").trim();
      String activity = props.getProperty("activity_"+keyCode, "").trim();
      String intentName = props.getProperty("intent_"+keyCode, "").trim();
      String event = props.getProperty("event_"+keyCode, "").trim();
      String media = props.getProperty("media_"+keyCode, "").trim();
      String keyevent = props.getProperty("keyevent_"+keyCode, "").trim();
      String command = props.getProperty("command_"+keyCode, "").trim();
      String mcucmd = props.getProperty("mcu_"+keyCode, "").trim();
      String function = props.getProperty("function_"+keyCode, "").trim();
      if (!event.isEmpty())
        // ��������������� ������
        buttonPress(context, intent, event);
      else if (app.isEmpty() && action.isEmpty() && activity.isEmpty() && intentName.isEmpty() && media.isEmpty() && keyevent.isEmpty() && command.isEmpty() && mcucmd.isEmpty() && function.isEmpty())
        // �������� ���������� ��-��������� ���� �� ������ ������� �������� �� �������
        musicReceiver.onReceive(context, intent);
    }
  };
  
  // ��������������� ������
  public void buttonPress(Context context, Intent intent, String event)
  {
    try
    {
      int keyCode = Integer.parseInt(event);
      // ������� �������� ���� ������� � �������
      intent.putExtra("keyCode", keyCode);
      // �������� ���������� ��-���������
      Log.d(TAG,"emulate event "+keyCode);
      musicReceiver.onReceive(context, intent);
    }
    catch (Exception e)
    {
      Log.d(TAG,"invalid event "+event);
    }
  }
  
};
