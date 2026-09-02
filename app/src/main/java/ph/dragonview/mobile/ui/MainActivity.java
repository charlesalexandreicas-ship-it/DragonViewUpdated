package ph.dragonview.mobile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.navigation.NavController;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.databinding.ActivityMainBinding;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SessionManager session;
    private AppBarConfiguration appBarConfiguration;

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
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.dashboardFragment,
                R.id.inventoryFragment,
                R.id.scannerFragment,
                R.id.plantingFragment,
                R.id.analyticsFragment,
                R.id.salesFragment,
                R.id.recentlyRemovedFragment,
                R.id.settingsFragment)
                .setOpenableLayout(binding.drawerLayout)
                .build();

        NavigationUI.setupWithNavController(binding.bottomNavigation, controller);
        NavigationUI.setupActionBarWithNavController(this, controller, appBarConfiguration);
        configureDrawer(controller);
        binding.bottomNavigation.setOnItemReselectedListener(item -> {
            if (controller.getCurrentDestination() != null
                    && controller.getCurrentDestination().getId() == item.getItemId()) {
                return;
            }
            if (!controller.popBackStack(item.getItemId(), false)) {
                controller.navigate(item.getItemId());
            }
        });
        binding.scannerButton.setOnClickListener(view ->
                binding.bottomNavigation.setSelectedItemId(R.id.scannerFragment));
    }

    private void configureDrawer(NavController controller) {
        SessionUser user = session.getUser();
        if (user != null && binding.navigationView.getHeaderCount() > 0) {
            TextView userText = binding.navigationView.getHeaderView(0)
                    .findViewById(R.id.drawerUserText);
            userText.setText(user.getDisplayName());
        }

        binding.navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                signOut();
                return true;
            }
            boolean handled = NavigationUI.onNavDestinationSelected(item, controller);
            if (handled) {
                item.setChecked(true);
                binding.drawerLayout.closeDrawer(GravityCompat.START);
            }
            return handled;
        });

        controller.addOnDestinationChangedListener((navController, destination, arguments) -> {
            int destinationId = destination.getId();
            if (appBarConfiguration.getTopLevelDestinations().contains(destinationId)) {
                binding.toolbar.setNavigationIcon(R.drawable.icon_streamline_menu);
            }
            if (binding.navigationView.getMenu().findItem(destinationId) != null) {
                binding.navigationView.setCheckedItem(destinationId);
            }
            int bottomDestinationId = bottomDestinationFor(destinationId);
            if (bottomDestinationId != View.NO_ID) {
                binding.bottomNavigation.getMenu()
                        .findItem(bottomDestinationId)
                        .setChecked(true);
            }
            boolean settingsScreen = destinationId == R.id.settingsFragment;
            binding.bottomNavigation.setVisibility(settingsScreen ? View.GONE : View.VISIBLE);
            binding.scannerButton.setVisibility(settingsScreen ? View.GONE : View.VISIBLE);
        });
    }

    private static int bottomDestinationFor(int destinationId) {
        if (destinationId == R.id.harvestFragment
                || destinationId == R.id.inventoryDetailsFragment) {
            return R.id.inventoryFragment;
        }
        if (destinationId == R.id.plantingDetailsFragment) {
            return R.id.plantingFragment;
        }
        if (destinationId == R.id.dashboardFragment
                || destinationId == R.id.inventoryFragment
                || destinationId == R.id.scannerFragment
                || destinationId == R.id.plantingFragment
                || destinationId == R.id.analyticsFragment) {
            return destinationId;
        }
        return View.NO_ID;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        return host != null && NavigationUI.navigateUp(
                host.getNavController(), appBarConfiguration);
    }

    public void signOut() {
        if (session != null) session.clear();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void logout() {
        signOut();
    }
}
