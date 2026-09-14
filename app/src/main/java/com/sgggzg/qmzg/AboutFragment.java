package com.sgggzg.qmzg;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AboutFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        // 绑定 6 个折叠卡片的标题和内容
        setupFold(view, R.id.tvTitle1, R.id.tvContent1);
        setupFold(view, R.id.tvTitle2, R.id.tvContent2);
        setupFold(view, R.id.tvTitle3, R.id.tvContent3);
        setupFold(view, R.id.tvTitle4, R.id.tvContent4);
        setupFold(view, R.id.tvTitle5, R.id.tvContent5);
        setupFold(view, R.id.tvTitle6, R.id.tvContent6);

        return view;
    }

    // 通用的折叠逻辑封装
    private void setupFold(View view, int titleId, int contentId) {
        TextView tvTitle = view.findViewById(titleId);
        final TextView tvContent = view.findViewById(contentId);

        tvTitle.setOnClickListener(v -> {
            if (tvContent.getVisibility() == View.GONE) {
                tvContent.setVisibility(View.VISIBLE);
            } else {
                tvContent.setVisibility(View.GONE);
            }
        });
    }
}
