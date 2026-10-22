package ph.dragonview.mobile.ui.planting;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.data.model.PropagationMethod;

public final class PlantingStagePresentationTest {
    @Test public void stageCountComesFromPropagationMethodNotMockup() {
        PlantingStagePresentation cutting = PlantingStagePresentation.forStage(
                PropagationMethod.STEM_CUTTING, PlantingStage.PLANTED);
        PlantingStagePresentation seed = PlantingStagePresentation.forStage(
                PropagationMethod.SEED, PlantingStage.SEED_SOWING);
        assertEquals(PlantingStage.forMethod(PropagationMethod.STEM_CUTTING).size(),
                cutting.stageCount());
        assertEquals(PlantingStage.forMethod(PropagationMethod.SEED).size(),
                seed.stageCount());
        assertEquals(1, cutting.stageNumber());
        assertEquals(PlantingStage.ESTABLISHMENT, cutting.nextStage());
    }

    @Test public void finalStageDoesNotInventAnotherStep() {
        PlantingStagePresentation finalStage = PlantingStagePresentation.forStage(
                PropagationMethod.STEM_CUTTING, PlantingStage.READY_FOR_HARVEST);
        assertEquals(finalStage.stageCount(), finalStage.stageNumber());
        assertEquals(null, finalStage.nextStage());
    }
}
