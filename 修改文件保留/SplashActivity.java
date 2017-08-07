package org.gauto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.gauto.db.DbHelper;
import org.gauto.login.LoginManager;
import org.gauto.login.LoginQrActivity;
import org.gauto.media.MediaController;
import org.gauto.media.TtsResourceLoader;
import org.gauto.subject2.SubjectTwoActivity;
import org.gauto.tests.TestMainActivity;
import org.gauto.update.ObdTool;
import org.gauto.update.ResourceDownloadService;
import org.gauto.update.ResourceUpdateChecker;
import org.gauto.update.SetCarTypeFragment;
import org.gauto.utils.FileUtil;
import org.gauto.utils.MediaUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by bss on 2015/8/2.
 */
public class SplashActivity extends Activity {
    private static final String TAG = "SplashActivity";

    private static final String OBD_CONFIG_NAME = "/gauto/obd.txt";

    private Context context;
    private Handler handler = new Handler();

    private static final long TIME_SHOW_SLOGAN = 1500;
    private static final long TIME_START_ACTIVITY = 700;

    private ImageView imageViewSplashBg;
    private ImageView imageViewLogo;
    private ImageView imageViewSlogan;
    private ImageView imageViewInit;
    private ImageView imageViewUpdateResource;
    private ImageView imageViewConfigObd;
    private TextView textViewRunTests;

    private boolean isVisible = false;

    private int runTestsButtonClickCount = 0;

    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

		Button test_Button;//测试按钮，用于进入后台
        //测试按钮，用于进入后台
        test_Button=(Button)findViewById(R.id.test_buttonView);
        test_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, TestMainActivity.class);
                startActivity(intent);
                finish();
            }
        });


        context = getApplicationContext();
        imageViewSplashBg = (ImageView) findViewById(R.id.imageViewSplashBg);
        imageViewLogo = (ImageView) findViewById(R.id.imageViewLogo);
        imageViewSlogan = (ImageView) findViewById(R.id.imageViewSlogan);
        imageViewInit = (ImageView) findViewById(R.id.imageViewInit);
        imageViewUpdateResource = (ImageView) findViewById(R.id.imageViewUpdateResource);
        imageViewConfigObd = (ImageView) findViewById(R.id.imageViewConfigObd);
        textViewRunTests = (TextView) findViewById(R.id.textViewRunTests);

        imageViewSlogan.setVisibility(View.INVISIBLE);
        imageViewInit.setVisibility(View.INVISIBLE);
        imageViewUpdateResource.setVisibility(View.INVISIBLE);
        imageViewConfigObd.setVisibility(View.INVISIBLE);

