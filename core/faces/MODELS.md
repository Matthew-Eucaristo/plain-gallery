# Face models

Two small models run on the CPU through [LiteRT](https://github.com/google-ai-edge/LiteRT) (Apache-2.0).
They are bundled in `src/main/assets/faces/`; the app never downloads anything.

| File | Source | License | Size | Input → output |
|---|---|---|---|---|
| `face_detection_yunet.tflite` | [YuNet 2023mar](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet), float32 | MIT (`LICENSE-yunet.txt`) | 246 KB | 640×640 BGR 0–255 NHWC → `[1, 8400, 16]` |
| `face_recognition_sface.tflite` | [SFace 2021dec](https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface) (MobileFaceNet + SFace loss), int8 weights | Apache-2.0 (`LICENSE-sface.txt`) | 9.9 MB | 112×112 RGB 0–255 NHWC, aligned → 128-d embedding |

Rebuild with `scripts/convert-face-models.py` (downloads from OpenCV Zoo, converts with onnx2tf, checks
against ONNX Runtime).

## Why these

- **Licenses** allow redistribution in an Apache-2.0 app. InsightFace models (SCRFD, ArcFace `buffalo_*`)
  are non-commercial; ML Kit is closed source and pulls in Play Services; neither fits.
- **YuNet** is tiny (75 k parameters), finds faces from about 10 px up and gives the five landmarks SFace
  needs for alignment. BlazeFace (MediaPipe) is tuned for selfies at arm's length and misses faces in group
  photos.
- **SFace** reaches 99.4% on LFW. Dynamic-range int8 keeps embeddings within cosine ≥ 0.998 of float32.

## Checks done when choosing them (Nov 2026)

- Kotlin `YuNet.decode` and `FaceAlignment.similarity` match a NumPy reference, which matches OpenCV's
  `FaceDetectorYN` (boxes and landmarks identical) and `FaceRecognizerSF.alignCrop` (cosine ≥ 0.99).
- TFLite vs ONNX: YuNet boxes within 0.0002 px; SFace int8 cosine ≥ 0.998.
- On 300 labeled pairs (deepface test set) plus 5 identities from face_recognition's examples: same person
  cosine ≥ 0.52 (median 0.79), different people ≤ 0.37 (median 0.13). `FaceClusterer` uses 0.48 to join a
  group and 0.55 to merge groups; over 20 random orders it never mixed two people and never split one.
- Quality filter (`FaceAlignment.isRecognizable`): detection score ≥ 0.7, face ≥ 40 px, nose between 10%
  and 90% along the eye line (drops profiles; frontal faces measured 18–85%).
