#!/usr/bin/env python3
"""Rebuilds the face models in core/faces/src/main/assets/faces from OpenCV Zoo's ONNX files.

  python3 -m venv /tmp/fm && /tmp/fm/bin/pip install onnx onnxruntime onnx2tf tensorflow tf_keras \
      onnx_graphsurgeon sng4onnx psutil ai-edge-litert numpy
  /tmp/fm/bin/python scripts/convert-face-models.py /tmp/fm-work

Steps (see core/faces/MODELS.md for why):
  1. Download YuNet 2023mar (MIT) and SFace 2021dec (Apache-2.0) from OpenCV Zoo.
  2. YuNet: concatenate its 12 outputs into one [1, 8400, 16] tensor so the app never depends on the
     converter's output order. SFace: drop initializers listed as graph inputs.
  3. Convert with onnx2tf through the TensorFlow Lite converter: YuNet float32, SFace with
     dynamic-range int8 weights (a quarter of the size).
  4. Check the TFLite models against ONNX Runtime on random input.
"""
import os
import sys
import urllib.request

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper

ZOO = "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/"
YUNET = "face_detection_yunet/face_detection_yunet_2023mar.onnx"
SFACE = "face_recognition_sface/face_recognition_sface_2021dec.onnx"
ASSETS = os.path.join(os.path.dirname(__file__), "..", "core", "faces", "src", "main", "assets", "faces")


def fetch(path, work):
    target = os.path.join(work, os.path.basename(path))
    if not os.path.exists(target):
        urllib.request.urlretrieve(ZOO + path, target)
    return target


def yunet_single_output(src, dst):
    model = onnx.load(src)
    graph = model.graph
    per_stride = []
    for stride in (8, 16, 32):
        out = f"anchors_{stride}"
        graph.node.append(helper.make_node(
            "Concat", [f"cls_{stride}", f"obj_{stride}", f"bbox_{stride}", f"kps_{stride}"], [out], axis=2))
        per_stride.append(out)
    graph.node.append(helper.make_node("Concat", per_stride, ["detections"], axis=1))
    del graph.output[:]
    graph.output.append(helper.make_tensor_value_info("detections", TensorProto.FLOAT, [1, 8400, 16]))
    onnx.checker.check_model(model)
    onnx.save(model, dst)


def sface_clean(src, dst):
    model = onnx.load(src)
    initializers = {t.name for t in model.graph.initializer}
    inputs = [i for i in model.graph.input if i.name not in initializers]
    del model.graph.input[:]
    model.graph.input.extend(inputs)
    onnx.save(model, dst)


def convert(onnx_path, out_dir, quantize):
    flags = "-odrqt" if quantize else ""
    code = os.system(f"onnx2tf -i {onnx_path} -o {out_dir} -tb tf_converter {flags} -n > {out_dir}.log 2>&1")
    if code != 0:
        sys.exit(f"onnx2tf failed, see {out_dir}.log")
    name = os.path.splitext(os.path.basename(onnx_path))[0]
    return os.path.join(out_dir, f"{name}_{'dynamic_range_quant' if quantize else 'float32'}.tflite")


def check(onnx_path, tflite_path, shape_nchw, min_cosine):
    from ai_edge_litert.interpreter import Interpreter
    session = ort.InferenceSession(onnx_path)
    interpreter = Interpreter(tflite_path)
    interpreter.allocate_tensors()
    rng = np.random.default_rng(0)
    for _ in range(5):
        x = rng.uniform(0, 255, shape_nchw).astype(np.float32)
        expected = session.run(None, {session.get_inputs()[0].name: x})[0].ravel()
        interpreter.set_tensor(interpreter.get_input_details()[0]["index"], x.transpose(0, 2, 3, 1))
        interpreter.invoke()
        actual = interpreter.get_tensor(interpreter.get_output_details()[0]["index"]).ravel()
        cosine = float(expected @ actual / np.linalg.norm(expected) / np.linalg.norm(actual))
        if cosine < min_cosine:
            sys.exit(f"{tflite_path}: cosine {cosine:.5f} < {min_cosine}")
    print(f"ok {os.path.basename(tflite_path)}")


def main():
    work = sys.argv[1] if len(sys.argv) > 1 else "face-models"
    os.makedirs(work, exist_ok=True)
    yunet = os.path.join(work, "yunet_concat.onnx")
    sface = os.path.join(work, "sface_clean.onnx")
    yunet_single_output(fetch(YUNET, work), yunet)
    sface_clean(fetch(SFACE, work), sface)
    yunet_tflite = convert(yunet, os.path.join(work, "yunet_tf"), quantize=False)
    sface_tflite = convert(sface, os.path.join(work, "sface_tf"), quantize=True)
    check(yunet, yunet_tflite, (1, 3, 640, 640), 0.9999)
    check(sface, sface_tflite, (1, 3, 112, 112), 0.99)
    os.makedirs(ASSETS, exist_ok=True)
    os.replace(yunet_tflite, os.path.join(ASSETS, "face_detection_yunet.tflite"))
    os.replace(sface_tflite, os.path.join(ASSETS, "face_recognition_sface.tflite"))
    print("Models written to", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
