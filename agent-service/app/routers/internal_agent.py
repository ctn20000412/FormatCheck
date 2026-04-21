from fastapi import APIRouter, HTTPException

from app.models.contracts import CheckRequest, CheckResponse, RuleExtractRequest, RuleExtractResponse
from app.services.checker_agent import CheckerAgent
from app.services.extractor_agent import ExtractorAgent


router = APIRouter(prefix="/internal/agent", tags=["internal-agent"])

extractor_agent = ExtractorAgent()
checker_agent = CheckerAgent()


@router.post("/rules/extract", response_model=RuleExtractResponse)
async def extract_rules(request: RuleExtractRequest) -> RuleExtractResponse:
    try:
        return await extractor_agent.run(request)
    except Exception as exc:  # pragma: no cover - returned to integration caller
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post("/checks/execute", response_model=CheckResponse)
async def execute_check(request: CheckRequest) -> CheckResponse:
    try:
        return await checker_agent.run(request)
    except Exception as exc:  # pragma: no cover - returned to integration caller
        raise HTTPException(status_code=400, detail=str(exc)) from exc
