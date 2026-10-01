# Gameplay core interface

Load `core.js` before UI code. It exposes `globalThis.GameCore` and CommonJS `module.exports`. No network, persistence, DOM, API key, or Android bridge is touched.

## Calls

- `newCampaign({name, job, world, worldDescription, mode})`: new validated campaign. `job` is `검사`, `도적`, or `마법사`; `mode` is `demo` or `ai`. Description defaults to empty.
- `validateCampaign(state)`: validates and returns a detached JSON clone. Throws Korean `Error` on invalid input. Unknown fields, including nested metadata, are rejected.
- `applyTurn(state, action, proposal, roll)`: validates everything before applying to a clone; returns a new campaign. Never mutates input. Caller persists only after successful return.
- `demoTurn(state, action, roll)`: deterministic authored Korean offline proposal. Demo campaigns only.
- `buildRequest(state, action, model = 'gpt-6.1-sol', roll)`: Responses API request object. Pass `undefined` as model to use default. It uses `store:false`, low reasoning, 3500 output tokens, strict JSON schema, current state/summary, and 12 latest messages.
- `parseResponse(response)`: validates Responses API JSON and returns proposal, optionally with normalized usage. Accepts root `output_text` or `output[].content[].output_text`. Refusal, incomplete, API error, and malformed JSON throw.
- `exportSave(state)`: validated JSON string. No key or settings.
- `importSave(text)`: JSON parse, 2 MiB byte limit, full validation; returns detached campaign.

All actions are nonempty strings up to 2000 characters; all rolls are integer 1–20. Generate d20 once per attempted turn and preserve it on retry if desired. UI should display error without altering campaign, then permit retry. Render all state fields as plain text.

## Campaign

`{version:1,id,name,job,world,worldDescription,mode,level,hp,maxHp,mp,maxMp,xp,gold,inventory,location,quest,companions,summary,messages,rewardIds,turnCount,usage,lastChanges}`

`inventory` entries: `{id,name,qty}`. IDs use ASCII letters, digits, underscore and hyphen; reserved prototype names are rejected. Quantity 1–999; at most 100 entries. Names are display text. `companions` are up to 12 strings. `messages` are `{role:'user'|'assistant',text}`; at most 120. `usage` is `{inputTokens,outputTokens,totalTokens}` and accumulates safe integer usage for applied turns only. `lastChanges` are display strings for roll, stat, inventory, and level changes. The assistant message contains narration, d20, and suggested choices as text; choices are not separately persisted.

## Proposal

`{narration,choices,hpDelta,mpDelta,xpDelta,goldDelta,inventoryChanges,rewardId,location,quest,companions,summary}`. All properties required; strings may be empty except narration/choice/item names. `inventoryChanges` entries are `{id,name,delta}` with one entry per ID. Optional `usage` is injected by parser, never requested from model. `location`, `quest`, `companions`, `summary` replace current canon.

Proposal parsing requires safe integer deltas. At application HP/MP deltas must be within plus/minus their current maximum; XP must be 0–10,000 and gold -10,000–10,000 per turn (inventory -999 to 999). Item consumption greater than owned quantity throws atomically. HP/MP clamp to maxima; XP/gold clamp 0–1,000,000. XP is progress toward next level: requirement current level × 100; level up adds 10 max HP and 5 max MP, restores 5 MP, and stops at level 100. HP remains unchanged on level up. Inventory removes entries at zero; overconsumption rejects the entire turn. Positive XP/gold/items with an already-used nonempty reward ID are ignored; costs/damage still apply. Blank reward IDs are appropriate for repeatable costs or actions without rewards. Maximum 1000 unique reward IDs; reaching this limit throws atomically instead of forgetting anti-duplication records.

The app validates mechanical bounds, not story truth. A malicious GM can propose new reward IDs; this is a personal game, not a secure economy. Keep the API key entirely in the native encrypted settings. User-imported narration/world metadata remains untrusted plain text.

Narrative instructions adapt MIT-licensed ClaudioDrews/rpg-llm-adventure: Korean second person, 2–3 short sensory paragraphs, recurring named NPC continuity, visible consequences, three distinct suggested choices, and no imposed player decision. AI campaigns initialize in the chosen world instead of the demo village. Demo HP-zero scenes require rest/item recovery before further combat rewards.
