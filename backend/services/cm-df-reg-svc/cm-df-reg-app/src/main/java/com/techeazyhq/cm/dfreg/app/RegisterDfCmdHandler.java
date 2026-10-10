/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
