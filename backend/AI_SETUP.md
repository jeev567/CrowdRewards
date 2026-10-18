# AI Campaign Architect (v0.4)

## Setup
Set `OPENAI_API_KEY` in the backend process environment. Do not commit API keys or expose them to Vite. Optional: `OPENAI_MODEL` (default `gpt-4.1-mini`).

On macOS:
```bash
export OPENAI_API_KEY='your-key-here'
gradle bootRun
```

## Smoke test
1. Open React frontend and select **New campaign**.
2. Enter product name, USD price, optional audience and notes.
3. Click **Generate with AI**.
4. Confirm title, description, discount and group target populate.
5. Review/edit the values and click **Create campaign**.
6. Refresh the browser and confirm the campaign persisted.

## API
`POST /api/ai/campaign-suggestion` with JSON:
```json
{"product":"Coffee bundle","price":49.99,"audience":"Local coffee lovers","merchantNotes":"Small promotion"}
```
Returns `title`, `description`, `discount`, `goal`, `rationale`.
The endpoint requires a configured key and does not store suggestions until a merchant saves the campaign.

Note: AI outputs are suggestions, not financial guarantees. Review discounts before publishing. Authentication, rate limiting and spending caps are required before public deployment.
