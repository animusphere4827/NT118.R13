package com.example.nt118_project.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.ui.auth.GoogleSignInHelper;
import com.example.nt118_project.ui.notification.BellBadge;
import com.example.nt118_project.ui.common.BottomNavHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment dùng chung cho mọi màn hình. Layout được chọn qua argument "layout" trong nav_graph,
 * còn flow giữa các màn hình được khai báo ngay trên layout bằng android:tag:
 * <pre>
 *   nav:&lt;màn_hình&gt;              mở màn hình đó
 *   back                         quay lại
 *   password:&lt;id&gt;               ẩn/hiện mật khẩu của EditText
 *   pick:&lt;nhóm&gt;                 chọn một view trong nhóm (đáp án, chip...)
 *   show:&lt;id&gt; / hide:&lt;id&gt;       hiện / ẩn view (ví dụ bottom sheet tra nghĩa)
 *   check:&lt;nhóm&gt;:&lt;id đúng&gt;:&lt;màn đúng&gt;:&lt;màn sai&gt;   chấm đáp án rồi chuyển màn
 *   logout                       đăng xuất rồi về màn đăng nhập
 *   navtab:&lt;tab&gt;                (đặt trên bottomNavHost) tab đang chọn
 * </pre>
 */
public class ScreenFragment extends Fragment {

    @LayoutRes
    private int layoutRes;
    private final List<View> tagged = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        layoutRes = args != null ? args.getInt("layout") : 0;
        return inflater.inflate(layoutRes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        tagged.clear();
        collect(view);
        for (View v : tagged) {
            bind(view, nav, v, (String) v.getTag());
        }
        // Trang chủ: badge số thông báo chưa đọc trên chuông
        View bell = view.findViewById(R.id.btnBell);
        if (bell != null) {
            BellBadge.attach(this, bell);
        }
    }

    private void collect(View v) {
        if (v.getTag() instanceof String) {
            String tag = (String) v.getTag();
            if (tag.endsWith("*")) {
                v.setTag(tag.substring(0, tag.length() - 1));
                v.setSelected(true);
            }
            tagged.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i));
            }
        }
    }

    private void bind(View root, NavController nav, View v, String tag) {
        String[] p = tag.split(":");
        switch (p[0]) {
            case "nav":
                v.setOnClickListener(x -> go(nav, p[1]));
                break;
            case "back":
                v.setOnClickListener(x -> nav.popBackStack());
                break;
            case "password":
                v.setOnClickListener(x -> togglePassword(root.findViewById(id(p[1]))));
                break;
            case "pick":
                v.setOnClickListener(x -> pick(p[1], x));
                break;
            case "show":
                v.setOnClickListener(x -> root.findViewById(id(p[1])).setVisibility(View.VISIBLE));
                break;
            case "hide":
                v.setOnClickListener(x -> root.findViewById(id(p[1])).setVisibility(View.GONE));
                break;
            case "check":
                v.setOnClickListener(x -> check(nav, p));
                break;
            case "logout":
                v.setOnClickListener(x -> logout(nav, x));
                break;
            case "navtab":
                BottomNavHelper.bind(root, nav, tabId(p[1]));
                break;
            default:
                break;
        }
    }

    /** POST /auth/logout, xóa token local và trạng thái Credential Manager, rồi về màn đăng nhập. */
    private void logout(NavController nav, View button) {
        button.setEnabled(false);
        android.content.Context app = requireContext().getApplicationContext();
        App.container().authRepository.logout(() -> {
            GoogleSignInHelper.clearState(app);
            android.widget.Toast.makeText(app, R.string.auth_logged_out, android.widget.Toast.LENGTH_SHORT).show();
            if (isAdded()) {
                go(nav, "login");
            }
        });
    }

    @IdRes
    private int id(String name) {
        return getResources().getIdentifier(name, "id", requireContext().getPackageName());
    }

    @IdRes
    private static int tabId(String name) {
        switch (name) {
            case "learn_path":
                return R.id.tabLearn;
            case "review_decks":
                return R.id.tabReview;
            case "exam_list":
                return R.id.tabExam;
            case "profile":
                return R.id.tabProfile;
            default:
                return R.id.tabHome;
        }
    }

    private void go(NavController nav, String dest) {
        int destId = id(dest);
        if (destId == 0) {
            return;
        }
        if (destId != R.id.login && destId != R.id.home && nav.popBackStack(destId, false)) {
            return;
        }
        NavOptions.Builder options = new NavOptions.Builder();
        if (destId == R.id.login) {
            options.setPopUpTo(R.id.nav_graph, true);
        } else if (destId == R.id.home) {
            boolean fromAuth = nav.getCurrentDestination() != null
                    && (nav.getCurrentDestination().getId() == R.id.login
                    || nav.getCurrentDestination().getId() == R.id.register);
            options.setPopUpTo(fromAuth ? R.id.nav_graph : R.id.home, true);
        }
        nav.navigate(destId, null, options.build());
    }

    private void togglePassword(View view) {
        if (!(view instanceof EditText)) {
            return;
        }
        EditText edit = (EditText) view;
        boolean hidden = (edit.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0;
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (hidden
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        edit.setSelection(edit.length());
    }

    private void pick(String group, View picked) {
        for (View v : tagged) {
            if (("pick:" + group).equals(v.getTag())) {
                boolean on = v == picked;
                v.setSelected(on);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(ContextCompat.getColor(requireContext(),
                            on ? R.color.app_primary : R.color.app_text_secondary));
                }
            }
        }
    }

    /** check:&lt;nhóm&gt;:&lt;id đáp án đúng&gt;:&lt;màn nếu đúng&gt;:&lt;màn nếu sai&gt; */
    private void check(NavController nav, String[] p) {
        View chosen = null;
        for (View v : tagged) {
            if (("pick:" + p[1]).equals(v.getTag()) && v.isSelected()) {
                chosen = v;
            }
        }
        if (chosen == null) {
            return;
        }
        go(nav, chosen.getId() == id(p[2]) ? p[3] : p[4]);
    }
}
