/**
 *
 *
 */
package org.openhab.binding.viratec.internal.discovery;

import static org.openhab.binding.viratec.ViraTecBindingConstants.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.config.discovery.internal.UpnpDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link ViraCubeDiscoveryParticipant} is responsible for discovering new and removed cubes. It uses the central
 * {@link UpnpDiscoveryService}.
 *
 *
 *
 * 2018-02-15 17:00:44.791 [DEBUG] [s.c.d.AbstractDiscoveryService:372 ] - Background discovery for discovery
 * service 'org.eclipse.smarthome.config.discovery.upnp.internal.UpnpDiscoveryService' enabled.
 *
 */
@Component(immediate = true, service = UpnpDiscoveryParticipant.class) // depricated but still in official eclipse
                                                                       // smarthome documentation for upnp discoveries
public class ViraCubeDiscoveryParticipant implements UpnpDiscoveryParticipant {

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(THING_TYPE_BRIDGE);
    }

    @Override
    public DiscoveryResult createResult(RemoteDevice device) {
        ThingUID uid = getThingUID(device);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(HOST, device.getIdentity().getDescriptorURL().getHost());
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties)
                    .withLabel(device.getDetails().getFriendlyName()).build();
            return result;
        } else {
            return null;
        }
    }

    @Override
    public ThingUID getThingUID(RemoteDevice device) {
        if (device != null) {

            DeviceDetails details = device.getDetails();
            if (details != null) {
                ModelDetails modelDetails = details.getModelDetails();
                if (modelDetails != null) {
                    String modelName = modelDetails.getModelName();
                    if (modelName != null) {
                        if (modelName.startsWith("MIYO")) {
                            return new ThingUID(THING_TYPE_BRIDGE, "1");
                        }
                    }
                }
            }
        }
        return null;
    }
}
