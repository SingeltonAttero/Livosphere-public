package app.livosphere.buildlogic;

import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public abstract class SetContractExtension {
    private final Property<String> setId;
    private final Property<String> surface;

    @Inject
    public SetContractExtension(ObjectFactory objects) {
        setId = objects.property(String.class);
        surface = objects.property(String.class);
    }

    public Property<String> getSetId() {
        return setId;
    }

    public Property<String> getSurface() {
        return surface;
    }
}
