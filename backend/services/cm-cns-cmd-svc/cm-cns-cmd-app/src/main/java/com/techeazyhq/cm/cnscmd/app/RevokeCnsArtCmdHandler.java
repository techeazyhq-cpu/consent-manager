package com.techeazyhq.cm.cnscmd.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Stage 4: Asynchronous Revocation (plan §7c step 5). {@code CnsArt.revoke()} raises {@code CnsRevokedEvt} itself. */
public class RevokeCnsArtCmdHandler implements CmdHandler<RevokeCnsArtCmd, CnsArt> {

    private final CnsArtRepoPort cnsArtRepo;

    public RevokeCnsArtCmdHandler(CnsArtRepoPort cnsArtRepo) {
        this.cnsArtRepo = cnsArtRepo;
    }

    @Override
    @Transactional
    public Mono<CnsArt> handle(RevokeCnsArtCmd cmd) {
        return cnsArtRepo.findById(cmd.cnsArtId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "cns-art-not-found", "No CnsArt " + cmd.cnsArtId())))
                .flatMap(cnsArt -> {
                    try {
                        cnsArt.revoke();
                    } catch (IllegalStateException e) {
                        return Mono.error(new DomRuleViolation(409, "cns-art-not-issued", e.getMessage()));
                    }
                    return cnsArtRepo.save(cnsArt);
                });
    }
}
