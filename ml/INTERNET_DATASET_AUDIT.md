# External quality-dataset audit

The external source is mapped as approved by the project owner:

- `Ripe_frames` -> Class A
- `Overripe_frames` -> Class B
- `Ripe2_frames` -> Class C
- `Rotten_frames` -> excluded

Run the non-destructive audit with:

```powershell
ml\.venv\Scripts\python.exe ml\audit_external_quality_dataset.py
```

The audit treats a numbered frame sequence as one capture group. Adjacent frames
from the same video are not independent fruit samples and must never be divided
between training, validation, and test. The source remains quarantined if any
class lacks three defensible capture groups or if exact cross-class conflicts are
found. Quarantined files are not copied into `raw/` and cannot affect the exported
Android model.

Generated per-file and summary reports are written under
`ml/outputs/external_dataset_audit/` and remain outside Git.

The project owner explicitly approved a training-only override after reviewing
the audit limitation. `import_external_quality_dataset.py` samples every tenth
video frame, preserves each source folder as one capture group, and never makes
these correlated frames eligible for validation or final test metrics.
