package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.rinda.app.R;
import com.rinda.app.databinding.ActivityOnboardingBinding;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;

    private static class Slide {
        final int iconRes;
        final int titleRes;
        final int descRes;

        Slide(int iconRes, int titleRes, int descRes) {
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.descRes = descRes;
        }
    }

    private final Slide[] slides = new Slide[]{
            new Slide(R.drawable.ic_rinda_sprout, R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc),
            new Slide(R.drawable.ic_nav_plots, R.string.onboarding_satellite_title, R.string.onboarding_satellite_desc),
            new Slide(R.drawable.ic_nav_claims, R.string.onboarding_claims_title, R.string.onboarding_claims_desc)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.viewPager.setAdapter(new OnboardingAdapter());
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(position);
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());

        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < slides.length - 1) {
                binding.viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });
    }

    private void updatePageIndicator(int position) {
        binding.tvPageIndicator.setText((position + 1) + " / " + slides.length);
        if (position == slides.length - 1) {
            binding.btnNext.setText(R.string.btn_continue);
            binding.btnSkip.setVisibility(View.INVISIBLE);
        } else {
            binding.btnNext.setText(R.string.btn_continue);
            binding.btnSkip.setVisibility(View.VISIBLE);
        }
    }

    private void finishOnboarding() {
        startActivity(new Intent(this, ConsentActivity.class));
        finish();
    }

    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

        @NonNull
        @Override
        public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false);
            return new SlideViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
            Slide slide = slides[position];
            holder.ivImage.setImageResource(slide.iconRes);
            holder.tvTitle.setText(slide.titleRes);
            holder.tvDesc.setText(slide.descRes);
        }

        @Override
        public int getItemCount() {
            return slides.length;
        }

        class SlideViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivImage;
            final TextView tvTitle;
            final TextView tvDesc;

            SlideViewHolder(View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.ivSlideImage);
                tvTitle = itemView.findViewById(R.id.tvSlideTitle);
                tvDesc = itemView.findViewById(R.id.tvSlideDesc);
            }
        }
    }
}
