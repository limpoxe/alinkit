package com.limpoxe.app;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.limpoxe.alinkit.DeviceManager;
import com.limpoxe.alinkit.LinkitInfo;
import com.limpoxe.alinkit.LogUtil;

public class MainActivity extends AppCompatActivity {

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
                        return null;
                    }
                }.execute();
            }
        });

        check();
    }

    private void check() {
        handler.removeCallbacks(checkRunnable);
        handler.postDelayed(checkRunnable, 5000);
    }

}
