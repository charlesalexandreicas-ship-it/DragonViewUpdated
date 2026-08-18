package ph.dragonview.mobile.ui.scanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import ph.dragonview.mobile.databinding.FragmentScannerBinding;

public final class ScannerFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        FragmentScannerBinding binding = FragmentScannerBinding.inflate(inflater, parent, false);
        View.OnClickListener deferred = view -> Snackbar.make(view,
                "MobileNetV2 model integration is deferred.", Snackbar.LENGTH_LONG).show();
        binding.uploadButton.setOnClickListener(deferred);
        binding.captureButton.setOnClickListener(deferred);
        binding.retakeButton.setOnClickListener(deferred);
        return binding.getRoot();
    }
}

