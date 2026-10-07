package fr.liveveille.radar.collector.source;

import java.time.Instant;
import java.util.List;

/** Une source de veille. Ajouter une source = implémenter cette interface et la déclarer en bean. */
public interface SourceClient {

    String name();

    boolean enabled();

    List<SignalPayload> fetch(Instant since);
}
