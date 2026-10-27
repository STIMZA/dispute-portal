package com.example.disputeportal.entity;

/**
 * The reasons a customer can choose from on the dispute form. Each has the wording shown to the customer.
 */
public enum DisputeReason {
    UNRECOGNISED_TRANSACTION("I don't recognise this transaction"),
    DUPLICATE_CHARGE("I was charged twice"),
    INCORRECT_AMOUNT("The amount is wrong"),
    GOODS_NOT_RECEIVED("I didn't receive the goods or service"),
    FRAUD("My card or account was used without permission"),
    OTHER("Something else");

    private final String label;

    /**
     * @param label the wording shown in the dropdown and on later pages
     */
    DisputeReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
