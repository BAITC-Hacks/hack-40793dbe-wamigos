import asyncio
import shutil
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Annotated, BinaryIO

from fastapi import APIRouter, Depends, File, Form, Request, Response, UploadFile
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from pydantic import AwareDatetime, ValidationError

from app.audio_pipeline import AudioMeetingPipeline
from app.pipeline import MeetingPipeline
from app.schemas import (
    AnalyzeTranscriptRequest,
    ErrorResponse,
    HealthResponse,
    MeetingAnalysisResult,
    MeetingContext,
)
from app.service_error import ServiceError

health_router = APIRouter()
dev_router = APIRouter(prefix="/dev")
internal_router = APIRouter(prefix="/internal/v1")


def get_pipeline(request: Request) -> MeetingPipeline:
    return request.app.state.pipeline


def get_audio_pipeline(request: Request) -> AudioMeetingPipeline:
    return request.app.state.audio_pipeline


@health_router.get(
    "/health",
    response_model=HealthResponse,
    tags=["health"],
    responses={503: {"model": HealthResponse}},
)
async def health(request: Request, response: Response) -> HealthResponse:
    settings = request.app.state.settings
    provider_health = await request.app.state.llm_provider.check_health()
    ready = provider_health.status == "ready"
    response.status_code = 200 if ready else 503
    return HealthResponse(
        service=settings.service_name,
        version=settings.service_version,
        status="ok" if ready else "unhealthy",
        dev_endpoints_enabled=settings.enable_dev_endpoints,
        intelligence_ready=ready,
        llm_provider=provider_health,
    )


@dev_router.post(
    "/analyze-transcript",
    response_model=MeetingAnalysisResult,
    tags=["development"],
    description="Development-only transcript extraction using the configured local LLM.",
    responses={status: {"model": ErrorResponse} for status in (413, 502, 503, 504)},
)
async def analyze_transcript(
    request: AnalyzeTranscriptRequest,
    pipeline: Annotated[MeetingPipeline, Depends(get_pipeline)],
) -> MeetingAnalysisResult:
    return await pipeline.analyze_transcript(request.segments, request.context)


@internal_router.post(
    "/analyze",
    response_model=MeetingAnalysisResult,
    tags=["internal"],
    description="Synchronous audio analysis for the Java backend. No jobs are created.",
    responses={status: {"model": ErrorResponse} for status in (413, 422, 502, 503, 504)},
)
async def analyze_audio(
    file: Annotated[UploadFile, File()],
    pipeline: Annotated[AudioMeetingPipeline, Depends(get_audio_pipeline)],
    started_at: Annotated[AwareDatetime | None, Form(alias="startedAt")] = None,
    time_zone: Annotated[str | None, Form(alias="timeZone")] = None,
) -> MeetingAnalysisResult:
    try:
        context = MeetingContext(started_at=started_at, time_zone=time_zone)
    except ValidationError as error:
        raise RequestValidationError(error.errors()) from error
    suffix = Path(file.filename or "audio").suffix[:16]
    try:
        with TemporaryDirectory(prefix="hackalem-upload-") as directory:
            media_path = Path(directory) / f"input{suffix}"
            await asyncio.to_thread(_copy_upload, file.file, media_path)
            return await pipeline.analyze_file(media_path, context)
    finally:
        await file.close()


def _copy_upload(source: BinaryIO, destination: Path) -> None:
    source.seek(0)
    with destination.open("wb") as output:
        shutil.copyfileobj(source, output, length=1024 * 1024)


async def handle_service_error(request: Request, error: ServiceError) -> JSONResponse:
    status = {
        "LLM_NOT_CONFIGURED": 503,
        "LLM_MODEL_NOT_FOUND": 503,
        "LLM_UNAVAILABLE": 503,
        "LLM_TIMEOUT": 504,
        "LLM_REQUEST_REJECTED": 502,
        "LLM_INVALID_RESPONSE": 502,
        "LLM_INVALID_FACTS": 502,
        "TRANSCRIPT_TOO_LARGE": 413,
        "ANALYSIS_BUSY": 503,
        "SPEECH_NOT_CONFIGURED": 503,
        "ASR_UNAVAILABLE": 503,
        "DIARIZATION_NOT_CONFIGURED": 503,
        "DIARIZATION_UNAVAILABLE": 503,
        "ASR_FAILED": 502,
        "DIARIZATION_FAILED": 502,
        "MEDIA_NOT_FOUND": 404,
        "MEDIA_NOT_DECODABLE": 422,
        "NO_AUDIO_TRACK": 422,
        "SPEECH_EMPTY": 422,
        "DIARIZATION_EMPTY": 422,
    }.get(error.code, 500)
    return JSONResponse(
        status_code=status,
        content=ErrorResponse(code=error.code, message=error.message).model_dump(
            by_alias=True
        ),
        headers={"Retry-After": "5"} if error.code == "ANALYSIS_BUSY" else None,
    )
