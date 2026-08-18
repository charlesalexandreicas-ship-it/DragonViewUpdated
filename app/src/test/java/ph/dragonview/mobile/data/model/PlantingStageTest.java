package ph.dragonview.mobile.data.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PlantingStageTest {
    @Test
    public void invalidStoredStageFallsBackSafely() {
        assertEquals(PlantingStage.PLANTED, PlantingStage.fromCode(null));
        assertEquals(PlantingStage.PLANTED, PlantingStage.fromCode("unknown"));
    }

    @Test
    public void ageSuggestionStopsBeforeFarmDependentMilestones() {
        assertEquals(PlantingStage.PLANTED, PlantingStage.suggestedForAge(0));
        assertEquals(PlantingStage.ESTABLISHMENT, PlantingStage.suggestedForAge(1));
        assertEquals(PlantingStage.ROOTING_AND_BUD, PlantingStage.suggestedForAge(15));
        assertEquals(PlantingStage.SHOOT_DEVELOPMENT, PlantingStage.suggestedForAge(31));
        assertEquals(PlantingStage.TRELLIS_TRAINING, PlantingStage.suggestedForAge(61));
    }

    @Test
    public void finalStageDoesNotOverflow() {
        assertEquals(PlantingStage.ESTABLISHMENT, PlantingStage.PLANTED.next());
        assertEquals(PlantingStage.HARVESTED, PlantingStage.HARVESTED.next());
    }
}
