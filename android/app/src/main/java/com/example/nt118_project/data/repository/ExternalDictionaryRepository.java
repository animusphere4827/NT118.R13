package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.ExternalDictionaryApi;
import com.example.nt118_project.data.remote.dto.DatamuseWordDto;
import com.example.nt118_project.data.remote.dto.FreeDictionaryEntryDto;
import com.example.nt118_project.data.remote.dto.TranslationDto;
import com.example.nt118_project.data.remote.dto.VocabularyDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import retrofit2.Call;

/**
 * Tra mọi từ tiếng Anh qua dịch vụ ngoài: gợi ý (Datamuse), định nghĩa (Free Dictionary API),
 * nghĩa tiếng Việt dịch máy (MyMemory). Từ ngoài DB được biểu diễn bằng VocabularyDto có id = 0.
 */
public class ExternalDictionaryRepository {

    private static final int MAX_SUGGEST = 15;
    private static final int MAX_DEFINITIONS = 3;
    private static final int MAX_RELATED = 6;

    private final ExternalDictionaryApi api;
    private final ApiCalls calls;
    @Nullable
    private Call<List<DatamuseWordDto>> pendingSuggest;

    public ExternalDictionaryRepository(ExternalDictionaryApi api, ApiCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    public static boolean isExternal(VocabularyDto word) {
        return word.id <= 0;
    }

    /** Gợi ý theo tiền tố; hủy lần gọi trước khi người dùng gõ tiếp. */
    public void suggest(String prefix, ApiCalls.Result<List<VocabularyDto>> result) {
        if (pendingSuggest != null) {
            pendingSuggest.cancel();
        }
        pendingSuggest = api.suggest(prefix, MAX_SUGGEST);
        calls.enqueue(pendingSuggest, r -> {
            if (!r.isSuccess()) {
                result.onResult(Resource.error(r.message, r.code));
                return;
            }
            List<VocabularyDto> rows = new ArrayList<>();
            if (r.data != null) {
                for (DatamuseWordDto s : r.data) {
                    if (s.word == null || s.word.trim().isEmpty()) {
                        continue;
                    }
                    VocabularyDto w = new VocabularyDto();
                    w.word = s.word.trim();
                    rows.add(w);
                }
            }
            result.onResult(Resource.success(rows));
        });
    }

    /** Gọi song song định nghĩa và bản dịch; trả kết quả khi cả hai xong, có ít nhất một phần là thành công. */
    public void lookup(String word, ApiCalls.Result<VocabularyDto> result) {
        VocabularyDto out = new VocabularyDto();
        out.word = word;
        Pending pending = new Pending(out, result);
        calls.enqueue(api.entries(word), r -> {
            if (r.isSuccess() && r.data != null && !r.data.isEmpty()) {
                fill(out, r.data);
                pending.found = true;
            } else if (r.code != 404) {
                pending.error = r;
            }
            pending.done();
        });
        calls.enqueue(api.translate(word, "en|vi"), r -> {
            String text = translated(r.data, word);
            if (r.isSuccess() && text != null) {
                out.meaningVi = text;
                pending.found = true;
            }
            pending.done();
        });
    }

    /** Callback của Retrofit chạy trên main thread nên đếm bằng biến thường. */
    private final class Pending {
        final VocabularyDto out;
        final ApiCalls.Result<VocabularyDto> result;
        int left = 2;
        boolean found;
        @Nullable
        Resource<?> error;

        Pending(VocabularyDto out, ApiCalls.Result<VocabularyDto> result) {
            this.out = out;
            this.result = result;
        }

        void done() {
            if (--left > 0) {
                return;
            }
            if (found) {
                result.onResult(Resource.success(out));
            } else if (error != null) {
                result.onResult(Resource.error(error.message, error.code));
            } else {
                result.onResult(Resource.error(calls.string(R.string.dict_external_not_found), 404));
            }
        }
    }

    @Nullable
    private static String translated(@Nullable TranslationDto t, String word) {
        if (t == null || t.responseData == null || t.responseData.translatedText == null) {
            return null;
        }
        String text = t.responseData.translatedText.trim();
        // Hết hạn mức thì MyMemory trả câu cảnh báo thay cho bản dịch; dịch y nguyên nghĩa là không dịch được
        if (text.isEmpty() || text.toUpperCase(Locale.ROOT).startsWith("MYMEMORY WARNING")
                || text.equalsIgnoreCase(word)) {
            return null;
        }
        return text;
    }

    private static void fill(VocabularyDto out, List<FreeDictionaryEntryDto> entries) {
        Set<String> pos = new LinkedHashSet<>();
        Set<String> synonyms = new LinkedHashSet<>();
        Set<String> antonyms = new LinkedHashSet<>();
        StringBuilder definitions = new StringBuilder();
        int count = 0;
        for (FreeDictionaryEntryDto e : entries) {
            if (out.ipa == null) {
                out.ipa = nonEmpty(e.phonetic);
            }
            if (e.phonetics != null) {
                for (FreeDictionaryEntryDto.Phonetic p : e.phonetics) {
                    if (out.ipa == null) {
                        out.ipa = nonEmpty(p.text);
                    }
                    if (out.audioUrl == null) {
                        out.audioUrl = nonEmpty(p.audio);
                    }
                }
            }
            if (e.meanings == null) {
                continue;
            }
            for (FreeDictionaryEntryDto.Meaning m : e.meanings) {
                if (nonEmpty(m.partOfSpeech) != null) {
                    pos.add(m.partOfSpeech.trim());
                }
                addAll(synonyms, m.synonyms);
                addAll(antonyms, m.antonyms);
                if (m.definitions == null) {
                    continue;
                }
                for (FreeDictionaryEntryDto.Definition d : m.definitions) {
                    addAll(synonyms, d.synonyms);
                    addAll(antonyms, d.antonyms);
                    if (out.exampleSentence == null) {
                        out.exampleSentence = nonEmpty(d.example);
                    }
                    if (count < MAX_DEFINITIONS && nonEmpty(d.definition) != null) {
                        if (definitions.length() > 0) {
                            definitions.append('\n');
                        }
                        definitions.append("• ");
                        if (nonEmpty(m.partOfSpeech) != null) {
                            definitions.append('(').append(m.partOfSpeech.trim()).append(") ");
                        }
                        definitions.append(d.definition.trim());
                        count++;
                    }
                }
            }
        }
        out.partOfSpeech = pos.isEmpty() ? null : String.join(", ", pos);
        out.definitionEn = definitions.length() == 0 ? null : definitions.toString();
        out.synonyms = join(synonyms);
        out.antonyms = join(antonyms);
    }

    private static void addAll(Set<String> into, @Nullable List<String> words) {
        if (words == null) {
            return;
        }
        for (String w : words) {
            if (into.size() < MAX_RELATED && nonEmpty(w) != null) {
                into.add(w.trim());
            }
        }
    }

    @Nullable
    private static String join(Set<String> words) {
        return words.isEmpty() ? null : String.join(", ", words);
    }

    @Nullable
    private static String nonEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
