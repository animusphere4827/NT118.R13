package com.example.nt118_project;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.databinding.ActivityMainBinding;

/** Activity duy nhất: chứa NavHost, mọi màn hình là Fragment nối với nhau trong nav_graph. */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Server trả 401: phiên đã bị xóa trong AuthInterceptor, đưa người dùng về màn đăng nhập
        App.container().session.sessionExpired().observe(this, event -> {
            if (event.consume() != null) {
                Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show();
                navController().navigate(R.id.login, null,
                        new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
            }
        });
    }

    private NavController navController() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost);
        return host.getNavController();
    }
}