//        if (!isDebuggable(context)) {
//            findViewById(R.id.imageViewDebug).setVisibility(View.INVISIBLE);
//        }

        File file = new File(Environment.getExternalStorageDirectory() + "/gauto/splash_background.jpg");
        if (file.exists()) {
            Uri uri = Uri.fromFile(file);
            imageViewSplashBg.setImageURI(uri);
        }

        runTestsButtonClickCount = 0;

        // 屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        isVisible = true;

        FileUtil.broadcastScanDirInSdCard(context, "gauto");

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String nextActivity = sharedPreferences.getString("next_activity", "");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isVisible) {
                    return;
                }

                // 显示口号
                imageViewLogo.setVisibility(View.INVISIBLE);
                imageViewSlogan.setVisibility(View.VISIBLE);
                // 打开下一界面
                startNextActivityDelayed(nextActivity);
            }
        }, TIME_SHOW_SLOGAN);

        // 资源更新完成的广播
        IntentFilter intentFilter = new IntentFilter(MediaUtil.ACTION_FINISH_UPDATE_RESOURCE);
        intentFilter.addAction(MediaUtil.ACTION_FINISH_CONFIG_OBD);
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastListener, intentFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        isVisible = false;

        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastListener);
        super.onPause();
    }

    /**
     * 根据名称启动下一界面
     */
    private void startNextActivityDelayed(final String nextActivity) {
        try {
            // 数据库初始化
            DbHelper.initInstance(getApplicationContext());
            // load car id
            DbHelper.getDbHelper().setCarId(-1L);
        } catch (Exception ex) {
            ex.printStackTrace();
            MediaController.instance().showTip("数据库加载失败");
        }

        // 设置的车型
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final String carType = settings.getString("set_car_type", "NULL");
        final double currentTime = System.currentTimeMillis();
        String sTime = settings.getString("last_timestamp", "0");
        final double inTime = Double.parseDouble(sTime);


        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isVisible) {
                    return;
                }

                // 禁止 runTests
                runTestsButtonClickCount = 100;

                // 是否需要重新初始化
                if (!MainApplication.initialized) {
                    ResourceDownloadService.tipAndRestartDelayed();
                    return;
                }

                if ("NULL".equals(carType)) {
                    // 选择车型
                    getCarTypes();

                    imageViewSlogan.setVisibility(View.INVISIBLE);
                    imageViewInit.setVisibility(View.VISIBLE);

                    return;
                }

                Intent intent;
                // 上一次是否正常退出
                if (Math.abs(currentTime - inTime) <= 300000 && nextActivity.equals("LoginQrActivity")) {
                    intent = new Intent(context, SubjectTwoActivity.class);
                } else {
                    switch (nextActivity) {
                        case "TestMainActivity":
                            intent = new Intent(context, TestMainActivity.class);
                            break;
                        case "SubjectTwoActivity":
                            intent = new Intent(context, SubjectTwoActivity.class);
                            break;
                        default:
                            intent = new Intent(context, LoginQrActivity.class);
                            break;
                    }
                }
                startActivity(intent);
                finish();
            }
        }, TIME_START_ACTIVITY);
    }

    public void onClickRunTests(View view) {    // 在正式版本中不使用
        RelativeLayout.LayoutParams params;
        switch (runTestsButtonClickCount) {
            case 0:
                // 移到左下角
                params = (RelativeLayout.LayoutParams)textViewRunTests.getLayoutParams();
                params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                textViewRunTests.setLayoutParams(params);
                runTestsButtonClickCount++;
                break;
            case 1:
                // 移回右下角
                params = (RelativeLayout.LayoutParams)textViewRunTests.getLayoutParams();
                params.removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                textViewRunTests.setLayoutParams(params);
                runTestsButtonClickCount++;
                break;
            case 2:
                Intent intent = new Intent(context, TestMainActivity.class);
                startActivity(intent);
                finish();
                break;
            default:
                break;
        }
    }

    /**
     * 获取设备登录 url 方法的委托
     */
    private Runnable getCarTypesRunnable = new Runnable() {
        @Override
        public void run() {
            getCarTypes();
        }
    };

    /**
     * 获取该驾校的车型列表
     */
    private void getCarTypes() {
        final RequestQueue volleyQueue = LoginManager.createRequestQueue();
        final String serverUri = context.getResources().getString(R.string.get_car_type_uri);

        HashMap<String, String> mapValue = new HashMap<>();
        SharedPreferences sharedPreferences;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceId = sharedPreferences.getString("set_device_id", "NULL");
        if ("NULL".equals(deviceId)) {
            // 没有设置过 device ID
            handler.post(new Runnable() {
                             @Override
                             public void run() {
                                 showFailedDialog("Device ID 未设置，请联系售后支持。");
                                 imageViewInit.setVisibility(View.INVISIBLE);
                                 imageViewLogo.setVisibility(View.VISIBLE);
                                 return;
                             }
                         });
        }

        mapValue.put("GautoDeviceId", deviceId);  //device随便先写个，因为现在只有海驾的资源
        mapValue.put("GautoResourceType", "init");
        mapValue.put("GautoResourceValue", "");
        JSONObject params = new JSONObject(mapValue);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, serverUri, params, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    System.out.println("onResponse:");
                    System.out.println(response);
                    // 取车型
                    ArrayList<String> carTypes = new ArrayList<>();
                    final HashMap<String, String> carIds = new HashMap<>();
                    final HashMap<String, String> teachIds = new HashMap<>();
                    // 解析
                    JSONArray carAndTeachs = response.getJSONArray("values");
                    for (int i = 0; i < carAndTeachs.length(); i++) {
                        JSONObject carAndTeach = carAndTeachs.getJSONObject(i);
                        String carType = carAndTeach.getString("car-name");
                        String carTypeId = carAndTeach.getString("car-id");
                        String teachId = carAndTeach.getString("teach-id");

                        carTypes.add(carType);
                        carIds.put(carType, carTypeId);
                        teachIds.put(carType, teachId);
                    }
                    // 选择车型
                    gotCarTypes(carTypes, carIds, teachIds);
                    volleyQueue.stop();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                Log.e(TAG, "error: " + volleyError.getMessage());

                // 若失败，重试
                long delayMillis = 5000;
                handler.postDelayed(getCarTypesRunnable, delayMillis);
            }
        });

        volleyQueue.add(jsonObjectRequest);
    }

    /**
     * 选择车型
     */
    public void gotCarTypes(final ArrayList<String> carTypes,
                            final HashMap<String, String> carIds,
                            final HashMap<String, String> teachIds) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                imageViewInit.setVisibility(View.INVISIBLE);

                // 进入选择车型界面
                getFragmentManager().beginTransaction()
                        .add(android.R.id.content, SetCarTypeFragment.createCarTypeFragment(carTypes,
                                new SetCarTypeFragment.SetCarTypeListener() {
                                    @Override
                                    public void setCarType(String carType) {
                                        // 实际的设置车型代码等
                                        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
                                        editor.putString("set_car_type", String.format("%s %s", carIds.get(carType), carType));
                                        editor.putString("car_id", carIds.get(carType));
                                        editor.putString("teach_id", teachIds.get(carType));
                                        editor.apply();

                                        imageViewUpdateResource.setVisibility(View.VISIBLE);

                                        // 检查资源
                                        ResourceUpdateChecker.checkNormalUpdate(context, new ResourceUpdateChecker.OnHasNewResourceListener() {
                                            @Override
                                            public void onHasNewResource(boolean hasNew, final String resUrl, final ArrayList<String> files) {
                                                if (!hasNew) {
                                                    // 没有新资源，看是否要配置 OBD
                                                    handler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            tryConfigObdAndRestart();
                                                        }
                                                    });
                                                    return;
                                                }
                                                // 有新版本
                                                handler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Intent intent = new Intent(context, ResourceDownloadService.class);
                                                        intent.putExtra("ResourceUrl", resUrl);
                                                        intent.putStringArrayListExtra("Files", files);
                                                        context.startService(intent);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }))
                        .commit();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        imageViewSplashBg.setImageDrawable(null);
    }

    /**
     * Debug 模式
     */
    public static boolean isDebuggable(Context context) {
        boolean debuggable = false;

        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo appinfo = pm.getApplicationInfo(context.getPackageName(), 0);
            debuggable = (0 != (appinfo.flags & ApplicationInfo.FLAG_DEBUGGABLE));
        } catch (PackageManager.NameNotFoundException e) {
        }

        return debuggable;
    }

    /**
     * 显示报错对话框，确定退出
     */
    private void showFailedDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this);
        builder.setMessage(message);
        builder.setTitle("警告");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 退出
                System.exit(1);
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    /**
     * 试图配置 OBD
     */
    private void tryConfigObdAndRestart() {
        // 检查 SD 卡中是否有配置文件，有的话让用户决定要不要更新
        final String obdConfig = TtsResourceLoader.loadStringFromFile(
                Environment.getExternalStorageDirectory() + OBD_CONFIG_NAME);
        if (obdConfig != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this);
            builder.setMessage("是否进行 OBD 配置？");
            builder.setTitle("OBD 配置");
            builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            imageViewUpdateResource.setVisibility(View.INVISIBLE);
                            imageViewConfigObd.setVisibility(View.VISIBLE);
                            // 配置 OBD
                            new ObdTool().sendObdConfig(context, obdConfig);
                        }
                    });
                }
            });
            builder.setNegativeButton("否", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 退出
                    MediaController.instance().showTip("设置完成，请重启 APP");
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 2000);
                }
            });
            builder.setCancelable(false);
            builder.create().show();
        } else {
            MediaController.instance().showTip("设置完成，请重启 APP");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, 2000);
        }
    }

    /**
     * 广播服务
     */
    private BroadcastReceiver broadcastListener = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {
            // 资源更新完成，看情况决定是否配置 OBD
            if (intent.getAction().equals(MediaUtil.ACTION_FINISH_UPDATE_RESOURCE)) {
                tryConfigObdAndRestart();
            }
            // OBD 配置完成，重启
            if (intent.getAction().equals(MediaUtil.ACTION_FINISH_CONFIG_OBD)) {
                boolean succeed = intent.getBooleanExtra("SUCCEED", false);
                if (succeed) {
                    MediaController.instance().showTip("设置完成，请重启 APP");
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 2000);
                } else {
                    showFailedDialog("车型 OBD 配置失败");
                }
            }
        }
    };
}
