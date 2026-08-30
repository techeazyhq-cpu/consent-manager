package com.techeazyhq.cm.dfreg.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Mono;

public class RegisterDfCmdHandler implements CmdHandler<RegisterDfCmd, Df> {

    private final DfRepoPort dfRepo;

    public RegisterDfCmdHandler(DfRepoPort dfRepo) {
        this.dfRepo = dfRepo;
    }

    @Override
    @Transactional
    public Mono<Df> handle(RegisterDfCmd cmd) {
        Df df = DfFactory.register(cmd.name(), cmd.contactEmail());
        return dfRepo.save(df);
    }
}
