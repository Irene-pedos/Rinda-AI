package com.rinda.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayoutMediator;
import com.rinda.app.R;
import com.rinda.app.databinding.ActivityOnboardingBinding;
import com.rinda.app.databinding.ItemOnboardingPageBinding;

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
            new Slide(R.drawable.ic_leaf, R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc),
            new Slide(R.drawable.ic_satellite, R.string.onboarding_satellite_title, R.string.onboarding_satellite_desc),
            new Slide(R.drawable.ic_shield, R.string.onboarding_claims_title, R.string.onboarding_claims_desc)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.viewPager.setAdapter(new OnboardingAdapter());
        new TabLayoutMediator(binding.tabDots, binding.viewPager, (tab, position) -> {
        }).attach();
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateControls(position);
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());
        binding.btnBack.setOnClickListener(v -> binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() - 1));
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < slides.length - 1) {
                binding.viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });
        updateControls(0);
    }

    private void updateControls(int position) {
        boolean last = position == slides.length - 1;
        binding.tvPageIndicator.setText(getString(R.string.step_of, position + 1, slides.length));
        binding.btnNext.setText(last ? R.string.btn_get_started : R.string.btn_next);
        binding.btnBack.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        binding.btnSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    private void finishOnboarding() {
        startActivity(new Intent(this, ConsentActivity.class));
        finish();
    }

    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

        @NonNull
        @Override
        public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SlideViewHolder(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
            Slide slide = slides[position];
            holder.binding.ivSlideImage.setImageResource(slide.iconRes);
            holder.binding.tvSlideTitle.setText(slide.titleRes);
            holder.binding.tvSlideDesc.setText(slide.descRes);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull SlideViewHolder holder) {
            // A small "pop" of the picture each time a slide appears
            View art = holder.binding.illustration;
            art.setScaleX(0.9f);
            art.setScaleY(0.9f);
            art.animate().scaleX(1f).scaleY(1f).setDuration(350).start();
        }

        @Override
        public int getItemCount() {
            return slides.length;
        }

        class SlideViewHolder extends RecyclerView.ViewHolder {
            final ItemOnboardingPageBinding binding;

            SlideViewHolder(ItemOnboardingPageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
