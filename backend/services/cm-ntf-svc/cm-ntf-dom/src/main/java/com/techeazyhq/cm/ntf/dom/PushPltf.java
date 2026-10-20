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

package com.techeazyhq.cm.ntf.dom;

/**
 * Where a push token came from: the DP portal in a browser, or the DP mobile app (ADR-050). Every one of them is a
 * Firebase Cloud Messaging registration token, since notification-service pushes through FCM only, so the platform
 * only tells the DP which of their devices is which.
 */
public enum PushPltf {
    WEB,
    ANDROID,
    IOS
}
