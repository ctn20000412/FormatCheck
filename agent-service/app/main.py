from fastapi import FastAPI

from app.routers.internal_agent import router as internal_agent_router


app = FastAPI(
    title="format-check-agent-service",
    version="0.1.0",
    description="Python agent service for rule extraction and document format checking.",
)

app.include_router(internal_agent_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
