package ph.dragonview.mobile.ui.inventory;

/** Translates the inventory uniqueness constraint into an actionable form message. */
final class HarvestErrorMessage {
    private static final String DUPLICATE_CONSTRAINT =
            "inventory_workspace_batch_size_grade_key";

    private HarvestErrorMessage() { }

    static String display(String serverMessage) {
        if (serverMessage != null && serverMessage.contains(DUPLICATE_CONSTRAINT)) {
            return "This batch number already contains the same size and grade. "
                    + "Choose another batch number for a separate harvest, or update the "
                    + "existing inventory to add pieces.";
        }
        return serverMessage;
    }
}
