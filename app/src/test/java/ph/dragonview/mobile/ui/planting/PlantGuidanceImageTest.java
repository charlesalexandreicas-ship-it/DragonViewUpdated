package ph.dragonview.mobile.ui.planting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import ph.dragonview.mobile.data.model.PlantingStage;

public final class PlantGuidanceImageTest {
    @Test public void unverifiedAndUnmappedStagesHaveNoReferenceImage() {
        assertEquals(0, PlantGuidanceContent.imageResource(PlantingStage.SHOOT_DEVELOPMENT));
        assertEquals(0, PlantGuidanceContent.imageResource(PlantingStage.CANOPY_DEVELOPMENT));
        assertEquals(0, PlantGuidanceContent.imageResource(PlantingStage.SEED_SOWING));
    }

    @Test public void verifiedStagesHaveDistinctReferenceImages() {
        int planted = PlantGuidanceContent.imageResource(PlantingStage.PLANTED);
        int bud = PlantGuidanceContent.imageResource(PlantingStage.FLOWER_BUD);
        int flower = PlantGuidanceContent.imageResource(PlantingStage.FLOWERING);
        int fruitSet = PlantGuidanceContent.imageResource(PlantingStage.FRUIT_SET);
        int mature = PlantGuidanceContent.imageResource(PlantingStage.FRUIT_MATURATION);
        int harvested = PlantGuidanceContent.imageResource(PlantingStage.HARVESTED);
        assertNotEquals(0, planted);
        assertNotEquals(planted, bud);
        assertNotEquals(bud, flower);
        assertNotEquals(flower, fruitSet);
        assertNotEquals(fruitSet, mature);
        assertNotEquals(mature, harvested);
    }
}
