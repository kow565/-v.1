# Jev bridge (KLASWatch email automation + CoinPilot signals)

This is a **standalone Python 3.10+ integration module** for the Z Flip5 Termux host, not an Android APK change. It uses the **official** TypeSafe API, not a third-party reseller.

## Setup

1. Obtain a key at https://console.typesafe.ai/. Keep it in the private Termux environment: `export TYPESAFE_API_KEY='...'` (or load from an appropriately permissioned secret store). **Do not add it to GitHub, an APK, or a public webhook.**
2. Install Python 3.10+; only Python standard library is needed.
3. Classify a notice: `printf '%s\n' '{"subject":"중간고사 안내","body":"내일 강의실 변경"}' | python3 jev_bridge.py email`
4. Evaluate a simulated coin signal: `printf '%s\n' '{"symbol":"BTCUSDT","side":"BUY","timeframe":"1h","indicators":{"rsi":42,"volume_ratio":1.1}}' | python3 jev_bridge.py trade`
5. Run tests from this directory: `PYTHONPATH=. python3 -m unittest discover -s tests -v`

## How to wire it in

- **KLASWatch** already emits notices to Gmail via Apps Script. Do **not** change the Android app. In the Termux/Gmail-processing worker, feed just a safe email subject/body excerpt as JSON to `email_triage` and use `route`, `requires_action_probability`, `has_deadline_probability` as *suggestions* for the existing ChatGPT/Calendar/Todoist/Slack pipeline. Keep the existing classifier as fallback for low confidence, credentials and provider failures. Let the existing LLM/regex extract dates—Jev does not do text generation.
- **CoinPilot**: call `trade_review(signal)` **after** strategy/rule-based market analysis and **before** composing the simulated-trade approval email. Use `suggested_quality`, `risk_score_0_to_3` and confidence for *logging and comparison only* initially. **Never** pass `order_authorized` through as a buy/sell instruction: it is hardcoded `false`. Existing Binance balance, exposure, max-loss checks, sender/thread/expiry verification and human reply `승인` must stay outside Jev. Test in Binance Spot Testnet and shadow mode before even considering integration into an approval candidate filter.

## Security & operational notes

- The bridge deliberately skips OTP/security-code and trading approval emails, redacts common secrets, truncates sent text, and whitelists trade signal feature fields. If the input has sensitive personal data beyond these patterns, pre-redact it upstream; the `state` is sent to a third party.
- Calls are HTTPS with a 12-second timeout and no retry. If the key is absent or the service fails, returns `unavailable`, with email routed to the existing pipeline and trading authorization always denied.
- Thresholds (0.80 email / 0.85 trade) are conservative *initial hypotheses*, **not calibrated performance evidence**. Benchmark on Korean email notices and historical coin signals before using them for anything consequential. Log model, probabilities and costs from `usage` without logging raw email bodies.
- This code provides integration interfaces; it does not deploy a worker, read Gmail itself, or access Binance. CoinPilot's actual repository is not currently connected; integrate its existing source with this module when available.
- Official API: https://docs.typesafe.ai/api/system-one ; pinned `jev-1.13.0` so results will not change unexpectedly with alias upgrades. Official list https://docs.typesafe.ai/models.
