from typing import Annotated

from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import JSONResponse

from app.pipeline import MeetingPipeline
from app.schemas import (
    AnalyzeTranscriptRequest,
    ErrorResponse,
    HealthResponse,
    MeetingAnalysisResult,
)
from app.service_error import ServiceError

health_router = APIRouter()
dev_router = APIRouter(prefix="/dev")


def get_pipeline(request: Request) -> MeetingPipeline:
    return request.app.state.pipeline


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
    }.get(error.code, 500)
    return JSONResponse(
        status_code=status,
        content=ErrorResponse(code=error.code, message=error.message).model_dump(
            by_alias=True
        ),
        headers={"Retry-After": "5"} if error.code == "ANALYSIS_BUSY" else None,
    )
