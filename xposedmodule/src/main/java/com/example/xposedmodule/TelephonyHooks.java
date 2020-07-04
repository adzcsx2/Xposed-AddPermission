package com.example.xposedmodule;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.content.Context.MODE_PRIVATE;
import static com.example.xposedmodule.MainActivity.TAG;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by hoyn on 2020/7/1.
 */

public class TelephonyHooks implements IXposedHookLoadPackage {
    Activity activity = null;
    View v;
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.contains("com.example.xposedmodule")) {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Context context = (Context) param.args[0];
                    String sdcard_path = Environment.getExternalStorageDirectory().getAbsolutePath();
                    writeFileData(sdcard_path + File.separator+"x86"+ File.separator+"as.txt","asdasd");
                    XposedBridge.log("writeFileData");
                }
            });

        }
        if (lpparam.packageName.contains("com.example.hoyn.example")) {
            final Context[] context = new Context[1];
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    context[0] = (Context) param.args[0];
                }
            });
            //Android N以下获取imei的方法
            findAndHookMethod("android.telephony.TelephonyManager", lpparam.classLoader, "getDeviceId", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    param.setResult("123133212121");
                }
            });
            //Android N以上获取imei的方法
            findAndHookMethod("android.telephony.TelephonyManager", lpparam.classLoader, "getImei", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log("hoyn已经hook了");
                    return "12345678";
                }
            });


            findAndHookMethod("com.example.hoyn.example.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    activity = (Activity) param.thisObject;
                    XposedBridge.log(activity.getPackageName());
                }
            });
            //由于OnClickListener是内部类，在hook的时候应是$+数字，具体是数字几可以在反编译后的smali文件里面看到，我这个项目就一个内部类所以是$1
            findAndHookMethod("com.example.hoyn.example.MainActivity$1", lpparam.classLoader, "onClick", View.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    XposedBridge.log("点击了之前");
                    //获取按钮对象
                    v = (View) param.args[0];
                    //获取权限
                    requestPermission(activity);
                }
            });

            findAndHookMethod("com.example.hoyn.example.MainActivity", lpparam.classLoader, "onRequestPermissionsResult", int.class,String[].class,int[].class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    onRequestPermissionsResult(param);
                }
            });

            //hook自定义的getText方法
            findAndHookMethod("com.example.hoyn.example.MainActivity", lpparam.classLoader, "getText", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    String data ;
                    MultiprocessSharedPreferences.setAuthority("com.example.xposedmodule.provider");
                    SharedPreferences test = MultiprocessSharedPreferences.getSharedPreferences(context[0], "test", MODE_PRIVATE);
                     data = test.getString("hello", "");
                    XposedBridge.log("获取到了"+data);
                    if(TextUtils.isEmpty(data)){
                        data = "ccccc";
                    }
                    param.setResult(data);
                    XposedBridge.log(param.getResult().toString());
                }
            });
        }
    }




    public void writeFileData(String filename, String content){
        try {
            File file = new File(filename);
            FileOutputStream fos = new FileOutputStream(file);
            //将要写入的字符串转换为byte数组
            byte[]  bytes = content.getBytes();
            fos.write(bytes);//将byte数组写入文件
            fos.close();//关闭文件输出流

        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
            e.printStackTrace();
        }
    }
    //请求权限
    private void requestPermission(Activity activity) {
        //需要的权限
        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        //检测是否没有该权限
        if (ContextCompat.checkSelfPermission(activity,permission)!= PackageManager.PERMISSION_GRANTED) {
//            没有该权限则请求权限，请求码 100
            ActivityCompat.requestPermissions(activity,new String[]{permission},100);
        }
    }
    //权限请求回调
    private void onRequestPermissionsResult(XC_MethodHook.MethodHookParam param) {
        int requestCode = (int) param.args[0];
        XposedBridge.log(requestCode+"");//100
        //如果不加判断，Xpose会Hook所有onRequestPermissionsResult，造成不好的影响
        if(requestCode ==100){
            String[] permissions = (String[]) param.args[1];
            for (String permission:permissions) {
                XposedBridge.log(permission); //android.permission.WRITE_EXTERNAL_STORAGE
            }
            int[] grantResults = (int[]) param.args[2];
            for (int grantResult:grantResults) {
                //拒绝-1， 允许0
                XposedBridge.log(grantResult+"");
                if(grantResult==-1){
                    //如果不同意请求退出页面
                    activity.finish();
                    Toast.makeText(activity,"没有权限",Toast.LENGTH_SHORT).show();
                    XposedBridge.log("退出了页面");
                }else{
                    //由于是异步的，所以之前的点击事件没有起作用，要同意之后才能起作用，所以在这里再重新调用它的点击事件
                    //因为R.id.xx是xpose模块的ID，不是原HOOK的app的id，所以无法用activity.findViewById去找到按钮。
                    //由于在之前hook了它的click方法，而click方法第一个参数就是view，所以能直接拿到
                    v.callOnClick();
                }
            }
        }
    }
}
