package com.example.nt118_project.ui.badge;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.databinding.ItemBadgeBinding;

import java.util.Objects;

/** Lưới huy hiệu: đã mở khóa có màu, chưa mở khóa xám mờ với biểu tượng khóa. */
public class BadgeAdapter extends ListAdapter<BadgeDto, BadgeAdapter.Holder> {

    public interface OnClick {
        void onClick(BadgeDto badge);
    }

    private final OnClick onClick;

    public BadgeAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<BadgeDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull BadgeDto a, @NonNull BadgeDto b) {
                return Objects.equals(a.code, b.code);
            }

            @Override
            public boolean areContentsTheSame(@NonNull BadgeDto a, @NonNull BadgeDto b) {
                return a.unlocked == b.unlocked && Objects.equals(a.name, b.name);
            }
        });
        this.onClick = onClick;
    }

    @DrawableRes
    public static int iconOf(@Nullable String icon) {
        if (icon == null) {
            return R.drawable.ic_award;
        }
        switch (icon) {
            case "flame":
                return R.drawable.ic_flame;
            case "book_open":
                return R.drawable.ic_book_open;
            case "mail":
                return R.drawable.ic_mail;
            case "target":
                return R.drawable.ic_target;
            case "calendar_check":
                return R.drawable.ic_calendar_check;
            case "trending_up":
                return R.drawable.ic_trending_up;
            default:
                return R.drawable.ic_award;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBadgeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        BadgeDto b = getItem(position);
        h.b.txtName.setText(b.name);
        h.b.txtRequirement.setText(b.requirement);
        h.b.imgIcon.setImageResource(b.unlocked ? iconOf(b.icon) : R.drawable.ic_lock);
        h.b.iconFrame.setBackgroundResource(b.unlocked ? R.drawable.bg_circle_soft : R.drawable.bg_circle_neutral);
        ImageViewCompat.setImageTintList(h.b.imgIcon, ColorStateList.valueOf(ContextCompat.getColor(
                h.itemView.getContext(), b.unlocked ? R.color.app_primary : R.color.app_text_secondary)));
        // Chưa mở khóa: xám mờ
        h.itemView.setAlpha(b.unlocked ? 1f : 0.55f);
        h.itemView.setContentDescription(h.itemView.getContext().getString(
                b.unlocked ? R.string.badge_desc_unlocked : R.string.badge_desc_locked, b.name, b.requirement));
        h.itemView.setOnClickListener(v -> onClick.onClick(b));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemBadgeBinding b;

        Holder(ItemBadgeBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
