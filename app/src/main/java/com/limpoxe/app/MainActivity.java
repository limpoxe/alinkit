package com.limpoxe.app;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.limpoxe.alinkit.DeviceManager;
import com.limpoxe.alinkit.GateWay;
import com.limpoxe.alinkit.LinkitInfo;
import com.limpoxe.alinkit.LogUtil;
import com.limpoxe.alinkit.ThingModel;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private boolean added = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            new AsyncTask() {
                @Override
                protected Object doInBackground(Object[] objects) {
                    DeviceManager.getInstance().beat();
                    return null;
                }
            }.execute();
            check();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //打开日志
        GateWay.debugOn();
        LogUtil.setLogger(new LogUtil.Logger() {
            @Override
            public void log(String msg) {
                System.out.println(msg);
            }
        });

        DeviceManager.getInstance().init(getApplicationContext());

        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //aliyun物联网三元组
                LinkitInfo linkitInfo = new LinkitInfo(
                        "grvltir7CLT",
                        "4dcf5f02c80e38d3756967b2977a5d98",
                        "ce4e4c0e4e5b40aa8888b67a13f5ff02",
                        LinkitInfo.DEV_TYPE_GW,
                        LinkitInfo.PRODUCT_TYPE_GW);
                //aliyun物联网三元组
                LinkitInfo subLinkitInfo = new LinkitInfo(
                        "grvltir7CLT",
                        "4dcf5f02c80e38d3756967b2977a5_01",
                        "aa856073f3b95704b71581c8114a9a68",
                        LinkitInfo.DEV_TYPE_SUB,
                        LinkitInfo.PRODUCT_TYPE_GW);

                new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {
                        DeviceManager.getInstance().addDev(linkitInfo);
                        DeviceManager.getInstance().addDev(subLinkitInfo);
                        added = true;
                        return null;
                    }
                }.execute();
            }
        });

        findViewById(R.id.btn_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!added) {
                    return;
                }
                ThingModel.addServiceCallback(
                        DeviceManager.getInstance().getGwDev().getLinkitInfo().deviceName,
                        "NoticeCancel",
                        new ThingModel.ServiceCallback() {
                            @Override
                            public void handleService(String deviceName, String serviceName, Map<String, Object> params, ThingModel.ServiceResponser serviceResponser) {
                                String msgID = (String)params.get("msgID");
                                Toast.makeText(MainActivity.this.getApplicationContext(), "NoticeCancel(" + msgID + ")", Toast.LENGTH_SHORT).show();
                                // 响应服务
                                Map<String, Object> result = new HashMap<>();
                                result.put("retCode", 1);
                                result.put("retMsg", "Message " + msgID + " Canceled!");
                                serviceResponser.send(result);
                            }
                        });
                ThingModel.addServiceCallback(
                        DeviceManager.getInstance().getGwDev().getLinkitInfo().deviceName,
                        "NoticeDisplay",
                        new ThingModel.ServiceCallback() {
                            @Override
                            public void handleService(String deviceName, String serviceName, Map<String, Object> params, ThingModel.ServiceResponser serviceResponser) {
                                String content = (String)params.get("DisplayContent");
                                String msgID = (String)params.get("msgID");
                                Toast.makeText(MainActivity.this.getApplicationContext(), content + "(" + msgID + ")", Toast.LENGTH_SHORT).show();
                                // 响应服务
                                Map<String, Object> result = new HashMap<>();
                                result.put("retCode", 1);
                                result.put("retMsg", "Message Showed!");
                                serviceResponser.send(result);
                            }
                        });
                ThingModel.addServiceCallback(
                        DeviceManager.getInstance().getSubDev().get(0).getLinkitInfo().deviceName,
                        "NoticeCancel",
                        new ThingModel.ServiceCallback() {
                            @Override
                            public void handleService(String deviceName, String serviceName, Map<String, Object> params, ThingModel.ServiceResponser serviceResponser) {
                                String msgID = (String)params.get("msgID");
                                Toast.makeText(MainActivity.this.getApplicationContext(), "NoticeCancel(" + msgID + ")", Toast.LENGTH_SHORT).show();
                                // 响应服务
                                Map<String, Object> result = new HashMap<>();
                                result.put("retCode", 1);
                                result.put("retMsg", "Message " + msgID + " Canceled!");
                                serviceResponser.send(result);
                            }
                        });
                ThingModel.addServiceCallback(
                        DeviceManager.getInstance().getSubDev().get(0).getLinkitInfo().deviceName,
                        "NoticeDisplay",
                        new ThingModel.ServiceCallback() {
                            @Override
                            public void handleService(String deviceName, String serviceName, Map<String, Object> params, ThingModel.ServiceResponser serviceResponser) {
                                String content = (String)params.get("DisplayContent");
                                String msgID = (String)params.get("msgID");
                                Toast.makeText(MainActivity.this.getApplicationContext(), content + "(" + msgID + ")", Toast.LENGTH_SHORT).show();
                                // 响应服务
                                Map<String, Object> result = new HashMap<>();
                                result.put("retCode", 1);
                                result.put("retMsg", "Message Showed!");
                                serviceResponser.send(result);
                            }
                        });
            }
        });

        findViewById(R.id.btn_post).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 上报事件
                Map<String, Object> result = new HashMap<>();
                result.put("Status", 1);
                result.put("Message", "It's OK");
                ThingModel.postEvent(DeviceManager.getInstance().getGwDev().getLinkitInfo().deviceName,
                        "StatusEvent", result);
                // 上报属性
                ThingModel.postProperty(DeviceManager.getInstance().getGwDev().getLinkitInfo().deviceName,
                        "Version", "1.0");

            }
        });

        check();
    }

    private void check() {
        handler.removeCallbacks(checkRunnable);
        handler.postDelayed(checkRunnable, 5000);
    }

}
