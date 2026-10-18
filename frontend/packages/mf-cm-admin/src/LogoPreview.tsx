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

import { useEffect, useRef } from "react";

/**
 * A preview of a logo chosen for upload, decoded and drawn onto a canvas. Nothing derived from the chosen file goes
 * into the page's markup (no image URL), so the file can only ever be pixels.
 */
export default function LogoPreview({ image, size = 72 }: { image: ImageBitmap; size?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const el = canvas.current;
    const ctx = el?.getContext("2d");
    if (!el || !ctx) return;
    const scale = window.devicePixelRatio || 1;
    el.width = size * scale;
    el.height = size * scale;
    ctx.clearRect(0, 0, el.width, el.height);
    // fit inside the box, keeping the logo's proportions, with a little margin like the tiles
    const box = el.width * 0.84;
    const ratio = Math.min(box / image.width, box / image.height);
    const w = image.width * ratio;
    const h = image.height * ratio;
    ctx.drawImage(image, (el.width - w) / 2, (el.height - h) / 2, w, h);
  }, [image, size]);

  return (
    <div className="cm-logo" style={{ width: size, height: size, marginBottom: 12 }}>
      <canvas ref={canvas} style={{ width: size, height: size }} role="img" aria-label="Logo preview" />
    </div>
  );
}
