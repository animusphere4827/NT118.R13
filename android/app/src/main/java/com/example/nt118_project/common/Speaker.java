package com.example.nt118_project.common;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import com.example.nt118_project.R;

import java.util.Locale;

/** Phát âm tiếng Anh bằng TextToSpeech (Locale.US). Tạo trong onCreate, gọi {@link #shutdown()} ở onDestroy. */
public class Speaker {

    private final Context context;
    private TextToSpeech tts;
    private boolean ready;
    /** Engine đã trả kết quả khởi tạo (thành công hay không). */
    private boolean initialized;
    /** Chỉ báo "không có giọng tiếng Anh" một lần, tránh lặp lại mỗi lần tự lật thẻ. */
    private boolean warned;
    private String pending;

    public Speaker(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> {
            initialized = true;
            if (status != TextToSpeech.SUCCESS || tts == null) {
                return;
            }
            int lang = tts.setLanguage(Locale.US);
            ready = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(0.9f);
            if (ready && pending != null) {
                speak(pending);
                pending = null;
            }
        });
    }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty() || tts == null) {
            return;
        }
        if (!ready) {
            // Engine chưa khởi tạo xong: đọc ngay khi sẵn sàng
            pending = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak");
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Gọi khi người dùng bấm loa: báo một lần nếu máy không có giọng tiếng Anh.
     * Engine còn đang khởi tạo thì không báo, câu sẽ được đọc ngay khi sẵn sàng.
     */
    public void warnIfUnavailable() {
        if (initialized && !ready && !warned) {
            warned = true;
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    public void stop() {
        pending = null;
        if (tts != null && ready) {
            tts.stop();
        }
    }

    public void shutdown() {
        pending = null;
        if (tts != null) {
            if (ready) {
                tts.stop();
            }
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
