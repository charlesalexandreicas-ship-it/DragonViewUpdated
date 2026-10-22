package ph.dragonview.mobile.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.navigation.NavController;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.SessionUser;
import ph.dragonview.mobile.databinding.ActivityMainBinding;

/**
 * Hosts DragonView's navigation drawer, primary tabs, and centered scanner action.
 *
 * <p>Learning note: feature screens live in Fragments. This Activity coordinates the
 * shared application shell and keeps its navigation indicators synchronized.</p>
 */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SessionManager session;
    private AppBarConfiguration appBarConfiguration;
    private boolean sessionReceiverRegistered;
    private boolean redirectingToLogin;
    private final BroadcastReceiver sessionExpiredReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (SessionManager.ACTION_SESSION_EXPIRED.equals(intent.getAction())) {
                openLoginForExpiredSession();
            }
        }
    };

    @Override
    /** Creates the shared app shell and connects it to the Navigation component. */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isAuthenticated()) {
            logout();
            return;
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Match the system status bar to the shared berry toolbar so the header reads as one surface.
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.botanical_header));
        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility()
                        & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.dashboardFragment) {
                return returnToDashboard(controller);
            }
            return NavigationUI.onNavDestinationSelected(item, controller);
        });
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
        if (session.getActiveWorkspaceId().isEmpty()) {
            LocalRepository.get(this).ensureActiveWorkspace(new LocalRepository.Callback<>() {
                @Override public void onSuccess(Void ignored) {
                    if (!isFinishing() && !isDestroyed()) recreate();
                }
                @Override public void onError(String message) {
                    // The workspace screen surfaces the actionable backend error.
                }
            });
        }
    }

    /** Starts listening for authentication expiry while the Activity is visible. */
    @Override protected void onStart() {
        super.onStart();
        if (!sessionReceiverRegistered) {
            ContextCompat.registerReceiver(this, sessionExpiredReceiver,
                    new IntentFilter(SessionManager.ACTION_SESSION_EXPIRED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            sessionReceiverRegistered = true;
        }
    }

    /** Rechecks authentication after returning from another screen or application. */
    @Override protected void onResume() {
        super.onResume();
        if (session != null && binding != null && !session.isAuthenticated()) {
            openLoginForExpiredSession();
        }
    }

    /** Unregisters the receiver to avoid retaining an inactive Activity instance. */
    @Override protected void onStop() {
        if (sessionReceiverRegistered) {
            unregisterReceiver(sessionExpiredReceiver);
            sessionReceiverRegistered = false;
        }
        super.onStop();
    }

    /** Opens a primary tab through the same navigation path used by the bottom bar. */
    public void navigateToPrimaryDestination(int destinationId) {
        binding.bottomNavigation.setSelectedItemId(destinationId);
    }

    /** Returns to the existing dashboard destination instead of stacking duplicates. */
    private static boolean returnToDashboard(NavController controller) {
        if (controller.getCurrentDestination() != null
                && controller.getCurrentDestination().getId() == R.id.dashboardFragment) {
            return true;
        }
        if (!controller.popBackStack(R.id.dashboardFragment, false)) {
            controller.navigate(R.id.dashboardFragment);
        }
        return true;
    }

    /** Binds account information, role visibility, drawer actions, and tab state. */
    private void configureDrawer(NavController controller) {
        SessionUser user = session.getUser();
        if (user != null && binding.navigationView.getHeaderCount() > 0) {
            View header = binding.navigationView.getHeaderView(0);
            TextView userText = header.findViewById(R.id.drawerUserText);
            userText.setText(user.getDisplayName());
            TextView workspaceText = header.findViewById(R.id.drawerWorkspaceText);
            TextView roleText = header.findViewById(R.id.drawerRoleText);
            workspaceText.setText(session.getActiveWorkspaceName().isEmpty()
                    ? "No workspace selected" : session.getActiveWorkspaceName());
            roleText.setText(session.isWorkspaceOwner() ? "Workspace owner"
                    : session.isWorkspaceFarmer() ? "Farmer" : "Workspace loading");
        }

        boolean owner = session.isWorkspaceOwner();
        binding.navigationView.getMenu().findItem(R.id.recentlyRemovedFragment)
                .setVisible(owner);
        binding.bottomNavigation.getMenu().findItem(R.id.analyticsFragment)
                .setVisible(owner);

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
            boolean fullScreenDestination = destinationId == R.id.settingsFragment
                    || destinationId == R.id.workspaceFragment;
            binding.bottomNavigation.setVisibility(
                    fullScreenDestination ? View.GONE : View.VISIBLE);
            binding.scannerButton.setVisibility(
                    fullScreenDestination ? View.GONE : View.VISIBLE);
        });
    }

    /** Maps child screens back to the primary tab that conceptually owns them. */
    private static int bottomDestinationFor(int destinationId) {
        if (destinationId == R.id.harvestFragment
                || destinationId == R.id.inventoryDetailsFragment) {
            return R.id.inventoryFragment;
        }
        if (destinationId == R.id.plantingDetailsFragment) {
            return R.id.plantingFragment;
        }
        if (destinationId == R.id.historicalRecordsFragment
                || destinationId == R.id.historicalCaptureFragment
                || destinationId == R.id.historicalEditorFragment) {
            return R.id.analyticsFragment;
        }
        if (destinationId == R.id.salesFragment) {
            return R.id.dashboardFragment;
        }
        if (destinationId == R.id.recentlyRemovedFragment) {
            return R.id.inventoryFragment;
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
    /** Gives the toolbar Up button the same behavior as the navigation graph. */
    public boolean onSupportNavigateUp() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        return host != null && NavigationUI.navigateUp(
                host.getNavController(), appBarConfiguration);
    }

    /** Clears local authentication, opens Login, and removes authenticated screens. */
    public void signOut() {
        LocalRepository.get(this).signOut();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    /** Kept as the unauthenticated-startup entry point for the common sign-out flow. */
    private void logout() {
        signOut();
    }

    /** Redirects once when a token expires and explains why the user must sign in. */
    private void openLoginForExpiredSession() {
        if (redirectingToLogin || isFinishing() || isDestroyed()) return;
        redirectingToLogin = true;
        startActivity(new Intent(this, LoginActivity.class)
                .putExtra(LoginActivity.EXTRA_AUTH_MESSAGE,
                        "Your session expired. Sign in again to continue.")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
