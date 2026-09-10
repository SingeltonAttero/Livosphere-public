package app.livosphere.buildlogic;

import java.util.Locale;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

enum BuildProfile {
    PHONE,
    LEGACY;

    static final String PROPERTY = "livosphere.buildProfile";

    static BuildProfile parse(String value) {
        String normalized = value == null || value.isBlank() ? "phone" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "phone" -> PHONE;
            case "legacy" -> LEGACY;
            default -> throw new GradleException("Gradle property '" + PROPERTY
                    + "' supports only phone or legacy, got: " + value);
        };
    }

    static BuildProfile from(Project project) {
        return parse(project.getProviders().gradleProperty(PROPERTY).getOrElse("phone"));
    }

    String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean requiresPhysicalAssets(String surface) {
        return this == LEGACY || !surface.equals("watchface");
    }
}
