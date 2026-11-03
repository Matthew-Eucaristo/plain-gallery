#!/usr/bin/env python3
"""Rebuilds the OCR models in core/text/src/main/assets/text from PaddleOCR's PP-OCRv6 tiny (Apache-2.0).

  python3 -m venv /tmp/fm && /tmp/fm/bin/pip install onnx onnxruntime onnx2tf tensorflow tf_keras \
      onnx_graphsurgeon sng4onnx psutil ai-edge-litert numpy pillow
  PATH=/tmp/fm/bin:$PATH /tmp/fm/bin/python scripts/convert-text-models.py /tmp/ocr-work

The ONNX export comes from the npm package @arcships/light-ocr-model-ppocrv6-tiny (Apache-2.0, built from
PaddlePaddle's official archives). Conversion notes (see core/text/MODELS.md):
  - Erf (GELU) is replaced with built-in ops (-rtpo Erf); otherwise it needs TensorFlow's Flex delegate.
  - The detector gets a fixed 736x736 input: dynamic shapes fail in XNNPACK. The app tiles larger images.
  - The recognizer keeps a dynamic width (resized per line at run time). Its head (Squeeze, Transpose,
    MatMul, Softmax) is rewritten as two 1x1 convolutions with logits out: onnx2tf otherwise applies the
    softmax over the wrong axis. The app computes the softmax itself.
"""
import io
import json
import os
import sys
import tarfile
import urllib.request

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper

PACKAGE = "https://registry.npmjs.org/@arcships/light-ocr-model-ppocrv6-tiny/-/light-ocr-model-ppocrv6-tiny-0.1.0.tgz"
ASSETS = os.path.join(os.path.dirname(__file__), "..", "core", "text", "src", "main", "assets", "text")


def extract(work):
    with tarfile.open(fileobj=io.BytesIO(urllib.request.urlopen(PACKAGE).read())) as tar:
        tar.extractall(work, filter="data")
    return os.path.join(work, "package", "bundle")


def rewrite_rec_head(src, dst):
    model = onnx.load(src)
    graph = model.graph
    inits = {t.name: t for t in graph.initializer}
    start = [n.name for n in graph.node].index("Squeeze.4")
    tail = graph.node[start:]
    bn = next(n for n in tail if n.op_type == "BatchNormalization")
    hs = next(n for n in tail if n.op_type == "HardSigmoid")
    mm0, mm1 = [n for n in tail if n.op_type == "MatMul"]
    adds = [n for n in tail if n.op_type == "Add"]
    conv_out = tail[0].input[0]
    w0 = numpy_helper.to_array(inits[mm0.input[1]])
    b0 = numpy_helper.to_array(inits[[i for i in adds[0].input if i in inits][0]])
    w1 = numpy_helper.to_array(inits[mm1.input[1]])
    b1 = numpy_helper.to_array(inits[[i for i in adds[1].input if i in inits][0]])
    del graph.node[start:]
    for name, arr in [("head_w0", w0.T.reshape(w0.shape[1], w0.shape[0], 1, 1)), ("head_b0", b0),
                      ("head_w1", w1.T.reshape(w1.shape[1], w1.shape[0], 1, 1)), ("head_b1", b1)]:
        graph.initializer.append(numpy_helper.from_array(arr.astype(np.float32), name))
    attr = lambda node, key: next(a for a in node.attribute if a.name == key).f
    graph.node.extend([
        helper.make_node("BatchNormalization", [conv_out] + list(bn.input[1:]), ["head_bn"], epsilon=attr(bn, "epsilon")),
        helper.make_node("HardSigmoid", ["head_bn"], ["head_hs"], alpha=attr(hs, "alpha"), beta=attr(hs, "beta")),
        helper.make_node("Mul", ["head_hs", "head_bn"], ["head_act"]),
        helper.make_node("Conv", ["head_act", "head_w0", "head_b0"], ["head_c0"], kernel_shape=[1, 1]),
        helper.make_node("Conv", ["head_c0", "head_w1", "head_b1"], ["logits"], kernel_shape=[1, 1]),
    ])
    del graph.output[:]
    graph.output.append(helper.make_tensor_value_info("logits", TensorProto.FLOAT, ["N", w1.shape[1], 1, "T"]))
    onnx.checker.check_model(model)
    onnx.save(model, dst)


