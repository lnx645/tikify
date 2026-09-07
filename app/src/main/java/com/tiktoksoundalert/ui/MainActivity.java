package com.tiktoksoundalert.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationView;
import com.tiktoksoundalert.R;
import com.tiktoksoundalert.SettingsManager;
import com.tiktoksoundalert.db.Account;
import com.tiktoksoundalert.db.AccountDao;
import com.tiktoksoundalert.db.AppDatabase;
import com.tiktoksoundalert.service.TikTokService;
import com.tiktoksoundalert.SettingsRepository;
import com.tiktoksoundalert.tiktok.TikTokAvatarResolver;
import com.tiktoksoundalert.ui.SettingsActivity;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private NavigationView navView;
    private MaterialButton btnConnect;
    private ProgressBar progressConnect;
    private TextView statusText;
    private TextView drawerAccount;
    private ImageView ivDrawerAvatar;
    private View viewStatusDot;

    private LocalBroadcastManager broadcastManager;

    private boolean connecting = false;
    private boolean connected = false;
    private String connectedHost;
    private boolean dialogShown = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(TikTokService.EXTRA_STATUS);
            String detail = intent.getStringExtra(TikTokService.EXTRA_ERROR);
            String host = intent.getStringExtra(TikTokService.EXTRA_HOSTNAME);
            handleStatus(status, detail, host);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        broadcastManager = LocalBroadcastManager.getInstance(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        bottomNav = findViewById(R.id.bottom_nav);
        navView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        bottomNav.setOnNavigationItemSelectedListener(this::onBottomNavSelected);
        navView.setNavigationItemSelectedListener(this::onDrawerItemSelected);

        View header = navView.getHeaderView(0);
        if (header != null) {
            btnConnect = header.findViewById(R.id.btn_connect);
            progressConnect = header.findViewById(R.id.progress_connect);
            statusText = header.findViewById(R.id.status_text);
            drawerAccount = header.findViewById(R.id.drawer_account);
            ivDrawerAvatar = header.findViewById(R.id.iv_drawer_avatar);
            viewStatusDot = header.findViewById(R.id.view_status_dot);
        }

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> toggleConnect());
        }

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(TikTokService.BROADCAST_STATUS);
        broadcastManager.registerReceiver(statusReceiver, filter);
        refreshDrawerAccount();
        if (isServiceRunning()) {
            Intent q = new Intent(this, TikTokService.class);
            q.setAction(TikTokService.ACTION_QUERY_STATUS);
            startService(q);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        broadcastManager.unregisterReceiver(statusReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDrawerAccount();
    }

    private boolean onBottomNavSelected(@NonNull MenuItem item) {
        Fragment fragment;
        int id = item.getItemId();
        if (id == R.id.nav_alert) {
            fragment = new AlertQueueFragment();
        } else if (id == R.id.nav_tts) {
            fragment = new TtsFragment();
        } else if (id == R.id.nav_viewers) {
            fragment = new ViewersFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        return true;
    }

    private boolean onDrawerItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        int id = item.getItemId();
        if (id == R.id.nav_accounts) {
            startActivity(new Intent(this, AccountsActivity.class));
        } else if (id == R.id.nav_general_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_SECTION, SettingsActivity.KEY_GENERAL);
            startActivity(intent);
        } else if (id == R.id.nav_about) {
            startActivity(new Intent(this, AboutActivity.class));
        }
        return true;
    }

    private void refreshDrawerAccount() {
        AccountDao dao = AppDatabase.get(this).accountDao();
        AppDatabase.runInBackground(() -> {
            Account active = dao.getActiveNow();
            runOnUiThread(() -> {
                if (drawerAccount != null) {
                    if (active != null) {
                        drawerAccount.setText("@" + active.nickname);
                    } else {
                        drawerAccount.setText("No account selected");
                    }
                }
                loadHostAvatar(active);
            });
        });
    }

    /** Load the active host's TikTok avatar into the drawer, preferring the
     *  URL persisted on the account, then the in-memory cache, then the network. */
    private void loadHostAvatar(Account account) {
        if (ivDrawerAvatar == null) return;
        if (account == null) {
            ivDrawerAvatar.setImageResource(R.drawable.noavatar);
            return;
        }
        if (account.avatarUrl != null && !account.avatarUrl.isEmpty()) {
            Glide.with(this).load(account.avatarUrl).circleCrop().into(ivDrawerAvatar);
            return;
        }
        String cached = TikTokAvatarResolver.cached(account.nickname);
        if (cached != null) {
            Glide.with(this).load(cached).circleCrop().into(ivDrawerAvatar);
            return;
        }
        new TikTokAvatarResolver().resolve(account.nickname, new TikTokAvatarResolver.Callback() {
            @Override
            public void onUrl(String url) {
                if (isDestroyed()) return;
                Glide.with(MainActivity.this).load(url).circleCrop().into(ivDrawerAvatar);
                persistAvatar(account.nickname, url);
            }

            @Override
            public void onError() {
                // Keep the placeholder silhouette.
            }
        });
    }

    private void persistAvatar(String nickname, String url) {
        AppDatabase.runInBackground(() -> {
            try {
                AccountDao dao = AppDatabase.get(MainActivity.this).accountDao();
                Account account = dao.findByNickname(nickname);
                if (account != null) {
                    dao.updateAvatarUrl(account.id, url);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void toggleConnect() {
        if (connected) {
            stopConnection();
        } else if (connecting) {
            stopConnection();
        } else {
            startConnection();
        }
    }

    private void startConnection() {
        AccountDao dao = AppDatabase.get(this).accountDao();
        AppDatabase.runInBackground(() -> {
            Account active = dao.getActiveNow();
            runOnUiThread(() -> {
                if (active == null) {
                    new AlertDialog.Builder(this)
                            .setTitle("No account selected")
                            .setMessage("Add and select a TikTok account first, then try connecting again.")
                            .setPositiveButton("Open Accounts", (d, w) ->
                                    startActivity(new Intent(this, AccountsActivity.class)))
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
                setConnectingState(true, active.nickname);
                connectedHost = null;
                dialogShown = false;

                Intent serviceIntent = new Intent(this, TikTokService.class);
                serviceIntent.setAction(TikTokService.ACTION_START);
                serviceIntent.putExtra(TikTokService.EXTRA_HOSTNAME, active.nickname);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            });
        });
    }

    private void stopConnection() {
        connected = false;
        connecting = false;
        setUiConnected(false, null);
        Intent stopIntent = new Intent(this, TikTokService.class);
        stopIntent.setAction(TikTokService.ACTION_STOP);
        startService(stopIntent);
    }

    private boolean isServiceRunning() {
        android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo info :
                am.getRunningServices(Integer.MAX_VALUE)) {
            if (TikTokService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void handleStatus(String status, String detail, String host) {
        if (status == null) return;
        switch (status) {
            case TikTokService.STATUS_CONNECTING:
                setConnectingState(true, host);
                break;
            case TikTokService.STATUS_CONNECTED:
                connected = true;
                connecting = false;
                connectedHost = host;
                setUiConnected(true, host);
                refreshDrawerAccount();
                break;
            case TikTokService.STATUS_DISCONNECTED:
                boolean wasConnectedOrConnecting = connected || connecting;
                connected = false;
                connecting = false;
                if (wasConnectedOrConnecting) {
                    setUiConnected(false, null);
                }
                break;
            case TikTokService.STATUS_ERROR:
                boolean wasConnectedBefore = connected;
                connected = false;
                connecting = false;
                if (!wasConnectedBefore) {
                    setUiConnected(false, null);
                    if (!dialogShown) {
                        dialogShown = true;
                        showConnectionError(detail, host);
                    }
                } else {
                    setUiConnected(false, null);
                }
                break;
            default:
                break;
        }
    }

    private void showConnectionError(String detail, String host) {
        String target = host != null ? "@" + host : "";
        new AlertDialog.Builder(this)
                .setTitle("Connection Failed")
                .setMessage("Couldn't connect to " + target + ".\n\n"
                        + (detail != null ? detail : "Please check the username and try again."))
                .setPositiveButton("OK", (d, w) -> dialogShown = false)
                .show();
    }

    private void setConnectingState(boolean isConnecting, String host) {
        connecting = isConnecting;
        if (btnConnect != null) {
            btnConnect.setEnabled(!isConnecting);
            btnConnect.setText(isConnecting ? "Connecting..." : getString(R.string.btn_connect));
        }
        if (progressConnect != null) {
            progressConnect.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
        }
        if (statusText != null) {
            if (isConnecting) {
                statusText.setText("Connecting to @" + (host != null ? host : ""));
            } else {
                statusText.setText("Not connected");
            }
        }
        setStatusDot(false);
    }

    private void setStatusDot(boolean connected) {
        if (viewStatusDot == null) return;
        viewStatusDot.setBackgroundColor(getColor(connected
                ? R.color.connected_color : R.color.disconnected_color));
    }

    private void setUiConnected(boolean isConnected, String host) {
        String hostname = host != null ? host : connectedHost;
        if (btnConnect != null) {
            btnConnect.setEnabled(true);
            btnConnect.setText(isConnected ? "Disconnect" : getString(R.string.btn_connect));
            btnConnect.setIcon(isConnected
                    ? AppCompatResources.getDrawable(this, R.drawable.ic_stop)
                    : AppCompatResources.getDrawable(this, R.drawable.ic_connect));
        }
        if (progressConnect != null) {
            progressConnect.setVisibility(View.GONE);
        }
        if (statusText != null) {
            statusText.setText(isConnected
                    ? "Connected to @" + hostname
                    : "Not connected");
        }
        setStatusDot(isConnected);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (!connected && !connecting
                && bottomNav.getSelectedItemId() != R.id.nav_home) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}