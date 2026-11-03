# Text models (OCR)

[PP-OCRv6 tiny](https://github.com/PaddlePaddle/PaddleOCR) (Apache-2.0, `LICENSE-ppocr.txt`), 49 languages
including English and Indonesian, run on the CPU through LiteRT. Bundled in `src/main/assets/text/`.

| File | Size | Input → output |
|---|---|---|
| `text_detection.tflite` (DB detector, int8 weights) | 0.63 MB | 736×736 BGR, ImageNet mean/std, NHWC → text probability map `[1, 1, 736, 736]` |
| `text_recognition.tflite` (CTC recognizer, int8 weights) | 1.36 MB | 48×W BGR scaled to −1..1, NHWC, W resized per line → logits `[1, W/8, 1, 6906]` |
| `text_dictionary.txt` | 27 KB | 6905 characters, one per line; class 0 is the CTC blank |

Rebuild with `scripts/convert-text-models.py` (it explains the three conversion fixes needed).

## Why this model

- Smallest current choice that reads Latin text well: about 2 MB with int8 weights. Tesseract is larger and
  slower; ML Kit is closed source and pulls in Play Services; PP-OCRv5 mobile is 4× larger.
- Apache-2.0, so it can ship in this Apache-2.0 app.

## Checks (Nov 2026)

On 24 synthetic phone screenshots (1080×2400, light and dark, chat bubbles, 34–56 px text, Indonesian and
English, 5 fonts): 98.9% of words found (793/802), with the pipeline the app uses (tiled detection,
axis-aligned boxes, per-line recognition). The same as ONNX Runtime with the original models (99.0%).
Kotlin `TextDetection.boxes` and `Recognizer.decode` match the Python reference exactly on real model output.
