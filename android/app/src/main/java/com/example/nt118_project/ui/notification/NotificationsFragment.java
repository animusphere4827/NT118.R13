package com.example.nt118_project.ui.notification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nt118_project.R;
import com.example.nt118_project.ui.common.StateView;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.NotificationDto;
import com.example.nt118_project.databinding.FragmentNotificationsBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/** Thông báo: chạm để đọc và mở màn liên quan, vuốt trái để xóa, nút đọc tất cả, lọc chưa đọc. */
public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private NotificationsViewModel viewModel;
    private NotificationAdapter adapter;
    private StateView stateView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        stateView = new StateView(binding.state);

        adapter = new NotificationAdapter(n -> {
            viewModel.markRead(n);
            int dest = destinationFor(n);
            if (dest != 0) {
                Bundle args = new Bundle();
                if (dest == R.id.exam_result && n.data != null && n.data.has("attempt_id")
                        && n.data.get("attempt_id").isJsonPrimitive()
                        && n.data.getAsJsonPrimitive("attempt_id").isNumber()) {
                    args.putLong("attemptId", n.data.get("attempt_id").getAsLong());
                    args.putString("kind", "mock");
                }
                nav.navigate(dest, args);
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        binding.rvNotifications.setLayoutManager(lm);
        binding.rvNotifications.setAdapter(adapter);
        binding.rvNotifications.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                // Tiêu đề nhóm không vuốt được
                return adapter.notificationAt(vh.getBindingAdapterPosition()) == null ? 0 : super.getSwipeDirs(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
                                  @NonNull RecyclerView.ViewHolder b) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                NotificationDto n = adapter.notificationAt(vh.getBindingAdapterPosition());
                if (n != null) {
                    viewModel.delete(n);
                    Snackbar.make(binding.getRoot(), R.string.notif_deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.notif_undo, v -> viewModel.undoDelete(n))
                            .addCallback(new Snackbar.Callback() {
                                @Override
                                public void onDismissed(Snackbar bar, int event) {
                                    if (event != DISMISS_EVENT_ACTION) {
                                        viewModel.confirmDelete(n);
                                    }
                                }
                            })
                            .show();
                }
            }
        }).attachToRecyclerView(binding.rvNotifications);

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnReadAll.setOnClickListener(v -> viewModel.readAll());
        binding.txtUnread.setOnClickListener(v -> viewModel.toggleUnreadOnly());

        viewModel.unreadCount().observe(getViewLifecycleOwner(), n -> showUnread(n == null ? 0 : n));
        viewModel.items().observe(getViewLifecycleOwner(), state -> render(state, nav));
        viewModel.message().observe(getViewLifecycleOwner(), e -> {
            String m = e.consume();
            if (m != null) {
                Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.reload();
    }

    private void render(@Nullable Resource<List<NotificationDto>> state, NavController nav) {
        if (state == null) {
            return;
        }
        if (state.isLoading()) {
            stateView.loading();
            refreshUnreadLabel();
            return;
        }
        if (state.isError()) {
            stateView.error(state.message, viewModel::reload);
            refreshUnreadLabel();
            return;
        }
        List<NotificationDto> rows = state.data;
        if (rows == null || rows.isEmpty()) {
            adapter.submitList(null);
            if (viewModel.isUnreadOnly()) {
                stateView.empty(R.drawable.ic_check_check, getString(R.string.notif_no_unread), null,
                        getString(R.string.notif_show_all), viewModel::toggleUnreadOnly);
                refreshUnreadLabel();
            } else if (viewModel.hasPendingDeletes()) {
                // Vừa xóa thông báo cuối: ở lại màn này để còn bấm Hoàn tác
                stateView.empty(R.drawable.ic_bell, getString(R.string.notifications_empty_01),
                        getString(R.string.notifications_empty_02), null, null);
                refreshUnreadLabel();
            } else {
                // Chưa có thông báo nào: thay bằng màn trống theo thiết kế
                nav.navigate(R.id.notifications_empty, null,
                        new NavOptions.Builder().setPopUpTo(R.id.notifications, true).build());
            }
            return;
        }
        stateView.hide();
        refreshUnreadLabel();
        adapter.submitList(NotificationAdapter.group(requireContext(), rows));
    }

    private void showUnread(int n) {
        binding.txtUnread.setText(viewModel.isUnreadOnly()
                ? getString(R.string.notif_show_all)
                : n > 0 ? getString(R.string.notif_unread_count, n) : getString(R.string.notif_all_read));
        binding.btnReadAll.setEnabled(n > 0);
        binding.btnReadAll.setAlpha(n > 0 ? 1f : 0.4f);
    }

    private void refreshUnreadLabel() {
        Integer n = viewModel.unreadCount().getValue();
        showUnread(n == null ? 0 : n);
    }

    /** Chọn màn mở khi chạm thông báo, theo data.screen hoặc loại thông báo. */
    private static int destinationFor(NotificationDto n) {
        String screen = n.screen();
        if ("mock_result".equals(screen)) {
            return R.id.exam_result;
        }
        if (n.type == null) {
            return 0;
        }
        switch (n.type) {
            case "STUDY_REMINDER":
                return R.id.review_decks;
            case "ACHIEVEMENT":
                return R.id.badges;
            case "NEW_CONTENT":
                return R.id.reading_list;
            default:
                return 0;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
