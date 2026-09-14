package com.sgggzg.qmzg;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_LOG = "com.sgggzg.qmzg.LOG";
    public static final String EXTRA_LOG = "log_msg";
    public static final String ACTION_IMAGE = "com.sgggzg.qmzg.IMAGE";
    public static final String EXTRA_IMAGE = "image_bytes";
    public static final String ACTION_SERVICE_STOPPED = "com.sgggzg.qmzg.SERVICE_STOPPED";

    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigation = findViewById(R.id.bottom_navigation);

        // 只在冷启动时记录日志
        if (savedInstanceState == null) {
            AppState.appendLog("应用已启动");
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.navigation_settings) {
                selectedFragment = new SettingsFragment();
            } else if (itemId == R.id.navigation_about) {
                // 切换到说明页面
                selectedFragment = new AboutFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
                return true;
            }
            return false;
        });
    }
}
