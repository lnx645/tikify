package com.tiktoksoundalert.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.tiktoksoundalert.BuildConfig;
import com.tiktoksoundalert.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView version = findViewById(R.id.tv_about_version);
        version.setText("Versi " + BuildConfig.VERSION_NAME);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}