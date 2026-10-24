package com.example.nt118_project.ui.profile;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.Locale;

/** Hiển thị avatar tròn: ảnh từ URL nếu tải được, ngược lại là chữ cái đầu của họ tên. */
public final class AvatarBinder {

    private AvatarBinder() {
    }

    public static void bind(View frame, TextView initials, ImageView image,
                            @Nullable String fullName, @Nullable String avatarUrl) {
        frame.setClipToOutline(true);
        initials.setText(initials(fullName));
        initials.setVisibility(View.VISIBLE);
        if (!isHttpUrl(avatarUrl)) {
            Glide.with(image).clear(image);
            image.setVisibility(View.GONE);
            return;
        }
        image.setVisibility(View.VISIBLE);
        Glide.with(image)
                .load(avatarUrl)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                @NonNull Target<Drawable> target, boolean isFirstResource) {
                        image.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target,
                                                   @NonNull DataSource dataSource, boolean isFirstResource) {
                        initials.setVisibility(View.INVISIBLE);
                        return false;
                    }
                })
                .into(image);
    }

    /** "Nguyễn Minh Anh" thành "MA": chữ đầu của hai từ cuối. */
    public static String initials(@Nullable String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        String[] words = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, words.length - 2); i < words.length; i++) {
            sb.appendCodePoint(words[i].codePointAt(0));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    public static boolean isHttpUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        return (u.startsWith("https://") || u.startsWith("http://"))
                && android.util.Patterns.WEB_URL.matcher(url.trim()).matches() && url.length() <= 2048;
    }
}
