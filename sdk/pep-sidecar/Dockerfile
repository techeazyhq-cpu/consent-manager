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

# The fiduciary-side consent sidecar (ADR-076). Build from the repository root:
#   docker build -f sdk/pep-sidecar/Dockerfile -t cm-pep-sidecar .
FROM node:22.14.0-alpine
WORKDIR /app
COPY sdk/ai-ingestion-guard/package.json sdk/ai-ingestion-guard/package.json
COPY sdk/ai-ingestion-guard/src sdk/ai-ingestion-guard/src
COPY sdk/pep-sidecar/package.json sdk/pep-sidecar/package.json
COPY sdk/pep-sidecar/src sdk/pep-sidecar/src
USER node
# the sidecar answers whoever can reach it: inside a pod, localhost is the pod; elsewhere publish the port deliberately
ENV PEP_HOST=0.0.0.0 PEP_PORT=8484
EXPOSE 8484
HEALTHCHECK --interval=15s --timeout=3s CMD wget -qO- http://127.0.0.1:8484/healthz || exit 1
CMD ["node", "sdk/pep-sidecar/src/server.mjs"]
