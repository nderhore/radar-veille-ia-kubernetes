package fr.liveveille.radar.api.feedback;

import java.time.Clock;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.liveveille.radar.api.radar.RadarService;

/** Boucle de rétroaction : verdict d'un analyste sur un sujet du radar. */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    public record FeedbackRequest(
            @NotBlank @Size(max = 200) String term,
            @NotBlank @Pattern(regexp = "pertinent|bruit") String verdict,
            @Size(max = 100) String analyst,
            @Size(max = 1000) String comment) {
    }

    private final FeedbackRepository repository;
    private final RadarService radar;
    private final Clock clock;

    public FeedbackController(FeedbackRepository repository, RadarService radar, Clock clock) {
        this.repository = repository;
        this.radar = radar;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackEntity record(@RequestBody @Valid FeedbackRequest request) {
        var saved = repository.save(new FeedbackEntity(request.term().toLowerCase(), request.verdict(),
                request.analyst(), request.comment(), clock.instant()));
        log.info("verdict analyste : « {} » = {}", saved.getTerm(), saved.getVerdict());
        radar.recompute();
        return saved;
    }

    @GetMapping
    public List<FeedbackEntity> list() {
        return repository.findAll();
    }
}
