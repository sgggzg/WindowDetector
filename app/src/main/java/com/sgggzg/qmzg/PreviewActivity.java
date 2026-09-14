package com.sgggzg.qmzg;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

public class PreviewActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 100;

    private ImageView ivPreview;
    private RegionAdjustView regionView;
    private EditText etTop, etBottom;
    private Button btnSelect, btnApply;

    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        ivPreview = findViewById(R.id.ivPreviewImage);
        regionView = findViewById(R.id.regionView);
        etTop = findViewById(R.id.etTopPreview);
        etBottom = findViewById(R.id.etBottomPreview);
        btnSelect = findViewById(R.id.btnSelectImage);
        btnApply = findViewById(R.id.btnApply);

        // 接收传入的top/bottom
        float top = getIntent().getFloatExtra("top", 0.66f);
        float bottom = getIntent().getFloatExtra("bottom", 0.81f);
        etTop.setText(String.valueOf(top));
        etBottom.setText(String.valueOf(bottom));
        regionView.setRegion(top, bottom);

        // 监听区域变化，同步EditText
        regionView.setOnRegionChangedListener((t, b) -> {
            etTop.setText(String.format("%.2f", t));
            etBottom.setText(String.format("%.2f", b));
        });

        // 选择图片
        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        });

        // 应用并返回
        btnApply.setOnClickListener(v -> {
            float newTop = Float.parseFloat(etTop.getText().toString());
            float newBottom = Float.parseFloat(etBottom.getText().toString());
            Intent result = new Intent();
            result.putExtra("top", newTop);
            result.putExtra("bottom", newBottom);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                ivPreview.setImageBitmap(currentBitmap);
                // 通知RegionAdjustView图片尺寸变化（无需额外操作，onDraw中会根据View尺寸计算）
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
