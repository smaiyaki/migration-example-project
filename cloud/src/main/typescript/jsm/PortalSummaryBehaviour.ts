const summary = getFieldById("summary");

if (summary) {
    summary.setDescription("Please summarise the problem in one line.");

    // Only prefill an empty field, so a customer who has already typed something keeps it.
    if (!summary.getValue()) {
        summary.setValue("[Bug] ");
    }
} else {
    logger.warn("The summary field is not present on this portal form");
}
