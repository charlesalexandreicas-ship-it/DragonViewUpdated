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
import java.util.List;

import ph.dragonview.mobile.data.model.DashboardData;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.databinding.FragmentDashboardBinding;
import ph.dragonview.mobile.ui.MainActivity;

/** Displays the farm overview and routes each quick action to its owning feature. */
public final class DashboardFragment extends Fragment {
    private FragmentDashboardBinding binding;

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
        binding.viewSalesAction.setOnClickListener(clicked ->
                Navigation.findNavController(clicked)
                        .navigate(ph.dragonview.mobile.R.id.salesFragment));
        binding.viewInventoryAction.setOnClickListener(clicked -> navigateToPrimaryDestination(
                ph.dragonview.mobile.R.id.inventoryFragment));
        binding.viewPlantGuidanceAction.setOnClickListener(clicked -> navigateToPrimaryDestination(
                ph.dragonview.mobile.R.id.plantingFragment));
        binding.gradeOverviewToggle.setOnClickListener(clicked -> {
            boolean opening = binding.gradeOverviewContent.getVisibility() != View.VISIBLE;
            binding.gradeOverviewContent.setVisibility(opening ? View.VISIBLE : View.GONE);
            binding.gradeOverviewToggle.setText(opening
                    ? "Hide Grade Overview  ▴" : "View Grade Overview  ▾");
        });
        load();
    }

    private void navigateToPrimaryDestination(int destinationId) {
        ((MainActivity) requireActivity()).navigateToPrimaryDestination(destinationId);
    }

    /** Loads overview totals and stock grades from their existing data sources. */
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
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        binding.swipeRefresh.setRefreshing(false);
                        showError();
                    }
                });
        LocalRepository.get(requireContext()).inventory(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<InventoryLot> lots) {
                if (binding == null) return;
                int a = 0, b = 0, c = 0;
                for (InventoryLot lot : lots) {
                    if (lot.getGrade() == null) continue;
                    switch (lot.getGrade().trim().toUpperCase(Locale.US)) {
                        case "A": a += lot.getAvailablePieces(); break;
                        case "B": b += lot.getAvailablePieces(); break;
                        case "C": c += lot.getAvailablePieces(); break;
                        default: break;
                    }
                }
                binding.gradeAText.setText("Grade A  " + a + " pcs");
                binding.gradeBText.setText("Grade B  " + b + " pcs");
                binding.gradeCText.setText("Grade C  " + c + " pcs");
            }
            @Override public void onError(String message) {
                if (binding == null) return;
                binding.gradeAText.setText("Grades unavailable. Pull to retry.");
                binding.gradeBText.setText("");
                binding.gradeCText.setText("");
            }
        });
    }

    /** Binds only the four overview values approved for Home. */
    private void bind(DashboardData.Summary summary) {
        binding.batchesValue.setText(String.valueOf(summary.getActiveBatches()));
        binding.inventoryValue.setText(String.valueOf(summary.getInventoryPieces()));
        NumberFormat php = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        binding.monthlyRevenueValue.setText(php.format(summary.getMonthlyRevenue()));
        binding.plantingValue.setText(String.valueOf(summary.getPlantingGroups()));
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
