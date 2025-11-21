package Utils;

import java.util.UUID;

public final class NameFactory {

    private NameFactory() {
        // utility class - prevent instantiation
    }

    public static String uniqueSuffix() {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                .format(new java.util.Date());
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + randomSuffix;
    }

    public static String uniqueName(String prefix) {
        return prefix + " - " + uniqueSuffix();
    }
}
