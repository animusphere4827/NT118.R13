package com.example.nt118_project.ui.badge;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.databinding.ItemBadgeBinding;

import java.util.Objects;

/** Lưới huy hiệu linh vật cừu: đã mở khóa có màu, chưa mở khóa đen trắng mờ kèm ổ khóa nhỏ. */
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

    private static final android.graphics.ColorMatrixColorFilter GRAYSCALE;
    static {
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
        matrix.setSaturation(0f);
        GRAYSCALE = new android.graphics.ColorMatrixColorFilter(matrix);
    }

    /** Ảnh huy hiệu linh vật cừu theo tên icon server trả về (badge_definitions.icon). */
    @DrawableRes
    public static int artOf(@Nullable String icon) {
        if (icon == null) {
            return R.drawable.badge_award;
        }
        switch (icon) {
            case "flame":
                return R.drawable.badge_flame;
            case "book_open":
                return R.drawable.badge_book_open;
            case "mail":
                return R.drawable.badge_mail;
            case "target":
                return R.drawable.badge_target;
            default:
                return R.drawable.badge_award;
        }
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
        h.b.imgIcon.setImageResource(artOf(b.icon));
        // Chưa mở khóa: huy hiệu đen trắng, mờ, kèm ổ khóa nhỏ ở góc
        h.b.imgIcon.setColorFilter(b.unlocked ? null : GRAYSCALE);
        h.b.imgLock.setVisibility(b.unlocked ? android.view.View.GONE : android.view.View.VISIBLE);
        h.itemView.setAlpha(b.unlocked ? 1f : 0.6f);
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
