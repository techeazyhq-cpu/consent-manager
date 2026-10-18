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

/** What cm-df-reg-svc accepts as a logo (DfLogo): the same checks, made before anything is uploaded. */
export const LOGO_MAX_BYTES = 256 * 1024;

const SIGNATURES: Record<string, number[]> = {
  "image/png": [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a],
  "image/jpeg": [0xff, 0xd8, 0xff],
};

/** Why a file can't be a logo, from its declared type, size and leading bytes; null if it can. */
export function logoProblem(type: string, size: number, head: Uint8Array): string | null {
  const signature = SIGNATURES[type];
  if (!signature) return "The logo must be a PNG or JPEG image.";
  if (size > LOGO_MAX_BYTES) return "The logo must be at most 256 KB.";
  if (size === 0) return "The logo file is empty.";
  if (!signature.every((byte, i) => head[i] === byte)) return "That file isn't really a PNG or JPEG image.";
  return null;
}

export interface CheckedImage {
  file: File;
  image: ImageBitmap;
}

export type CheckedLogo = CheckedImage | { problem: string };

/** Checks a chosen file and decodes it, so it can be previewed as pixels and is known to be an image. */
export async function checkLogo(file: File): Promise<CheckedLogo> {
  const head = new Uint8Array(await file.slice(0, 8).arrayBuffer());
  const problem = logoProblem(file.type, file.size, head);
  if (problem) return { problem };
  try {
    return { file, image: await createImageBitmap(file) };
  } catch {
    return { problem: "That image couldn't be read; it may be damaged." };
  }
}
