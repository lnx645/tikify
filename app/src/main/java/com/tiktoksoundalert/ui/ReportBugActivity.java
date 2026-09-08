package com.tiktoksoundalert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.tiktoksoundalert.BuildConfig;
import com.tiktoksoundalert.CrashHandler;
import com.tiktoksoundalert.R;

/** Shows a crash report and lets the user email it to the developer. */
public class ReportBugActivity extends AppCompatActivity {

    public static final String EXTRA_CRASH_REPORT = CrashHandler.EXTRA_CRASH_REPORT;
    /** Developer inbox for bug reports. */
    public static final String REPORT_EMAIL = "dadanhidyt@gmail.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bug_report);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        TextView tvStatus = findViewById(R.id.tv_bug_status);
        TextView tvDesc = findViewById(R.id.tv_bug_desc);
        EditText etReport = findViewById(R.id.et_bug_report);

        String report = getIntent().getStringExtra(EXTRA_CRASH_REPORT);
        boolean isCrash = report != null && !report.trim().isEmpty();
        if (!isCrash) {
            report = CrashHandler.loadLastReport(this);
        }

        if (report == null || report.trim().isEmpty()) {
            tvStatus.setText(R.string.bug_report_no_report_title);
            tvDesc.setVisibility(TextView.GONE);
            etReport.setText(R.string.bug_report_no_report_body);
        } else {
            tvStatus.setText(R.string.bug_report_saved_title);
            tvDesc.setVisibility(TextView.VISIBLE);
            etReport.setText(report);
        }

        Button btnSend = findViewById(R.id.btn_bug_send);
        btnSend.setOnClickListener(v -> sendReport(etReport.getText().toString()));

        Button btnCopy = findViewById(R.id.btn_bug_copy);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("report",
                        etReport.getText().toString()));
                Toast.makeText(this, R.string.bug_report_copied, Toast.LENGTH_SHORT).show();
            }
        });

        Button btnClose = findViewById(R.id.btn_bug_close);
        btnClose.setOnClickListener(v -> finish());
    }

    private void sendReport(String body) {
        String subject = getString(R.string.bug_report_email_subject,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        Intent email = new Intent(Intent.ACTION_SENDTO);
        email.setData(Uri.parse("mailto:" + REPORT_EMAIL));
        email.putExtra(Intent.EXTRA_EMAIL, new String[]{REPORT_EMAIL});
        email.putExtra(Intent.EXTRA_SUBJECT, subject);
        email.putExtra(Intent.EXTRA_TEXT, body == null ? "" : body);
        try {
            startActivity(Intent.createChooser(email,
                    getString(R.string.bug_report_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.bug_report_no_email_app, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}