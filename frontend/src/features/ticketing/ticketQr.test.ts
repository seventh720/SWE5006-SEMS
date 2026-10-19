import { expect, it } from "vitest";
import QRCode from "qrcode";
import jsQR from "jsqr";

it("generated electronic ticket QR round-trips through the staff scanner decoder", () => {
  const payload = "sems:ticket:12345678-1234-1234-1234-123456789abc";
  const { modules } = QRCode.create(payload, { errorCorrectionLevel: "M" });
  const scale = 8, margin = 4, width = (modules.size + margin * 2) * scale;
  const pixels = new Uint8ClampedArray(width * width * 4).fill(255);
  for (let y = 0; y < modules.size; y++) for (let x = 0; x < modules.size; x++) {
    if (!modules.get(y, x)) continue;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
      const offset = (((y + margin) * scale + dy) * width + (x + margin) * scale + dx) * 4;
      pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = 0;
    }
  }
  expect(jsQR(pixels, width, width)?.data).toBe(payload);
});
