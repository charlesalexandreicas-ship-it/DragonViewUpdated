package ph.dragonview.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.databinding.ActivityMainBinding;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isAuthenticated()) {
            logout();
            return;
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host == null) throw new IllegalStateException("Navigation host is missing.");
        NavController controller = host.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNavigation, controller);
        NavigationUI.setupActionBarWithNavController(this, controller);
        binding.scannerButton.setOnClickListener(view ->
                binding.bottomNavigation.setSelectedItemId(R.id.scannerFragment));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_sales) {
            NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host);
            if (host != null) host.getNavController().navigate(R.id.salesFragment);
            return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        return host != null && host.getNavController().navigateUp();
    }

    private void logout() {
        if (session != null) session.clear();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
