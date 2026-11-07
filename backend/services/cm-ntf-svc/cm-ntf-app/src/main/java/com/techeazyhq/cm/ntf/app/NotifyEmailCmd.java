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

package com.techeazyhq.cm.ntf.app;

/**
 * Consumed from {@code cm/ntf/cmd-notify-email-v1} (ADR-060): an email another service wants sent to an address it
 * holds, such as cm-df-reg-svc telling a fiduciary its usage is near its allowance. {@code ref} identifies the message,
 * so the same one sent twice is the same in notification-service's eyes.
 */
public record NotifyEmailCmd(String destination, String subject, String body, String ref) {
}
