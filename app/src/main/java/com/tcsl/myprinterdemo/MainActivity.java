package com.tcsl.myprinterdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    //权限申请
    private String[] NEED_PERMISSION = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private List<String> NO_PERMISSION = new ArrayList<String>();
    private static final int REQUEST_CAMERA = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CheckAllPermission();
    }

    private void CheckAllPermission() {
        NO_PERMISSION.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (int i = 0; i < NEED_PERMISSION.length; i++) {
                if (checkSelfPermission(NEED_PERMISSION[i]) != PackageManager.PERMISSION_GRANTED) {
                    NO_PERMISSION.add(NEED_PERMISSION[i]);
                }
            }
            if (NO_PERMISSION.size() == 0) {
                recordVideo();
            } else {
                requestPermissions(NO_PERMISSION.toArray(new String[NO_PERMISSION.size()]), REQUEST_CAMERA);
            }
        } else {
            recordVideo();
        }

    }

    private void recordVideo() {
        Log.d("MainActivity", "有权限了");
    }
}
