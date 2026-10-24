package com.example.nt118_project.ui.review;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.databinding.ItemDeckBinding;

import java.util.Objects;

/** Lưới bộ thẻ (item_deck). Thanh tiến độ = phần thẻ chưa đến hạn. */
public class DeckAdapter extends ListAdapter<DeckDto, DeckAdapter.Holder> {

    public interface Listener {
        void onOpen(DeckDto deck);

        void onMenu(DeckDto deck, View anchor);
    }

    private final Listener listener;

    public DeckAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<DeckDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull DeckDto a, @NonNull DeckDto b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull DeckDto a, @NonNull DeckDto b) {
                return Objects.equals(a.name, b.name) && a.cardCount == b.cardCount
                        && a.dueCount == b.dueCount && a.pinnedCount == b.pinnedCount;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDeckBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        DeckDto d = getItem(position);
        Context c = h.itemView.getContext();
        h.b.txtName.setText(d.name);
        String info = c.getString(R.string.deck_word_count, d.cardCount);
        if (d.pinnedCount > 0) {
            info += c.getString(R.string.deck_pinned_suffix, d.pinnedCount);
        }
        h.b.txtInfo.setText(info);
        h.b.txtDueBadge.setVisibility(d.dueCount > 0 ? View.VISIBLE : View.GONE);
        h.b.txtDueBadge.setText(c.getString(R.string.deck_due_badge, d.dueCount));
        int progress = d.cardCount == 0 ? 0 : Math.round(100f * (d.cardCount - d.dueCount) / d.cardCount);
        h.b.progress.setProgress(progress);
        h.itemView.setOnClickListener(v -> listener.onOpen(d));
        h.itemView.setOnLongClickListener(v -> {
            listener.onMenu(d, v);
            return true;
        });
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDeckBinding b;

        Holder(ItemDeckBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
