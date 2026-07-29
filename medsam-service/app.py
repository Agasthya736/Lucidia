import io
import numpy as np
import torch
import cv2
from fastapi import FastAPI, UploadFile, File, Form
from PIL import Image
from segment_anything import sam_model_registry, SamPredictor

app = FastAPI()

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
CHECKPOINT = "work_dir/MedSAM/medsam_vit_b.pth"

sam_model = sam_model_registry["vit_b"](checkpoint=None)
state_dict = torch.load(CHECKPOINT, map_location=torch.device(DEVICE))
sam_model.load_state_dict(state_dict)
sam_model.to(DEVICE)
predictor = SamPredictor(sam_model)


def mask_to_findings(mask: np.ndarray, ct_slice: np.ndarray, spacing_mm: float = 1.0):
    ys, xs = np.where(mask)
    if len(xs) == 0:
        return {"lesion_found": False}

    area_px = int(mask.sum())
    area_mm2 = area_px * (spacing_mm ** 2)
    bbox = [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())]
    centroid = [float(xs.mean()), float(ys.mean())]

    hu_values = ct_slice[mask]
    return {
        "lesion_found": True,
        "area_px": area_px,
        "area_mm2": round(area_mm2, 2),
        "bbox": bbox,
        "centroid": centroid,
        "hu_mean": float(np.mean(hu_values)),
        "hu_min": float(np.min(hu_values)),
        "hu_max": float(np.max(hu_values)),
    }


@app.post("/segment")
async def segment(
    file: UploadFile = File(...),
    x1: int = Form(...),
    y1: int = Form(...),
    x2: int = Form(...),
    y2: int = Form(...),
    spacing_mm: float = Form(1.0),
):
    raw = await file.read()
    img = np.array(Image.open(io.BytesIO(raw)).convert("RGB"))

    predictor.set_image(img)
    box = np.array([x1, y1, x2, y2])
    masks, scores, _ = predictor.predict(box=box, multimask_output=False)

    mask = masks[0]
    gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY).astype(np.float32)
    findings = mask_to_findings(mask, gray, spacing_mm)
    findings["confidence"] = float(scores[0])

    return findings


@app.get("/health")
def health():
    return {"status": "ok", "device": DEVICE}