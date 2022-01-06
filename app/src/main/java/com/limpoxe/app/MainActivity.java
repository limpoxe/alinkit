package com.limpoxe.app;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.limpoxe.alinkit.DeviceManager;
import com.limpoxe.alinkit.LinkitInfo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //LinkitInfo linkitInfo = new LinkitInfo();
                //DeviceManager.getInstance().addDev(linkitInfo);
                //DeviceManager.getInstance().beat();
            }
        });
    }

}
