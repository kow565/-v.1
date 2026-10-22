"""TypeSafe Jev advisory bridge for email triage and Binance Spot signal review.

Standalone Python 3.10+ module. No order execution and no side effects besides
an optional TypeSafe API request. Credentials never appear in requests' state.
"""

import json
import os
import re
import sys
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

API_URL = "https://api.typesafe.ai/v1/systemone"
DEFAULT_MODEL = "jev-1.13.0"  # pin until manually re-evaluated
# Conservative defaults for triage, NOT empirically calibrated thresholds.
EMAIL_MIN_CONFIDENCE = 0.80
TRADE_MIN_CONFIDENCE = 0.85
ALLOWED_INDICATORS = {
    "rsi", "macd", "macd_signal", "ema_fast", "ema_slow", "sma_20",
    "sma_50", "atr", "volatility_pct", "volume_ratio", "spread_bps",
    "momentum", "trend_strength", "stoch_k", "stoch_d", "adx",
    "bb_width", "bb_position",
}

SECRET_RE = re.compile(
    r"(?i)(?:\b(?:api[_ -]?key|secret|password|token|인증번호|인증코드|otp)\b\s*[:=]?\s*)"
    r"([\w+/.=@:-]{4,})"
)
OTP_MAIL_RE = re.compile(r"(?i)(?:인증번호|인증코드|verification code|one.time password|\botp\b)")


def _clean(text: Any, limit: int) -> str:
    value = str(text or "")[:limit]
    return SECRET_RE.sub(lambda m: m.group(0).replace(m.group(1), "[REDACTED]"), value)


def _confidence(answer: dict[str, Any]) -> float:
    conf = answer.get("confidence")
    try:
        val = float(conf)
    except (TypeError, ValueError):
        return 0.0
    return val if 0.0 <= val <= 1.0 else 0.0


def _number(value: Any, default: float = 0.5) -> float:
    try:
        val = float(value)
    except (TypeError, ValueError):
        return default
    return val if 0.0 <= val <= 1.0 else default


def _request_jev(state: dict, questions: dict, *,
                 transport: Callable[[dict], dict] | None = None) -> dict:
    body = {"model": os.getenv("JEV_MODEL", DEFAULT_MODEL),
            "state": state, "questions": questions}
    if transport is not None:
        return transport(body)
    api_key = os.getenv("TYPESAFE_API_KEY")
    if not api_key:
        raise RuntimeError("TYPESAFE_API_KEY not configured")
    req = Request(
        API_URL,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}",
                 "Content-Type": "application/json"},
        method="POST",
    )
    # No automatic retry, to avoid duplicate charges or hidden decisions.
    with urlopen(req, timeout=12) as resp:
        return json.load(resp)


def email_triage(message: dict, *, transport=None) -> dict:
    """Return classification only. Calling code owns notifications and calendar edits."""
    subject = _clean(message.get("subject"), 300)
    body = _clean(message.get("body"), 2400)
    kind = message.get("kind", "")
    # Approval and credential messages must never leave the existing workflow.
    if kind in {"approval", "trade_approval", "security_code"} or OTP_MAIL_RE.search(subject + " " + body):
        return {"status": "bypass", "reason": "sensitive_or_approval_mail",
                "route": "existing_pipeline", "automatic_action": False}
    state = {"subject": subject, "body_excerpt": body}
    questions = {
        "category": {"type": "choice", "instructions": "Classify the user's incoming email by its main purpose.",
                     "criteria": {
                         "school": "School course, assignment, lecture, exam or scholarship notices",
                         "security": "Account or security alert, excluding login codes",
                         "payment": "Invoices, receipts, spending and billing alerts",
                         "administration": "Administrative procedures, deadlines and applications",
                         "other": "Other personal messages or marketing"}},
        "requires_action": {"type": "noul", "instructions": "Does the message require the recipient to act rather than only read?"},
        "deadline": {"type": "noul", "instructions": "Does this message explicitly specify a deadline or due date?"},
    }
    try:
        response = _request_jev(state, questions, transport=transport)
        answer = response["answers"]["category"]
        category = answer["choice"]
        if category not in questions["category"]["criteria"]:
            raise ValueError("Unexpected Jev category")
        confidence = _confidence(answer)
        return {"status": "classified" if confidence >= EMAIL_MIN_CONFIDENCE else "review",
                "route": category if confidence >= EMAIL_MIN_CONFIDENCE else "existing_pipeline",
                "suggested_category": category, "confidence": confidence,
                "requires_action_probability": _number(response["answers"]["requires_action"].get("noul")),
                "has_deadline_probability": _number(response["answers"]["deadline"].get("noul")),
                "automatic_action": False,
                "model": response.get("model", DEFAULT_MODEL),
                "usage": response.get("usage", {})}
    except (HTTPError, URLError, RuntimeError, ValueError, KeyError, TypeError, OSError) as exc:
        return {"status": "unavailable", "reason": type(exc).__name__,
                "route": "existing_pipeline", "automatic_action": False}


