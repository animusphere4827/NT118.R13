package com.example.nt118_project.ui.notification;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.NotificationDto;
import com.example.nt118_project.databinding.ItemNotificationBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Danh sách thông báo chia nhóm "Hôm nay" và "Trước đó". Phần tử là String (tiêu đề nhóm) hoặc NotificationDto. */
public class NotificationAdapter extends ListAdapter<Object, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface OnClick {
        void onClick(NotificationDto n);
    }

    private final OnClick onClick;

    public NotificationAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<Object>() {
            @Override
            public boolean areItemsTheSame(@NonNull Object a, @NonNull Object b) {
                if (a instanceof NotificationDto && b instanceof NotificationDto) {
                    return ((NotificationDto) a).id == ((NotificationDto) b).id;
                }
                return a.equals(b);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Object a, @NonNull Object b) {
                if (a instanceof NotificationDto && b instanceof NotificationDto) {
                    NotificationDto x = (NotificationDto) a;
                    NotificationDto y = (NotificationDto) b;
                    return x.isRead == y.isRead && Objects.equals(x.title, y.title) && Objects.equals(x.body, y.body);
                }
                return a.equals(b);
            }
        });
        this.onClick = onClick;
    }

    /** Chèn tiêu đề nhóm trước thông báo đầu tiên của hôm nay và của các ngày trước. */
    public static List<Object> group(Context c, List<NotificationDto> rows) {
        List<Object> out = new ArrayList<>();
        Boolean lastToday = null;
        for (NotificationDto n : rows) {
            boolean today = isToday(CardDto.parse(n.createdAt));
            if (lastToday == null || lastToday != today) {
                out.add(c.getString(today ? R.string.notif_group_today : R.string.notif_group_earlier));
                lastToday = today;
            }
            out.add(n);
        }
        return out;
    }

    @Nullable
    public NotificationDto notificationAt(int position) {
        Object o = getItem(position);
        return o instanceof NotificationDto ? (NotificationDto) o : null;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof NotificationDto ? TYPE_ITEM : TYPE_HEADER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(inflater.inflate(R.layout.item_notification_header, parent, false)) {
            };
        }
        return new Holder(ItemNotificationBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = getItem(position);
        if (!(holder instanceof Holder)) {
            ((TextView) holder.itemView).setText((String) item);
            return;
        }
        NotificationDto n = (NotificationDto) item;
        ItemNotificationBinding b = ((Holder) holder).b;
        Context c = holder.itemView.getContext();
        b.txtTitle.setText(n.title);
        b.txtBody.setText(n.body);
        b.txtBody.setVisibility(n.body == null || n.body.isEmpty() ? View.GONE : View.VISIBLE);
        b.txtTime.setText(formatTime(c, CardDto.parse(n.createdAt)));
        b.dotUnread.setVisibility(n.isRead ? View.INVISIBLE : View.VISIBLE);
        b.imgIcon.setImageResource(iconFor(n.type));
        holder.itemView.setAlpha(n.isRead ? 0.75f : 1f);
        holder.itemView.setOnClickListener(v -> onClick.onClick(n));
    }

    @DrawableRes
    static int iconFor(@Nullable String type) {
        if (type == null) {
            return R.drawable.ic_bell;
        }
        switch (type) {
            case "STUDY_REMINDER":
                return R.drawable.ic_layers;
            case "ACHIEVEMENT":
                return R.drawable.ic_award;
            case "RESULT":
                return R.drawable.ic_trending_up;
            case "NEW_CONTENT":
                return R.drawable.ic_file_text;
            default:
                return R.drawable.ic_bell;
        }
    }

    /** Hôm nay: "08:30"; hôm qua: "Hôm qua, 18:20"; còn lại: "06/10, 20:15". */
    static String formatTime(Context c, @Nullable Date date) {
        if (date == null) {
            return "";
        }
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        if (isToday(date)) {
            return time;
        }
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(date, y)) {
            return c.getString(R.string.notif_yesterday, time);
        }
        return new SimpleDateFormat("dd/MM, HH:mm", Locale.getDefault()).format(date);
    }

    private static boolean isToday(@Nullable Date date) {
        return date != null && sameDay(date, Calendar.getInstance());
    }

    private static boolean sameDay(Date date, Calendar day) {
        Calendar d = Calendar.getInstance();
        d.setTime(date);
        return d.get(Calendar.YEAR) == day.get(Calendar.YEAR) && d.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;

        Holder(ItemNotificationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
