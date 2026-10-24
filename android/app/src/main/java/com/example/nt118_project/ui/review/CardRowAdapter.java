package com.example.nt118_project.ui.review;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.databinding.ItemCardRowBinding;

import java.util.Objects;

/** Dòng thẻ trong chi tiết bộ thẻ: chấm trạng thái, từ, nghĩa, nhãn Đến hạn/Đã thuộc/Mới, nút ghim. */
public class CardRowAdapter extends ListAdapter<CardDto, CardRowAdapter.Holder> {

    public interface Listener {
        void onOpen(CardDto card);

        void onTogglePin(CardDto card);
    }

    private final Listener listener;

    public CardRowAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<CardDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull CardDto a, @NonNull CardDto b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull CardDto a, @NonNull CardDto b) {
                return Objects.equals(a.frontText, b.frontText) && Objects.equals(a.backText, b.backText)
                        && a.isPinned == b.isPinned && a.repetitions == b.repetitions
                        && Objects.equals(a.dueAt, b.dueAt);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCardRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CardDto card = getItem(position);
        Context c = h.itemView.getContext();
        h.b.txtWord.setText(card.frontText);
        h.b.txtMeaning.setText(card.backText);

        int status;
        int color;
        int dot;
        if (card.isNew()) {
            status = R.string.card_status_new;
            color = R.color.app_text_secondary;
            dot = R.drawable.bg_circle_neutral;
        } else if (card.isDue()) {
            status = R.string.card_status_due;
            color = R.color.app_primary;
            dot = R.drawable.bg_circle_primary;
        } else if (card.isMastered()) {
            status = R.string.card_status_mastered;
            color = R.color.app_teal;
            dot = R.drawable.bg_circle_success;
        } else {
            status = R.string.card_status_learning;
            color = R.color.app_orange;
            dot = R.drawable.bg_circle_success;
        }
        h.b.txtStatus.setText(status);
        h.b.txtStatus.setTextColor(ContextCompat.getColor(c, color));
        h.b.dot.setBackgroundResource(dot);

        ImageViewCompat.setImageTintList(h.b.btnPin, ColorStateList.valueOf(
                ContextCompat.getColor(c, card.isPinned ? R.color.app_orange : R.color.app_border)));
        h.b.btnPin.setContentDescription(c.getString(card.isPinned ? R.string.card_unpin : R.string.card_pin_label));
        h.b.btnPin.setOnClickListener(v -> listener.onTogglePin(card));
        h.itemView.setOnClickListener(v -> listener.onOpen(card));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemCardRowBinding b;

        Holder(ItemCardRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
