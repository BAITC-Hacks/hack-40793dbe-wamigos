from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI

from app.api import dev_router, handle_service_error, health_router, internal_router
from app.audio_pipeline import AudioMeetingPipeline
from app.config import get_settings
from app.intelligence.meeting_facts_extractor import MeetingFactsExtractor
from app.intelligence.summary import SummaryGenerator
from app.intelligence.task_verifier import TaskVerifier
from app.pipeline import MeetingPipeline
from app.providers.local_llm_provider import LocalLlmProvider
from app.providers.structured_llm import StructuredLlm
from app.service_error import ServiceError
from app.speech.audio_preprocessor import AudioPreprocessor
from app.speech.diarization import SpeakerDiarizer
from app.speech.pipeline import SpeechPipeline
from app.speech.whisper_asr import WhisperAsr


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
        llm = StructuredLlm(provider)
        application.state.llm_provider = provider
        intelligence = MeetingPipeline(
            MeetingFactsExtractor(llm, settings.local_llm_max_input_chars),
            TaskVerifier(llm),
            SummaryGenerator(llm),
        )
        application.state.pipeline = intelligence
        application.state.audio_pipeline = AudioMeetingPipeline(
            SpeechPipeline(
                AudioPreprocessor(settings.ffmpeg_path, settings.ffprobe_path),
                WhisperAsr(
                    settings.asr_model,
                    settings.asr_device,
                    settings.asr_compute_type,
                ),
                SpeakerDiarizer(
                    settings.diarization_model,
                    settings.diarization_model_path or None,
                    settings.hf_token,
                    settings.diarization_device,
                ),
                settings.speech_release_models_after_run,
            ),
            intelligence,
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
    application.include_router(internal_router)
    if settings.enable_dev_endpoints:
        application.include_router(dev_router)
    return application


app = create_app()
