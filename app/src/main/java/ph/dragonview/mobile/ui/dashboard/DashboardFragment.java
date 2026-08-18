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
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.databinding.FragmentDashboardBinding;

public final class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentDashboardBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding.swipeRefresh.setOnRefreshListener(this::load);
        binding.viewAnalyticsButton.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.analyticsFragment));
        binding.addInventoryAction.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.inventoryFragment));
        binding.recordHarvestAction.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.harvestFragment));
        binding.schedulePlantAction.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.plantingFragment));
        load();
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
        binding.inventoryValue.setText(summary.getInventoryPieces() + "\nInventory");
        binding.batchesValue.setText(summary.getActiveBatches() + "\nBatches");
        binding.salesValue.setText(summary.getSalesToday() + "\nSales today");
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        binding.revenueValue.setText(php.format(summary.getMonthlyRevenue()) + "\nRevenue");
        binding.plantingValue.setText(summary.getPlantingGroups() + "\nPlant groups");
        binding.classificationValue.setText(summary.getClassificationsToday() + "\nScans today");
    }

    private void bindAnalytics(DashboardData.AnalyticsOverview overview) {
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        binding.todayRevenueValue.setText(php.format(overview.getRevenueToday()));
        binding.todayPiecesValue.setText(String.valueOf(overview.getPiecesSoldToday()));
        binding.todayWeightValue.setText(
                String.format(Locale.US, "%.2f kg", overview.getWeightSoldToday()));

        Double change = overview.getRevenueChangePercent();
        if (change == null) {
            binding.revenueTrendText.setText("No sales baseline from yesterday");
            binding.revenueTrendText.setTextColor(
                    requireContext().getColor(ph.dragonview.mobile.R.color.dragon_green));
        } else {
            binding.revenueTrendText.setText(String.format(
                    Locale.US, "%+.1f%% revenue vs yesterday", change));
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
