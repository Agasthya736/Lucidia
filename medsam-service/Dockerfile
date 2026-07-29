FROM python:3.10-slim

WORKDIR /app
RUN apt-get update && apt-get install -y libgl1 libglib2.0-0 wget git && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN pip install --no-cache-dir git+https://github.com/facebookresearch/segment-anything.git

RUN mkdir -p work_dir/MedSAM
RUN wget -O work_dir/MedSAM/medsam_vit_b.pth https://zenodo.org/records/10689643/files/medsam_vit_b.pth

COPY app.py .

EXPOSE 8001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]