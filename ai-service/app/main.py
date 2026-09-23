from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from app.api import dev_router, handle_service_error, health_router
from app.config import get_settings
from app.intelligence.meeting_facts_extractor import MeetingFactsExtractor
from app.pipeline import MeetingPipeline
from app.providers.local_llm_provider import LocalLlmProvider
from app.service_error import ServiceError


@asynccontextmanager
async def lifespan(application: FastAPI):
    settings = application.state.settings
    async with httpx.AsyncClient(
        base_url=str(settings.local_llm_base_url).rstrip("/") + "/",
        timeout=httpx.Timeout(settings.local_llm_timeout_seconds, connect=5),
        follow_redirects=False,
        trust_env=False,
    ) as client:
        provider = LocalLlmProvider(client, settings)
        application.state.llm_provider = provider
        application.state.pipeline = MeetingPipeline(
            MeetingFactsExtractor(provider, settings.local_llm_max_input_chars)
        )
        yield


def create_app() -> FastAPI:
    settings = get_settings()
    application = FastAPI(
        title=settings.service_name,
        version=settings.service_version,
        lifespan=lifespan,
    )
    application.state.settings = settings
    application.add_exception_handler(ServiceError, handle_service_error)
    application.include_router(health_router)
    if settings.enable_dev_endpoints:
        application.include_router(dev_router)
    return application


app = create_app()
