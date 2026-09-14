package com.sgggzg.qmzg;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.materialswitch.MaterialSwitch;

public class HomeFragment extends Fragment {

    private static final int REQUEST_MEDIA_PROJECTION = 2001;
    private static final int REQUEST_OVERLAY = 2002;
    private static final int REQUEST_NOTIFICATION = 2003;

    private TextView tvStatus, tvLog;
    private ImageView ivPreview;
    private Button btnToggleLog, btnStart, btnTestAlarm;
    private MaterialSwitch swEnableImage, swEnableLog;

    private View cardLog, cardPreview;
    private boolean isLogVisible = false;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MainActivity.ACTION_LOG.equals(action)) {
                refreshLogDisplay();
            } else if (MainActivity.ACTION_IMAGE.equals(action)) {
                if (AppState.lastPreviewBitmap != null && ivPreview != null) {
                    ivPreview.setImageBitmap(AppState.lastPreviewBitmap);
                }
            } else if (MonitorService.ACTION_SERVICE_STATE_CHANGED.equals(action)) {
                syncUiWithService();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvStatus = view.findViewById(R.id.tvStatus);
        tvLog = view.findViewById(R.id.tvLog);
        ivPreview = view.findViewById(R.id.ivPreview);
        btnToggleLog = view.findViewById(R.id.btnToggleLog);
        btnStart = view.findViewById(R.id.btnStart);
        btnTestAlarm = view.findViewById(R.id.btnTestAlarm);
        swEnableImage = view.findViewById(R.id.swEnableImage);
        swEnableLog = view.findViewById(R.id.swEnableLog);
        cardLog = view.findViewById(R.id.cardLog);
        cardPreview = view.findViewById(R.id.cardPreview);

        //开关绑定
        android.content.SharedPreferences prefs = requireActivity().getSharedPreferences("config", Context.MODE_PRIVATE);
        swEnableImage.setChecked(prefs.getBoolean("enable_image", true));
        swEnableLog.setChecked(prefs.getBoolean("enable_log", true));
        AppState.isImageCaptureEnabled = swEnableImage.isChecked();
        AppState.isLogWriteEnabled = swEnableLog.isChecked();

        swEnableImage.setOnCheckedChangeListener((v, isChecked) -> {
            AppState.isImageCaptureEnabled = isChecked;
            prefs.edit().putBoolean("enable_image", isChecked).apply();
        });

        swEnableLog.setOnCheckedChangeListener((v, isChecked) -> {
            AppState.isLogWriteEnabled = isChecked;
            prefs.edit().putBoolean("enable_log", isChecked).apply();
        });

        btnToggleLog.setOnClickListener(v -> {
            isLogVisible = !isLogVisible;
            if (isLogVisible) {
                cardLog.setVisibility(View.VISIBLE);
                cardPreview.setVisibility(View.GONE);
                btnToggleLog.setText("隐藏日志");
            } else {
                cardLog.setVisibility(View.GONE);
                cardPreview.setVisibility(View.VISIBLE);
                btnToggleLog.setText("显示日志");
            }
        });

        btnStart.setOnClickListener(v -> {
            if (AppState.isMonitoring) {
                Intent stopIntent = new Intent(requireActivity(), MonitorService.class);
                stopIntent.setAction(MonitorService.ACTION_STOP_MONITOR);
                requireActivity().startService(stopIntent);
            } else {
                checkPermissionsAndStart();
            }
        });

        btnTestAlarm.setOnClickListener(v -> {
            Intent testIntent = new Intent(requireActivity(), MonitorService.class);
            testIntent.setAction(MonitorService.ACTION_TEST_ALARM);
            requireActivity().startService(testIntent);
        });

        // 启动日志
        AppState.appendLog("HomeFragment已加载");
        refreshLogDisplay();

        return view;
    }

    private void syncUiWithService() {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            if (AppState.isMonitoring) {
                tvStatus.setText("监控运行中");
                btnStart.setText("停止监控");
            } else {
                tvStatus.setText("等待启动...");
                btnStart.setText("开始监控");
            }
        });
    }

    private void checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(requireActivity())) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                           Uri.parse("package:" + requireActivity().getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
                return;
            }
        }
        AppState.appendLog("请求屏幕录制授权");
        refreshLogDisplay();
        MediaProjectionManager mpm = (MediaProjectionManager) requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(requireActivity(), "必须授予通知权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(requireActivity())) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(requireActivity(), "需要悬浮窗权限", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                MonitorService.sCaptureIntent = data;
                MonitorService.sResultCode = resultCode;
                AppState.isAlarming = false;
                AppState.isMonitoring = true;

                Intent serviceIntent = new Intent(requireActivity(), MonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireActivity().startForegroundService(serviceIntent);
                } else {
                    requireActivity().startService(serviceIntent);
                }

                syncUiWithService();
                AppState.appendLog("MonitorService 已启动");
                refreshLogDisplay();
            } else {
                Toast.makeText(requireActivity(), "授权失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refreshLogDisplay() {
        if (tvLog == null) return;
        requireActivity().runOnUiThread(() -> {
            tvLog.setText(AppState.logBuffer.toString());
            View parent = (View) tvLog.getParent();
            if (parent != null) {
                parent.post(() -> {
                    android.widget.ScrollView sv = (android.widget.ScrollView) parent;
                    sv.fullScroll(View.FOCUS_DOWN);
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_LOG));
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, new IntentFilter(MainActivity.ACTION_IMAGE));
        LocalBroadcastManager.getInstance(requireActivity()).registerReceiver(receiver, new IntentFilter(MonitorService.ACTION_SERVICE_STATE_CHANGED));

        syncUiWithService();
        refreshLogDisplay();
        if (AppState.lastPreviewBitmap != null && ivPreview != null) {
            ivPreview.setImageBitmap(AppState.lastPreviewBitmap);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(receiver);
    }
}
