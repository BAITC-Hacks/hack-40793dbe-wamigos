from functools import lru_cache

from pydantic import Field, HttpUrl
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = Field(default="HackAlem AI Service", alias="AI_SERVICE_NAME")
    service_version: str = Field(default="0.1.0", alias="AI_SERVICE_VERSION")
    enable_dev_endpoints: bool = Field(default=True, alias="ENABLE_DEV_ENDPOINTS")
    local_llm_base_url: HttpUrl = Field(
        default="http://localhost:11434/v1",
        alias="LOCAL_LLM_BASE_URL",
    )
    local_llm_model: str | None = Field(default=None, alias="LOCAL_LLM_MODEL")
    local_llm_timeout_seconds: float = Field(
        default=180, gt=0, alias="LOCAL_LLM_TIMEOUT_SECONDS"
    )
    local_llm_health_timeout_seconds: float = Field(
        default=3, gt=0, alias="LOCAL_LLM_HEALTH_TIMEOUT_SECONDS"
    )
    local_llm_max_tokens: int = Field(default=4096, gt=0, alias="LOCAL_LLM_MAX_TOKENS")
    local_llm_max_input_chars: int = Field(
        default=20000, gt=0, alias="LOCAL_LLM_MAX_INPUT_CHARS"
    )
    ffmpeg_path: str = Field(default="ffmpeg", alias="FFMPEG_PATH")
    ffprobe_path: str = Field(default="ffprobe", alias="FFPROBE_PATH")
    asr_model: str = Field(default="large-v3", alias="ASR_MODEL")
    asr_device: str = Field(default="cuda", alias="ASR_DEVICE")
    asr_compute_type: str = Field(default="float16", alias="ASR_COMPUTE_TYPE")
    diarization_model: str = Field(
        default="speechbrain/spkrec-ecapa-voxceleb",
        alias="DIARIZATION_MODEL",
    )
    diarization_model_path: str | None = Field(
        default=None, alias="DIARIZATION_MODEL_PATH"
    )
    diarization_device: str = Field(default="cuda", alias="DIARIZATION_DEVICE")
    diarization_similarity_threshold: float = Field(
        default=0.55,
        ge=-1,
        le=1,
        alias="DIARIZATION_SIMILARITY_THRESHOLD",
    )
    diarization_max_speakers: int = Field(
        default=8, ge=1, le=32, alias="DIARIZATION_MAX_SPEAKERS"
    )
    speech_release_models_after_run: bool = Field(
        default=True, alias="SPEECH_RELEASE_MODELS_AFTER_RUN"
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
