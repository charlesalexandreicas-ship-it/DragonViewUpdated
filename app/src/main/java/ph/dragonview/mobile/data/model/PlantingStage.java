package ph.dragonview.mobile.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public enum PlantingStage {
    SEED_SOWING("Seed sowing", "Day 0", 4, seed(),
            "Seeds are placed in the nursery medium and the section-level record begins.",
            "No cactus growth is visible yet; the nursery area and labels should remain undisturbed.",
            "• Protect the nursery area from disturbance.\n• Follow the farm's validated moisture and light practice.",
            "Record sowing date, seed source, quantity, nursery location, and starting condition.",
            "Standing water, contamination, a completely dry medium, or disturbed labels.",
            "Move forward when emergence is first observed.", visualSource(), visualUrl()),
    GERMINATION("Germination and emergence", "Estimated Days 1–14", 14, seed(),
            "Seeds begin germinating and very small seedlings emerge; timing is provisional.",
            "Tiny green seedlings become visible, with uneven emergence possible across the group.",
            "• Monitor without disturbing seedlings.\n• Record how many seeds have emerged.",
            "Record first emergence, germination count, condition, and a section photo.",
            "Mold, collapse, persistent waterlogging, or severe drying.",
            "Move forward when seedlings remain upright and keep developing.", visualSource(), visualUrl()),
    SEEDLING_ESTABLISHMENT("Seedling establishment", "Estimated Days 15–31", 27, seed(),
            "New seedlings establish and develop a recognizable young cactus form.",
            "Small upright green seedlings show gradual growth and increasing structure.",
            "• Compare the group over time.\n• Follow validated nursery care and spacing.",
            "Record survivors, uneven development, environmental notes, and a photo.",
            "Yellowing, stretched growth, collapse, or worsening discoloration.",
            "Move forward when the young cactus body is clearly established.", visualSource(), visualUrl()),
    EARLY_CACTUS_GROWTH("Early cactus growth", "Estimated Days 32–50", 42, seed(),
            "Seedlings increasingly resemble young cactus plants rather than sprouts.",
            "The green cactus body becomes more defined and increases in height or thickness.",
            "• Compare with earlier photos.\n• Note the strongest and weakest development.",
            "Record approximate size, general condition, visible differences, and a photo.",
            "Soft tissue, severe discoloration, thin growth, or prolonged stalled growth.",
            "Move forward when juvenile stems continue developing consistently.", visualSource(), visualUrl()),
    JUVENILE_STEM_GROWTH("Juvenile stem development", "Estimated Days 51–75", 58, seed(),
            "Juvenile stems become longer, thicker, and more suitable for later handling.",
            "Plants show a clearer dragon-fruit cactus form and stronger stem structure.",
            "• Monitor the entire nursery group.\n• Prepare for transfer or grafting assessment.",
            "Record size range, damaged seedlings, vigor, and representative photos.",
            "Soft or dark tissue, crowding, severe growth differences, or damage.",
            "Move forward when advanced, stable nursery growth is present.", visualSource(), visualUrl()),
    ADVANCED_NURSERY_GROWTH("Advanced nursery growth", "Estimated Days 76–107", 76, seed(),
            "Plants approach the final early-growth period represented by the visual dataset.",
            "Young cactus stems are clearly developed, although size can vary considerably.",
            "• Document the final early-growth condition.\n• Request professional assessment before transfer or grafting.",
            "Record Day-107 condition, measurements, intended next action, and a photo.",
            "Weak development, rot, severe discoloration, or poor handling tolerance.",
            "Move forward only after the next action is professionally confirmed.", visualSource(), visualUrl()),
    TRANSFER_OR_GRAFTING("Transfer or grafting decision", "After Day 107; professionally confirmed", 100, seed(),
            "This is a decision point, not proof that every Day-107 plant is grafting-ready.",
            "A professionally assessed plant is selected for continued growth, transfer, or grafting.",
            "• Ask a qualified grower to assess readiness.\n• Use Record Grafting only when grafting is performed.",
            "Record the professional decision, destination, transfer date, or continued-growth reason.",
            "Using age alone as proof of grafting or transplant readiness.",
            "The seed pathway ends when its next validated action is recorded.",
            "Professional validation required", ""),

    PLANTED("Stem planting", "Day 0", 4, cutting(),
            "A prepared stem cutting is planted beside its support and the farm-section record begins.",
            "The cutting is stable near its support and may show little immediate change.",
            "• Confirm stability.\n• Keep the area appropriately drained.\n• Capture the starting condition.",
            "Record date, variety, quantity, section, rooting type, and starting photo.",
            "An unstable cutting, soft or dark base, discoloration, or standing water.",
            "Move forward when the cutting remains stable and begins establishing.", ifasSource(), ifasUrl()),
    ESTABLISHMENT("Establishment and root formation", "Estimated Days 1–42", 12, cutting(),
            "The cutting establishes roots and adapts to its planted environment.",
            "The cutting remains firm and green; roots or early buds may become visible.",
            "• Monitor stability and drainage.\n• Check the section for failed cuttings.",
            "Record survivors, visible roots or buds, losses, problems, and a photo.",
            "A rotting base, soft tissue, worsening discoloration, looseness, or collapse.",
            "Move forward when stable rooting or active growth is observed.", sarawakSource(), sarawakUrl()),
    ROOTING_AND_BUD("First new shoot emergence", "Estimated Weeks 3–8", 22, cutting(),
            "Tender new shoots indicate active vegetative growth across the section.",
            "New green shoots appear and begin developing from established cuttings.",
            "• Identify healthy primary growth.\n• Protect tender shoots from damage.",
            "Record cuttings with shoots, shoot condition, and a representative photo.",
            "Damaged or discolored shoots, pests, or prolonged deterioration.",
            "Move forward when a healthy main shoot can be selected.", sarawakSource(), sarawakUrl()),
    SHOOT_DEVELOPMENT("Main stem selection and vertical training", "Estimated Weeks 6–12", 34, cutting(),
            "One healthy main stem is guided upward along the support.",
            "The selected green stem grows upward; aerial roots may begin attaching.",
            "• Guide one healthy main stem upward.\n• Check ties regularly and keep them loose.",
            "Record height, tie condition, section progress, damage, and an optional photo.",
            "Tight ties, bent stems, competing growth, soft tissue, or dark areas.",
            "Move forward when the main stem approaches the top support.", ifasSource(), ifasUrl()),
    TRELLIS_TRAINING("Trellis-top transition", "Estimated Weeks 10–14", 46, cutting(),
            "The primary stem reaches the top and transitions toward overhanging growth.",
            "The stem reaches the support top and its growing tip begins bending over the frame.",
            "• Guide the stem over the support.\n• Protect the bend from breakage.",
            "Record the section reaching the trellis, damaged stems, and milestone date.",
            "Stem snapping, poor alignment, trellis damage, or weak top growth.",
            "Move forward when overhanging growth and secondary branches appear.", sarawakSource(), sarawakUrl()),
    TRELLIS_REACHED("Secondary branch development", "Estimated Months 3–5", 55, cutting(),
            "Secondary branches develop near the trellis and spread around the support.",
            "Several healthy branches create early horizontal or hanging growth.",
            "• Retain healthy, positioned branches.\n• Follow validated pruning practice.",
            "Record branch distribution, pruning, damage, and a section photo.",
            "Crowding, rubbing, weak branches, poor airflow, or damaged growth.",
            "Move forward when a balanced hanging canopy begins forming.", sarawakSource(), sarawakUrl()),
    CANOPY_DEVELOPMENT("Canopy formation and management", "Estimated Months 4–7", 64, cutting(),
            "Secondary and tertiary growth forms the productive hanging canopy.",
            "Healthy branches are distributed around the trellis with increasing coverage.",
            "• Balance branch distribution.\n• Preserve access and airflow.\n• Remove damaged growth using validated practice.",
            "Record canopy condition, pruning, trellis condition, damage, and a photo.",
            "Dense tangles, poor ventilation, disease, or excessive support weight.",
            "Move forward when mature, healthy hanging stems are present.", ifasSource(), ifasUrl()),
    MATURE_GROWTH("Mature, flower-capable growth", "Estimated Months 6–9; highly variable", 70, productive(),
            "Established mature stems may become capable of reproductive growth; age does not guarantee flowering.",
            "The trellis carries stable, healthy mature hanging stems and a manageable canopy.",
            "• Monitor for reproductive buds.\n• Record natural or lighting induction separately when used.",
            "Record mature-stem condition, canopy readiness, induction method, and a photo.",
            "A weak canopy, severe disease, crowding, or an unstable trellis.",
            "Move forward when a reproductive flower bud is clearly visible.", bbchSource(), bbchUrl()),
    FLOWER_BUD("Flower-bud development", "Approximately 10–20 days to bloom", 76, productive(),
            "A reproductive bud becomes visible, enlarges, and develops toward flowering.",
            "Flower buds progressively enlarge and elongate on mature stems.",
            "• Avoid damaging buds.\n• Monitor likely flowering date.\n• Prepare pollination method.",
            "Record first-bud date, bud count, condition, and photos.",
            "Bud abortion, insects, rot, discoloration, or premature drop.",
            "Move forward when the flower is ready to open.", sarawakSource(), sarawakUrl()),
    FLOWERING("Flowering and pollination", "Usually one night", 82, productive(),
            "The flower opens for a limited period and the pollination record begins.",
            "A large pale flower opens at night and begins closing afterward.",
            "• Follow validated pollination practice.\n• Record natural or assisted pollination.",
            "Record opening date, flower count, pollination method, time, and photo.",
            "A missed window, damaged flowers, poor pollen, or weather interference.",
            "Move forward when successful fruit set becomes visible.", ifasSource(), ifasUrl()),
    FRUIT_SET("Fruit set and early development", "Estimated Days 1–7 after pollination", 88, productive(),
            "Successfully pollinated flowers begin forming small green fruit.",
            "The flower base enlarges and a young green fruit becomes visible.",
            "• Monitor successful and failed set.\n• Protect developing fruit from damage.",
            "Record pollinated flowers, developing fruit, failed set, losses, and photos.",
            "Fruit drop, lack of enlargement, rot, discoloration, or pests.",
            "Move forward when fruit continues enlarging steadily.", bbchSource(), bbchUrl()),
    FRUIT_DEVELOPMENT("Fruit growth and ripening", "Approximately 35–45 days after pollination", 94, productive(),
            "Fruit increases in size and develops variety-specific mature characteristics.",
            "Green fruit enlarges before peel color and other maturity indicators change.",
            "• Monitor size, condition, and color.\n• Prepare harvest records without harvesting by age alone.",
            "Record fruit count, condition, color development, losses, and photos.",
            "Cracking, rot, pests, physical injury, or premature drop.",
            "Move forward when validated harvest indicators are present.", sarawakSource(), sarawakUrl()),
    FRUIT_MATURATION("Harvest readiness", "Approximately Day 35–45 after pollination", 98, productive(),
            "Fruit approaches harvest using appearance and the farm's validated maturity standard.",
            "Fruit shows variety-appropriate peel color, mature-looking bracts, and no serious decay.",
            "• Confirm maturity using accepted farm indicators.\n• Prepare a grouped Inventory harvest batch.",
            "Record confirmation date, estimated fruit count, damage, and photos.",
            "Harvesting early, overripe or split fruit, disease, or removal damage.",
            "Move forward when the farmer confirms harvest readiness.", sarawakSource(), sarawakUrl()),
    READY_FOR_HARVEST("Harvest confirmed", "Farmer-confirmed milestone", 100, productive(),
            "The farmer confirms that fruit meets the farm's harvest criteria.",
            "Confirmed mature fruit is ready to be collected and recorded as a batch.",
            "• Create the Inventory harvest batch.\n• Grade and count using the existing workflow.",
            "Record harvest date, batch, counts by grade and size, losses, and final photo.",
            "The app does not independently certify maturity, grade, or quality.",
            "The perennial planting record remains active for later cycles.", ifasSource(), ifasUrl()),
    HARVESTED("Harvest recorded", "After Inventory batch creation", 100, none(),
            "The current fruiting cycle has been harvested; the perennial planting remains active.",
            "The canopy remains available for future growth and flowering cycles.",
            "Keep the planting record and begin another cycle when new buds appear.",
            "Record post-harvest observations through the normal recording workflow.",
            "Do not delete the farm record after one harvest cycle.",
            "Return to mature-growth or flower-bud guidance for the next cycle.", ifasSource(), ifasUrl()),

    GRAFT_PREPARATION("Grafting preparation", "Grafting Day 0", 12, grafted(),
            "A professionally selected seed-grown record is prepared for a grafting event.",
            "The selected rootstock and scion are documented before the procedure.",
            "• Confirm suitability with a qualified grower.\n• Record scion variety and grafting date.",
            "Record source section, scion variety, operator, date, and starting photo.",
            "Grafting without suitable material, sanitation, or trained guidance.",
            "Move forward when the grafting event is completed and documented.",
            "Professional validation required", ""),
    GRAFT_UNION("Graft union", "Estimated Days 1–14", 30, grafted(),
            "The graft union is protected while attachment and healing are monitored.",
            "Joined material remains stable without separation or worsening damage.",
            "• Avoid disturbing the union.\n• Follow professional protection and monitoring instructions.",
            "Record union condition, losses, interventions, and optional photos.",
            "Separation, discoloration, softness, contamination, or collapse.",
            "Move forward only when the union is professionally judged stable.",
            "Professional validation required", ""),
    GRAFT_RECOVERY("Graft recovery", "Estimated Weeks 2–4", 48, grafted(),
            "The grafted material recovers and begins showing stable growth.",
            "The union remains stable and healthy new growth may become visible.",
            "• Continue monitoring the union.\n• Follow validated aftercare and support guidance.",
            "Record new growth, union condition, failed grafts, and photos.",
            "Wilting, separation, rot, dark tissue, or stalled deterioration.",
            "Move forward when stable grafted growth is confirmed.",
            "Professional validation required", ""),
    GRAFTED_ESTABLISHMENT("Grafted establishment", "Estimated Months 1–3", 62, grafted(),
            "A successful graft develops toward stable managed growth.",
            "Healthy grafted growth continues while the union and support remain stable.",
            "• Guide healthy growth.\n• Continue monitoring the union and plant condition.",
            "Record growth, support condition, canopy progress, and photos.",
            "Union failure, weak growth, damage, disease, or unsuitable support.",
            "Move forward when mature flower-capable growth is confirmed.",
            "Professional validation required", "");

    public static final String VALIDATION_STATUS =
            "Provisional guidance — professional validation required";

    private final String displayName;
    private final String estimatedTiming;
    private final int progressPercent;
    private final EnumSet<PropagationMethod> methods;
    private final String overview;
    private final String expectedAppearance;
    private final String tasks;
    private final String recordGuidance;
    private final String warnings;
    private final String completionIndicator;
    private final String referenceTitle;
    private final String referenceUrl;

    PlantingStage(String displayName, String estimatedTiming, int progressPercent,
                  EnumSet<PropagationMethod> methods, String overview,
                  String expectedAppearance, String tasks, String recordGuidance,
                  String warnings, String completionIndicator,
                  String referenceTitle, String referenceUrl) {
        this.displayName = displayName;
        this.estimatedTiming = estimatedTiming;
        this.progressPercent = progressPercent;
        this.methods = methods;
        this.overview = overview;
        this.expectedAppearance = expectedAppearance;
        this.tasks = tasks;
        this.recordGuidance = recordGuidance;
        this.warnings = warnings;
        this.completionIndicator = completionIndicator;
        this.referenceTitle = referenceTitle;
        this.referenceUrl = referenceUrl;
    }

    private static EnumSet<PropagationMethod> seed() { return only(PropagationMethod.SEED); }
    private static EnumSet<PropagationMethod> cutting() { return only(PropagationMethod.STEM_CUTTING); }
    private static EnumSet<PropagationMethod> grafted() { return only(PropagationMethod.GRAFTED); }
    private static EnumSet<PropagationMethod> productive() {
        return only(PropagationMethod.STEM_CUTTING, PropagationMethod.GRAFTED);
    }
    private static EnumSet<PropagationMethod> none() { return EnumSet.noneOf(PropagationMethod.class); }
    private static EnumSet<PropagationMethod> only(PropagationMethod... values) {
        EnumSet<PropagationMethod> result = none();
        Collections.addAll(result, values);
        return result;
    }
    private static String visualSource() { return "Kaggle growth-process dataset (visual reference candidate)"; }
    private static String visualUrl() { return "https://www.kaggle.com/datasets/mdahshanhabib/dragon-fruit-cactus-groth-proccess"; }
    private static String ifasSource() { return "UF/IFAS Extension: Pitaya Growing in the Florida Home Landscape"; }
    private static String ifasUrl() { return "https://ask.ifas.ufl.edu/publication/HS303"; }
    private static String sarawakSource() { return "Sarawak Department of Agriculture: Pitaya Cultivation in Sarawak"; }
    private static String sarawakUrl() { return "https://doa.sarawak.gov.my/web/subpage/webpage_view/360"; }
    private static String bbchSource() { return "Phenological growth stages of dragon fruit (extended BBCH scale)"; }
    private static String bbchUrl() { return "https://www.sciencedirect.com/science/article/pii/S0304423816305477"; }

    public String getDisplayName() { return displayName; }
    public String getEstimatedTiming() { return estimatedTiming; }
    public int getProgressPercent() { return progressPercent; }
    public String getOverview() { return overview; }
    public String getExpectedAppearance() { return expectedAppearance; }
    public String getTasks() { return tasks; }
    public String getRecordGuidance() { return recordGuidance; }
    public String getWarnings() { return warnings; }
    public String getCompletionIndicator() { return completionIndicator; }
    public String getReferenceTitle() { return referenceTitle; }
    public String getReferenceUrl() { return referenceUrl; }
    public String getValidationStatus() { return VALIDATION_STATUS; }

    public static List<PlantingStage> forMethod(PropagationMethod method) {
        List<PlantingStage> result = new ArrayList<>();
        if (method == PropagationMethod.SEED) {
            Collections.addAll(result, SEED_SOWING, GERMINATION,
                    SEEDLING_ESTABLISHMENT, EARLY_CACTUS_GROWTH,
                    JUVENILE_STEM_GROWTH, ADVANCED_NURSERY_GROWTH,
                    TRANSFER_OR_GRAFTING);
        } else if (method == PropagationMethod.GRAFTED) {
            Collections.addAll(result, GRAFT_PREPARATION, GRAFT_UNION,
                    GRAFT_RECOVERY, GRAFTED_ESTABLISHMENT, MATURE_GROWTH,
                    FLOWER_BUD, FLOWERING, FRUIT_SET, FRUIT_DEVELOPMENT,
                    FRUIT_MATURATION, READY_FOR_HARVEST);
        } else {
            Collections.addAll(result, PLANTED, ESTABLISHMENT,
                    ROOTING_AND_BUD, SHOOT_DEVELOPMENT, TRELLIS_TRAINING,
                    TRELLIS_REACHED, CANOPY_DEVELOPMENT, MATURE_GROWTH,
                    FLOWER_BUD, FLOWERING, FRUIT_SET, FRUIT_DEVELOPMENT,
                    FRUIT_MATURATION, READY_FOR_HARVEST);
        }
        return Collections.unmodifiableList(result);
    }

    public PlantingStage next(PropagationMethod method) {
        List<PlantingStage> stages = forMethod(method);
        int index = stages.indexOf(this);
        return index < 0 || index >= stages.size() - 1 ? this : stages.get(index + 1);
    }

    public PlantingStage next() { return next(PropagationMethod.STEM_CUTTING); }

    public static PlantingStage firstFor(PropagationMethod method) {
        List<PlantingStage> stages = forMethod(method);
        return stages.isEmpty() ? PLANTED : stages.get(0);
    }

    public static PlantingStage fromCode(String value, PropagationMethod method) {
        if (value == null || value.trim().isEmpty()) return firstFor(method);
        try {
            PlantingStage stage = valueOf(value.trim().toUpperCase(Locale.US));
            return stage.methods.contains(method) ? stage : firstFor(method);
        } catch (IllegalArgumentException ignored) {
            return firstFor(method);
        }
    }

    public static PlantingStage fromCode(String value) {
        if (value == null || value.trim().isEmpty()) return PLANTED;
        try { return valueOf(value.trim().toUpperCase(Locale.US)); }
        catch (IllegalArgumentException ignored) { return PLANTED; }
    }

    public static PlantingStage suggestedForAge(PropagationMethod method, int days) {
        if (method == PropagationMethod.SEED) {
            if (days < 1) return SEED_SOWING;
            if (days < 15) return GERMINATION;
            if (days < 32) return SEEDLING_ESTABLISHMENT;
            if (days < 51) return EARLY_CACTUS_GROWTH;
            if (days < 76) return JUVENILE_STEM_GROWTH;
            if (days < 108) return ADVANCED_NURSERY_GROWTH;
            return TRANSFER_OR_GRAFTING;
        }
        if (method == PropagationMethod.GRAFTED) {
            if (days < 1) return GRAFT_PREPARATION;
            if (days < 15) return GRAFT_UNION;
            if (days < 29) return GRAFT_RECOVERY;
            if (days < 91) return GRAFTED_ESTABLISHMENT;
            return MATURE_GROWTH;
        }
        if (days < 1) return PLANTED;
        if (days < 22) return ESTABLISHMENT;
        if (days < 43) return ROOTING_AND_BUD;
        if (days < 71) return SHOOT_DEVELOPMENT;
        if (days < 99) return TRELLIS_TRAINING;
        if (days < 151) return TRELLIS_REACHED;
        if (days < 211) return CANOPY_DEVELOPMENT;
        return MATURE_GROWTH;
    }

    public static PlantingStage suggestedForAge(int days) {
        return suggestedForAge(PropagationMethod.STEM_CUTTING, days);
    }
}
