#!/usr/bin/env bash
# Copyright 2026 Vasantha Kumar
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# @author Vasantha Kumar <vasantha.kumar@hotmail.com>

# Creates the cm tenant and its namespaces. `docker compose up` already does this through the pulsar-init container
# (init-tenant.sh); run this only to repeat it by hand, for example after resetting Pulsar's data while services run.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose run --rm -T pulsar-init
