package com.techeazyhq.cm.ntf.app;

import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushDvcRepoPort;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A DP registering, listing and removing the browsers they want consent notifications pushed to. */
public class PushDvcCmdHandler {

    private final PushDvcRepoPort repo;

    public PushDvcCmdHandler(PushDvcRepoPort repo) {
        this.repo = repo;
    }

    public Mono<PushDvc> register(String dpId, String token, PushPltf platform) {
        if (token == null || token.isBlank() || token.length() > PushDvc.MAX_TOKEN_LENGTH) {
            return Mono.error(new DomRuleViolation(400, "push-token-invalid",
                    "token must be 1-" + PushDvc.MAX_TOKEN_LENGTH + " characters"));
        }
        return repo.upsert(dpId, token, platform == null ? PushPltf.WEB : platform);
    }

    public Flux<PushDvc> list(String dpId) {
        return repo.findByDpId(dpId);
    }

    public Mono<Void> remove(String dpId, String id) {
        return repo.delete(dpId, id)
                .flatMap(existed -> existed ? Mono.<Void>empty()
                        : Mono.error(new DomRuleViolation(404, "push-dvc-not-found", "No such device: " + id)));
    }
}
