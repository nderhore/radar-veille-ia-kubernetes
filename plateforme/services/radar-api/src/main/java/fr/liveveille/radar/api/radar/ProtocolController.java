package fr.liveveille.radar.api.radar;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.liveveille.radar.api.config.RadarProperties;

/** Expose le protocole de veille en vigueur : finalité, axes, KIQ, consignes par anneau, technologies suivies. */
@RestController
@RequestMapping("/api/protocole")
public class ProtocolController {

    private final RadarService radar;

    public ProtocolController(RadarService radar) {
        this.radar = radar;
    }

    @GetMapping
    public RadarProperties.Protocol protocol() {
        return radar.protocol();
    }
}
