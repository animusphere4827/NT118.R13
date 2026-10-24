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
    private String pending;

    public Speaker(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> {
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

    /** Gọi khi người dùng bấm loa: báo nếu máy không có giọng tiếng Anh. */
    public void warnIfUnavailable() {
        if (!ready) {
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
