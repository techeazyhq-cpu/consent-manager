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

package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * An invoice is {@code daysOverdue} days past its due date and has not been paid (ADR-105). Consumed by cm-df-reg-svc, which holds the
 * fiduciary's contact address and tells it. Ids, the invoice number and the amount only: the address never leaves cm-df-reg-svc.
 *
 * @param stepDays the step of the schedule this is (3, 10, 20 days after the due date), which is also what the message says
 */
public record InvoiceReminderEvt(UUID evtId, Instant occurredAt, String dfId, String invoiceId, String number, long totalMinor,
                                 String currency, Instant dueAt, int stepDays) implements DomainEvt {
}
