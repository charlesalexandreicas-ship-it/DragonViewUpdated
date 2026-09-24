package ph.dragonview.mobile.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.text.NumberFormat;
import java.util.Locale;

import ph.dragonview.mobile.data.model.DashboardData;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.databinding.FragmentDashboardBinding;
import ph.dragonview.mobile.ui.MainActivity;

public final class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;
    private int salesToday;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentDashboardBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        boolean owner = new SessionManager(requireContext()).isWorkspaceOwner();
        binding.viewAnalyticsButton.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.viewAnalyticsButton.setOnClickListener(clicked -> navigateToPrimaryDestination(
                ph.dragonview.mobile.R.id.analyticsFragment));
        binding.recordHarvestAction.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.harvestFragment));
        binding.viewInventoryAction.setOnClickListener(clicked -> navigateToPrimaryDestination(
                ph.dragonview.mobile.R.id.inventoryFragment));
        binding.recordPlantingAction.setOnClickListener(clicked -> navigateToPrimaryDestination(
                ph.dragonview.mobile.R.id.plantingFragment));
        load();
    }

    private void navigateToPrimaryDestination(int destinationId) {
        ((MainActivity) requireActivity()).navigateToPrimaryDestination(destinationId);
    }

    private void load() {
        binding.swipeRefresh.setRefreshing(true);
        binding.errorText.setVisibility(View.GONE);
        LocalRepository.get(requireContext()).dashboard(
                new LocalRepository.Callback<>() {
                    @Override
                    public void onSuccess(DashboardData data) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        bind(data.getSummary());
                        bindAnalytics(data.getAnalyticsOverview());
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        showError();
                    }
                });
    }

    private void bind(DashboardData.Summary summary) {
        binding.inventoryValue.setText(String.valueOf(summary.getInventoryPieces()));
        binding.inventoryContextText.setText("Current workspace");
        salesToday = summary.getSalesToday();
        binding.plantingValue.setText(String.valueOf(summary.getPlantingGroups()));
        binding.classificationValue.setText(String.valueOf(summary.getClassificationsToday()));
    }

    private void bindAnalytics(DashboardData.AnalyticsOverview overview) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        binding.todayRevenueValue.setText(php.format(overview.getRevenueToday()));
        binding.salesContextText.setText(String.format(
                Locale.US, "%d sales · %.2f kg",
                salesToday, overview.getWeightSoldToday()));

        Double change = overview.getRevenueChangePercent();
        if (change == null) {
            binding.revenueTrendText.setText("—");
            binding.revenueTrendText.setTextColor(
                    requireContext().getColor(ph.dragonview.mobile.R.color.dragon_green));
        } else {
            binding.revenueTrendText.setText(String.format(Locale.US, "%+.1f%%", change));
            binding.revenueTrendText.setTextColor(requireContext().getColor(
                    change >= 0
                            ? ph.dragonview.mobile.R.color.dragon_green
                            : ph.dragonview.mobile.R.color.dragon_error));
        }
    }

    private void showError() {
        binding.errorText.setVisibility(View.VISIBLE);
        binding.errorText.setText("Dashboard data could not be loaded. Pull down to retry.");
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
