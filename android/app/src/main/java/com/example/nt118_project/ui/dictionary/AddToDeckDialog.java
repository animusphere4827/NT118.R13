package com.example.nt118_project.ui.dictionary;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.repository.DeckRepository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Chọn bộ thẻ để thêm một từ trong từ điển (POST /learn/decks/{id}/cards): từ trong DB gửi vocabulary_id,
 * từ ngoài DB gửi sẵn nội dung thẻ. Chưa có bộ thẻ nào thì cho tạo bộ mới ngay trong hộp thoại.
 */
public final class AddToDeckDialog {

    private AddToDeckDialog() {
    }

    public static void show(Fragment fragment, long vocabularyId) {
        show(fragment, CardRequest.fromVocabulary(vocabularyId));
    }

    public static void show(Fragment fragment, CardRequest card) {
        Context context = fragment.requireContext().getApplicationContext();
        DeckRepository repo = App.container().deckRepository;
        repo.decks(r -> {
            if (!fragment.isAdded()) {
                return;
            }
            if (r.isError()) {
                Toast.makeText(context, r.message, Toast.LENGTH_LONG).show();
                return;
            }
            List<DeckDto> decks = r.data;
            String[] items = new String[decks.size() + 1];
            for (int i = 0; i < decks.size(); i++) {
                items[i] = decks.get(i).name;
            }
            items[decks.size()] = fragment.getString(R.string.deck_new);
            new MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.deck_pick_title)
                    .setItems(items, (d, which) -> {
                        if (which == decks.size()) {
                            askNewDeck(fragment, card);
                        } else {
                            add(context, repo, decks.get(which), card);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private static void askNewDeck(Fragment fragment, CardRequest card) {
        EditText input = new EditText(fragment.requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.deck_name_hint);
        input.setSingleLine(true);
        FrameLayout box = new FrameLayout(fragment.requireContext());
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                fragment.getResources().getDisplayMetrics());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        Context context = fragment.requireContext().getApplicationContext();
        DeckRepository repo = App.container().deckRepository;
        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.deck_new_title)
                .setView(box)
                .setPositiveButton(R.string.deck_create, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || name.length() > 150) {
                input.setError(context.getString(R.string.error_deck_name));
                return;
            }
            dialog.dismiss();
            repo.createDeck(name, "", r -> {
                if (r.isSuccess() && r.data != null) {
                    DeckDto deck = new DeckDto();
                    deck.id = r.data.id;
                    deck.name = name;
                    add(context, repo, deck, card);
                } else {
                    Toast.makeText(context, r.code == 409
                            ? context.getString(R.string.error_deck_exists) : r.message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private static void add(Context context, DeckRepository repo, DeckDto deck, CardRequest card) {
        repo.addCard(deck.id, card, r -> {
            String message;
            if (r.isSuccess()) {
                message = context.getString(R.string.deck_added, deck.name);
            } else if (r.code == 409) {
                message = context.getString(R.string.deck_word_exists, deck.name);
            } else {
                message = r.message;
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
}
