package ph.dragonview.mobile.ui.planting;

import java.util.List;

import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PropagationMethod;

/** A display-only stage snapshot derived from the record's propagation method. */
final class PlantingStagePresentation {
    private final int stageNumber;
    private final int stageCount;
    private final PlantingStage nextStage;

    private PlantingStagePresentation(int stageNumber, int stageCount,
                                      PlantingStage nextStage) {
        this.stageNumber = stageNumber;
        this.stageCount = stageCount;
        this.nextStage = nextStage;
    }

    static PlantingStagePresentation forStage(PropagationMethod method, PlantingStage current) {
        List<PlantingStage> stages = PlantingStage.forMethod(method);
        int index = Math.max(0, stages.indexOf(current));
        PlantingStage next = index + 1 < stages.size() ? stages.get(index + 1) : null;
        return new PlantingStagePresentation(index + 1, stages.size(), next);
    }

    int stageNumber() { return stageNumber; }
    int stageCount() { return stageCount; }
    PlantingStage nextStage() { return nextStage; }
}
