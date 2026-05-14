import os
import pathlib
import tempfile

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile

from app.pipeline_runner import PipelineRunner
from app.schemas import AnalyzeResponse

router = APIRouter()


def get_runner(request: Request) -> PipelineRunner:
    return request.app.state.runner


@router.post("/analyze", response_model=AnalyzeResponse)
async def analyze(
    file: UploadFile = File(...),
    runner: PipelineRunner = Depends(get_runner),
) -> AnalyzeResponse:
    if not file.content_type or (not file.content_type.startswith("video/") and file.content_type != "application/octet-stream"):
        raise HTTPException(
            status_code=415,
            detail=(
                f"Unsupported media type: {file.content_type}. "
                "Expected a video file."
            ),
        )

    ext = pathlib.Path(file.filename).suffix if file.filename else ".tmp"
    if not ext:
        ext = ".tmp"

    data = await file.read()

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=ext)
    tmp_path = tmp.name
    try:
        tmp.write(data)
        tmp.close()
        result = runner.run(tmp_path)
    finally:
        os.unlink(tmp_path)

    return result
