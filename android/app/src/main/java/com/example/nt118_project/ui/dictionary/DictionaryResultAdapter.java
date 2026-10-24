package com.example.nt118_project.ui.dictionary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.databinding.ItemDictionaryResultBinding;

import java.util.Objects;

/** Danh sách kết quả tra từ (layout item_dictionary_result). */
public class DictionaryResultAdapter extends ListAdapter<VocabularyDto, DictionaryResultAdapter.Holder> {

    public interface OnClick {
        void onClick(VocabularyDto word);
    }

    private final OnClick onClick;

    public DictionaryResultAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<VocabularyDto>() {
            @Override
            public boolean areItemsTheSame(@NonNull VocabularyDto a, @NonNull VocabularyDto b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull VocabularyDto a, @NonNull VocabularyDto b) {
                return Objects.equals(a.word, b.word) && Objects.equals(a.ipa, b.ipa)
                        && Objects.equals(a.meaningVi, b.meaningVi) && Objects.equals(a.partOfSpeech, b.partOfSpeech);
            }
        });
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDictionaryResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        VocabularyDto w = getItem(position);
        holder.b.txtWord.setText(w.word);
        holder.b.txtIpa.setText(w.ipa);
        holder.b.txtIpa.setVisibility(isEmpty(w.ipa) ? View.GONE : View.VISIBLE);
        holder.b.txtMeaning.setText(isEmpty(w.partOfSpeech) ? w.meaningVi : w.partOfSpeech + " · " + w.meaningVi);
        holder.b.getRoot().setOnClickListener(v -> onClick.onClick(w));
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDictionaryResultBinding b;

        Holder(ItemDictionaryResultBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
