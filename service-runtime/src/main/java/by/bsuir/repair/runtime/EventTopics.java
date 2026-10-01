package by.bsuir.repair.runtime;

public final class EventTopics {
    private EventTopics() {}
    public static final String USER="repair.user-events";
    public static final String DEVICE="repair.device-events";
    public static final String REQUEST="repair.request-events";
    public static final String WORK_ORDER="repair.work-order-events";
    public static final String PAYMENT="repair.payment-events";
    public static final String CATALOG="repair.catalog-events";
    public static final String[] ALL={USER,DEVICE,REQUEST,WORK_ORDER,PAYMENT,CATALOG};
}
