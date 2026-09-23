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


@lru_cache
def get_settings() -> Settings:
    return Settings()