def trade_review(signal: dict, *, transport=None) -> dict:
    """Shadow-only trade signal annotation. NEVER authorizes exchange orders."""
    # Strict whitelist: no exchange credentials, balances, customer data or email.
    raw_indicators = signal.get("indicators", {})
    indicators = {}
    if isinstance(raw_indicators, dict):
        for key, val in list(raw_indicators.items())[:25]:
            if (isinstance(key, str) and key in ALLOWED_INDICATORS
                    and isinstance(val, (int, float)) and not isinstance(val, bool)):
                indicators[key] = val
    state = {
        "symbol": _clean(signal.get("symbol"), 24),
        "side": _clean(signal.get("side"), 8),
        "timeframe": _clean(signal.get("timeframe"), 24),
        "signal_reason": _clean(signal.get("signal_reason"), 350),
        "indicators": indicators,
    }
    questions = {
        "signal_quality": {"type": "choice",
                           "instructions": "Based only on provided evidence, categorize this existing trading signal for further independent review; do not predict profitability.",
                           "criteria": {
                               "insufficient": "Insufficient or contradictory supporting market evidence",
                               "caution": "Evidence is mixed or signal is exposed to notable uncertainty",
                               "review_candidate": "Several internally consistent indicators justify human review, not an order"}},
        "signal_risk": {"type": "score", "instructions": "How uncertain or risky is the described signal evidence?",
                        "criteria": ["No clear warning within the supplied evidence",
                                     "Some gaps or market uncertainty",
                                     "Major contradictions or very unstable conditions",
                                     "Very unreliable or nearly no usable evidence"]},
    }
    try:
        response = _request_jev(state, questions, transport=transport)
        answer = response["answers"]["signal_quality"]
        selected = answer["choice"]
        if selected not in questions["signal_quality"]["criteria"]:
            raise ValueError("Unexpected Jev signal classification")
        confidence = _confidence(answer)
        raw_score = float(response["answers"]["signal_risk"]["score"])
        if not 0 <= raw_score <= 3:
            raise ValueError("Invalid risk score")
        return {"status": "shadow_review", "suggested_quality": selected,
                "confidence": confidence, "risk_score_0_to_3": raw_score,
                "is_confident_review_candidate": selected == "review_candidate" and confidence >= TRADE_MIN_CONFIDENCE,
                "approval_required": True, "order_authorized": False,
                "model": response.get("model", DEFAULT_MODEL), "usage": response.get("usage", {})}
    except (HTTPError, URLError, RuntimeError, ValueError, KeyError, TypeError, OSError) as exc:
        return {"status": "unavailable", "reason": type(exc).__name__,
                "approval_required": True, "order_authorized": False}


def main() -> int:
    if len(sys.argv) != 2 or sys.argv[1] not in {"email", "trade"}:
        print("Usage: python3 jev_bridge.py email|trade < input.json", file=sys.stderr)
        return 2
    try:
        record = json.load(sys.stdin)
        if not isinstance(record, dict):
            raise ValueError("Expected JSON object")
        result = email_triage(record) if sys.argv[1] == "email" else trade_review(record)
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":")))
        return 0 if result["status"] not in {"unavailable"} else 1
    except (ValueError, json.JSONDecodeError) as exc:
        print(json.dumps({"status": "invalid_input", "error": type(exc).__name__}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