def convert(onnx_path, out_dir, extra=""):
    code = os.system(f"onnx2tf -i {onnx_path} -o {out_dir} -tb tf_converter -odrqt -rtpo Erf {extra} -n > {out_dir}.log 2>&1")
    if code != 0:
        sys.exit(f"onnx2tf failed, see {out_dir}.log")
    name = os.path.splitext(os.path.basename(onnx_path))[0]
    return os.path.join(out_dir, f"{name}_dynamic_range_quant.tflite")


def run_tflite(path, x_nhwc):
    from ai_edge_litert.interpreter import Interpreter
    it = Interpreter(path)
    d = it.get_input_details()[0]
    it.resize_tensor_input(d["index"], x_nhwc.shape)
    it.allocate_tensors()
    it.set_tensor(d["index"], x_nhwc)
    it.invoke()
    return it.get_tensor(it.get_output_details()[0]["index"])


def main():
    work = sys.argv[1] if len(sys.argv) > 1 else "ocr-models"
    os.makedirs(work, exist_ok=True)
    bundle = extract(work)
    det_src = os.path.join(bundle, "det", "inference.onnx")
    rec_src = os.path.join(bundle, "rec", "inference.onnx")
    # onnx2tf may rewrite the input file when fixing shapes, so convert copies.
    det = os.path.join(work, "det.onnx")
    rec = os.path.join(work, "rec_head.onnx")
    onnx.save(onnx.load(det_src), det)
    rewrite_rec_head(rec_src, rec)
    det_tflite = convert(det, os.path.join(work, "det_tf"), "-ois x:1,3,736,736")
    rec_tflite = convert(rec, os.path.join(work, "rec_tf"))

    # Detector: compare text maps on rendered text (int8 weights shift raw values on noise, not on text).
    from PIL import Image, ImageDraw, ImageFont
    page = Image.new("RGB", (736, 736), "white")
    draw = ImageDraw.Draw(page)
    for i, line in enumerate(["Boarding pass GA 404", "Total Rp 125.000", "Selamat ulang tahun", "Order shipped"]):
        draw.text((40, 60 + i * 150), line, fill="black", font=ImageFont.load_default(size=48))
    rgb = np.asarray(page, np.float32) / 255.0
    x = ((rgb[..., ::-1] - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]).astype(np.float32)
    expected = ort.InferenceSession(det_src).run(None, {"x": x.transpose(2, 0, 1)[None].copy()})[0].ravel() > 0.3
    actual = run_tflite(det_tflite, x[None].copy()).ravel() > 0.3
    if expected.sum() == 0 or (expected == actual).mean() < 0.99:
        sys.exit("detector differs from ONNX")
    rng = np.random.default_rng(0)
    session = ort.InferenceSession(rec_src)
    for width in (320, 960):
        x = rng.normal(size=(1, 3, 48, width)).astype(np.float32)
        probs = session.run(None, {"x": x})[0][0]
        logits = run_tflite(rec_tflite, x.transpose(0, 2, 3, 1).copy()).reshape(-1, probs.shape[-1])
        if (probs.argmax(1) != logits.argmax(1)).mean() > 0.02:
            sys.exit(f"recognizer differs from ONNX at width {width}")
    print("ok: TFLite matches ONNX")

    os.makedirs(ASSETS, exist_ok=True)
    os.replace(det_tflite, os.path.join(ASSETS, "text_detection.tflite"))
    os.replace(rec_tflite, os.path.join(ASSETS, "text_recognition.tflite"))
    characters = json.load(open(os.path.join(bundle, "rec", "dictionary.json"), encoding="utf-8"))["characters"]
    assert not any("\n" in c for c in characters)
    with open(os.path.join(ASSETS, "text_dictionary.txt"), "w", encoding="utf-8") as out:
        out.write("\n".join(characters))
    print("Models written to", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
