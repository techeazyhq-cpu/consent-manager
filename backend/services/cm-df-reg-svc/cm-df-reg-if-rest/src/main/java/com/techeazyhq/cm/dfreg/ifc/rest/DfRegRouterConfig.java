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

package com.techeazyhq.cm.dfreg.ifc.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.DfApiKeyHandler;
import com.techeazyhq.cm.dfreg.app.DfEndContractHandler;
import com.techeazyhq.cm.dfreg.app.DfExportHandler;
import com.techeazyhq.cm.dfreg.app.DfLeavingHandler;
import com.techeazyhq.cm.dfreg.app.DfRetentionHandler;
import com.techeazyhq.cm.dfreg.app.DfSignupHandler;
import com.techeazyhq.cm.dfreg.app.DfSuspensionHandler;
import com.techeazyhq.cm.dfreg.app.DfTeamHandler;
import com.techeazyhq.cm.dfreg.app.DfTermsHandler;
import com.techeazyhq.cm.dfreg.app.VerifyApiRequestHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfLogoQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.UploadDfLogoCmdHandler;

@Configuration
public class DfRegRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> dfRegRoutes(RegisterDfCmdHandler registerHandler,
                                                        ActivateDfCmdHandler activateHandler,
                                                        GetDfActiveQryHandler getActiveHandler,
                                                        ListDfsQryHandler listHandler,
                                                        GetDfQryHandler getDfHandler,
                                                        UploadDfLogoCmdHandler uploadLogoHandler,
                                                        GetDfLogoQryHandler getLogoHandler,
                                                        DfTeamHandler teamHandler,
                                                        DfApiKeyHandler apiKeyHandler,
                                                        DfTermsHandler termsHandler,
                                                        DfSuspensionHandler suspensionHandler,
                                                        DfLeavingHandler leavingHandler,
                                                        DfRetentionHandler retentionHandler,
                                                        DfEndContractHandler endContractHandler,
                                                        DfExportHandler exportHandler,
                                                        DfSignupHandler signupHandler,
                                                        VerifyApiRequestHandler verifyApiRequestHandler) {
        DfHandler dfHandler = new DfHandler(registerHandler, activateHandler, getActiveHandler, listHandler, getDfHandler,
                uploadLogoHandler, getLogoHandler);
        DfTeamHttpHandler team = new DfTeamHttpHandler(teamHandler);
        DfApiKeyHttpHandler apiKeys = new DfApiKeyHttpHandler(apiKeyHandler, verifyApiRequestHandler);
        DfTermsHttpHandler termsHttp = new DfTermsHttpHandler(termsHandler);
        DfSuspensionHttpHandler suspensions = new DfSuspensionHttpHandler(suspensionHandler);
        DfLeavingHttpHandler leaving = new DfLeavingHttpHandler(leavingHandler);
        DfLegalHoldHttpHandler legalHold = new DfLegalHoldHttpHandler(retentionHandler);
        DfEndContractHttpHandler endContract = new DfEndContractHttpHandler(endContractHandler);
        DfExportHttpHandler export = new DfExportHttpHandler(exportHandler);
        DfSignupHttpHandler signup = new DfSignupHttpHandler(signupHandler);
        return RouterFunctions.route()
                .GET("/api/df-reg/dfs", dfHandler::list)
                .POST("/api/df-reg/dfs", dfHandler::register)
                .POST("/api/df-reg/dfs/{id}/activate", dfHandler::activate)
                .GET("/api/df-reg/dfs/{id}", dfHandler::getOne)
                .GET("/api/df-reg/dfs/{id}/profile", dfHandler::profile)
                .PUT("/api/df-reg/dfs/{id}/logo", dfHandler::uploadLogo)
                .GET("/api/df-reg/dfs/{id}/logo", dfHandler::logo)
                .POST("/api/df-reg/dfs/{id}/members", team::inviteOwner)
                .GET("/api/df-reg/me/members", team::list)
                .POST("/api/df-reg/me/members", team::invite)
                .PUT("/api/df-reg/me/members/{memberId}", team::changeRole)
                .DELETE("/api/df-reg/me/members/{memberId}", team::remove)
                .GET("/internal/dfs/{id}/active", dfHandler::isActive)
                // the fiduciary's name, for the consent records and receipts that name it as controller (ADR-025)
                .GET("/internal/dfs/{id}", dfHandler::getOne)
                .GET("/api/df-reg/me/api-keys", apiKeys::list)
                .POST("/api/df-reg/me/api-keys", apiKeys::issue)
                .DELETE("/api/df-reg/me/api-keys/{id}", apiKeys::revoke)
                .PUT("/api/df-reg/me/api-keys/{id}/signing-required", apiKeys::signingRequired)
                .POST("/internal/api-keys/verify", apiKeys::verify)
                .POST("/api/df-reg/dfs/{id}/suspend", suspensions::suspend)
                .POST("/api/df-reg/dfs/{id}/resume", suspensions::resume)
                .POST("/api/df-reg/dfs/{id}/end-contract", endContract::proposeEnd)
                .POST("/api/df-reg/dfs/{id}/cancel-end-contract", endContract::proposeCancel)
                .GET("/api/df-reg/leave-proposals", endContract::list)
                .POST("/api/df-reg/leave-proposals/{id}/approve", endContract::approve)
                .POST("/api/df-reg/leave-proposals/{id}/reject", endContract::reject)
                .POST("/api/df-reg/leave-proposals/{id}/withdraw", endContract::withdraw)
                .PUT("/api/df-reg/dfs/{id}/legal-hold", legalHold::place)
                .DELETE("/api/df-reg/dfs/{id}/legal-hold", legalHold::release)
                .POST("/api/df-reg/signup", signup::apply)
                .POST("/api/df-reg/signup/verify", signup::verify)
                .POST("/api/df-reg/dfs/{id}/reject", signup::reject)
                .GET("/api/df-reg/me/export", export::export)
                .GET("/api/df-reg/me/leave", leaving::status)
                .POST("/api/df-reg/me/leave", leaving::leave)
                .POST("/api/df-reg/me/leave/cancel", leaving::cancel)
                .GET("/api/df-reg/me/terms", termsHttp::status)
                .POST("/api/df-reg/me/terms", termsHttp::accept)
                .GET("/api/df-reg/dfs/{id}/terms", termsHttp::evidence)
                .build();
    }
}
